package com.veil.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Applies the per-IP API limit. Created by SecurityConfig so it runs once,
// inside the security chain, and never as a separate servlet filter.
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig limits;

    public RateLimitFilter(RateLimitConfig limits) {
        this.limits = limits;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Static files and page loads are not rate limited.
        if (!path.startsWith("/api/") || limits.allow(path, clientIp(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"error\":\"Rate limit exceeded. Please slow down.\"}");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");

        // The edge proxy appends the real client address last; earlier values
        // can be supplied by the caller, so only the last hop is trusted.
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }

        String realIp = request.getHeader("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
