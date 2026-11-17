package com.veil.analysis;

import com.veil.ast.Condition;
import com.veil.ast.JoinClause;
import com.veil.ast.LogicalCondition;
import com.veil.ast.SelectStatement;
import com.veil.ast.WhereCondition;

import java.util.List;

// shared walk over a query's conditions that collects the columns used
// in comparisons. Used by the FilterIndexRule and by the IndexAdvisor,
// so both always agree on what "the filtered columns" are
public final class FilterColumns {

	private FilterColumns() {
	}

	public static List<String> of(SelectStatement select) {

		List<String> columns = new java.util.ArrayList<>();

		collect(select.getWhereCondition(), columns);

		for (JoinClause join : select.getJoins()) {
			collect(join.getCondition(), columns);
		}

		return columns;
	}

	// walks the condition tree: leaf comparisons add their column,
	// AND/OR nodes are recursed into. Duplicates are skipped
	private static void collect(Condition condition, List<String> into) {

		if (condition instanceof WhereCondition where) {

			if (!into.contains(where.getColumn())) {
				into.add(where.getColumn());
			}

		} else if (condition instanceof LogicalCondition logical) {

			collect(logical.getLeft(), into);
			collect(logical.getRight(), into);
		}
	}
}
