package com.veil.analysis;

import com.veil.core.database.DatabaseAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

// HypoPG integration for hypothetical index evaluation.
// HypoPG is a PostgreSQL extension that allows creating hypothetical indexes
// and testing their effect on query plans without actually creating them.
@Component
@ConditionalOnProperty(name = "veil.features.hypopg", havingValue = "true", matchIfMissing = false)
public class HypoPgService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseAdapter adapter;
    private final boolean hypopgAvailable;

    public HypoPgService(DatabaseAdapter adapter) {
        this.adapter = adapter;
        this.jdbcTemplate = getJdbcTemplate(adapter);
        this.hypopgAvailable = checkHypoPgAvailable();
    }

    private JdbcTemplate getJdbcTemplate(DatabaseAdapter adapter) {
        // Try to get JdbcTemplate from the adapter
        try {
            return (JdbcTemplate) adapter.getClass().getMethod("getJdbcTemplate").invoke(adapter);
        } catch (Exception e) {
            // Fallback: try to get from context
            return null;
        }
    }

    // Check if HypoPG extension is available
    private boolean checkHypoPgAvailable() {
        if (jdbcTemplate == null) return false;
        try {
            String result = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'hypopg')",
                    String.class);
            return "true".equalsIgnoreCase(result);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHypoPgAvailable() {
        return hypopgAvailable;
    }

    // Create a hypothetical index and return its OID
    public Long createHypotheticalIndex(String tableName, String columns, String whereClause, String indexName) {
        if (!hypopgAvailable) return null;
        if (jdbcTemplate == null) return null;

        try {
            StringBuilder sql = new StringBuilder("SELECT hypopg_create_index(");
            sql.append("'CREATE INDEX ").append(indexName)
                    .append(" ON ").append(tableName)
                    .append(" USING BTREE (").append(columns).append(")'");

            if (whereClause != null && !whereClause.isBlank()) {
                sql.append(", '").append(whereClause.replace("'", "''")).append("'");
            }
            sql.append(")");

            Long oid = jdbcTemplate.queryForObject(sql.toString(), Long.class);
            return oid;
        } catch (Exception e) {
            // Hypothetical index creation failed
            return null;
        }
    }

    // Drop a hypothetical index by OID
    public void dropHypotheticalIndex(Long oid) {
        if (!hypopgAvailable || oid == null || jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute("SELECT hypopg_drop_index(" + oid + ")");
        } catch (Exception ignored) {
            // Ignore cleanup failures
        }
    }

    // Get all hypothetical indexes
    public List<Map<String, Object>> getHypotheticalIndexes() {
        if (!hypopgAvailable || jdbcTemplate == null) return List.of();
        try {
            return jdbcTemplate.queryForList("SELECT * FROM hypopg()");
        } catch (Exception e) {
            return List.of();
        }
    }

    // Clean up all hypothetical indexes
    public void clearAllHypotheticalIndexes() {
        if (!hypopgAvailable || jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute("SELECT hypopg_reset()");
        } catch (Exception ignored) {
        }
    }

    // Evaluate a query plan with hypothetical indexes active
    public String explainWithHypothetical(String sql) {
        if (!hypopgAvailable || jdbcTemplate == null) return null;
        try {
            String explainSql = "EXPLAIN (FORMAT JSON, ANALYZE, BUFFERS) " + sql;
            return jdbcTemplate.queryForObject(explainSql, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    // Compare plans: with and without hypothetical index
    public Map<String, Object> comparePlans(String sql, String tableName, String columns, String whereClause) {
        Map<String, Object> result = new HashMap<>();

        // Plan without hypothetical index
        String baselineJson = explainWithHypothetical(sql);
        result.put("baseline", baselineJson);

        // Create hypothetical index
        String indexName = "hypo_" + tableName + "_" + columns.replace(", ", "_").replace(".", "_");
        Long oid = createHypotheticalIndex(tableName, columns, whereClause, indexName);

        if (oid != null) {
            // Plan with hypothetical index
            String hypoJson = explainWithHypothetical(sql);
            result.put("withHypothetical", hypoJson);
            result.put("indexOid", oid);

            // Compare costs
            double baselineCost = extractTotalCost(baselineJson);
            double hypoCost = extractTotalCost(hypoJson);
            result.put("baselineCost", baselineCost);
            result.put("hypotheticalCost", hypoCost);

            if (baselineCost > 0 && hypoCost > 0) {
                double improvement = ((baselineCost - hypoCost) / baselineCost) * 100.0;
                result.put("improvementPercent", improvement);
            }

            // Clean up
            dropHypotheticalIndex(oid);
        } else {
            result.put("error", "Failed to create hypothetical index");
        }

        return result;
    }

    private double extractTotalCost(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (root.isArray() && root.size() > 0 && root.get(0).has("Plan")) {
                return root.get(0).get("Plan").get("Total Cost").asDouble();
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0.0;
    }
}