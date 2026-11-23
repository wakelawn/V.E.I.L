package com.veil.connections;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists databases on a PostgreSQL server so the Add Connection dialog can offer
 * a picker instead of making the user type a name.
 */
@Component
public class DatabaseDiscovery {

    private final ConnectionTarget target;

    public DatabaseDiscovery(ConnectionTarget target) {
        this.target = target;
    }

    public List<String> listDatabases(String host, int port,
            String username, String password) {

        target.validateHost(host);
        target.validatePort(port);

        SQLException lastFailure = null;

        // "postgres" is the usual maintenance database; template1 is the fallback.
        for (String maintenance : List.of("postgres", "template1")) {

            String url = target.jdbcUrl(host, port, maintenance);

            try (Connection connection = DriverManager.getConnection(
                    url, username, password == null ? "" : password);
                    Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            SELECT datname
                            FROM pg_database
                            WHERE datistemplate = false
                              AND datallowconn = true
                            ORDER BY datname
                            """)) {

                List<String> databases = new ArrayList<>();

                while (result.next()) {
                    databases.add(result.getString(1));
                }

                return databases;

            } catch (SQLException e) {
                lastFailure = e;
            }
        }

        throw new IllegalArgumentException(
                "Could not list databases on " + host + ":" + port
                        + " as " + username
                        + ". Check the host, port and credentials.",
                lastFailure);
    }
}
