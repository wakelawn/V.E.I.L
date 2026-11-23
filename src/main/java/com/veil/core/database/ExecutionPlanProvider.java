package com.veil.core.database;

public interface ExecutionPlanProvider {

    DatabaseType getDatabaseType();

    String getExecutionPlan(String sql);

    // EXPLAIN ANALYZE - executes the query and returns actual metrics
    // Implementations should enforce safety: read-only, timeout, etc.
    default String getExecutionPlanAnalyze(String sql) {
        throw new UnsupportedOperationException("EXPLAIN ANALYZE not supported by this provider");
    }
}