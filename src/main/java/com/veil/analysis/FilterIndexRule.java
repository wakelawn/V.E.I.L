package com.veil.analysis;

import com.veil.ast.SelectStatement;

import java.util.List;

// rule 3: columns used in WHERE/ON comparisons are candidates for an
// index IF they are filtered often. The rule itself only looks at the
// AST; IndexAdvisor later verifies the hint against the real database
// indexes and adds a CREATE INDEX suggestion where one is justified
public class FilterIndexRule implements OptimizationRule {

	@Override
	public void check(SelectStatement select, List<OptimizationFinding> findings) {

		List<String> filterColumns = FilterColumns.of(select);

		if (filterColumns.isEmpty()) {
			return;
		}

		findings.add(new OptimizationFinding(
				"FILTER_INDEX",
				"medium",
				"Consider an index on filtered columns",
				"WHERE condition on column(s) " + String.join(", ", filterColumns)
						+ ". If these columns are filtered frequently, an index "
						+ "may speed the query up.",
				null));
	}
}
