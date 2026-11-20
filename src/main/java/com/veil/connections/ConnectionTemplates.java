package com.veil.connections;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Hands out the JdbcTemplate that database code should use right now: the default Spring datasource, or the activated saved connection. Injecting this instead of a bare JdbcTemplate is what makes "switch connection" work without changing any SQL.
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
