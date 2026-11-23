package com.veil.benchmark;

import com.veil.security.SqlValidator;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

// REST endpoint for query benchmarking.
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    // Upper bounds for client-supplied benchmark configuration, so a
    // malicious or accidental payload cannot exhaust the connection pool
    // or CPU.
    private static final int MAX_WARMUP_RUNS = 10;
    private static final int MAX_MEASUREMENT_RUNS = 50;
    private static final long MAX_STATEMENT_TIMEOUT_MS = 300_000;

    private final BenchmarkService benchmarkService;
    private final SqlValidator sqlValidator;

    public BenchmarkController(BenchmarkService benchmarkService,
                               SqlValidator sqlValidator) {
        this.benchmarkService = benchmarkService;
        this.sqlValidator = sqlValidator;
    }

    @PostMapping
    public Map<String, Object> benchmark(@RequestBody Map<String, Object> request) {

        if (request == null) {
            return Map.of("error", "Request body is required");
        }

        String originalSql = asString(request.get("originalSql"));
        String optimizedSql = asString(request.get("optimizedSql"));

        // Both queries must be read-only SELECT/WITH statements.
        sqlValidator.validateSelectOnly(originalSql);
        sqlValidator.validateSelectOnly(optimizedSql);

        BenchmarkConfig config = BenchmarkConfig.defaults();

        // Parse optional config with hard upper bounds.
        if (request.containsKey("warmupRuns")) {
            config.setWarmupRuns(clamp(request.get("warmupRuns"), 0, MAX_WARMUP_RUNS));
        }
        if (request.containsKey("measurementRuns")) {
            config.setMeasurementRuns(clamp(request.get("measurementRuns"), 1, MAX_MEASUREMENT_RUNS));
        }
        if (request.containsKey("statementTimeoutMs")) {
            config.setStatementTimeoutMs(clamp(
                    request.get("statementTimeoutMs"), 1000L, MAX_STATEMENT_TIMEOUT_MS));
        }
        if (request.containsKey("explainAnalyze")) {
            config.setExplainAnalyze(asBoolean(request.get("explainAnalyze")));
        }
        if (request.containsKey("readOnly")) {
            config.setReadOnly(asBoolean(request.get("readOnly")));
        }

        try {
            BenchmarkResult result = benchmarkService.benchmark(originalSql, optimizedSql, config);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("originalSql", result.getOriginalSql());
            response.put("optimizedSql", result.getOptimizedSql());
            response.put("config", Map.of(
                    "warmupRuns", config.getWarmupRuns(),
                    "measurementRuns", config.getMeasurementRuns(),
                    "statementTimeoutMs", config.getStatementTimeoutMs(),
                    "explainAnalyze", config.isExplainAnalyze(),
                    "readOnly", config.isReadOnly()
            ));
            response.put("medianExecutionMs", result.getMedianExecutionMs());
            response.put("avgExecutionMs", result.getAvgExecutionMs());
            response.put("minExecutionMs", result.getMinExecutionMs());
            response.put("maxExecutionMs", result.getMaxExecutionMs());
            response.put("stdDevExecutionMs", result.getStdDevExecutionMs());
            response.put("medianPlanningMs", result.getMedianPlanningMs());
            response.put("medianActualRows", result.getMedianActualRows());
            response.put("avgActualTimeMs", result.getAvgActualTimeMs());
            response.put("totalActualLoops", result.getTotalActualLoops());
            response.put("successfulRuns", result.getSuccessfulRuns());
            response.put("failedRuns", result.getFailedRuns());
            response.put("warmupRuns", result.getWarmupRuns().stream().map(r -> Map.of(
                    "run", r.getRunNumber(),
                    "success", r.isSuccess(),
                    "timeMs", r.getExecutionTimeMs(),
                    "error", r.getError()
            )).collect(Collectors.toList()));
            response.put("measurementRuns", result.getMeasurementRuns().stream().map(r -> Map.of(
                    "run", r.getRunNumber(),
                    "success", r.isSuccess(),
                    "timeMs", r.getExecutionTimeMs(),
                    "planningMs", r.getPlanningTimeMs(),
                    "actualRows", r.getActualRows(),
                    "actualTimeMs", r.getActualTotalTimeMs(),
                    "loops", r.getActualLoops(),
                    "error", r.getError()
            )).collect(Collectors.toList()));

            return response;
        } catch (Exception e) {
            return Map.of("error", "Benchmark failed");
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean b && b;
    }

    private static int clamp(Object value, int min, int max) {
        int parsed;
        try {
            parsed = Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return min;
        }
        return Math.min(max, Math.max(min, parsed));
    }

    private static long clamp(Object value, long min, long max) {
        long parsed;
        try {
            parsed = Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return min;
        }
        return Math.min(max, Math.max(min, parsed));
    }
}
