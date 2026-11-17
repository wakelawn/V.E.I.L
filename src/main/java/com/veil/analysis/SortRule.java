package com.veil.analysis;

import com.veil.ast.SelectStatement;

import java.util.List;

// rule 4: ORDER BY without LIMIT must sort the ENTIRE result before the
// first row can be returned. With a LIMIT only the top N rows are needed,
// which the database can sort much more cheaply
public class SortRule implements OptimizationRule {

	@Override
	public void check(SelectStatement select, List<OptimizationFinding> findings) {

		if (select.getOrderBy().isEmpty() || select.getLimit() != null) {
			return;
		}

		findings.add(new OptimizationFinding(
				"SORT_WITHOUT_LIMIT",
				"low",
				"ORDER BY without LIMIT sorts everything",
				"The query sorts all matching rows before returning any. "
						+ "If you only need the first few, add a LIMIT to make "
						+ "the sort cheaper.",
				null));
	}
}
