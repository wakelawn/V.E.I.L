package com.veil.ai;

// Body of POST /api/ai/recommend. Optional apiKey and model overrides.
public record AiRecommendRequest(
        String sql,
        Object plan,
        Object schema,
        String apiKey,
        String model
) {
}
