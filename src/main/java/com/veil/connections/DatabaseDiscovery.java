package com.veil.connections;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Lists databases on a PostgreSQL server for Add Connection modal.
@Component
public class DatabaseDiscovery {

	public List<String> listDatabases(String host, int port,
			String username, String password) {

		SQLException lastFailure = null;

// postgres is the usual maintenance DB; template1 is the fallback
		for (String maintenance : List.of("postgres", "template1")) {

			String url = "jdbc:postgresql://" + host + ":" + port
					+ "/" + maintenance + "?connectTimeout=5";

			try (Connection connection = DriverManager.getConnection(
					url, username, password);
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
