package com.veil.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final String jwtSecret;
    private final long jwtExpirationMs;

    public AuthController(
            @Value("${veil.auth.jwt-secret:dev-secret-change-in-production}") String jwtSecret,
            @Value("${veil.auth.jwt-expiration-ms:86400000}") long jwtExpirationMs) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        // TODO: Replace with real user validation against database
        // For now, accept demo credentials (will be removed in production)
        if ("demo@veil.app".equals(email) && "veil123".equals(password)) {
            String token = generateToken(email);
            return ResponseEntity.ok(Map.of("token", token, "email", email));
        }

        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }

        String token = authHeader.substring(7).trim();
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            // In production: verify signature
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (claims.getExpirationTime().before(Date.from(Instant.now()))) {
                return ResponseEntity.status(401).body(Map.of("valid", false, "error", "Token expired"));
            }

            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "email", claims.getSubject(),
                    "exp", claims.getExpirationTime().getTime()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("valid", false, "error", "Invalid token"));
        }
    }

    private String generateToken(String subject) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("veil")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusMillis(jwtExpirationMs)))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );
            signedJWT.sign(new MACSigner(jwtSecret.getBytes()));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }
}