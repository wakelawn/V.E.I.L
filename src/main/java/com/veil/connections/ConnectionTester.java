package com.veil.connections;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Tests whether the given credentials can reach a PostgreSQL database by
 * opening a short-lived connection and running {@code SELECT 1}. Nothing is
 * cached; the driver is discovered by {@link DriverManager} at runtime.
 */
@Component
public class ConnectionTester {

    private final ConnectionTarget target;

    public ConnectionTester(ConnectionTarget target) {
        this.target = target;
    }

    public void test(String host, int port, String databaseName,
            String username, String password) {

        String url = target.jdbcUrl(host, port, databaseName);

        try (Connection connection = DriverManager.getConnection(
                url, username, password == null ? "" : password)) {

            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT 1")) {
                result.next();
            }

        } catch (java.sql.SQLException e) {

            // A failed test is an input problem, not a server error. The
            // driver message can contain the host, but never the password.
            throw new IllegalArgumentException(
                    "Could not connect to " + host + ":" + port + "/"
                            + databaseName + " as " + username
                            + ". Check the host, port, database and credentials.");
        }
    }
}
