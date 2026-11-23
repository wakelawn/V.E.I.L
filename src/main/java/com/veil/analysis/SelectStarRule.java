package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule 1: SELECT * asks the database for every column.
public class SelectStarRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "SELECT_STAR"; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (!select.getColumns().contains("*")) {
            return;
        }

        String tableName = select.getTable() != null ? select.getTable().getTableName() : "table";

        findings.add(new OptimizationFinding(
                "SELECT_STAR",
                "high",
                "Avoid SELECT *",
                "SELECT * reads every column of '" + tableName
                        + "'. List only the columns you need to reduce I/O and network transfer.",
                null)
                .withEvidence("SELECT * found in column list")
                .withConfidence(0.95)
                .withEvidenceLevel("AST_ONLY")
                .withVerificationStatus("UNVERIFIED"));
    }
}