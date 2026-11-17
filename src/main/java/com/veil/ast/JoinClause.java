package com.veil.ast;

// one JOIN clause: JOIN <table> [alias] ON <condition>
public class JoinClause {

	private final TableReference table;
	private final Condition condition;

	public JoinClause(TableReference table, Condition condition) {

		this.table = table;
		this.condition = condition;
	}

	public TableReference getTable() {
		return table;
	}

	public Condition getCondition() {
		return condition;
	}

	@Override
	public String toString() {
		return "JOIN " + table + " ON " + condition;
	}
}
