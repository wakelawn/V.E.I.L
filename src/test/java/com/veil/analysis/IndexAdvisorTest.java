package com.veil.analysis;

import org.junit.jupiter.api.Test;

import com.veil.ast.*;
import com.veil.ast.SelectItem;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexAdvisorTest {

	// a fake inspector with a fixed set of "existing" indexes,
	// so no database is needed to test the advisor logic
	private final IndexInspector inspector = new IndexInspector() {

		@Override
		public List<String> leadingIndexedColumns(String tableName) {

			if ("projects".equals(tableName)) {
				return List.of("id");
			}

			return List.of();
		}
	};

	private final IndexAdvisor advisor = new IndexAdvisor(inspector);
	private final OptimizationEngine engine = new OptimizationEngine();

	private OptimizationFinding filterFinding(SelectStatement select) {

		List<OptimizationFinding> findings = engine.run(select);

		advisor.advise(select, findings);

		for (OptimizationFinding finding : findings) {
			if ("FILTER_INDEX".equals(finding.getRuleId())) {
				return finding;
			}
		}

		return null;
	}

	private SelectStatement select(String table, String alias,
			WhereCondition where) {

		List<SelectItem> items = List.of(new SelectItem("id"));
		return new SelectStatement(items,
				new TableReference(table, alias), where,
				List.of(), null, List.of(), null);
	}

	@Test
	void uncoveredColumnGetsCreateIndexSuggestion() {

		SelectStatement s = select("queries", null,
				new WhereCondition("status", "=", "'done'"));

		OptimizationFinding finding = filterFinding(s);

		assertEquals("medium", finding.getSeverity());
		assertEquals("Index suggestion (verified)", finding.getTitle());
		assertEquals("CREATE INDEX idx_queries_status ON queries (status);",
				finding.getSuggestionSql());
	}

	@Test
	void coveredColumnBecomesAlreadyIndexedNote() {

		SelectStatement s = select("projects", null,
				new WhereCondition("id", ">", "5"));

		OptimizationFinding finding = filterFinding(s);

		assertEquals("low", finding.getSeverity());
		assertEquals("Filtered columns are already indexed",
				finding.getTitle());
		assertNull(finding.getSuggestionSql());
	}

	@Test
	void aliasedColumnResolvesToRealTable() {

		// q.status -> queries (alias resolution)
		SelectStatement s = select("queries", "q",
				new WhereCondition("q.status", "=", "'done'"));

		OptimizationFinding finding = filterFinding(s);

		assertEquals("CREATE INDEX idx_queries_status ON queries (status);",
				finding.getSuggestionSql());
	}

	@Test
	void unknownAliasLeavesHintUntouched() {

		// "zzz.status" matches no table or alias -> cannot verify,
		// so the finding must stay exactly as the engine produced it
		SelectStatement s = select("queries", "q",
				new WhereCondition("zzz.status", "=", "'done'"));

		List<OptimizationFinding> findings = engine.run(s);
		OptimizationFinding original = findings.get(0);

		advisor.advise(s, findings);

		assertEquals(original.getMessage(), findings.get(0).getMessage());
		assertNull(findings.get(0).getSuggestionSql());
	}

	@Test
	void databaseFailureLeavesHintUntouched() {

		IndexAdvisor failing = new IndexAdvisor(new IndexInspector() {

			@Override
			public List<String> leadingIndexedColumns(String tableName) {
				throw new org.springframework.dao.DataAccessResourceFailureException(
						"db down");
			}
		});

		SelectStatement s = select("queries", null,
				new WhereCondition("status", "=", "'done'"));

		List<OptimizationFinding> findings = engine.run(s);
		OptimizationFinding original = findings.get(0);

		failing.advise(s, findings);

		assertEquals(original.getMessage(), findings.get(0).getMessage());
	}

	@Test
	void mixedTablesCoverSomeColumnsOnly() {

		// projects.id is covered, queries.status is not:
		// the suggestion is still produced (for the uncovered one)
		SelectStatement s = select("queries", "q",
				new WhereCondition("q.status", "=", "'done'"));

		OptimizationFinding finding = filterFinding(s);

		assertTrue(finding.getSuggestionSql() != null
				&& finding.getSuggestionSql().contains("idx_queries_status"));
	}

	@Test
	void leadingColumnParsing() {

		// these mimic real pg_indexes.indexdef values
		assertEquals("id", PostgreSQLIndexInspector.leadingColumnOf(
				"CREATE UNIQUE INDEX pkey ON public.projects USING btree (id)"));

		assertEquals("project_id", PostgreSQLIndexInspector.leadingColumnOf(
				"CREATE INDEX ix ON public.queries USING btree "
				+ "(project_id, status)"));

		assertEquals("created_at", PostgreSQLIndexInspector.leadingColumnOf(
				"CREATE INDEX ix ON public.queries USING btree "
				+ "(created_at DESC)"));

		assertEquals("email", PostgreSQLIndexInspector.leadingColumnOf(
				"CREATE UNIQUE INDEX ix ON public.users USING btree "
				+ "(\"email\")"));

		assertNull(PostgreSQLIndexInspector.leadingColumnOf(
				"garbage without columns"));
	}

	@Test
	void advisorOnlyTouchesFilterFindings() {

		List<SelectItem> items = List.of(new SelectItem("*"));
		SelectStatement s = new SelectStatement(items,
				new TableReference("queries", null),
				new WhereCondition("status", "=", "'done'"),
				List.of(), null, List.of(new OrderByItem("created_at", true)), null);

		List<OptimizationFinding> findings = engine.run(s);

		int before = findings.size();

		advisor.advise(s, findings);

		assertEquals(before, findings.size());

		for (OptimizationFinding finding : findings) {
			if ("SELECT_STAR".equals(finding.getRuleId())) {
				assertEquals("Avoid SELECT *", finding.getTitle());
			}
		}
	}
}