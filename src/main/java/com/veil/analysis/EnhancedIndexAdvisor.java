package com.veil.analysis;

import com.veil.ast.*;
import com.veil.core.database.DatabaseAdapter;
import com.veil.explain.PlanSummary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

// Enhanced Index Advisor with HypoPG support, partial indexes, expression indexes,
// and hypothetical index evaluation.
@Component
public class EnhancedIndexAdvisor {

    private final IndexInspector inspector;
    private final HypoPgService hypoPgService;
    private final DatabaseAdapter adapter;

    public EnhancedIndexAdvisor(IndexInspector inspector, HypoPgService hypoPgService, DatabaseAdapter adapter) {
        this.inspector = inspector;
        this.hypoPgService = hypoPgService;
        this.adapter = adapter;
    }

    // Main entry point: advises on all findings and returns index recommendations
    public List<IndexRecommendation> advise(SelectStatement select, List<OptimizationFinding> findings, PlanSummary plan) {
        List<IndexRecommendation> recommendations = new ArrayList<>();

        // 1. Verify FILTER_INDEX findings against real indexes
        verifyFilterIndexFindings(select, findings);

        // 2. Generate standard index recommendations
        recommendations.addAll(generateStandardRecommendations(select, findings));

        // 3. Generate partial index recommendations
        recommendations.addAll(generatePartialIndexRecommendations(select, plan));

        // 4. Generate expression index recommendations
        recommendations.addAll(generateExpressionIndexRecommendations(select, plan));

        // 5. If HypoPG available, evaluate hypothetical indexes
        if (hypoPgService != null && hypoPgService.isHypoPgAvailable() && plan != null) {
            recommendations.addAll(evaluateHypotheticalIndexes(select, plan));
        }

        // Sort by confidence/benefit
        recommendations.sort((a, b) -> {
            int cmp = Double.compare(b.getConfidence(), a.getConfidence());
            if (cmp != 0) return cmp;
            return Double.compare(b.getEstimatedBenefit(), a.getEstimatedBenefit());
        });

        return recommendations;
    }

    // Verify FILTER_INDEX findings against real indexes (existing logic enhanced)
    private void verifyFilterIndexFindings(SelectStatement select, List<OptimizationFinding> findings) {
        for (OptimizationFinding finding : findings) {
            if (!"FILTER_INDEX".equals(finding.getRuleId())) continue;

            List<String> suggestions = new ArrayList<>();
            List<String> covered = new ArrayList<>();
            List<String> checked = new ArrayList<>();

            for (String column : FilterColumns.of(select)) {
                String table = resolveTable(column, select);
                if (table == null) continue;

                checked.add(column + " (" + table + ")");

                try {
                    if (inspector.leadingIndexedColumns(table).stream()
                            .anyMatch(c -> c.equalsIgnoreCase(bareName(column)))) {
                        covered.add(column);
                    } else {
                        String idxName = "idx_" + table + "_" + bareName(column).replace(".", "_");
                        suggestions.add("CREATE INDEX " + idxName + " ON " + table
                                + " (" + bareName(column) + ");");
                    }
                } catch (Exception e) {
                    // DB error: leave hint as-is
                    return;
                }
            }

            if (checked.isEmpty()) return;

            if (covered.size() == checked.size()) {
                // All covered - replace the finding
                int idx = findings.indexOf(finding);
                if (idx >= 0) {
                    findings.set(idx, new OptimizationFinding(
                            "FILTER_INDEX",
                            "low",
                            "Filtered columns are already indexed",
                            "Filtered column(s) " + String.join(", ", covered)
                                    + " are the leading column of an existing index. No new index needed.",
                            null)
                            .withEvidence("All filtered columns covered by existing indexes")
                            .withConfidence(0.9)
                            .withEvidenceLevel("PLAN_ESTIMATE")
                            .withVerificationStatus("PLAN_VERIFIED"));
                }
            } else if (!suggestions.isEmpty()) {
                // Some uncovered - replace the finding
                int idx = findings.indexOf(finding);
                if (idx >= 0) {
                    findings.set(idx, new OptimizationFinding(
                            "FILTER_INDEX",
                            "medium",
                            "Index suggestion (verified)",
                            "Filtered column(s) " + String.join(", ", checked)
                                    + ". No existing index leads with the uncovered column(s). Suggested index:",
                            String.join("\n", suggestions))
                            .withEvidence("Uncovered columns: " + suggestions)
                            .withConfidence(0.85)
                            .withEvidenceLevel("PLAN_ESTIMATE")
                            .withVerificationStatus("PLAN_VERIFIED"));
                }
            }
        }
    }

    // Standard single-column and composite index recommendations
    private List<IndexRecommendation> generateStandardRecommendations(SelectStatement select, List<OptimizationFinding> findings) {
        List<IndexRecommendation> recommendations = new ArrayList<>();

        // Collect all filter columns from WHERE and JOIN
        Set<String> allFilterCols = new LinkedHashSet<>();
        allFilterCols.addAll(FilterColumns.of(select));

        // Add ORDER BY columns
        List<String> orderByCols = select.getOrderBy().stream()
                .map(OrderByItem::getColumn)
                .collect(Collectors.toList());

        // Group by table
        Map<String, List<String>> colsByTable = new HashMap<>();
        for (String col : allFilterCols) {
            String table = resolveTable(col, select);
            if (table != null) {
                colsByTable.computeIfAbsent(table, k -> new ArrayList<>()).add(col);
            }
        }

        for (Map.Entry<String, List<String>> entry : colsByTable.entrySet()) {
            String table = entry.getKey();
            List<String> cols = entry.getValue();

            // Check if already covered
            Set<String> covered = new HashSet<>(inspector.leadingIndexedColumns(table));

            // Find uncovered columns
            List<String> uncovered = cols.stream()
                    .filter(c -> !covered.contains(bareName(c)))
                    .collect(Collectors.toList());

            if (!uncovered.isEmpty()) {
                // Determine if composite index is better
                List<String> allCols = new ArrayList<>(uncovered);
                for (String oc : orderByCols) {
                    if (!uncovered.contains(oc) && !allCols.contains(oc)) {
                        allCols.add(oc);
                    }
                }

                String idxName = "idx_" + table + "_" + allCols.get(0).replace(".", "_");
                if (allCols.size() > 1) {
                    idxName += "_composite";
                }

                String columns = String.join(", ", allCols);
                String sql = "CREATE INDEX " + idxName + " ON " + table + " (" + columns + ");";

                recommendations.add(new IndexRecommendation()
                        .withTableName(table)
                        .withIndexName(idxName)
                        .withIndexType("BTREE")
                        .withColumns(columns)
                        .withSql(sql)
                        .withReason("Filtered columns " + uncovered + " not covered by existing indexes")
                        .withSeverity("medium")
                        .withConfidence(0.75)
                        .withEstimatedBenefit(0.6));
            }
        }

        return recommendations;
    }

    // Partial index recommendations for common filter values
    private List<IndexRecommendation> generatePartialIndexRecommendations(SelectStatement select, PlanSummary plan) {
        List<IndexRecommendation> recommendations = new ArrayList<>();

        if (plan == null) return recommendations;

        // Look for Seq Scans with filters that have highly selective constant values
        for (PlanSummary node : flattenPlan(plan)) {
            if (!"Seq Scan".equals(node.getNodeType()) && !"Bitmap Heap Scan".equals(node.getNodeType())) continue;
            if (node.getFilter() == null) continue;

            String filter = node.getFilter();
            String relation = node.getRelation();
            if (relation == null || "NotAvailable".equals(relation)) continue;

            // Look for equality filters with constant values
            // e.g., "status = 'done'", "type = 5"
            List<PartialIndexCandidate> candidates = extractPartialIndexCandidates(filter, relation);
            for (PartialIndexCandidate candidate : candidates) {
                String idxName = "idx_" + relation + "_" + candidate.column + "_" + candidate.value.replaceAll("[^a-zA-Z0-9]", "");
                String whereClause = candidate.column + " = " + candidate.value;
                String sql = "CREATE INDEX " + idxName + " ON " + relation
                        + " (...) WHERE " + whereClause + ";";

                double benefit = estimatePartialIndexBenefit(node, candidate);

                recommendations.add(new IndexRecommendation()
                        .withTableName(relation)
                        .withIndexName(idxName)
                        .withIndexType("BTREE")
                        .withColumns(candidate.column)
                        .withWhereClause(whereClause)
                        .withSql(sql)
                        .withReason("Partial index for highly selective filter: " + whereClause)
                        .withSeverity("medium")
                        .withConfidence(0.7)
                        .withPartial(true)
                        .withEstimatedBenefit(benefit));
            }
        }

        return recommendations;
    }

    // Expression index recommendations
    private List<IndexRecommendation> generateExpressionIndexRecommendations(SelectStatement select, PlanSummary plan) {
        List<IndexRecommendation> recommendations = new ArrayList<>();

        // Look for expressions in WHERE conditions that could benefit from expression indexes
        // e.g., LOWER(email), EXTRACT(YEAR FROM created_at), etc.
        for (String column : FilterColumns.of(select)) {
            if (column.matches(".*\\(.*\\).*.*")) {
                // Likely an expression
                String table = resolveTable(column, select);
                if (table == null) continue;

                String idxName = "idx_" + table + "_expr_" + column.replaceAll("[^a-zA-Z0-9]", "_");
                String sql = "CREATE INDEX " + idxName + " ON " + table + " (" + column + ");";

                recommendations.add(new IndexRecommendation()
                        .withTableName(table)
                        .withIndexName(idxName)
                        .withIndexType("BTREE")
                        .withColumns(column)
                        .withExpression(column)
                        .withSql(sql)
                        .withReason("Expression index for: " + column)
                        .withSeverity("low")
                        .withConfidence(0.6)
                        .withExpressionIndex(true)
                        .withEstimatedBenefit(0.5));
            }
        }

        // Also check for function calls in ORDER BY
        for (OrderByItem item : select.getOrderBy()) {
            String col = item.getColumn();
            if (col.matches(".*\\(.*\\).*")) {
                String table = resolveTable(col, select);
                if (table == null) continue;

                String idxName = "idx_" + table + "_expr_" + col.replaceAll("[^a-zA-Z0-9]", "_");
                String order = item.isDescending() ? " DESC" : "";
                String sql = "CREATE INDEX " + idxName + " ON " + table + " (" + col + order + ");";

                recommendations.add(new IndexRecommendation()
                        .withTableName(table)
                        .withIndexName(idxName)
                        .withIndexType("BTREE")
                        .withColumns(col + order)
                        .withExpression(col)
                        .withSql(sql)
                        .withReason("Expression index for ORDER BY: " + col)
                        .withSeverity("low")
                        .withConfidence(0.6)
                        .withExpressionIndex(true)
                        .withEstimatedBenefit(0.5));
            }
        }

        return recommendations;
    }

    // Evaluate hypothetical indexes using HypoPG
    private List<IndexRecommendation> evaluateHypotheticalIndexes(SelectStatement select, PlanSummary plan) {
        List<IndexRecommendation> recommendations = new ArrayList<>();

        if (!hypoPgService.isHypoPgAvailable()) return recommendations;

        // Get the SQL for the query
        String querySql = select.toString();

        // Extract candidate indexes from the plan
        List<HypotheticalCandidate> candidates = extractHypotheticalCandidates(plan);

        for (HypotheticalCandidate candidate : candidates) {
            Map<String, Object> comparison = hypoPgService.comparePlans(
                    querySql, candidate.table, candidate.columns, candidate.whereClause);

            Double improvement = (Double) comparison.get("improvementPercent");
            if (improvement != null && improvement > 5.0) { // > 5% improvement
                String idxName = "idx_hypo_" + candidate.table + "_" + candidate.columns.replace(", ", "_").replace(".", "_");
                String createSql = "CREATE INDEX " + idxName + " ON " + candidate.table
                        + " (" + candidate.columns + ");";

                recommendations.add(new IndexRecommendation()
                        .withTableName(candidate.table)
                        .withIndexName(idxName)
                        .withIndexType("BTREE")
                        .withColumns(candidate.columns)
                        .withWhereClause(candidate.whereClause)
                        .withSql(createSql)
                        .withReason("HypoPG verified: " + String.format("%.1f", improvement) + "% improvement")
                        .withSeverity(improvement > 50 ? "high" : "medium")
                        .withConfidence(0.9)
                        .withHypothetical(true)
                        .withEstimatedBenefit(Math.min(improvement / 100.0, 0.95)));
            }

            // Cleanup hypothetical index
            Long oid = (Long) comparison.get("indexOid");
            if (oid != null) {
                hypoPgService.dropHypotheticalIndex(oid);
            }
        }

        return recommendations;
    }

    // ========== Helper Classes and Methods ==========

    private static class PartialIndexCandidate {
        String column;
        String value;
        String operator;
    }

    private List<PartialIndexCandidate> extractPartialIndexCandidates(String filter, String relation) {
        List<PartialIndexCandidate> candidates = new ArrayList<>();

        // Simple regex for equality with constant: col = 'value' or col = 123
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\w+)\\s*(=|!=|<>|<|>|<=|>=)\\s*('[^']*'|\\d+(\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(filter);

        while (matcher.find()) {
            PartialIndexCandidate c = new PartialIndexCandidate();
            c.column = matcher.group(1);
            c.operator = matcher.group(2);
            c.value = matcher.group(3);
            candidates.add(c);
        }

        return candidates;
    }

    private double estimatePartialIndexBenefit(PlanSummary node, PartialIndexCandidate candidate) {
        if (!node.hasActualMetrics()) return 0.5;

        long estimatedRows = node.getEstimatedRows();
        long actualRows = node.getActualRows();

        if (estimatedRows <= 0) return 0.5;

        double selectivity = (double) actualRows / estimatedRows;
        // Highly selective filter -> higher benefit for partial index
        if (selectivity < 0.01) return 0.9;
        if (selectivity < 0.05) return 0.7;
        if (selectivity < 0.1) return 0.5;
        return 0.3;
    }

    private static class HypotheticalCandidate {
        String table;
        String columns;
        String whereClause;
    }

    private List<HypotheticalCandidate> extractHypotheticalCandidates(PlanSummary plan) {
        List<HypotheticalCandidate> candidates = new ArrayList<>();

        for (PlanSummary node : flattenPlan(plan)) {
            if (!"Seq Scan".equals(node.getNodeType())) continue;
            if (node.getFilter() == null) continue;

            String relation = node.getRelation();
            if (relation == null || "NotAvailable".equals(relation)) continue;

            // Extract columns from filter
            String filter = node.getFilter();
            List<String> cols = extractColumnsFromFilter(filter, relation);

            if (!cols.isEmpty()) {
                HypotheticalCandidate c = new HypotheticalCandidate();
                c.table = relation;
                c.columns = String.join(", ", cols);
                c.whereClause = filter;
                candidates.add(c);
            }
        }

        return candidates;
    }

    private List<String> extractColumnsFromFilter(String filter, String relation) {
        List<String> cols = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\w+)\\s*(=|!=|<>|<|>|<=|>=)");
        java.util.regex.Matcher matcher = pattern.matcher(filter);
        while (matcher.find()) {
            String col = matcher.group(1);
            // Check if column belongs to this relation
            if (col.startsWith(relation + ".") || !col.contains(".")) {
                cols.add(col.replace(relation + ".", ""));
            }
        }
        return cols;
    }

    private List<PlanSummary> flattenPlan(PlanSummary node) {
        List<PlanSummary> out = new ArrayList<>();
        out.add(node);
        if (node.getChildren() != null) {
            for (PlanSummary child : node.getChildren()) {
                out.addAll(flattenPlan(child));
            }
        }
        return out;
    }

    private List<PlanSummary> flattenPlanStatic(PlanSummary node) {
        return flattenPlan(node);
    }

    // Helper methods from original IndexAdvisor
    private String bareName(String column) {
        int dot = column.indexOf('.');
        return dot >= 0 ? column.substring(dot + 1) : column;
    }

    private String resolveTable(String column, SelectStatement select) {
        if (!column.contains(".")) {
            return select.getTable().getTableName();
        }
        String qualifier = column.substring(0, column.indexOf('.'));
        TableReference main = select.getTable();
        if (qualifier.equals(main.getAlias()) || qualifier.equals(main.getTableName())) {
            return main.getTableName();
        }
        for (JoinClause join : select.getJoins()) {
            TableReference ref = join.getTable();
            if (qualifier.equals(ref.getAlias()) || qualifier.equals(ref.getTableName())) {
                return ref.getTableName();
            }
        }
        return null;
    }
}