package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: High-cost sequential scans on large tables.
public class ScanCostRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "SCAN_COST"; }

    @Override
    public boolean requiresPlan() { return true; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (plan == null) return;

        for (PlanSummary node : flatten(plan)) {
            if (!"Seq Scan".equals(node.getNodeType())) continue;

            double totalCost = node.getTotalCost();
            long estimatedRows = node.getEstimatedRows();
            String relation = node.getRelation();

            if (totalCost >= 1000 && estimatedRows > 10000) {
                findings.add(new OptimizationFinding(
                        "SCAN_COST",
                        "high",
                        "Expensive sequential scan on large table",
                        "Sequential scan on '" + relation + "' has estimated cost "
                                + Math.round(totalCost) + " for " + estimatedRows + " rows. "
                                + "Consider adding an index on the filtered columns.",
                        null)
                        .withEvidence("Seq Scan cost=" + totalCost + ", rows=" + estimatedRows)
                        .withConfidence(0.9)
                        .withEvidenceLevel("PLAN_ESTIMATE")
                        .withVerificationStatus("UNVERIFIED")
                        .withRelatedPlanNode("Seq Scan"));
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