package com.veil.connections;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;

// tests whether the given credentials can actually reach a PostgreSQL
// database, by opening a short-lived JDBC connection and running SELECT 1.
// The connection is opened per test and closed right after - nothing
// is cached. The driver itself is found at runtime by DriverManager
@Component
public class ConnectionTester {

    private static final Pattern HOST_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+$");
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    public void test(String host, int port, String databaseName,
            String username, String password) {

        validateHost(host);
        validateDatabaseName(databaseName);
        validatePort(port);

        // connectTimeout=5 keeps a dead host from hanging the request
        String url = "jdbc:postgresql://" + host + ":" + port
                + "/" + databaseName + "?connectTimeout=5";

        try (Connection connection = DriverManager.getConnection(
                url, username, password)) {

            // the simplest possible sanity check: the database answers
            try (Statement statement = connection.createStatement()) {

                try (ResultSet result = statement.executeQuery("SELECT 1")) {
                    result.next();
                }
            }

        } catch (java.sql.SQLException e) {

            // convert to a 400-style error with a readable message,
            // because a failed connection test is the caller's input
            // problem, not a server problem
            throw new IllegalArgumentException(
                    "Could not connect to " + host + ":" + port + "/"
                            + databaseName + " as " + username
                            + ". Check the host, port, database and credentials.");
        }
    }

    private void validateHost(String host) {
        if (host == null || host.isBlank() || !HOST_PATTERN.matcher(host).matches()) {
            throw new IllegalArgumentException("Invalid host: must contain only letters, digits, dots, and hyphens");
        }
        if (host.length() > 255) {
            throw new IllegalArgumentException("Host too long (max 255 chars)");
        }
    }

    private void validateDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank() || !DB_NAME_PATTERN.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Invalid database name: must contain only letters, digits, and underscores");
        }
        if (databaseName.length() > 63) {
            throw new IllegalArgumentException("Database name too long (max 63 chars)");
        }
    }

    private void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }
}
