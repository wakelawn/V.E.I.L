package com.veil.benchmark;

// Benchmark run configuration.
public class BenchmarkConfig {

    private int warmupRuns = 3;
    private int measurementRuns = 10;
    private long statementTimeoutMs = 30_000;
    private boolean explainAnalyze = true;
    private boolean readOnly = true;

    // Getters and setters
    public int getWarmupRuns() { return warmupRuns; }
    public void setWarmupRuns(int warmupRuns) { this.warmupRuns = Math.max(0, warmupRuns); }

    public int getMeasurementRuns() { return measurementRuns; }
    public void setMeasurementRuns(int measurementRuns) { this.measurementRuns = Math.max(1, measurementRuns); }

    public long getStatementTimeoutMs() { return statementTimeoutMs; }
    public void setStatementTimeoutMs(long statementTimeoutMs) { this.statementTimeoutMs = Math.max(1000, statementTimeoutMs); }

    public boolean isExplainAnalyze() { return explainAnalyze; }
    public void setExplainAnalyze(boolean explainAnalyze) { this.explainAnalyze = explainAnalyze; }

    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    // Builder-style
    public BenchmarkConfig withWarmupRuns(int warmupRuns) { setWarmupRuns(warmupRuns); return this; }
    public BenchmarkConfig withMeasurementRuns(int measurementRuns) { setMeasurementRuns(measurementRuns); return this; }
    public BenchmarkConfig withStatementTimeoutMs(long statementTimeoutMs) { setStatementTimeoutMs(statementTimeoutMs); return this; }
    public BenchmarkConfig withExplainAnalyze(boolean explainAnalyze) { setExplainAnalyze(explainAnalyze); return this; }
    public BenchmarkConfig withReadOnly(boolean readOnly) { setReadOnly(readOnly); return this; }

    // Defaults
    public static BenchmarkConfig defaults() { return new BenchmarkConfig(); }
}