package com.veil.explain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExplainService {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ExplainService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public PlanSummary explain(String sql) {

		if (sql == null || sql.isBlank()) {
			throw new IllegalArgumentException("SQL query cannot be empty.");
		}

		String query = sql.trim();

		// Remove one trailing semicolon
		if (query.endsWith(";")) {
			query = query.substring(0, query.length() - 1).trim();
		}

		String upper = query.toUpperCase();

		// Only SELECT / WITH for now
		if (!upper.startsWith("SELECT ") && !upper.startsWith("WITH ")) {

			throw new IllegalArgumentException(
					"Only SELECT or WITH queries are supported.");
		}

		// Prevent multiple statements
		if (query.contains(";")) {

			throw new IllegalArgumentException(
					"Multiple SQL statements are not allowed.");
		}

		String explainSql = "EXPLAIN (FORMAT JSON) " + query;

		String json = jdbcTemplate.queryForObject(explainSql,
				(resultSet, rowNum) -> resultSet.getString(1));

		try {

			JsonNode root = objectMapper.readTree(json);

			JsonNode plan = root.get(0).get("Plan");

			return extractPlan(plan);

		} catch (Exception e) {

			throw new RuntimeException(
					"Failed to parse PostgreSQL execution plan.", e);
		}
	}

	private PlanSummary extractPlan(JsonNode plan) {

		PlanSummary summary = new PlanSummary();

		summary.setNodeType(getText(plan, "Node Type", "Unknown"));

		summary.setRelation(getText(plan, "Relation Name", "—"));

		summary.setIndex(getText(plan, "Index Name", "—"));

		summary.setStartupCost(getDouble(plan, "Startup Cost"));

		summary.setTotalCost(getDouble(plan, "Total Cost"));

		summary.setEstimatedRows(getLong(plan, "Plan Rows"));

		return summary;
	}

	private String getText(JsonNode node, String field, String defaultValue) {

		JsonNode value = node.get(field);

		if (value == null || value.isNull()) {
			return defaultValue;
		}

		return value.asText();
	}

	private double getDouble(JsonNode node, String field) {

		JsonNode value = node.get(field);

		if (value == null || value.isNull()) {
			return 0.0;
		}

		return value.asDouble();
	}

	private long getLong(JsonNode node, String field) {

		JsonNode value = node.get(field);

		if (value == null || value.isNull()) {
			return 0;
		}

		return value.asLong();
	}
}