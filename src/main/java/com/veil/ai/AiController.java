package com.veil.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final NvidiaNimClient client;

    public AiController(NvidiaNimClient client) {
        this.client = client;
    }

// Tells the UI whether the backend already has an NVIDIA NIM key.
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "configured", client.isConfigured(),
                "provider", "nvidia-nim",
                "model", client.model(),
                "baseUrl", client.baseUrl()
        );
    }

    @PostMapping("/recommend")
    public AiRecommendation recommend(@RequestBody AiRecommendRequest request) {
        return client.recommend(request);
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody AiChatRequest request) {
        return Map.of("reply", client.chat(request));
    }
}
