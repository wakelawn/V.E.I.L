package com.veil.ai;

// Body of POST /api/ai/recommend. <p>{@code apiKey} is optional. When present it is used for this request only and never persisted, which lets the UI work without restarting the backend. The preferred setup is the NVIDIA_API_KEY environment variable.</p> <p>{@code model} is also optional: it lets the AI Model dialog switch models without a backend restart. A blank value uses the configured default.</p>
public record AiRecommendRequest(
        String sql,
        Object plan,
        Object schema,
        String apiKey,
        String model
) {
}
