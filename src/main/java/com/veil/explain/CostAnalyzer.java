package com.veil.explain;

import java.util.ArrayList;
import java.util.List;

// builds the cost breakdown of a plan: every node's self cost
// (its own work, children excluded) as a share of the plan total.
// see plan-analysis.md section 3 for why self cost is the right unit
public class CostAnalyzer {

	public List<CostEntry> breakdown(PlanSummary root) {

		List<CostEntry> entries = new ArrayList<>();

		if (root == null || root.getTotalCost() <= 0) {
			return entries;
		}

		for (PlanSummary node : flatten(root)) {

			double self = selfCost(node);

			if (self <= 0) {
				continue;
			}

			entries.add(new CostEntry(
					node.getNodeType(),
					node.getRelation(),
					categoryOf(node.getNodeType()),
					round(self),
					round(self / root.getTotalCost() * 100.0)));
		}

		// most expensive work first
		entries.sort((a, b) -> Double.compare(b.getSelfCost(), a.getSelfCost()));

		return entries;
	}

	// one line summarizing where the plan cost went, by category
	public String summarize(List<CostEntry> entries) {

		if (entries.isEmpty()) {
			return "";
		}

		java.util.Map<String, Double> byCategory = new java.util.LinkedHashMap<>();
		double total = 0.0;

		for (CostEntry entry : entries) {
			byCategory.merge(entry.getCategory(), entry.getSelfCost(), Double::sum);
			total += entry.getSelfCost();
		}

		StringBuilder line = new StringBuilder("Cost breakdown: ");

		List<java.util.Map.Entry<String, Double>> sorted =
				new ArrayList<>(byCategory.entrySet());
		sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

		for (int i = 0; i < sorted.size(); i++) {

			var part = sorted.get(i);

			line.append(part.getKey()).append(" ")
					.append(round(part.getValue()))
					.append(" (")
					.append(Math.round(part.getValue() / total * 100.0))
					.append("%)");

			if (i < sorted.size() - 1) {
				line.append(", ");
			}
		}

		line.append(".");

		return line.toString();
	}

	private double selfCost(PlanSummary node) {

		double childrenCost = 0.0;

		for (PlanSummary child : node.getChildren()) {
			childrenCost += child.getTotalCost();
		}

		return Math.max(0.0, node.getTotalCost() - childrenCost);
	}

	// coarse work categories used by the summary line
	private String categoryOf(String nodeType) {

		if (nodeType.endsWith("Scan")) {
			return "scan";
		}

		return switch (nodeType) {

			case "Sort" -> "sort";

			case "HashAggregate", "GroupAggregate", "Aggregate" -> "aggregate";

			case "Hash Join", "Nested Loop", "Merge Join" -> "join";

			default -> "other";
		};
	}

	private List<PlanSummary> flatten(PlanSummary node) {

		List<PlanSummary> out = new ArrayList<>();
		out.add(node);

		for (PlanSummary child : node.getChildren()) {
			out.addAll(flatten(child));
		}

		return out;
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
