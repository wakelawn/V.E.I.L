package com.veil.ai;

import java.util.List;

// Body of POST /api/ai/chat for the copilot.
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
