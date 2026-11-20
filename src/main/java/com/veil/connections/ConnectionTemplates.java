package com.veil.connections;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// JdbcTemplate for default datasource or active saved connection.
@Component
public class ConnectionTemplates {

	private final JdbcTemplate defaultTemplate;
	private final ActiveConnection active;

	public ConnectionTemplates(JdbcTemplate defaultTemplate,
			ActiveConnection active) {

		this.defaultTemplate = defaultTemplate;
		this.active = active;
	}

	public JdbcTemplate current() {

		JdbcTemplate activeTemplate = active.jdbcTemplate();

		return activeTemplate != null ? activeTemplate : defaultTemplate;
	}
}
