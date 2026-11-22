package com.veil.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// In-memory token buckets, one per client IP and endpoint tier.
@Component
public class RateLimitConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean allow(String path, String clientIp) {
        // Bound memory if a lot of distinct clients appear.
        if (buckets.size() > 20_000) {
            buckets.clear();
        }

        String key = clientIp + "|" + tier(path);
        return buckets.computeIfAbsent(key, ignored -> new Bucket(limitFor(path))).tryConsume();
    }

    private static String tier(String path) {
        if (path.startsWith("/api/auth/")) {
            return "auth";
        }
        if (path.startsWith("/api/connections")) {
            return "connections";
        }
        if (path.startsWith("/api/ai/")) {
            return "ai";
        }
        return "default";
    }

    private static int limitFor(String path) {
        return switch (tier(path)) {
            case "auth" -> 10;        // login attempts per minute
            case "connections" -> 30; // connection CRUD per minute
            case "ai" -> 30;          // model calls per minute
            default -> 120;           // analyze/explain and everything else
        };
    }

    // Fixed-window token bucket; refills fully every 60 seconds.
    public static class Bucket {

        private final int capacity;
        private final AtomicInteger tokens;
        private volatile Instant windowStart;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
            this.windowStart = Instant.now();
        }

        synchronized boolean tryConsume() {
            if (Duration.between(windowStart, Instant.now()).toSeconds() >= 60) {
                tokens.set(capacity);
                windowStart = Instant.now();
            }
            return tokens.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0;
        }
    }
}
