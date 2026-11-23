package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: columns used in WHERE/ON are index candidates.
public class FilterIndexRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "FILTER_INDEX"; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        List<String> filterColumns = FilterColumns.of(select);

        if (filterColumns.isEmpty()) {
            return;
        }

        String tableName = select.getTable() != null ? select.getTable().getTableName() : "table";

        double confidence = 0.75;
        String evidence = "WHERE/JOIN conditions on columns: " + String.join(", ", filterColumns);

        // Check if plan shows Seq Scan with filter on these columns
        if (plan != null) {
            // Look for Seq Scan with Filter matching these columns
            boolean hasSeqScanFilter = hasSeqScanWithFilter(plan, filterColumns);
            if (hasSeqScanFilter) {
                confidence = 0.9;
            }
        }

        findings.add(new OptimizationFinding(
                "FILTER_INDEX",
                "medium",
                "Consider an index on filtered columns",
                "WHERE/JOIN condition on column(s) " + String.join(", ", filterColumns)
                        + ". If these columns are filtered frequently, an index "
                        + "may speed the query up.",
                null)
                .withEvidence(evidence)
                .withConfidence(confidence)
                .withEvidenceLevel(plan != null ? "PLAN_ESTIMATE" : "AST_ONLY")
                .withVerificationStatus("UNVERIFIED")
                .withRelatedPlanNode(plan != null ? "Seq Scan" : null));
    }

    private boolean hasSeqScanWithFilter(PlanSummary node, List<String> filterColumns) {
        if (node == null) return false;

        if ("Seq Scan".equals(node.getNodeType()) && node.getFilter() != null) {
            String filter = node.getFilter();
            for (String col : filterColumns) {
                if (filter.contains(col)) return true;
            }
        }

        if (node.getChildren() != null) {
            for (PlanSummary child : node.getChildren()) {
                if (hasSeqScanWithFilter(child, filterColumns)) return true;
            }
        }
        return false;
    }
}