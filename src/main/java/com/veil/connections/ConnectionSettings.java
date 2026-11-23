package com.veil.connections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits and timeouts applied to every connection VEIL opens against a target
 * database. Values come from properties so Render (or a local run) can tune
 * them without a rebuild.
 */
@Component
public class ConnectionSettings {

    private final int connectTimeoutSeconds;
    private final int socketTimeoutSeconds;
    private final int poolSize;
    private final String sslMode;
    private final boolean readOnly;
    private final int maxSavedConnections;

    public ConnectionSettings(
            @Value("${veil.target.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${veil.target.socket-timeout-seconds:30}") int socketTimeoutSeconds,
            @Value("${veil.target.pool-size:4}") int poolSize,
            @Value("${veil.target.ssl-mode:prefer}") String sslMode,
            @Value("${veil.target.read-only:true}") boolean readOnly,
            @Value("${veil.connections.max-saved:100}") int maxSavedConnections) {

        this.connectTimeoutSeconds = positive("connect-timeout-seconds", connectTimeoutSeconds);
        this.socketTimeoutSeconds = positive("socket-timeout-seconds", socketTimeoutSeconds);
        this.poolSize = Math.min(positive("pool-size", poolSize), 32);
        this.sslMode = normaliseSslMode(sslMode);
        this.readOnly = readOnly;
        this.maxSavedConnections = positive("max-saved", maxSavedConnections);
    }

    // Small default used by unit tests and any code that builds one directly.
    public ConnectionSettings() {
        this(5, 30, 4, "prefer", true, 100);
    }

    private static int positive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException("veil.target." + name + " must be at least 1");
        }
        return value;
    }

    private static String normaliseSslMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase();
        return switch (value) {
            case "disable", "allow", "prefer", "require", "verify-ca", "verify-full" -> value;
            default -> throw new IllegalArgumentException(
                    "veil.target.ssl-mode must be disable, allow, prefer, require, verify-ca or verify-full");
        };
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int getSocketTimeoutSeconds() {
        return socketTimeoutSeconds;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public String getSslMode() {
        return sslMode;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public int getMaxSavedConnections() {
        return maxSavedConnections;
    }
}
