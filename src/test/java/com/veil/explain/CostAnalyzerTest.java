package com.veil.explain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostAnalyzerTest {

	private PlanSummary node(String type, String relation,
			double totalCost, PlanSummary... children) {

		PlanSummary n = new PlanSummary();
		n.setNodeType(type);
		n.setRelation(relation == null ? "NotAvailable" : relation);
		n.setTotalCost(totalCost);

		for (PlanSummary child : children) {
			n.getChildren().add(child);
		}

		return n;
	}

	@Test
	void entriesAreSortedBySelfCostWithShares() {

		// scan does 90 of 100, the sort itself only 10
		PlanSummary scan = node("Seq Scan", "projects", 90.0);
		PlanSummary sort = node("Sort", "NotAvailable", 100.0, scan);

		List<CostEntry> breakdown = new CostAnalyzer().breakdown(sort);

		assertEquals(2, breakdown.size());

		// most expensive work first
		assertEquals("Seq Scan", breakdown.get(0).getNodeType());
		assertEquals(90.0, breakdown.get(0).getSelfCost());
		assertEquals(90.0, breakdown.get(0).getSharePercent());
		assertEquals("scan", breakdown.get(0).getCategory());

		assertEquals("Sort", breakdown.get(1).getNodeType());
		assertEquals(10.0, breakdown.get(1).getSelfCost());
		assertEquals("sort", breakdown.get(1).getCategory());
	}

	@Test
	void sharesOfAllEntriesSumToPlanTotal() {

		PlanSummary a = node("Seq Scan", "a", 10.0);
		PlanSummary b = node("Index Scan", "b", 20.0);
		PlanSummary join = node("Hash Join", "NotAvailable", 40.0, a, b);

		List<CostEntry> breakdown = new CostAnalyzer().breakdown(join);

		double selfSum = breakdown.stream()
				.mapToDouble(CostEntry::getSelfCost).sum();

		assertEquals(40.0, selfSum, 0.001);

		// sorted by self cost: the 20-cost scan first, the join last
		assertEquals("Index Scan", breakdown.get(0).getNodeType());
		assertEquals("scan", breakdown.get(0).getCategory());
		assertTrue(breakdown.stream().anyMatch(e ->
				"Hash Join".equals(e.getNodeType())
				&& "join".equals(e.getCategory())));
	}

	@Test
	void singleNodePlanIsOneHundredPercent() {

		PlanSummary scan = node("Index Scan", "queries", 8.17);

		List<CostEntry> breakdown = new CostAnalyzer().breakdown(scan);

		assertEquals(1, breakdown.size());
		assertEquals(100.0, breakdown.get(0).getSharePercent());
	}

	@Test
	void summarizeProducesCategoryLine() {

		PlanSummary scan = node("Seq Scan", "projects", 90.0);
		PlanSummary sort = node("Sort", "NotAvailable", 100.0, scan);

		List<CostEntry> breakdown = new CostAnalyzer().breakdown(sort);
		String line = new CostAnalyzer().summarize(breakdown);

		assertTrue(line.startsWith("Cost breakdown: "));
		assertTrue(line.contains("scan 90.0 (90%)"));
		assertTrue(line.contains("sort 10.0 (10%)"));
		assertTrue(line.endsWith("."));
	}

	@Test
	void emptyPlanGivesEmptyBreakdown() {

		assertTrue(new CostAnalyzer().breakdown(null).isEmpty());
		assertTrue(new CostAnalyzer().breakdown(new PlanSummary()).isEmpty());
	}
}
