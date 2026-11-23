package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: Expensive aggregation operations - HashAggregate, GroupAggregate.
public class AggregationCostRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "AGGREGATION_COST"; }

    @Override
    public boolean requiresPlan() { return true; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (plan == null) return;

        for (PlanSummary node : flatten(plan)) {
            String nodeType = node.getNodeType();
            if (nodeType == null) continue;

            if (isAggregationNode(nodeType)) {
                long estimatedRows = node.getEstimatedRows();
                double totalCost = node.getTotalCost();

                // Expensive aggregation on large input
                if (estimatedRows > 50000) {
                    findings.add(new OptimizationFinding(
                            "AGGREGATION_COST",
                            "medium",
                            "Expensive aggregation on large input (" + estimatedRows + " rows)",
                            nodeType + " processing " + estimatedRows + " rows with cost "
                                    + Math.round(totalCost) + ". "
                                    + "Consider: reducing input size with earlier filters, "
                                    + "using partial indexes, or materialized views for frequent aggregations.",
                            null)
                            .withEvidence(nodeType + " rows: " + estimatedRows + ", cost: " + totalCost)
                            .withConfidence(0.75)
                            .withEvidenceLevel("PLAN_ESTIMATE")
                            .withVerificationStatus("UNVERIFIED")
                            .withRelatedPlanNode(nodeType));
                }

                // Check for HashAggregate spill risk
                if ("HashAggregate".equals(nodeType) && totalCost > 5000) {
                    findings.add(new OptimizationFinding(
                            "AGGREGATION_COST",
                            "medium",
                            "HashAggregate may spill to disk",
                            "HashAggregate cost " + Math.round(totalCost)
                                    + " suggests work_mem may be insufficient. "
                                    + "Increase work_mem or consider using GroupAggregate with pre-sorted input.",
                            null)
                            .withEvidence("HashAggregate cost: " + totalCost)
                            .withConfidence(0.8)
                            .withEvidenceLevel("PLAN_ESTIMATE")
                            .withVerificationStatus("UNVERIFIED")
                            .withRelatedPlanNode("HashAggregate"));
                }
            }
        }
    }

    private boolean isAggregationNode(String type) {
        return "HashAggregate".equals(type)
                || "GroupAggregate".equals(type)
                || "Aggregate".equals(type)
                || "HashAggregate".equals(type);
    }

    private List<PlanSummary> flatten(PlanSummary node) {
        java.util.List<PlanSummary> out = new java.util.ArrayList<>();
        out.add(node);
        if (node.getChildren() != null) {
            for (PlanSummary child : node.getChildren()) {
                out.addAll(flatten(child));
            }
        }
        return out;
    }
}