package com.veil.explain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.core.database.ExecutionPlanProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ExplainService {

	private final ExecutionPlanProvider executionPlanProvider;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final PlanAnalyzer planAnalyzer = new PlanAnalyzer();
	private final CostAnalyzer costAnalyzer = new CostAnalyzer();

	public ExplainService(
			ExecutionPlanProvider executionPlanProvider
	) {
		this.executionPlanProvider = executionPlanProvider;
	}

	public PlanSummary explain(String sql) {

		if (sql == null || sql.isBlank()) {
			throw new IllegalArgumentException(
					"SQL query cannot be empty."
			);
		}

		String query = validateSql(sql);

		String json = executionPlanProvider.getExecutionPlan(query);

		try {
			JsonNode root = objectMapper.readTree(json);

			if (root == null
					|| !root.isArray()
					|| root.isEmpty()
					|| !root.get(0).has("Plan")) {

				throw new IllegalStateException(
						"PostgreSQL returned an unexpected execution-plan format."
				);
			}

			PlanSummary summary = extractPlan(root.get(0).get("Plan"));

			populatePlanMetrics(summary);

			return summary;

		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(
					"Failed to parse PostgreSQL execution plan.", e
			);
		}
	}

	private static String validateSql(String sql) {

		String query = sql.trim();

		// Remove one trailing semicolon.
		if (query.endsWith(";")) {
			query = query.substring(0, query.length() - 1).trim();
		}

		String upper = query.toUpperCase(Locale.ROOT);

		// Allow SELECT and WITH queries only.
		if (!upper.startsWith("SELECT ")
				&& !upper.startsWith("SELECT\n")
				&& !upper.startsWith("SELECT\t")
				&& !upper.startsWith("WITH ")
				&& !upper.startsWith("WITH\n")
				&& !upper.startsWith("WITH\t")) {

			throw new IllegalArgumentException(
					"Only SELECT or WITH queries are supported."
			);
		}

		// Reject multiple statements.
		if (query.contains(";")) {
			throw new IllegalArgumentException(
					"Multiple SQL statements are not allowed."
			);
		}

		return query;
	}

	private PlanSummary extractPlan(JsonNode plan) {

		PlanSummary summary = new PlanSummary();

		summary.setNodeType(
				getText(plan, "Node Type", "Unknown"));

		summary.setRelation(
				getText(plan, "Relation Name", "NotAvailable"));

		summary.setIndex(
				getText(plan, "Index Name", "NotAvailable"));

		summary.setStartupCost(
				getDouble(plan, "Startup Cost"));

		summary.setTotalCost(
				getDouble(plan, "Total Cost"));

		summary.setEstimatedRows(
				getLong(plan, "Plan Rows"));

		summary.setStrategy(
				getText(plan, "Strategy", "NotAvailable"));

		summary.setScanDirection(
				getText(plan, "Scan Direction", "NotAvailable"));

		summary.setEstimatedWidth(
				getDouble(plan, "Plan Width"));

		summary.setParallelAware(
				getBoolean(plan, "Parallel Aware"));

		// the row filter a scan applies, and the sort keys of a Sort node.
		// both feed the deeper plan analysis
		JsonNode filter = plan.get("Filter");

		if (filter != null && !filter.isNull()) {
			summary.setFilter(filter.asText());
		}

		JsonNode sortKey = plan.get("Sort Key");

		if (sortKey != null && sortKey.isArray() && !sortKey.isEmpty()) {
			summary.setSortKey(sortKey.get(0).asText());
		}

		// analyze teh node using the available plan estimates
		summary.setRecommendations(
				generateRecommendations(plan, summary));

		// recursively extract all child plan nodes
		JsonNode plans = plan.get("Plans");

		if (plans != null && plans.isArray()) {
			List<PlanSummary> children = new ArrayList<>();

			for (JsonNode child : plans) {
				children.add(extractPlan(child));
			}

			summary.setChildren(children);
		}

		return summary;
	}

	// recopmendations part handler (feat: add ai generated ones for now stale ones)
	private List<String> generateRecommendations(
			JsonNode plan, PlanSummary summary) {

		List<String> recommendations = new ArrayList<>();

		String nodeType = summary.getNodeType();
		long estimatedRows = summary.getEstimatedRows();
		double totalCost = summary.getTotalCost();

		if ("Seq Scan".equals(nodeType)) {
			recommendations.add(
					"Sequential scan detected on table '"
							+ summary.getRelation()
							+ "'. Review table size, filter selectivity, "
							+ "and existing indexes before considering an index. "
							+ "A sequential scan may be optimal for small tables."
			);
		}

		if ("Sort".equals(nodeType)) {
			recommendations.add(
					"Sort operation detected. Review the sort cost and "
							+ "query ordering requirements. An appropriate index "
							+ "may help some queries, but this plan alone does "
							+ "not establish that an index is beneficial."
			);
		}

		if ("Nested Loop".equals(nodeType)
				&& estimatedRows > 1000) {
			recommendations.add(
					"Nested Loop has an estimated output of "
							+ estimatedRows
							+ " rows. Review its child plans, join conditions, "
							+ "and row estimates to determine whether the join "
							+ "strategy is appropriate."
			);
		}

		if (estimatedRows == 0) {
			recommendations.add(
					"PostgreSQL estimates zero output rows for this plan node. "
							+ "Check whether the query predicates and table "
							+ "statistics reflect the expected data."
			);
		}

		if (totalCost >= 1000) {
			recommendations.add(
					"This plan node has a relatively high estimated cost "
							+ "(" + totalCost + "). Inspect its child nodes "
							+ "to identify which operations contribute most "
							+ "to the plan cost."
			);
		}

		return recommendations;
	}

	//helpers (formatters) (4)
	private String getText(
			JsonNode node, String field, String defaultValue) {

		JsonNode value = node.get(field);

		if (value == null || value.isNull()) {
			return defaultValue;
		}

		return value.asText();
	}

	private double getDouble(JsonNode node, String field) {

		JsonNode value = node.get(field);

		if (value == null || value.isNull()
				|| !value.isNumber()) {
			return 0.0;
		}

		return value.asDouble();
	}

	private long getLong(JsonNode node, String field) {

		JsonNode value = node.get(field);

		if (value == null || value.isNull()
				|| !value.isNumber()) {
			return 0L;
		}

		return value.asLong();
	}

	private boolean getBoolean(JsonNode node, String field) {

		JsonNode value = node.get(field);

		return value != null && value.asBoolean(false);
	}

	//helpers for metrics (5)
	private void populatePlanMetrics(PlanSummary root) {
		int[] counts = new int[4];

		collectPlanMetrics(root, counts);

		root.setPlanNodeCount(counts[0]);
		root.setScanNodeCount(counts[1]);
		root.setIndexScanCount(counts[2]);
		root.setSequentialScanCount(counts[3]);

		// deeper analysis adds plan-level insights to the nodes;
		// must run before they are collected into the root
		planAnalyzer.analyze(root);

		Set<String> allRecommendations = new LinkedHashSet<>();
		collectRecommendations(root, allRecommendations);

		root.setRecommendations(new ArrayList<>(allRecommendations));
		root.setPlanSummary(buildPlanSummary(root, counts));

		// cost breakdown: where the plan's total cost went
		List<CostEntry> breakdown = costAnalyzer.breakdown(root);
		root.setCostBreakdown(breakdown);

		String costLine = costAnalyzer.summarize(breakdown);

		if (!costLine.isEmpty()) {
			root.setPlanSummary(root.getPlanSummary() + " " + costLine);
		}
	}

	private void collectRecommendations(
			PlanSummary node, Set<String> recommendations) {

		if (node == null) {
			return;
		}

		if (node.getRecommendations() != null) {
			recommendations.addAll(node.getRecommendations());
		}

		if (node.getChildren() != null) {
			for (PlanSummary child : node.getChildren()) {
				collectRecommendations(child, recommendations);
			}
		}
	}

	private void collectPlanMetrics(PlanSummary node, int[] counts) {
		if (node == null) {
			return;
		}

		counts[0]++;

		String type = node.getNodeType();

		if (isScanNode(type)) {
			counts[1]++;
		}

		if ("Index Scan".equals(type)
				|| "Index Only Scan".equals(type)
				|| "Bitmap Index Scan".equals(type)) {
			counts[2]++;
		}

		if ("Seq Scan".equals(type)) {
			counts[3]++;
		}

		if (node.getChildren() != null) {
			for (PlanSummary child : node.getChildren()) {
				collectPlanMetrics(child, counts);
			}
		}
	}

	private boolean isScanNode(String type) {
		return type != null && (
				type.equals("Seq Scan")
						|| type.equals("Index Scan")
						|| type.equals("Index Only Scan")
						|| type.equals("Bitmap Heap Scan")
						|| type.equals("Bitmap Index Scan")
						|| type.equals("Tid Scan")
						|| type.equals("Subquery Scan")
						|| type.equals("Function Scan")
						|| type.equals("Values Scan")
						|| type.equals("CTE Scan")
						|| type.equals("Foreign Scan")
						|| type.equals("Sample Scan")
		);
	}

	private String buildPlanSummary(PlanSummary root, int[] counts) {
		StringBuilder summary = new StringBuilder();

		summary.append("Root operation: ")
				.append(root.getNodeType())
				.append(". ");

		summary.append("Plan nodes: ")
				.append(counts[0])
				.append(", scan nodes: ")
				.append(counts[1])
				.append(", index-related scans: ")
				.append(counts[2])
				.append(", sequential scans: ")
				.append(counts[3])
				.append(". ");

		summary.append("Estimated output rows: ")
				.append(root.getEstimatedRows())
				.append("; estimated total cost: ")
				.append(root.getTotalCost())
				.append(" planner cost units.");

		return summary.toString();
	}
}