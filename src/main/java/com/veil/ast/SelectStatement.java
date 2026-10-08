package com.veil.ast;

import java.util.List;

public class SelectStatement implements Statement {

	private final List<String> columns;
	private final TableReference table;
	private final WhereCondition whereCondition;

	public SelectStatement(List<String> columns, TableReference table,
			WhereCondition whereCondition) {

		this.columns = columns;
		this.table = table;
		this.whereCondition = whereCondition;
	}

	public List<String> getColumns() {
		return columns;
	}

	public TableReference getTable() {
		return table;
	}

	public WhereCondition getWhereCondition() {
		return whereCondition;
	}

	@Override
	public String toString() {

		String result = "SELECT " + columns + " FROM " + table;

		if (whereCondition != null) {
			result += " WHERE " + whereCondition;
		}

		return result;
	}
}