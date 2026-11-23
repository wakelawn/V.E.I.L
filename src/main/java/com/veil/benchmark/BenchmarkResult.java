package com.veil.benchmark;

import java.util.List;
import java.util.stream.Collectors;

// Aggregated benchmark results.
public class BenchmarkResult {

    private final String originalSql;
    private final String optimizedSql;
    private final BenchmarkConfig config;
    private final List<BenchmarkRunResult> warmupRuns;
    private final List<BenchmarkRunResult> measurementRuns;

    private final Long medianExecutionMs;
    private final Long medianPlanningMs;
    private final Double avgExecutionMs;
    private final Double minExecutionMs;
    private final Double maxExecutionMs;
    private final Double stdDevExecutionMs;
    private final Long medianActualRows;
    private final Double avgActualTimeMs;
    private final Long totalActualLoops;
    private final int successfulRuns;
    private final int failedRuns;

    public BenchmarkResult(String originalSql, String optimizedSql,
                           BenchmarkConfig config,
                           List<BenchmarkRunResult> warmupRuns,
                           List<BenchmarkRunResult> measurementRuns) {

        this.originalSql = originalSql;
        this.optimizedSql = optimizedSql;
        this.config = config;
        this.warmupRuns = warmupRuns;
        this.measurementRuns = measurementRuns;

        // Compute statistics from successful measurement runs
        List<BenchmarkRunResult> successful = measurementRuns.stream()
                .filter(BenchmarkRunResult::isSuccess)
                .collect(Collectors.toList());

        this.successfulRuns = successful.size();
        this.failedRuns = measurementRuns.size() - successful.size();

        if (!successful.isEmpty()) {
            List<Long> execTimes = successful.stream().map(BenchmarkRunResult::getExecutionTimeMs).sorted().collect(Collectors.toList());
            List<Long> planTimes = successful.stream().map(BenchmarkRunResult::getPlanningTimeMs).sorted().collect(Collectors.toList());
            List<Long> rows = successful.stream().map(BenchmarkRunResult::getActualRows).sorted().collect(Collectors.toList());
            List<Double> actualTimes = successful.stream().map(BenchmarkRunResult::getActualTotalTimeMs).sorted().collect(Collectors.toList());
            List<Long> loops = successful.stream().map(BenchmarkRunResult::getActualLoops).sorted().collect(Collectors.toList());

            this.medianExecutionMs = percentile(execTimes, 50);
            this.medianPlanningMs = percentile(planTimes, 50);
            this.avgExecutionMs = execTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            this.minExecutionMs = (double) execTimes.get(0);
            this.maxExecutionMs = (double) execTimes.get(execTimes.size() - 1);
            this.stdDevExecutionMs = standardDeviation(execTimes.stream().mapToLong(Long::longValue).toArray(), this.avgExecutionMs);
            this.medianActualRows = percentile(rows, 50);
            this.avgActualTimeMs = actualTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            this.totalActualLoops = loops.stream().mapToLong(Long::longValue).sum();
        } else {
            this.medianExecutionMs = null;
            this.medianPlanningMs = null;
            this.avgExecutionMs = null;
            this.minExecutionMs = null;
            this.maxExecutionMs = null;
            this.stdDevExecutionMs = null;
            this.medianActualRows = null;
            this.avgActualTimeMs = null;
            this.totalActualLoops = null;
        }
    }

    private static Long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return null;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private static double standardDeviation(long[] values, double mean) {
        if (values.length < 2) return 0.0;
        double sum = 0;
        for (long v : values) {
            sum += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    // Getters
    public String getOriginalSql() { return originalSql; }
    public String getOptimizedSql() { return optimizedSql; }
    public BenchmarkConfig getConfig() { return config; }
    public List<BenchmarkRunResult> getWarmupRuns() { return warmupRuns; }
    public List<BenchmarkRunResult> getMeasurementRuns() { return measurementRuns; }

    public Long getMedianExecutionMs() { return medianExecutionMs; }
    public Long getMedianPlanningMs() { return medianPlanningMs; }
    public Double getAvgExecutionMs() { return avgExecutionMs; }
    public Double getMinExecutionMs() { return minExecutionMs; }
    public Double getMaxExecutionMs() { return maxExecutionMs; }
    public Double getStdDevExecutionMs() { return stdDevExecutionMs; }
    public Long getMedianActualRows() { return medianActualRows; }
    public Double getAvgActualTimeMs() { return avgActualTimeMs; }
    public Long getTotalActualLoops() { return totalActualLoops; }
    public int getSuccessfulRuns() { return successfulRuns; }
    public int getFailedRuns() { return failedRuns; }

    // Improvement calculation
    public Double getImprovementPercent(BenchmarkResult other) {
        if (this.medianExecutionMs == null || other.medianExecutionMs == null) return null;
        if (other.medianExecutionMs == 0) return null;
        return ((other.medianExecutionMs - this.medianExecutionMs) / (double) other.medianExecutionMs) * 100.0;
    }
}