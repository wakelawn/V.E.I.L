package com.veil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(this::resolveBucket);
    }

    public RateLimitBucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> createBucket(k));
    }

    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    private RateLimitBucket createBucket(String key) {
        // Different limits for different endpoint types
        int maxTokens;
        long refillIntervalSeconds;

        if (key.startsWith("/api/auth/")) {
            // Stricter limit for auth endpoints: 5 requests per minute
            maxTokens = 5;
            refillIntervalSeconds = 60;
        } else if (key.startsWith("/api/connections")) {
            // Connection endpoints: 20 requests per minute
            maxTokens = 20;
            refillIntervalSeconds = 60;
        } else if (key.startsWith("/api/ai/")) {
            // AI endpoints: 30 requests per minute
            maxTokens = 30;
            refillIntervalSeconds = 60;
        } else {
            // Default: 60 requests per minute
            maxTokens = 60;
            refillIntervalSeconds = 60;
        }

        return new RateLimitBucket(maxTokens, Duration.ofSeconds(refillIntervalSeconds));
    }

    // Simple token bucket implementation
    public static class RateLimitBucket {
        private final int maxTokens;
        private final Duration refillInterval;
        private final AtomicInteger tokens;
        private volatile Instant lastRefill;

        public RateLimitBucket(int maxTokens, Duration refillInterval) {
            this.maxTokens = maxTokens;
            this.refillInterval = refillInterval;
            this.tokens = new AtomicInteger(maxTokens);
            this.lastRefill = Instant.now();
        }

        public synchronized boolean tryConsume(int tokensToConsume) {
            refill();
            int current = tokens.get();
            if (current >= tokensToConsume) {
                tokens.addAndGet(-tokensToConsume);
                return true;
            }
            return false;
        }

        private void refill() {
            Instant now = Instant.now();
            if (Duration.between(lastRefill, now).compareTo(refillInterval) >= 0) {
                tokens.set(maxTokens);
                lastRefill = now;
            }
        }
    }
}