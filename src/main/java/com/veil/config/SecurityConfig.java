package com.veil.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(
            JwtService jwtService,
            RateLimitConfig rateLimitConfig,
            @Value("${veil.cors.allowed-origins:http://localhost:5500,http://127.0.0.1:5500}")
            String allowedOrigins) {

        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);
        this.rateLimitFilter = new RateLimitFilter(rateLimitConfig);
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private final List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Pages and their assets must load before anyone can log in.
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers(
                        "/", "/index.html", "/login.html", "/favicon.ico",
                        "/Css/**", "/JS/**", "/assets/**",
                        "/*.png", "/*.jpg", "/*.jpeg", "/*.svg", "/*.webp", "/*.ico",
                        "/error",
                        "/api/health", "/api/auth/**", "/api/ai/status"
                ).permitAll()
                // Everything else needs a valid bearer token.
                .anyRequest().authenticated()
            )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                        writeJson(response, 401, "Authentication required"))
                .accessDeniedHandler((request, response, e) ->
                        writeJson(response, 403, "Access denied")))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                        + "script-src 'self' 'unsafe-inline'; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data: blob:; "
                        + "font-src 'self' data:; "
                        + "connect-src 'self' https://integrate.api.nvidia.com "
                        + "https://generativelanguage.googleapis.com https://api.openai.com "
                        + "https://api.anthropic.com; "
                        + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000)
                        .includeSubDomains(true)));

        return http.build();
    }

    private static void writeJson(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"" + message + "\"}");
        } catch (Exception ignored) {
            // The response is already committed; nothing else to do.
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
