package com.veil.explain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanAnalyzerTest {

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

	private List<String> allRecommendations(PlanSummary root) {

		java.util.Set<String> out = new java.util.LinkedHashSet<>();
		collect(root, out);
		return List.copyOf(out);
	}

	private void collect(PlanSummary node, java.util.Set<String> into) {

		into.addAll(node.getRecommendations());

		for (PlanSummary child : node.getChildren()) {
			collect(child, into);
		}
	}

	@Test
	void findsBottleneckBySelfCost() {

		// Sort total 100 with a child that costs 90:
		// the Sort's own work is only 10, the child's is 90.
		// raw total would wrongly call the Sort the bottleneck
		PlanSummary scan = node("Seq Scan", "projects", 90.0);
		PlanSummary sort = node("Sort", "NotAvailable", 100.0, scan);
		sort.setPlanNodeCount(2);

		new PlanAnalyzer().analyze(sort);

		List<String> recs = allRecommendations(sort);

		assertTrue(recs.stream().anyMatch(r ->
				r.contains("Plan bottleneck: Seq Scan on projects")
				&& r.contains("~90%")),
				"bottleneck should be the scan, not the sort: " + recs);
	}

	@Test
	void skipsBottleneckForSingleNodePlan() {

		PlanSummary scan = node("Index Scan", "queries", 8.17);
		scan.setPlanNodeCount(1);

		new PlanAnalyzer().analyze(scan);

		assertTrue(allRecommendations(scan).stream()
				.noneMatch(r -> r.contains("Plan bottleneck")));
	}

	@Test
	void skipsBottleneckWhenNoNodeDominates() {

		// every node does the same self cost (10 of 40 = 25%, below
		// the 30% threshold) - no single bottleneck
		PlanSummary a = node("Seq Scan", "a", 10.0);
		PlanSummary b = node("Seq Scan", "b", 10.0);
		PlanSummary c = node("Seq Scan", "c", 10.0);
		PlanSummary join = node("Hash Join", "NotAvailable", 40.0, a, b, c);
		join.setPlanNodeCount(4);

		new PlanAnalyzer().analyze(join);

		assertTrue(allRecommendations(join).stream()
				.noneMatch(r -> r.contains("Plan bottleneck")));
	}

	@Test
	void addsIndexHintForSeqScanWithFilter() {

		PlanSummary scan = node("Seq Scan", "projects", 12.7);
		scan.setFilter("(id > 100)");
		scan.setPlanNodeCount(1);

		new PlanAnalyzer().analyze(scan);

		assertTrue(allRecommendations(scan).stream().anyMatch(r ->
				r.contains("Sequential scan on 'projects'")
				&& r.contains("(id > 100)")
				&& r.contains("index")));
	}

	@Test
	void collectsBottleneckAndFilterHintsTogether() {

		PlanSummary scan = node("Seq Scan", "queries", 90.0);
		scan.setFilter("(status = 'done')");
		PlanSummary sort = node("Sort", "NotAvailable", 100.0, scan);
		sort.setPlanNodeCount(2);

		new PlanAnalyzer().analyze(sort);

		List<String> recs = allRecommendations(sort);

		assertEquals(2, recs.size());
		assertTrue(recs.stream().anyMatch(r -> r.contains("Plan bottleneck")));
		assertTrue(recs.stream().anyMatch(r -> r.contains("(status = 'done')")));
	}
}
