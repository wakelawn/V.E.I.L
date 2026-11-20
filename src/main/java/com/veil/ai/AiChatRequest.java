package com.veil.ai;

import java.util.List;

// Body of POST /api/ai/chat. Context mirrors the recommendation request so the copilot answers using the same SQL, plan, schema, key, and model.
public record AiChatRequest(
        String message,
        List<ChatMessage> history,
        String sql,
        Object plan,
        Object schema,
        String apiKey,
        String model
) {

    public record ChatMessage(String role, String text) {
    }
}
