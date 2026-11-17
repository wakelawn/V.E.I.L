package com.veil.analysis;

import com.veil.ast.SelectStatement;

import java.util.List;

// rule 2: no WHERE and no JOIN means every row of the table is read.
// That is fine for small tables, but worth surfacing for large ones
public class NoFilterRule implements OptimizationRule {

	@Override
	public void check(SelectStatement select, List<OptimizationFinding> findings) {

		if (select.getWhereCondition() != null || !select.getJoins().isEmpty()) {
			return;
		}

		findings.add(new OptimizationFinding(
				"NO_FILTER",
				"low",
				"Query reads the whole table",
				"No WHERE clause on '" + select.getTable().getTableName()
						+ "': the query reads every row. Add a filter or LIMIT "
						+ "if you only need a subset.",
				null));
	}
}
