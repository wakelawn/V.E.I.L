package com.veil.analysis;

import com.veil.ast.*;
import com.veil.explain.PlanSummary;

import java.util.*;
import java.util.stream.Collectors;

// Rule: Composite index recommendations based on WHERE + ORDER BY patterns.
public class CompositeIndexRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "COMPOSITE_INDEX"; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        // Collect WHERE columns
        List<String> whereColumns = FilterColumns.of(select);

        // Collect ORDER BY columns
        List<String> orderByColumns = select.getOrderBy().stream()
                .map(com.veil.ast.OrderByItem::getColumn)
                .collect(Collectors.toList());

        // Collect JOIN columns
        List<String> joinColumns = new ArrayList<>();
        for (com.veil.ast.JoinClause join : select.getJoins()) {
            Condition cond = join.getCondition();
            extractJoinColumns(cond, joinColumns);
        }

        // Build column groups for composite index candidates
        Set<String> allFilterCols = new LinkedHashSet<>();
        allFilterCols.addAll(whereColumns);
        allFilterCols.addAll(joinColumns);

        if (!allFilterCols.isEmpty()) {
            // Suggest composite index: equality columns first, then ORDER BY
            List<String> equalityCols = new ArrayList<>();
            for (String col : allFilterCols) {
                // Simplified: assume all are equality for now
                equalityCols.add(col);
            }

            List<String> indexCols = new ArrayList<>(equalityCols);
            for (String col : orderByColumns) {
                if (!equalityCols.contains(col)) {
                    indexCols.add(col);
                }
            }

            if (indexCols.size() > 1) {
                String tableName = select.getTable() != null ? select.getTable().getTableName() : "table";
                String idxName = "idx_" + tableName + "_" + String.join("_", indexCols).replace(".", "_");

                findings.add(new OptimizationFinding(
                        "COMPOSITE_INDEX",
                        "medium",
                        "Composite index candidate: " + String.join(", ", indexCols),
                        "Query filters on " + String.join(", ", allFilterCols)
                                + (orderByColumns.isEmpty() ? "" : " and orders by " + orderByColumns)
                                + ". A composite index on (" + String.join(", ", indexCols) + ") "
                                + "could serve both filtering and ordering.",
                        "CREATE INDEX " + idxName + " ON " + select.getTable().getTableName()
                                + " (" + String.join(", ", indexCols) + ");")
                        .withEvidence("Filter columns: " + allFilterCols + ", Order by: " + orderByColumns)
                        .withConfidence(0.7)
                        .withEvidenceLevel("AST_ONLY")
                        .withVerificationStatus("UNVERIFIED"));
            }
        }
    }

    private void extractJoinColumns(Condition condition, List<String> into) {
        if (condition instanceof WhereCondition where) {
            String col = where.getColumn();
            if (col.contains(".") && !into.contains(col)) {
                into.add(col);
            }
        } else if (condition instanceof LogicalCondition logical) {
            extractJoinColumns(logical.getLeft(), into);
            extractJoinColumns(logical.getRight(), into);
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