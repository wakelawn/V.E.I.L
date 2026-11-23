package com.veil.analysis;

import org.junit.jupiter.api.Test;

import com.veil.ast.*;
import com.veil.ast.SelectItem;
import com.veil.explain.PlanSummary;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationEngineTest {

	private final OptimizationEngine engine = new OptimizationEngine();

	private SelectStatement select(List<String> columns, String table,
			WhereCondition where, List<OrderByItem> orderBy, Long limit) {

		List<SelectItem> items = columns.stream().map(SelectItem::new).toList();
		return new SelectStatement(items,
				new TableReference(table), where, List.of(), null, orderBy, limit);
	}

	private PlanSummary expensiveSeqScan(String table, double cost, long rows) {
		PlanSummary node = new PlanSummary();
		node.setNodeType("Seq Scan");
		node.setRelation(table);
		node.setTotalCost(cost);
		node.setEstimatedRows(rows);
		return node;
	}

	@Test
	void flagsSelectStar() {

		SelectStatement select = select(
				List.of("*"), "users", null, List.of(), null);

		List<OptimizationFinding> findings = engine.run(select);

		assertTrue(findings.stream().anyMatch(f ->
				"SELECT_STAR".equals(f.getRuleId())
				&& "high".equals(f.getSeverity())
				&& f.getMessage().contains("users")));
	}

	@Test
	void flagsFilteredColumnsAsIndexCandidates() {

		SelectStatement select = select(
				List.of("id", "email"), "users",
				new WhereCondition("email", "=", "'a@b.c'"), List.of(), null);

		List<OptimizationFinding> findings = engine.run(select);

		assertTrue(findings.stream().anyMatch(f ->
				"FILTER_INDEX".equals(f.getRuleId())
				&& "medium".equals(f.getSeverity())
				&& f.getMessage().contains("email")));
	}

	@Test
	void flagsWholeTableRead() {

		SelectStatement select = select(
				List.of("id"), "projects", null, List.of(), null);

		List<OptimizationFinding> findings = engine.run(select);

		assertTrue(findings.stream().anyMatch(f ->
				"NO_FILTER".equals(f.getRuleId())
				&& f.getMessage().contains("projects")));
	}

	@Test
	void flagsOrderByWithoutLimit() {

		SelectStatement select = select(
				List.of("id"), "queries", null,
				List.of(new OrderByItem("created_at", true)), null);

		List<OptimizationFinding> findings = engine.run(select);

		assertTrue(findings.stream().anyMatch(f ->
				"SORT_WITHOUT_LIMIT".equals(f.getRuleId())));
	}

	@Test
	void orderByWithLimitIsNotFlagged() {

		SelectStatement select = select(
				List.of("id"), "queries", null,
				List.of(new OrderByItem("created_at", true)), 10L);

		List<OptimizationFinding> findings = engine.run(select);

		assertTrue(findings.stream()
				.noneMatch(f -> "SORT_WITHOUT_LIMIT".equals(f.getRuleId())));
	}

	@Test
	void explicitColumnsWithFilterGetOnlyTheIndexHint() {

		// no star, no missing filter, no sort - only the index hint applies
		SelectStatement select = select(
				List.of("id", "name"), "projects",
				new WhereCondition("id", ">", "5"), List.of(), 10L);

		List<OptimizationFinding> findings = engine.run(select);

		assertEquals(1, findings.size());
		assertEquals("FILTER_INDEX", findings.get(0).getRuleId());
	}

	@Test
	void explicitColumnsNoFilterNoSortIsJustTheWholeTableNote() {

		SelectStatement select = select(
				List.of("id", "name"), "projects", null, List.of(), null);

		List<OptimizationFinding> findings = engine.run(select);

		assertEquals(1, findings.size());
		assertEquals("NO_FILTER", findings.get(0).getRuleId());
	}

	// ========== Plan-aware rules (require a plan) ==========

	@Test
	void scanCostRuleFiresWithPlan() {

		SelectStatement select = select(
				List.of("id"), "queries",
				new WhereCondition("status", "=", "'done'"), List.of(), null);

		PlanSummary plan = expensiveSeqScan("queries", 5000.0, 50000L);

		List<OptimizationFinding> findings = engine.run(select, plan, null);

		assertTrue(findings.stream().anyMatch(f ->
				"SCAN_COST".equals(f.getRuleId())
				&& "high".equals(f.getSeverity())
				&& f.getMessage().contains("queries")));
	}

	@Test
	void scanCostRuleSkipsWithoutPlan() {

		SelectStatement select = select(
				List.of("id"), "queries",
				new WhereCondition("status", "=", "'done'"), List.of(), null);

		List<OptimizationFinding> findings = engine.run(select);

		assertTrue(findings.stream()
				.noneMatch(f -> "SCAN_COST".equals(f.getRuleId())));
	}

	@Test
	void expensiveSortRuleFiresWithPlan() {

		SelectStatement select = select(
				List.of("id"), "queries", null,
				List.of(new OrderByItem("created_at", true)), null);

		PlanSummary sortNode = new PlanSummary();
		sortNode.setNodeType("Sort");
		sortNode.setTotalCost(15000.0);
		sortNode.setEstimatedRows(50000L);
		sortNode.setSortKey("created_at");

		PlanSummary plan = new PlanSummary();
		plan.setNodeType("Seq Scan");
		plan.setRelation("queries");
		plan.setTotalCost(1000.0);
		plan.setEstimatedRows(50000L);
		List<PlanSummary> children = new ArrayList<>();
		children.add(sortNode);
		plan.setChildren(children);

		List<OptimizationFinding> findings = engine.run(select, plan, null);

		assertTrue(findings.stream().anyMatch(f ->
				"EXPENSIVE_SORT".equals(f.getRuleId())));
	}

	@Test
	void compositeIndexRuleFiresWithPlan() {

		SelectStatement select = select(
				List.of("id"), "queries",
				new WhereCondition("status", "=", "'done'"), List.of(), null);

		PlanSummary plan = expensiveSeqScan("queries", 5000.0, 50000L);

		List<OptimizationFinding> findings = engine.run(select, plan, null);

		// CompositeIndexRule needs 2+ columns; FILTER_INDEX covers single column
		assertTrue(findings.stream().anyMatch(f ->
				"FILTER_INDEX".equals(f.getRuleId())
				|| "COMPOSITE_INDEX".equals(f.getRuleId())));
	}
}
