package com.veil.benchmark;

import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

// REST endpoint for query benchmarking.
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @PostMapping
    public Map<String, Object> benchmark(@RequestBody Map<String, Object> request) {

        String originalSql = (String) request.get("originalSql");
        String optimizedSql = (String) request.get("optimizedSql");

        if (originalSql == null || originalSql.isBlank()) {
            return Map.of("error", "originalSql is required");
        }
        if (optimizedSql == null || optimizedSql.isBlank()) {
            return Map.of("error", "optimizedSql is required");
        }

        BenchmarkConfig config = BenchmarkConfig.defaults();

        // Parse optional config
        if (request.containsKey("warmupRuns")) {
            config.setWarmupRuns(((Number) request.get("warmupRuns")).intValue());
        }
        if (request.containsKey("measurementRuns")) {
            config.setMeasurementRuns(((Number) request.get("measurementRuns")).intValue());
        }
        if (request.containsKey("statementTimeoutMs")) {
            config.setStatementTimeoutMs(((Number) request.get("statementTimeoutMs")).longValue());
        }
        if (request.containsKey("explainAnalyze")) {
            config.setExplainAnalyze((Boolean) request.get("explainAnalyze"));
        }
        if (request.containsKey("readOnly")) {
            config.setReadOnly((Boolean) request.get("readOnly"));
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
            return Map.of("error", "Benchmark failed: " + e.getMessage());
        }
    }
}