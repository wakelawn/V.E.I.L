package com.veil.connections;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Single place where connection details are validated and turned into a JDBC
 * URL. Keeping this in one class means the tester, the discovery endpoint and
 * the live connection all agree on what is acceptable input.
 */
@Component
public class ConnectionTarget {

    private static final Pattern HOST = Pattern.compile("^[a-zA-Z0-9.-]+$");
    private static final Pattern DATABASE = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern PRINTABLE = Pattern.compile("^[\\x20-\\x7E]+$");

    private final ConnectionSettings settings;

    public ConnectionTarget(ConnectionSettings settings) {
        this.settings = settings;
    }

    /** Validates everything a saved connection carries. */
    public void validate(String name, String host, int port, String databaseName, String username) {
        validateName(name);
        validateHost(host);
        validatePort(port);
        validateDatabaseName(databaseName);
        validateUsername(username);
    }

    public void validateHost(String host) {
        if (host == null || host.isBlank() || !HOST.matcher(host).matches()) {
            throw new IllegalArgumentException(
                    "Invalid host: use only letters, digits, dots and hyphens");
        }
        if (host.length() > 255) {
            throw new IllegalArgumentException("Host too long (max 255 characters)");
        }
    }

    public void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }

    public void validateDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank() || !DATABASE.matcher(databaseName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid database name: use only letters, digits, dots, hyphens and underscores");
        }
        if (databaseName.length() > 63) {
            throw new IllegalArgumentException("Database name too long (max 63 characters)");
        }
    }

    public void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("'username' is required");
        }
        if (username.length() > 63) {
            throw new IllegalArgumentException("Username too long (max 63 characters)");
        }
        if (!PRINTABLE.matcher(username).matches()) {
            throw new IllegalArgumentException("Username contains unsupported characters");
        }
    }

    public void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("'name' is required");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Connection name too long (max 100 characters)");
        }
        if (!PRINTABLE.matcher(name).matches()) {
            throw new IllegalArgumentException("Connection name contains unsupported characters");
        }
    }

    /**
     * JDBC URL with explicit timeouts and TLS mode. Host and database name are
     * validated first, so no user input can add its own URL parameters.
     */
    public String jdbcUrl(String host, int port, String databaseName) {
        validateHost(host);
        validatePort(port);
        validateDatabaseName(databaseName);

        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName
                + "?connectTimeout=" + settings.getConnectTimeoutSeconds() * 1000
                + "&socketTimeout=" + settings.getSocketTimeoutSeconds() * 1000
                + "&sslmode=" + settings.getSslMode()
                + "&ApplicationName=VEIL";
    }
}
