package com.veil.config;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// Tokens must only be accepted when the signature, issuer and expiry are valid.
class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    @Test
    void issuedTokenVerifiesAndKeepsSubject() {
        JwtService service = new JwtService(SECRET, 60_000);

        JWTClaimsSet claims = service.verify(service.issue("analyst@veil.local"));

        assertNotNull(claims);
        assertEquals("analyst@veil.local", claims.getSubject());
        assertEquals("veil", claims.getIssuer());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = new JwtService(SECRET, 60_000);
        JwtService verifier = new JwtService(SECRET + "-different", 60_000);

        assertNull(verifier.verify(issuer.issue("attacker@veil.local")));
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000);
        String token = service.issue("analyst@veil.local");

        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];

        assertNull(service.verify(tampered));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1_000);

        assertNull(service.verify(service.issue("analyst@veil.local")));
    }

    @Test
    void garbageIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000);

        assertNull(service.verify("not-a-jwt"));
        assertNull(service.verify(""));
    }

    @Test
    void shortSecretIsRefused() {
        try {
            new JwtService("too-short", 60_000);
        } catch (IllegalStateException expected) {
            assertEquals("JWT_SECRET must be at least 32 characters long", expected.getMessage());
            return;
        }
        throw new AssertionError("A short JWT secret must be refused");
    }
}
