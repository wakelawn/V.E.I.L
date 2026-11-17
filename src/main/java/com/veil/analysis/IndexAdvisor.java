package com.veil.analysis;

import com.veil.ast.JoinClause;
import com.veil.ast.SelectStatement;
import com.veil.ast.TableReference;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Phase 8: index analysis. Takes the findings the engine produced from
// the AST alone and verifies the FILTER_INDEX hint against the real
// database indexes:
//
// - column covered by an index (leading column) -> finding becomes a low
//   "already indexed" note, no suggestion
// - column NOT covered          -> the finding gets an honest
//   "CREATE INDEX idx_<table>_<col> ON <table> (<col>);" suggestion
//
// If the database cannot be reached, the findings are left untouched -
// analysis must never fail because index verification failed
@Component
public class IndexAdvisor {

	private final IndexInspector inspector;

	public IndexAdvisor(IndexInspector inspector) {
		this.inspector = inspector;
	}

	public void advise(SelectStatement select,
			List<OptimizationFinding> findings) {

		for (OptimizationFinding finding : findings) {

			if (!"FILTER_INDEX".equals(finding.getRuleId())) {
				continue;
			}

			verifyFinding(select, finding, findings);
		}
	}

	private void verifyFinding(SelectStatement select,
			OptimizationFinding finding, List<OptimizationFinding> findings) {

		List<String> suggestions = new ArrayList<>();
		List<String> covered = new ArrayList<>();
		List<String> checked = new ArrayList<>();

		for (String column : FilterColumns.of(select)) {

			String table = resolveTable(column, select);

			if (table == null) {
				// unknown alias - we cannot check what we cannot name,
				// so we do not touch this column
				continue;
			}

			checked.add(column + " (" + table + ")");

			try {
				if (inspector.leadingIndexedColumns(table).stream()
						.anyMatch(c -> c.equalsIgnoreCase(bareName(column)))) {

					covered.add(column);
				} else {
					suggestions.add("CREATE INDEX idx_" + table + "_"
							+ bareName(column) + " ON " + table
							+ " (" + bareName(column) + ");");
				}

			} catch (DataAccessException e) {
				// database not reachable: leave the hint as it is
				return;
			}
		}

		if (checked.isEmpty()) {
			return;
		}

		if (covered.size() == checked.size()) {

			// every filtered column is already covered by an index:
			// the hint would be misleading, downgrade to a note
			findings.set(findings.indexOf(finding), new OptimizationFinding(
					"FILTER_INDEX",
					"low",
					"Filtered columns are already indexed",
					"Filtered column(s) " + String.join(", ", covered)
							+ " are the leading column of an existing index. "
							+ "No new index needed for this query.",
					null));

		} else if (!suggestions.isEmpty()) {

			// at least one uncovered column: attach the verified suggestion
			findings.set(findings.indexOf(finding), new OptimizationFinding(
					"FILTER_INDEX",
					"medium",
					"Index suggestion (verified)",
					"Filtered column(s) " + String.join(", ", checked)
							+ ". No existing index leads with the uncovered "
							+ "column(s) " + String.join(", ", uncovered(checked, covered))
							+ ". Suggested index:",
					String.join("\n", suggestions)));
		}
	}

	private List<String> uncovered(List<String> checked,
			List<String> covered) {

		List<String> out = new ArrayList<>(checked);

		for (String column : covered) {
			out.removeIf(c -> c.startsWith(column + " "));
		}

		return out;
	}

	// "q.status" -> "status", plain "status" stays "status"
	private String bareName(String column) {

		int dot = column.indexOf('.');

		return dot >= 0 ? column.substring(dot + 1) : column;
	}

	// resolves a (possibly aliased) filter column to its real table name.
	// "status"        -> main table
	// "q.status"      -> main table if q is its alias, else a joined table
	// unknown alias   -> null (caller leaves the finding untouched)
	private String resolveTable(String column, SelectStatement select) {

		if (!column.contains(".")) {
			return select.getTable().getTableName();
		}

		String qualifier = column.substring(0, column.indexOf('.'));

		TableReference main = select.getTable();

		// matches the alias ("q") or the table name itself ("queries")
		if (qualifier.equals(main.getAlias())
				|| qualifier.equals(main.getTableName())) {
			return main.getTableName();
		}

		for (JoinClause join : select.getJoins()) {

			TableReference ref = join.getTable();

			if (qualifier.equals(ref.getAlias())
					|| qualifier.equals(ref.getTableName())) {
				return ref.getTableName();
			}
		}

		return null;
	}
}
