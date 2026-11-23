package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: ORDER BY without LIMIT sorts everything.
public class SortRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "SORT_WITHOUT_LIMIT"; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (select.getOrderBy().isEmpty() || select.getLimit() != null) {
            return;
        }

        String tableName = select.getTable() != null ? select.getTable().getTableName() : "table";

        double confidence = 0.8;
        String evidence = "ORDER BY " + select.getOrderBy() + " without LIMIT";

        // Higher confidence if plan shows Sort node with high row count
        if (plan != null) {
            if (hasSortNodeWithHighRows(plan)) {
                confidence = 0.95;
            }
        }

        findings.add(new OptimizationFinding(
                "SORT_WITHOUT_LIMIT",
                "low",
                "ORDER BY without LIMIT sorts everything",
                "The query sorts all matching rows before returning any. "
                        + "If you only need the first few, add a LIMIT to make "
                        + "the sort cheaper.",
                null)
                .withEvidence(evidence)
                .withConfidence(confidence)
                .withEvidenceLevel(plan != null ? "PLAN_ESTIMATE" : "AST_ONLY")
                .withVerificationStatus("UNVERIFIED")
                .withRelatedPlanNode(plan != null ? "Sort" : null));
    }

    private boolean hasSortNodeWithHighRows(PlanSummary node) {
        if (node == null) return false;

        if ("Sort".equals(node.getNodeType()) && node.getEstimatedRows() > 1000) {
            return true;
        }

        if (node.getChildren() != null) {
            for (PlanSummary child : node.getChildren()) {
                if (hasSortNodeWithHighRows(child)) return true;
            }
        }
        return false;
    }
}