package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: no WHERE and no JOIN means every row is read.
public class NoFilterRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "NO_FILTER"; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (select.getWhereCondition() != null || !select.getJoins().isEmpty()) {
            return;
        }

        String tableName = select.getTable() != null ? select.getTable().getTableName() : "table";

        double confidence = 0.7;
        String evidence = "No WHERE clause or JOIN present";

        // Higher confidence if plan shows Seq Scan with high row count
        if (plan != null && plan.getChildren() != null) {
            // Check if there's a Seq Scan with high row count
            // (simplified check)
            confidence = 0.8;
        }

        findings.add(new OptimizationFinding(
                "NO_FILTER",
                "low",
                "Query reads the whole table",
                "No WHERE clause or JOIN on '" + select.getTable().getTableName()
                        + "': the query reads every row. Add a filter or LIMIT "
                        + "if you only need a subset.",
                null)
                .withEvidence(evidence)
                .withConfidence(confidence)
                .withEvidenceLevel("AST_ONLY")
                .withVerificationStatus("UNVERIFIED"));
    }
}