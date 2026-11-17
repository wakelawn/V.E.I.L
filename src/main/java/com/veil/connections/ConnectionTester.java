package com.veil.connections;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// tests whether the given credentials can actually reach a PostgreSQL
// database, by opening a short-lived JDBC connection and running SELECT 1.
// The connection is opened per test and closed right after - nothing
// is cached. The driver itself is found at runtime by DriverManager
@Component
public class ConnectionTester {

	public void test(String host, int port, String databaseName,
			String username, String password) {

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
}
