package com.veil.ai;

import com.veil.core.database.DatabaseAdapter;
import com.veil.explain.PlanSummary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;

// Differential testing framework for AI-generated SQL.
// Executes both original and AI-optimized queries and compares results.
public class AiDifferentialTester {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public AiDifferentialTester(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Runs differential test: executes both queries and compares results.
    public DifferentialTestResult testEquivalence(String originalSql, String optimizedSql) {
        if (optimizedSql == null || optimizedSql.isBlank()) {
            return DifferentialTestResult.skipped("No optimized SQL provided");
        }

        try {
            // Execute original query
            QueryResult result1 = executeQuery(originalSql);
            if (!result1.success) {
                return DifferentialTestResult.error("Original query failed: " + result1.error);
            }

            // Execute optimized query
            QueryResult result2 = executeQuery(optimizedSql);
            if (!result2.success) {
                return DifferentialTestResult.error("Optimized query failed: " + result2.error);
            }

            // Compare results
            ComparisonResult comparison = compareResults(result1, result2);

            if (comparison.areEquivalent()) {
                return DifferentialTestResult.equivalent(
                        "Results are equivalent",
                        result1.executionTimeMs,
                        result2.executionTimeMs,
                        result1.rowCount,
                        result2.rowCount
                );
            } else {
                return DifferentialTestResult.different(
                        "Results differ: " + comparison.differenceDescription,
                        result1.executionTimeMs,
                        result2.executionTimeMs,
                        result1.rowCount,
                        result2.rowCount
                );
            }

        } catch (Exception e) {
            return DifferentialTestResult.error("Test execution error: " + e.getMessage());
        }
    }

    private QueryResult executeQuery(String sql) {
        long startNs = System.nanoTime();
        try (Connection conn = dataSource.getConnection()) {
            // Set statement timeout
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL statement_timeout = 30000");
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    List<List<Object>> rows = new ArrayList<>();
                    int columnCount = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(rs.getObject(i));
                        }
                        rows.add(row);
                    }
                    long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                    return new QueryResult(true, null, rows, rs.getMetaData().getColumnCount(), 
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
                }
            }
        } catch (SQLException e) {
            return new QueryResult(false, e.getMessage(), null, 0, 0);
        }
    }

    private ComparisonResult compareResults(QueryResult r1, QueryResult r2) {
        // Check row count
        if (r1.rowCount != r2.rowCount) {
            return new ComparisonResult(false, "Row count differs: " + r1.rowCount + " vs " + r2.rowCount);
        }

        // Check column count
        if (r1.columnCount != r2.columnCount) {
            return new ComparisonResult(false, "Column count differs: " + r1.columnCount + " vs " + r2.columnCount);
        }

        // Compare row by row (order-independent for now)
        // Note: This is a simplified comparison. In production, would need proper
        // handling of NULLs, floating point precision, unordered results, etc.
        for (int i = 0; i < r1.rowCount; i++) {
            List<Object> row1 = r1.rows.get(i);
            List<Object> row2 = r2.rows.get(i);

            if (row1.size() != row2.size()) {
                return new ComparisonResult(false, "Row " + i + " column count differs");
            }

            for (int j = 0; j < row1.size(); j++) {
                Object v1 = row1.get(j);
                Object v2 = row2.get(j);

                if (!valuesEqual(v1, v2)) {
                    return new ComparisonResult(false, 
                            "Row " + i + ", col " + j + " differs: " + v1 + " vs " + v2);
                }
            }
        }

        return new ComparisonResult(true, "Results match");
    }

    private boolean valuesEqual(Object v1, Object v2) {
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;
        
        // Handle numeric types
        if (v1 instanceof Number && v2 instanceof Number) {
            double d1 = ((Number) v1).doubleValue();
            double d2 = ((Number) v2).doubleValue();
            return Math.abs(d1 - d2) < 1e-9;
        }
        
        return v1.equals(v2);
    }

    // Inner classes
    static class QueryResult {
        final boolean success;
        final String error;
        final List<List<Object>> rows;
        final int columnCount;
        final int rowCount;
        final long executionTimeMs;

        QueryResult(boolean success, String error, List<List<Object>> rows, 
                    int columnCount, long executionTimeMs) {
            this.success = success;
            this.error = error;
            this.rows = rows;
            this.columnCount = columnCount;
            this.rowCount = rows != null ? rows.size() : 0;
            this.executionTimeMs = executionTimeMs;
        }
    }

    static class ComparisonResult {
        final boolean equivalent;
        final String differenceDescription;

        ComparisonResult(boolean equivalent, String differenceDescription) {
            this.equivalent = equivalent;
            this.differenceDescription = differenceDescription;
        }

        boolean areEquivalent() { return equivalent; }
    }

    public static class DifferentialTestResult {
        private final String status; // EQUIVALENT, DIFFERENT, ERROR, SKIPPED
        private final String message;
        private final long originalTimeMs;
        private final long optimizedTimeMs;
        private final int originalRows;
        private final int optimizedRows;

        private DifferentialTestResult(String status, String message, 
                long originalTimeMs, long optimizedTimeMs,
                int originalRows, int optimizedRows) {
            this.status = status;
            this.message = message;
            this.originalTimeMs = originalTimeMs;
            this.optimizedTimeMs = optimizedTimeMs;
            this.originalRows = originalRows;
            this.optimizedRows = optimizedRows;
        }

        public static DifferentialTestResult equivalent(String msg, long t1, long t2, int r1, int r2) {
            return new DifferentialTestResult("EQUIVALENT", msg, t1, t2, r1, r2);
        }

        public static DifferentialTestResult different(String msg, long t1, long t2, int r1, int r2) {
            return new DifferentialTestResult("DIFFERENT", msg, t1, t2, r1, r2);
        }

        public static DifferentialTestResult error(String msg) {
            return new DifferentialTestResult("ERROR", msg, 0, 0, 0, 0);
        }

        public static DifferentialTestResult skipped(String msg) {
            return new DifferentialTestResult("SKIPPED", msg, 0, 0, 0, 0);
        }

        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public long getOriginalTimeMs() { return originalTimeMs; }
        public long getOptimizedTimeMs() { return optimizedTimeMs; }
        public int getOriginalRows() { return originalRows; }
        public int getOptimizedRows() { return optimizedRows; }
        
        public double getImprovementPercent() {
            if (originalTimeMs == 0) return 0;
            return ((double)(originalTimeMs - optimizedTimeMs) / originalTimeMs) * 100.0;
        }
    }
}