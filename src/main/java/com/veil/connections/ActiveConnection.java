package com.veil.connections;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

// The connection VEIL is currently analysing against. The default Spring datasource (application.properties) is used until a saved connection is activated. Once activated, every schema/EXPLAIN/index lookup goes through a JdbcTemplate built from this connection instead. The password lives only in this in-memory object: it is never written to disk and is lost when the application restarts.
@Component
public class ActiveConnection {

	private volatile SavedConnection connection;
	private volatile JdbcTemplate jdbcTemplate;

	public synchronized void activate(SavedConnection saved) {

		DriverManagerDataSource dataSource = new DriverManagerDataSource();

		dataSource.setDriverClassName("org.postgresql.Driver");
		dataSource.setUrl("jdbc:postgresql://" + saved.getHost() + ":"
				+ saved.getPort() + "/" + saved.getDatabaseName()
				+ "?connectTimeout=5");
		dataSource.setUsername(saved.getUsername());
		dataSource.setPassword(saved.getPassword() == null
				? "" : saved.getPassword());

		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.connection = saved;
	}

	public synchronized void deactivate() {
		this.connection = null;
		this.jdbcTemplate = null;
	}

// The active connection, or null when the default datasource is in use.
	public SavedConnection get() {
		return connection;
	}

// A template for the active connection, or null for the default one.
	public JdbcTemplate jdbcTemplate() {
		return jdbcTemplate;
	}

	public boolean isActive() {
		return jdbcTemplate != null;
	}
}
