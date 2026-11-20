package com.veil.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

// NVIDIA NIM configuration bound from env vars.
@ConfigurationProperties(prefix = "veil.ai.nvidia")
public record NvidiaNimProperties(
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        int timeoutSeconds
) {

    public static final String DEFAULT_BASE_URL =
            "https://integrate.api.nvidia.com/v1";
    public static final String DEFAULT_MODEL =
            "nvidia/nemotron-3.5-lightning-30b-a3b";

    public NvidiaNimProperties {
        baseUrl = isBlank(baseUrl) ? DEFAULT_BASE_URL : stripTrailingSlash(baseUrl);
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = isBlank(model) ? DEFAULT_MODEL : model.trim();
        temperature = temperature > 0 ? temperature : 0.2;
        maxTokens = maxTokens > 0 ? maxTokens : 4096;
        timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
    }

// True when a backend API key is configured.
    public boolean configured() {
        return !apiKey.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }
}
