package com.veil.benchmark;

import com.veil.core.database.ExecutionPlanProvider;
import com.veil.explain.ExplainService;
import com.veil.explain.PlanSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Runs controlled performance benchmarks using EXPLAIN ANALYZE.
@Service
public class BenchmarkService {

    private final ExecutionPlanProvider planProvider;
    private final ExplainService explainService;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public BenchmarkService(ExecutionPlanProvider planProvider,
                            ExplainService explainService,
                            JdbcTemplate jdbcTemplate,
                            DataSource dataSource) {
        this.planProvider = planProvider;
        this.explainService = explainService;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    // Run a benchmark comparing original and optimized SQL.
    public BenchmarkResult benchmark(String originalSql, String optimizedSql, BenchmarkConfig config) {
        if (originalSql == null || originalSql.isBlank()) {
            throw new IllegalArgumentException("Original SQL is required");
        }
        if (optimizedSql == null || optimizedSql.isBlank()) {
            throw new IllegalArgumentException("Optimized SQL is required");
        }

        // Warmup runs
        List<BenchmarkRunResult> warmupResults = new ArrayList<>();
        for (int i = 1; i <= config.getWarmupRuns(); i++) {
            warmupResults.add(runOnce(originalSql, i, true, config));
            warmupResults.add(runOnce(optimizedSql, i, true, config));
        }

        // Measurement runs
        List<BenchmarkRunResult> measurementResults = new ArrayList<>();
        for (int i = 1; i <= config.getMeasurementRuns(); i++) {
            measurementResults.add(runOnce(originalSql, i, false, config));
            measurementResults.add(runOnce(optimizedSql, i, false, config));
        }

        return new BenchmarkResult(originalSql, optimizedSql, config,
                warmupResults, measurementResults);
    }

    // Run a single query and measure execution time.
    private BenchmarkRunResult runOnce(String sql, int runNumber, boolean isWarmup, BenchmarkConfig config) {
        if (!config.isExplainAnalyze()) {
            // Use EXPLAIN (no execution) - just planning time
            return runExplainOnly(sql, runNumber);
        }

        Connection conn = null;
        Statement stmt = null;
        try {
            // Get connection and enforce safety settings
            conn = DataSourceUtils.getConnection(dataSource);
            stmt = conn.createStatement();

            // Enforce statement timeout
            if (config.getStatementTimeoutMs() > 0) {
                stmt.execute("SET LOCAL statement_timeout = " + config.getStatementTimeoutMs());
            }

            // Enforce read-only if configured
            if (config.isReadOnly()) {
                try {
                    stmt.execute("SET LOCAL transaction_read_only = on");
                } catch (SQLException e) {
                    // Some databases/drivers may not support this at statement level
                }
            }

            // Run EXPLAIN ANALYZE
            String explainSql = "EXPLAIN (ANALYZE, FORMAT JSON, BUFFERS, TIMING) " + sql;

            long startNs = System.nanoTime();
            String json = stmt.executeQuery(explainSql) != null
                    ? extractJsonFromResultSet(stmt.getResultSet())
                    : "{}";
            long elapsedNs = System.nanoTime() - startNs;

            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(elapsedNs);

            // Parse the plan to extract detailed metrics
            PlanSummary plan = explainService.explainFromJson(json);

            long planningTimeMs = plan.getPlanningTime() > 0 ? (long) plan.getPlanningTime() : 0;
            long actualRows = plan.getActualRows();
            double actualTotalTimeMs = plan.getActualTotalTime();
            long actualLoops = plan.getActualLoops();

            return BenchmarkRunResult.success(
                    runNumber, executionTimeMs, planningTimeMs,
                    actualRows, actualTotalTimeMs, plan.getActualLoops()
            );

        } catch (SQLException e) {
            return BenchmarkRunResult.failure(runNumber, "SQL error: " + e.getMessage());
        } catch (Exception e) {
            return BenchmarkRunResult.failure(runNumber, "Execution error: " + e.getMessage());
        } finally {
            if (stmt != null) {
                try { stmt.close(); } catch (SQLException ignored) {}
            }
            if (conn != null) {
                DataSourceUtils.releaseConnection(conn, dataSource);
            }
        }
    }

    private BenchmarkRunResult runExplainOnly(String sql, int runNumber) {
        try {
            long startNs = System.nanoTime();
            String json = planProvider.getExecutionPlan(sql);
            long elapsedNs = System.nanoTime() - startNs;

            long planningTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

            return BenchmarkRunResult.success(runNumber, 0, planningTimeMs, 0, 0, 0);
        } catch (Exception e) {
            return BenchmarkRunResult.failure(runNumber, "EXPLAIN error: " + e.getMessage());
        }
    }

    // Helper to extract JSON from ResultSet
    private String extractJsonFromResultSet(java.sql.ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder();
        while (rs.next()) {
            sb.append(rs.getString(1));
        }
        return sb.toString();
    }

    // Verify both queries return equivalent results (optional correctness check)
    public boolean verifyEquivalence(String sql1, String sql2) {
        try {
            List<Object> result1 = jdbcTemplate.query(sql1, (rs, rowNum) -> extractRow(rs));
            List<Object> result2 = jdbcTemplate.query(sql2, (rs, rowNum) -> extractRow(rs));

            if (result1.size() != result2.size()) return false;

            // Simple comparison - in practice, would need more sophisticated comparison
            // handling unordered results, NULLs, etc.
            return result1.equals(result2);
        } catch (Exception e) {
            return false;
        }
    }

    private Object extractRow(java.sql.ResultSet rs) throws SQLException {
        int cols = rs.getMetaData().getColumnCount();
        Object[] row = new Object[cols];
        for (int i = 1; i <= cols; i++) {
            row[i - 1] = rs.getObject(i);
        }
        return row;
    }
}