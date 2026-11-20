package com.veil.connections;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

// The connection VEIL is analysing against; password lives in memory only.
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

// Active connection or null for default.
	public SavedConnection get() {
		return connection;
	}

// JdbcTemplate for active connection or null.
	public JdbcTemplate jdbcTemplate() {
		return jdbcTemplate;
	}

	public boolean isActive() {
		return jdbcTemplate != null;
	}
}
