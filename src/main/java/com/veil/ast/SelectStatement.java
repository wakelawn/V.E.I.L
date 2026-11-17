package com.veil.ast;

import java.util.List;

public class SelectStatement implements Statement {

	private final List<String> columns;
	private final TableReference table;
	private final Condition whereCondition;
	private final List<JoinClause> joins;
	private final List<OrderByItem> orderBy;
	private final Long limit;

	// kept for the simple grammar (no joins / order by / limit)
	public SelectStatement(List<String> columns, TableReference table,
			WhereCondition whereCondition) {

		this(columns, table, whereCondition, List.of(), List.of(), null);
	}

	public SelectStatement(List<String> columns, TableReference table,
			Condition whereCondition, List<JoinClause> joins,
			List<OrderByItem> orderBy, Long limit) {

		this.columns = columns;
		this.table = table;
		this.whereCondition = whereCondition;
		this.joins = joins;
		this.orderBy = orderBy;
		this.limit = limit;
	}

	public List<String> getColumns() {
		return columns;
	}

	public TableReference getTable() {
		return table;
	}

	public Condition getWhereCondition() {
		return whereCondition;
	}

	public List<JoinClause> getJoins() {
		return joins;
	}

	public List<OrderByItem> getOrderBy() {
		return orderBy;
	}

	public Long getLimit() {
		return limit;
	}

	@Override
	public String toString() {

		String result = "SELECT " + columns + " FROM " + table;

		for (JoinClause join : joins) {
			result += " " + join;
		}

		if (whereCondition != null) {
			result += " WHERE " + whereCondition;
		}

		if (!orderBy.isEmpty()) {
			result += " ORDER BY " + orderBy;
		}

		if (limit != null) {
			result += " LIMIT " + limit;
		}

		return result;
	}
}
