package com.veil.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

// Issues and verifies the HS256 tokens used by the API. A token is only
// trusted when its signature, issuer, expiry and subject all check out.
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ISSUER = "veil";

    private final byte[] secret;
    private final long expirationMs;

    public JwtService(
            @Value("${veil.auth.jwt-secret:}") String configuredSecret,
            @Value("${veil.auth.jwt-expiration-ms:86400000}") long expirationMs) {

        this.expirationMs = expirationMs;

        String value = configuredSecret == null ? "" : configuredSecret.trim();

        if (value.isEmpty()) {
            // No configured secret -> random per-process key. Tokens stop
            // working after a restart, but nobody can forge one.
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("JWT_SECRET is not set. Using a random key for this process; "
                    + "set JWT_SECRET (32+ chars) so logins survive restarts.");
        } else {
            if (value.length() < 32) {
                throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
            }
            this.secret = value.getBytes(StandardCharsets.UTF_8);
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String issue(String subject) {
        try {
            Instant now = Instant.now();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusMillis(expirationMs)))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();

        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    // Returns the verified claims, or null when the token is invalid,
    // tampered with, expired, or issued by someone else.
    public JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            if (!jwt.verify(new MACVerifier(secret))) {
                return null;
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();

            if (!ISSUER.equals(claims.getIssuer())
                    || expiration == null
                    || expiration.before(new Date())
                    || claims.getSubject() == null
                    || claims.getSubject().isBlank()) {
                return null;
            }

            return claims;

        } catch (Exception e) {
            return null;
        }
    }
}
