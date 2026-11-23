package com.veil.benchmark;

import java.util.List;

// Result of a single benchmark run.
public class BenchmarkRunResult {

    private final int runNumber;
    private final long executionTimeMs;
    private final long planningTimeMs;
    private final long actualRows;
    private final double actualTotalTimeMs;
    private final long actualLoops;
    private final boolean success;
    private final String error;

    public BenchmarkRunResult(int runNumber, long executionTimeMs, long planningTimeMs,
                              long actualRows, double actualTotalTimeMs, long actualLoops,
                              boolean success, String error) {
        this.runNumber = runNumber;
        this.executionTimeMs = executionTimeMs;
        this.planningTimeMs = planningTimeMs;
        this.actualRows = actualRows;
        this.actualTotalTimeMs = actualTotalTimeMs;
        this.actualLoops = actualLoops;
        this.success = success;
        this.error = error;
    }

    // Factory methods
    public static BenchmarkRunResult success(int runNumber, long executionTimeMs, long planningTimeMs,
                                              long actualRows, double actualTotalTimeMs, long actualLoops) {
        return new BenchmarkRunResult(runNumber, executionTimeMs, planningTimeMs,
                actualRows, actualTotalTimeMs, actualLoops, true, null);
    }

    public static BenchmarkRunResult failure(int runNumber, String error) {
        return new BenchmarkRunResult(runNumber, 0, 0, 0, 0, 0, false, error);
    }

    // Getters
    public int getRunNumber() { return runNumber; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public long getPlanningTimeMs() { return planningTimeMs; }
    public long getActualRows() { return actualRows; }
    public double getActualTotalTimeMs() { return actualTotalTimeMs; }
    public long getActualLoops() { return actualLoops; }
    public boolean isSuccess() { return success; }
    public String getError() { return error; }
}