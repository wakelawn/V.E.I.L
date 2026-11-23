package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: Expensive sort operations - detects large sorts, spill risk.
public class ExpensiveSortRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "EXPENSIVE_SORT"; }

    @Override
    public boolean requiresPlan() { return true; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (plan == null) return;

        for (PlanSummary node : flatten(plan)) {
            if (!"Sort".equals(node.getNodeType())) continue;

            long estimatedRows = node.getEstimatedRows();
            double totalCost = node.getTotalCost();
            String sortKey = node.getSortKey();

            // Large sort
            if (estimatedRows > 10000) {
                findings.add(new OptimizationFinding(
                        "EXPENSIVE_SORT",
                        "medium",
                        "Large sort operation (" + estimatedRows + " rows)",
                        "Sort operation on " + estimatedRows + " rows with cost "
                                + Math.round(totalCost) + ". "
                                + (sortKey != null ? "Sort key: " + sortKey + ". " : "")
                                + "Consider: adding index on sort column(s) to avoid sort, "
                                + "increasing work_mem to avoid disk spill, "
                                + "or adding LIMIT if only top N rows needed.",
                        null)
                        .withEvidence("Sort rows: " + estimatedRows + ", cost: " + totalCost)
                        .withConfidence(0.8)
                        .withEvidenceLevel("PLAN_ESTIMATE")
                        .withVerificationStatus("UNVERIFIED")
                        .withRelatedPlanNode("Sort"));
            }

            // Check for potential disk spill
            if (totalCost > 10000) {
                findings.add(new OptimizationFinding(
                        "EXPENSIVE_SORT",
                        "high",
                        "Sort likely to spill to disk (cost: " + Math.round(totalCost) + ")",
                        "Sort cost exceeds 10000 planner units, indicating likely disk spill. "
                                + "Increase work_mem or add supporting index to avoid sort.",
                        null)
                        .withEvidence("Sort cost: " + totalCost)
                        .withConfidence(0.85)
                        .withEvidenceLevel("PLAN_ESTIMATE")
                        .withVerificationStatus("UNVERIFIED")
                        .withRelatedPlanNode("Sort"));
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