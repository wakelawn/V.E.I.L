package com.veil.analysis;

import com.veil.ast.SelectStatement;

import java.util.List;

// rule 1: SELECT * asks the database for every column, including ones the
// caller may not need. That is extra data to read, send, and ignore
public class SelectStarRule implements OptimizationRule {

	@Override
	public void check(SelectStatement select, List<OptimizationFinding> findings) {

		if (!select.getColumns().contains("*")) {
			return;
		}

		findings.add(new OptimizationFinding(
				"SELECT_STAR",
				"high",
				"Avoid SELECT *",
				"SELECT * reads every column of '" + select.getTable().getTableName()
						+ "'. List only the columns you need to reduce the data read and transferred.",
				null));
	}
}
