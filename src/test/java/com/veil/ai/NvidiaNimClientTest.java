package com.veil.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NvidiaNimClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private int responseStatus;
    private String responseBody;
    private String lastAuthorization;
    private String lastRequestBody;

    @BeforeEach
    void startServer() throws IOException {
        responseStatus = 200;
        responseBody = "";
        lastAuthorization = null;
        lastRequestBody = null;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        lastRequestBody = new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private NvidiaNimClient client(String apiKey) {
        return new NvidiaNimClient(properties(apiKey), objectMapper);
    }

    private NvidiaNimProperties properties(String apiKey) {
        return new NvidiaNimProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                apiKey,
                "test/model",
                0.2,
                512,
                10
        );
    }

    private String completion(String content) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode choices = root.putArray("choices");
        choices.addObject().putObject("message").put("content", content);
        return objectMapper.writeValueAsString(root);
    }

    @Test
    void recommendParsesFencedJsonAndSendsTheExpectedRequest() throws Exception {
        String recommendation = """
                ```json
                {
                  "summary": "Sequential scan dominates the plan.",
                  "bottlenecks": ["Seq Scan on student"],
                  "findings": [
                    {
                      "title": "Index the filter column",
                      "message": "department_id is filtered without an index.",
                      "severity": "critical",
                      "suggestionSql": "CREATE INDEX idx_student_department ON student (department_id);"
                    }
                  ],
                  "estimatedImprovement": "~70% fewer rows read"
                }
                ```
                """.trim();
        responseBody = completion(recommendation);

        AiRecommendation result = client("backend-key").recommend(new AiRecommendRequest(
                "SELECT * FROM student WHERE department_id = 1;",
                Map.of("nodeType", "Seq Scan"),
                Map.of("tables", List.of(Map.of("table_name", "student"))),
                null,
                null
        ));

        assertThat(lastAuthorization).isEqualTo("Bearer backend-key");
        assertThat(lastRequestBody).contains("\"model\":\"test/model\"");
        assertThat(lastRequestBody).contains("SELECT * FROM student");
        assertThat(lastRequestBody).contains("\"stream\":false");

        assertThat(result.source()).isEqualTo("nvidia_nim");
        assertThat(result.provider()).isEqualTo("nvidia-nim");
        assertThat(result.model()).isEqualTo("test/model");
        assertThat(result.summary()).isEqualTo("Sequential scan dominates the plan.");
        assertThat(result.bottlenecks()).containsExactly("Seq Scan on student");
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).severity()).isEqualTo("high");
        assertThat(result.findings().get(0).suggestionSql())
                .isEqualTo("CREATE INDEX idx_student_department ON student (department_id);");
        assertThat(result.estimatedImprovement()).isEqualTo("~70% fewer rows read");
    }

    @Test
    void perRequestKeyCanReplaceTheBackendKey() throws Exception {
        responseBody = completion("{\"summary\":\"ok\",\"findings\":[]}");

        client("").recommend(new AiRecommendRequest(
                "SELECT 1;", null, null, "tab-key", null));

        assertThat(lastAuthorization).isEqualTo("Bearer tab-key");
    }

    @Test
    void perRequestModelCanReplaceTheBackendDefault() throws Exception {
        responseBody = completion("{\"summary\":\"ok\",\"findings\":[]}");

        AiRecommendation result = client("key").recommend(new AiRecommendRequest(
                "SELECT 1;", null, null, null, "nvidia/nemotron-3-ultra-550b-a55b"));

        assertThat(lastRequestBody).contains("\"model\":\"nvidia/nemotron-3-ultra-550b-a55b\"");
        assertThat(result.model()).isEqualTo("nvidia/nemotron-3-ultra-550b-a55b");
    }

    @Test
    void missingKeyFailsBeforeCallingNvidia() {
        assertThatThrownBy(() -> client("").recommend(new AiRecommendRequest(
                "SELECT 1;", null, null, null, null)))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("NVIDIA_API_KEY");

        assertThat(lastRequestBody).isNull();
    }

    @Test
    void upstreamErrorIsReportedWithStatusAndDetail() {
        responseStatus = 401;
        responseBody = "{\"error\":\"invalid credentials\"}";

        assertThatThrownBy(() -> client("wrong").recommend(new AiRecommendRequest(
                "SELECT 1;", null, null, null, null)))
                .isInstanceOf(AiUpstreamException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("invalid credentials")
                .hasMessageContaining("NVIDIA_API_KEY");
    }

    @Test
    void retiredModelExplainsHowToPickAnotherOne() {
        responseStatus = 410;
        responseBody = "{\"detail\":\"The model 'test/model' has reached its end of life.\"}";

        assertThatThrownBy(() -> client("key").recommend(new AiRecommendRequest(
                "SELECT 1;", null, null, null, null)))
                .isInstanceOf(AiUpstreamException.class)
                .hasMessageContaining("no longer serves the model 'test/model'")
                .hasMessageContaining("HTTP 410")
                .hasMessageContaining("NVIDIA_NIM_MODEL");
    }

    @Test
    void blankSqlIsRejectedBeforeAnyRequest() {
        assertThatThrownBy(() -> client("key").recommend(new AiRecommendRequest(
                "   ", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'sql' is required");

        assertThat(lastRequestBody).isNull();
    }

    @Test
    void proseResponseIsStillShownInsteadOfFailing() throws Exception {
        responseBody = completion("Add a partial index on status because the scan reads every row.");

        AiRecommendation result = client("key").recommend(new AiRecommendRequest(
                "SELECT * FROM queries WHERE status = 'done';", null, null, null, null));

        assertThat(result.summary()).contains("Add a partial index");
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).title()).isEqualTo("NVIDIA NIM analysis");
        assertThat(result.findings().get(0).severity()).isEqualTo("medium");
    }

    @Test
    void chatReturnsTheModelReplyAndMapsHistoryRoles() throws Exception {
        responseBody = completion("Use a composite index on (project_id, status).");

        String reply = client("key").chat(new AiChatRequest(
                "Why is this slow?",
                List.of(
                        new AiChatRequest.ChatMessage("user", "Earlier question"),
                        new AiChatRequest.ChatMessage("model", "Earlier answer")
                ),
                "SELECT * FROM queries;",
                null,
                null,
                null,
                null
        ));

        assertThat(reply).isEqualTo("Use a composite index on (project_id, status).");
        assertThat(lastRequestBody).contains("\"role\":\"assistant\"");
        assertThat(lastRequestBody).contains("Earlier answer");
        assertThat(lastRequestBody).contains("Why is this slow?");
    }
}
