package com.veil.ast;

// one entry of ORDER BY: <column> [ASC|DESC]
public class OrderByItem {

	private final String column;
	private final boolean descending;

	public OrderByItem(String column, boolean descending) {

		this.column = column;
		this.descending = descending;
	}

	public String getColumn() {
		return column;
	}

	public boolean isDescending() {
		return descending;
	}

	@Override
	public String toString() {
		return column + (descending ? " DESC" : " ASC");
	}
}
