package com.veil.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DatabaseService {

	private final JdbcTemplate jdbcTemplate;

	public DatabaseService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public String getDatabaseName() {

		return jdbcTemplate.queryForObject("SELECT current_database()",
				String.class);
	}

	public List<Map<String, Object>> getTables() {

		return jdbcTemplate.queryForList("""
				SELECT table_name
				FROM information_schema.tables
				WHERE table_schema = 'public'
				ORDER BY table_name
				""");
	}

	public List<Map<String, Object>> getIndexes() {

		return jdbcTemplate.queryForList("""
				SELECT
				    tablename,
				    indexname,
				    indexdef
				FROM pg_indexes
				WHERE schemaname = 'public'
				ORDER BY tablename, indexname
				""");
	}
}