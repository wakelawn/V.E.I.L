package com.veil.service;

import java.util.List;

public class AnalyzeResponse {

	private final List<String> tokens;
	private final String ast;
	private final String table;
	private final List<String> columns;
	private final String whereCondition;
	private final List<String> recommendations;

	public AnalyzeResponse(List<String> tokens, String ast, String table,
			List<String> columns, String whereCondition,
			List<String> recommendations) {

		this.tokens = tokens;
		this.ast = ast;
		this.table = table;
		this.columns = columns;
		this.whereCondition = whereCondition;
		this.recommendations = recommendations;
	}

	public List<String> getTokens() {
		return tokens;
	}

	public String getAst() {
		return ast;
	}

	public String getTable() {
		return table;
	}

	public List<String> getColumns() {
		return columns;
	}

	public String getWhereCondition() {
		return whereCondition;
	}

	public List<String> getRecommendations() {
		return recommendations;
	}
}