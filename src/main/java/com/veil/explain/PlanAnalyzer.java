package com.veil.explain;

import java.util.List;

// walks a finished plan tree and adds plan-level insights:
// 1. the bottleneck: the node doing the most of its OWN work.
//    a parent's total cost always includes its children, so the real
//    measure of a node's own work is totalCost minus its children's
//    totalCost (the "self cost")
// 2. sequential scans that also apply a filter - the classic case
//    where an index may help on large tables
public class PlanAnalyzer {

	private static final double BOTTLENECK_MIN_SHARE = 30.0;

	public void analyze(PlanSummary root) {

		if (root == null) {
			return;
		}

		// with a single node the plan is its own bottleneck - no insight
		if (root.getPlanNodeCount() <= 1) {
			addSeqScanFilterInsight(root);
			return;
		}

		addBottleneckInsight(root);
		addSeqScanFilterInsights(root);
	}

	// self cost = own work only (total minus what the children already include)
	private double selfCost(PlanSummary node) {

		double childrenCost = 0.0;

		for (PlanSummary child : node.getChildren()) {
			childrenCost += child.getTotalCost();
		}

		return node.getTotalCost() - childrenCost;
	}

	private void addBottleneckInsight(PlanSummary root) {

		PlanSummary bottleneck = null;
		double bestSelfCost = 0.0;

		for (PlanSummary node : flatten(root)) {

			double self = selfCost(node);

			if (self > bestSelfCost) {
				bestSelfCost = self;
				bottleneck = node;
			}
		}

		if (bottleneck == null || bestSelfCost <= 0) {
			return;
		}

		double share = bestSelfCost / root.getTotalCost() * 100.0;

		// only worth reporting when one node really dominates
		if (share < BOTTLENECK_MIN_SHARE) {
			return;
		}

		String target = bottleneck.getRelation() != null
				&& !"NotAvailable".equals(bottleneck.getRelation())
				? " on " + bottleneck.getRelation()
				: "";

		bottleneck.getRecommendations().add(
				"Plan bottleneck: " + bottleneck.getNodeType() + target
						+ " does " + Math.round(bestSelfCost * 100.0) / 100.0
						+ " of the " + Math.round(root.getTotalCost() * 100.0) / 100.0
						+ " total plan cost (~" + Math.round(share)
						+ "%). Optimizing this node helps the whole query most.");
	}

	private void addSeqScanFilterInsights(PlanSummary root) {

		for (PlanSummary node : flatten(root)) {

			addSeqScanFilterInsight(node);
		}
	}

	private void addSeqScanFilterInsight(PlanSummary node) {

		if (!"Seq Scan".equals(node.getNodeType())
				|| node.getFilter() == null) {
			return;
		}

		node.getRecommendations().add(
				"Sequential scan on '" + node.getRelation()
						+ "' also applies a filter (" + node.getFilter()
						+ "). If this table is large and only a few rows "
						+ "match, an index on the filtered column may help.");
	}

	// depth-first list of every node in the tree
	private List<PlanSummary> flatten(PlanSummary node) {

		List<PlanSummary> out = new java.util.ArrayList<>();
		out.add(node);

		for (PlanSummary child : node.getChildren()) {
			out.addAll(flatten(child));
		}

		return out;
	}
}
