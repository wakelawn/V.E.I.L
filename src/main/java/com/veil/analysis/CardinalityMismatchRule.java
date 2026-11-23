package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: Cardinality mismatch between estimated and actual rows.
public class CardinalityMismatchRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "CARDINALITY_MISMATCH"; }

    @Override
    public boolean requiresPlan() { return true; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (plan == null) return;

        for (PlanSummary node : flatten(plan)) {
            if (!node.hasActualMetrics()) continue;

            double ratio = node.getEstimatedVsActualRatio();
            if (ratio <= 0) continue;

            // Significant over-estimation (planner thought more rows)
            if (ratio < 0.1) {
                findings.add(new OptimizationFinding(
                        "CARDINALITY_MISMATCH",
                        "high",
                        "Cardinality over-estimation (" + String.format("%.0f", 1.0 / ratio) + "x)",
                        "Planner estimated " + node.getEstimatedRows()
                                + " rows but actual was " + node.getActualRows()
                                + " (~" + String.format("%.0f", 1.0 / ratio) + "x overestimate). "
                                + "This may cause poor join choices (e.g., Nested Loop instead of Hash Join). "
                                + "Run ANALYZE or increase statistics target.",
                        "ANALYZE " + node.getRelation() + ";")
                        .withEvidence("Estimated: " + node.getEstimatedRows() + ", Actual: " + node.getActualRows() + " (ratio: " + String.format("%.2f", ratio) + ")")
                        .withConfidence(0.95)
                        .withEvidenceLevel("ACTUAL_METRICS")
                        .withVerificationStatus("PLAN_VERIFIED")
                        .withRelatedPlanNode(node.getNodeType()));
            }
            // Significant under-estimation
            else if (ratio > 10.0) {
                findings.add(new OptimizationFinding(
                        "CARDINALITY_MISMATCH",
                        "high",
                        "Cardinality under-estimation (" + String.format("%.0f", ratio) + "x)",
                        "Planner estimated " + node.getEstimatedRows()
                                + " rows but actual was " + node.getActualRows()
                                + " (~" + String.format("%.0f", ratio) + "x underestimate). "
                                + "This can cause Nested Loop joins to run much longer than planned. "
                                + "Run ANALYZE or increase statistics target on filtered columns.",
                        "ANALYZE " + node.getRelation() + ";")
                        .withEvidence("Estimated: " + node.getEstimatedRows() + ", Actual: " + node.getActualRows() + " (ratio: " + String.format("%.2f", ratio) + ")")
                        .withConfidence(0.95)
                        .withEvidenceLevel("ACTUAL_METRICS")
                        .withVerificationStatus("PLAN_VERIFIED")
                        .withRelatedPlanNode(node.getNodeType()));
            }
        }
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