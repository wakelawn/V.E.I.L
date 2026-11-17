package com.veil.analysis;

import com.veil.ast.Condition;
import com.veil.ast.JoinClause;
import com.veil.ast.LogicalCondition;
import com.veil.ast.SelectStatement;
import com.veil.ast.WhereCondition;

import java.util.ArrayList;
import java.util.List;

// rule 3: columns used in WHERE/ON comparisons are candidates for an
// index IF they are filtered often. We cannot check real indexes here
// (that is Phase 8's job), so the advice stays a "consider", never a
// concrete CREATE INDEX
public class FilterIndexRule implements OptimizationRule {

	@Override
	public void check(SelectStatement select, List<OptimizationFinding> findings) {

		List<String> filterColumns = new ArrayList<>();

		collectColumns(select.getWhereCondition(), filterColumns);

		for (JoinClause join : select.getJoins()) {
			collectColumns(join.getCondition(), filterColumns);
		}

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

	// walks the condition tree and collects the column names used in
	// comparisons (leaves). AND/OR nodes are recursed into.
	private void collectColumns(Condition condition, List<String> into) {

		if (condition instanceof WhereCondition where) {

			if (!into.contains(where.getColumn())) {
				into.add(where.getColumn());
			}

		} else if (condition instanceof LogicalCondition logical) {

			collectColumns(logical.getLeft(), into);
			collectColumns(logical.getRight(), into);
		}
	}
}
