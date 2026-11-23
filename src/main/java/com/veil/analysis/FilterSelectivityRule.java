package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: Analyzes filter selectivity from actual execution metrics.
public class FilterSelectivityRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "FILTER_SELECTIVITY"; }

    @Override
    public boolean requiresPlan() { return true; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (plan == null) return;

        for (PlanSummary node : flatten(plan)) {
            if (!"Seq Scan".equals(node.getNodeType()) && !"Index Scan".equals(node.getNodeType())
                    && !"Bitmap Heap Scan".equals(node.getNodeType())) {
                continue;
            }

            if (!node.hasActualMetrics()) continue;

            long estimatedRows = node.getEstimatedRows();
            long actualRows = node.getActualRows();

            if (estimatedRows <= 0 || actualRows < 0) continue;

            // Calculate selectivity: actual rows / estimated rows
            // Low selectivity = few rows match = good index candidate
            double selectivity = (double) actualRows / estimatedRows;

            if (selectivity < 0.01) { // < 1% selectivity
                findings.add(new OptimizationFinding(
                        "FILTER_SELECTIVITY",
                        "high",
                        "Highly selective filter (" + String.format("%.2f", selectivity * 100) + "%)",
                        "Filter on '" + node.getRelation() + "' returns only "
                                + actualRows + " of " + estimatedRows + " rows ("
                                + String.format("%.2f", selectivity * 100) + "%). "
                                + "This is an excellent index candidate. "
                                + "Consider a partial index if the filter value is constant-ish.",
                        "CREATE INDEX idx_" + node.getRelation() + "_filter ON " + node.getRelation() + " (...) WHERE ...;")
                        .withEvidence("Selectivity: " + String.format("%.4f", selectivity) + " (" + actualRows + "/" + estimatedRows + ")")
                        .withConfidence(0.9)
                        .withEvidenceLevel("ACTUAL_METRICS")
                        .withVerificationStatus("PLAN_VERIFIED")
                        .withRelatedPlanNode(node.getNodeType()));
            }
            else if (selectivity > 0.5) { // > 50% - low selectivity
                findings.add(new OptimizationFinding(
                        "FILTER_SELECTIVITY",
                        "low",
                        "Low selectivity filter (" + String.format("%.1f", selectivity * 100) + "%)",
                        "Filter on '" + node.getRelation() + "' returns "
                                + actualRows + " of " + estimatedRows + " rows ("
                                + String.format("%.1f", selectivity * 100) + "%). "
                                + "Sequential scan may be optimal here. An index would not help much.",
                        null)
                        .withEvidence("Selectivity: " + String.format("%.2f", selectivity) + " (" + actualRows + "/" + estimatedRows + ")")
                        .withConfidence(0.8)
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