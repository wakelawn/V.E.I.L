package com.veil.config;

import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;

// Single-account login for VEIL. Credentials come from the environment:
// VEIL_AUTH_EMAIL plus VEIL_AUTH_PASSWORD_HASH (bcrypt) or VEIL_AUTH_PASSWORD.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtService jwtService;
    private final String configuredEmail;
    private final String passwordHash;
    private final String plainPassword;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(
            JwtService jwtService,
            @Value("${veil.auth.email:admin@veil.local}") String configuredEmail,
            @Value("${veil.auth.password-hash:}") String passwordHash,
            @Value("${veil.auth.password:}") String plainPassword) {

        this.jwtService = jwtService;
        this.configuredEmail = configuredEmail == null || configuredEmail.isBlank()
                ? "admin@veil.local"
                : configuredEmail.trim();
        this.passwordHash = passwordHash == null ? "" : passwordHash.trim();

        String plain = plainPassword == null ? "" : plainPassword;

        if (this.passwordHash.isEmpty() && plain.isEmpty()) {
            // Keep local runs usable without handing out a known password:
            // generate one and print it once in the server log.
            plain = randomPassword();
            log.warn("No VEIL_AUTH_PASSWORD or VEIL_AUTH_PASSWORD_HASH set. "
                    + "Generated password for {}: {}", this.configuredEmail, plain);
        }

        this.plainPassword = plain;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, String> request) {

        String email = request == null ? null : request.get("email");
        String password = request == null ? null : request.get("password");

        if (email == null || password == null
                || !constantTimeEquals(configuredEmail, email.trim())
                || !passwordMatches(password)) {

            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        return ResponseEntity.ok(Map.of(
                "token", jwtService.issue(configuredEmail),
                "email", configuredEmail,
                "expiresInMs", jwtService.getExpirationMs()
        ));
    }

    // Kept for older clients; verifies the signature instead of trusting claims.
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestHeader("Authorization") String authHeader) {

        JWTClaimsSet claims = claimsFrom(authHeader);

        if (claims == null) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "email", claims.getSubject(),
                "exp", claims.getExpirationTime().getTime()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader("Authorization") String authHeader) {

        JWTClaimsSet claims = claimsFrom(authHeader);

        if (claims == null) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "email", claims.getSubject()
        ));
    }

    private JWTClaimsSet claimsFrom(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return jwtService.verify(authHeader.substring(7).trim());
    }

    private boolean passwordMatches(String rawPassword) {
        if (!passwordHash.isEmpty()) {
            try {
                return encoder.matches(rawPassword, passwordHash);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return constantTimeEquals(plainPassword, rawPassword);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
