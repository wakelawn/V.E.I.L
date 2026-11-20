package com.veil.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Talks to NVIDIA NIM OpenAI-compatible API. Key never leaves backend.
@Component
public class NvidiaNimClient {

    private static final int MAX_FINDINGS = 8;
    private static final int MAX_PLAN_CHARS = 50_000;
    private static final int MAX_SCHEMA_CHARS = 50_000;

    private static final String RECOMMEND_SYSTEM_PROMPT = """
            You are VEIL AI, an expert PostgreSQL database administrator and
            query performance engineer.

            Analyse the supplied SQL query, PostgreSQL EXPLAIN plan, and
            schema. Return ONLY one valid JSON object, with no markdown and
            no code fences, using exactly this shape:

            {
              "summary": "Short executive summary of the plan and the main bottleneck.",
              "bottlenecks": ["Bottleneck 1", "Bottleneck 2"],
              "findings": [
                {
                  "title": "Short recommendation title",
                  "message": "Why the bottleneck happens and how to fix it.",
                  "severity": "high",
                  "suggestionSql": "Runnable PostgreSQL DDL, or an empty string."
                }
              ],
              "estimatedImprovement": "Qualitative or quantitative estimate, or an empty string."
            }

            Rules:
            - Base every claim on the supplied plan and schema.
            - Never invent tables, columns, indexes, row counts, or costs.
            - If the plan does not prove a number, describe the effect qualitatively.
            - suggestionSql must be valid PostgreSQL or an empty string.
            - Use severity "high", "medium", or "low".
            - Return at most 6 findings, ordered by impact.
            """;

    private static final String CHAT_SYSTEM_PROMPT = """
            You are VEIL AI, an elite PostgreSQL query optimisation copilot.
            You have the user's active SQL, its EXPLAIN plan, and the database
            schema in the conversation context.

            Answer with actionable, technically precise advice. Use Markdown,
            short bullet points, and fenced SQL code blocks where useful.
            Never invent tables, columns, indexes, or costs that are not in the
            supplied context. If the context is insufficient, say what is
            missing instead of guessing.
            """;

    private final NvidiaNimProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NvidiaNimClient(NvidiaNimProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    public String model() {
        return properties.model();
    }

    public String baseUrl() {
        return properties.baseUrl();
    }

// Structured recommendations for AI page.
    public AiRecommendation recommend(AiRecommendRequest request) {
        String sql = request.sql() == null ? "" : request.sql().trim();
        if (sql.isBlank()) {
            throw new IllegalArgumentException("'sql' is required");
        }

        String userPrompt = """
                SQL QUERY:
                %s

                EXECUTION PLAN (JSON):
                %s

                SCHEMA AND INDEXES (JSON):
                %s
                """.formatted(
                sql,
                cappedJson(request.plan(), MAX_PLAN_CHARS),
                cappedJson(request.schema(), MAX_SCHEMA_CHARS)
        );

        String model = effectiveModel(request.model());
        String content = complete(RECOMMEND_SYSTEM_PROMPT, userPrompt, request.apiKey(), model);
        return parseRecommendation(content, model);
    }

// Conversational copilot answer using same context.
    public String chat(AiChatRequest request) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("'message' is required");
        }

        String context = """
                ACTIVE SQL:
                %s

                EXECUTION PLAN (JSON):
                %s

                SCHEMA AND INDEXES (JSON):
                %s
                """.formatted(
                request.sql() == null || request.sql().isBlank() ? "No query loaded." : request.sql().trim(),
                cappedJson(request.plan(), MAX_PLAN_CHARS),
                cappedJson(request.schema(), MAX_SCHEMA_CHARS)
        );

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", CHAT_SYSTEM_PROMPT));
        messages.add(Map.of("role", "system", "content", context));

        if (request.history() != null) {
            int start = Math.max(0, request.history().size() - 12);
            for (AiChatRequest.ChatMessage item : request.history().subList(start, request.history().size())) {
                if (item == null || item.text() == null || item.text().isBlank()) {
                    continue;
                }
                String role = "user".equalsIgnoreCase(item.role()) ? "user" : "assistant";
                messages.add(Map.of("role", role, "content", item.text()));
            }
        }

        messages.add(Map.of("role", "user", "content", message));

        return complete(messages, request.apiKey(), effectiveModel(request.model()));
    }

    private String complete(String systemPrompt, String userPrompt, String keyOverride, String model) {
        return complete(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ), keyOverride, model);
    }

    private String complete(List<Map<String, String>> messages, String keyOverride, String model) {
        String apiKey = effectiveKey(keyOverride);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", properties.temperature());
        payload.put("top_p", 0.7);
        payload.put("max_tokens", properties.maxTokens());
        payload.put("stream", false);

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new AiUpstreamException("Could not serialize the NVIDIA NIM request.", e);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUpstreamException("The NVIDIA NIM request was interrupted.", e);
        } catch (IOException e) {
            String reason = e.getMessage();
            if (reason == null || reason.isBlank()) {
                reason = e.getClass().getSimpleName();
            }
            throw new AiUpstreamException(
                    "Could not reach NVIDIA NIM at " + properties.baseUrl() + ": " + reason, e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiUpstreamException(upstreamMessage(response.statusCode(), response.body(), model));
        }

        return extractContent(response.body());
    }

    private String upstreamMessage(int status, String body, String model) {
        String detail = detailSuffix(body);

        if (status == 401 || status == 403) {
            return "NVIDIA NIM rejected the API key (HTTP " + status + "). "
                    + "Check NVIDIA_API_KEY, or paste a valid key in the AI Model dialog."
                    + detail;
        }

        if (status == 404 || status == 410) {
            return "NVIDIA NIM no longer serves the model '" + model
                    + "' (HTTP " + status + "). Set NVIDIA_NIM_MODEL to a current model; "
                    + "the list is at " + properties.baseUrl() + "/models."
                    + detail;
        }

        return "NVIDIA NIM returned HTTP " + status + detail;
    }

    private String extractContent(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new AiUpstreamException("NVIDIA NIM returned a response that was not JSON.", e);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiUpstreamException("NVIDIA NIM returned no choices.");
        }

        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText("");
        if (content.isBlank()) {
// Reasoning models may put answer in reasoning_content.
            content = message.path("reasoning_content").asText("");
        }
        if (content.isBlank()) {
            throw new AiUpstreamException("NVIDIA NIM returned an empty message.");
        }
        return content;
    }

    private AiRecommendation parseRecommendation(String content, String model) {
        JsonNode root = readJsonObject(content);

        if (root == null) {
// Model ignored JSON; use prose as fallback.
            String prose = content.trim();
            return new AiRecommendation(
                    "nvidia_nim",
                    "nvidia-nim",
                    model,
                    prose,
                    List.of(),
                    List.of(new AiRecommendation.Finding(
                            "NVIDIA NIM analysis",
                            prose,
                            "medium",
                            ""
                    )),
                    ""
            );
        }

        return new AiRecommendation(
                "nvidia_nim",
                "nvidia-nim",
                model,
                text(root, "summary", "NVIDIA NIM analysed the query and plan."),
                stringList(root.path("bottlenecks")),
                findings(root.path("findings")),
                firstText(root, "estimatedImprovement", "estimated_improvement", "improvement")
        );
    }

    private JsonNode readJsonObject(String content) {
        String cleaned = stripCodeFences(content);
        JsonNode parsed = tryParse(cleaned);
        if (parsed != null) {
            return parsed;
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            parsed = tryParse(cleaned.substring(start, end + 1));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private JsonNode tryParse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            return node != null && node.isObject() ? node : null;
        } catch (IOException e) {
            return null;
        }
    }

    private List<AiRecommendation.Finding> findings(JsonNode array) {
        List<AiRecommendation.Finding> findings = new ArrayList<>();
        if (!array.isArray()) {
            return findings;
        }

        for (JsonNode node : array) {
            String message = text(node, "message", "");
            String suggestionSql = firstText(node, "suggestionSql", "suggestion_sql", "sql");
            if (message.isBlank() && suggestionSql.isBlank()) {
                continue;
            }

            findings.add(new AiRecommendation.Finding(
                    text(node, "title", "Recommendation"),
                    message,
                    severity(text(node, "severity", "medium")),
                    suggestionSql
            ));

            if (findings.size() == MAX_FINDINGS) {
                break;
            }
        }
        return findings;
    }

    private List<String> stringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (!array.isArray()) {
            return values;
        }

        for (JsonNode node : array) {
            String value = node.isTextual() ? node.asText() : text(node, "title", "");
            if (!value.isBlank()) {
                values.add(value);
            }
            if (values.size() == MAX_FINDINGS) {
                break;
            }
        }
        return values;
    }

    private String cappedJson(Object value, int maxChars) {
        if (value == null) {
            return "null";
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            json = String.valueOf(value);
        }

        if (json.length() <= maxChars) {
            return json;
        }
        return json.substring(0, maxChars) + "\n... (truncated)";
    }

    private String effectiveModel(String override) {
        String model = override == null ? "" : override.trim();
        return model.isBlank() ? properties.model() : model;
    }

    private String effectiveKey(String override) {
        String key = override == null ? "" : override.trim();
        if (key.isBlank()) {
            key = properties.apiKey();
        }
        if (key.isBlank()) {
            throw new AiUnavailableException(
                    "NVIDIA NIM is not configured. Set NVIDIA_API_KEY on the backend "
                            + "or paste a key in the AI Model settings.");
        }
        return key;
    }

    private static String stripCodeFences(String content) {
        String clean = content == null ? "" : content.trim();
        if (clean.startsWith("```")) {
            clean = clean.replaceFirst("^```[a-zA-Z]*\\s*", "");
            clean = clean.replaceFirst("\\s*```$", "");
        }
        return clean.trim();
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isTextual()) {
            String text = value.asText().trim();
            return text.isEmpty() ? fallback : text;
        }
        return fallback;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String severity(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase()) {
            case "high", "critical" -> "high";
            case "low", "info" -> "low";
            default -> "medium";
        };
    }

    private static String detailSuffix(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String detail = body.trim();
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "…";
        }
        return ": " + detail;
    }
}
