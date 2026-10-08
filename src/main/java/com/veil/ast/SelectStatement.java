package com.veil.ast;

import java.util.List;

public class SelectStatement implements Statement {

	private final List<String> columns;
	private final TableReference table;

	public SelectStatement(List<String> columns, TableReference table) {

		this.columns = columns;
		this.table = table;
	}

	public List<String> getColumns() {
		return columns;
	}

	public TableReference getTable() {
		return table;
	}

	@Override
	public String toString() {
		return "SELECT " + columns + " FROM " + table;
	}
}