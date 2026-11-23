package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;
import com.veil.core.database.DatabaseAdapter;

import java.util.*;
import java.util.stream.Collectors;

// Rule: Detects redundant indexes (same leading columns, same table).
public class RedundantIndexRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "REDUNDANT_INDEX"; }

    @Override
    public boolean requiresPlan() { return false; }

    private final DatabaseAdapter databaseAdapter;

    public RedundantIndexRule(DatabaseAdapter databaseAdapter) {
        this.databaseAdapter = databaseAdapter;
    }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (databaseAdapter == null) return;

        // Get all indexes for all tables in the query
        Set<String> tables = new HashSet<>();
        tables.add(select.getTable().getTableName());
        for (com.veil.ast.JoinClause join : select.getJoins()) {
            tables.add(join.getTable().getTableName());
        }

        for (String table : tables) {
            try {
                List<Map<String, Object>> indexes = databaseAdapter.getIndexesForTable(table);
                if (indexes == null || indexes.size() <= 1) continue;

                // Group by leading column(s)
                Map<String, List<Map<String, Object>>> byLeading = new HashMap<>();
                for (Map<String, Object> idx : indexes) {
                    String def = (String) idx.get("indexdef");
                    String leading = extractLeadingColumn(def);
                    if (leading != null) {
                        byLeading.computeIfAbsent(leading, k -> new ArrayList<>()).add(idx);
                    }
                }

                // Find groups with multiple indexes
                for (Map.Entry<String, List<Map<String, Object>>> entry : byLeading.entrySet()) {
                    List<Map<String, Object>> group = entry.getValue();
                    if (group.size() > 1) {
                        List<String> names = group.stream()
                                .map(m -> (String) m.get("indexname"))
                                .collect(Collectors.toList());

                        findings.add(new OptimizationFinding(
                                "REDUNDANT_INDEX",
                                "low",
                                "Potentially redundant indexes on '" + table + "'",
                                "Multiple indexes share the same leading column '" + entry.getKey()
                                        + "': " + String.join(", ", names)
                                        + ". Consider consolidating to a single composite index.",
                        null)
                                .withEvidence("Indexes on " + table + " with leading column " + entry.getKey() + ": " + names)
                                .withConfidence(0.6)
                                .withEvidenceLevel("AST_ONLY")
                                .withVerificationStatus("UNVERIFIED"));
                    }
                }
            } catch (Exception e) {
                // Ignore database errors
            }
        }
    }

    private String extractLeadingColumn(String indexDef) {
        if (indexDef == null) return null;
        // Simple extraction: CREATE INDEX ... ON table (col1, col2, ...)
        int paren = indexDef.indexOf('(');
        if (paren == -1) return null;
        int end = indexDef.indexOf(',', paren);
        if (end == -1) end = indexDef.indexOf(')', paren);
        if (end == -1) return null;
        return indexDef.substring(paren + 1, end).trim().replaceAll("[\"\\s]", "");
    }
}