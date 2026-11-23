package com.veil.analysis;

import com.veil.ast.*;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: Unnecessary columns in SELECT (projection) - wider rows = more I/O.
public class ProjectionRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "UNNECESSARY_PROJECTION"; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        List<SelectItem> items = select.getSelectItems();
        if (items == null || items.isEmpty()) return;

        // Count non-aggregate, non-expression columns
        int columnCount = 0;
        int aggregateCount = 0;
        int expressionCount = 0;

        for (SelectItem item : items) {
            com.veil.ast.Expression expr = item.getExpression();
            if (expr instanceof AggregateExpression) {
                aggregateCount++;
            } else if (expr instanceof com.veil.ast.ColumnReference) {
                columnCount++;
            } else {
                expressionCount++;
            }
        }

        // If many columns selected and no aggregates, flag as potential issue
        if (columnCount > 10 && aggregateCount == 0) {
            String tableName = select.getTable() != null ? select.getTable().getTableName() : "table";
            findings.add(new OptimizationFinding(
                    "UNNECESSARY_PROJECTION",
                    "low",
                    "Many columns selected (" + columnCount + ")",
                    "Query selects " + columnCount + " columns from '" + tableName + "'. "
                            + "Wide rows increase I/O, memory, and network transfer. "
                            + "Consider selecting only the columns actually needed by the application.",
                    null)
                    .withEvidence(columnCount + " columns selected, no aggregates")
                    .withConfidence(0.6)
                    .withEvidenceLevel("AST_ONLY")
                    .withVerificationStatus("UNVERIFIED"));
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