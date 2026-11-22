package com.veil.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Function;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Function<String, RateLimitConfig.RateLimitBucket> bucketResolver;

    public RateLimitFilter(Function<String, RateLimitConfig.RateLimitBucket> bucketResolver) {
        this.bucketResolver = bucketResolver;
    }

    @Override
    protected void doFilterInternal(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = getClientIp(request);
        String key = clientIp + ":" + path;

        RateLimitConfig.RateLimitBucket bucket = bucketResolver.apply(key);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please slow down.\"}");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}