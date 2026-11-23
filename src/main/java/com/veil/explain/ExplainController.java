package com.veil.explain;

import com.veil.core.database.ExecutionPlanProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/explain")
public class ExplainController {

    private final ExplainService explainService;
    private final ExecutionPlanProvider planProvider;

    public ExplainController(ExplainService explainService,
                             ExecutionPlanProvider planProvider) {

        this.explainService = explainService;
        this.planProvider = planProvider;
    }

    @PostMapping
    public Map<String, Object> explain(@RequestBody Map<String, String> request) {

        String sql = request.get("sql");

        PlanSummary plan = explainService.explain(sql);

        return Map.of("sql", sql, "plan", plan);
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, String> request) {

        String sql = request.get("sql");
        if (sql == null || sql.isBlank()) {
            return Map.of("error", "SQL is required");
        }

        try {
            String json = planProvider.getExecutionPlanAnalyze(sql);
            PlanSummary plan = explainService.explainFromJson(json);

            return Map.of("sql", sql, "plan", plan);
        } catch (UnsupportedOperationException e) {
            return Map.of("error", "EXPLAIN ANALYZE not supported by current provider");
        } catch (Exception e) {
            return Map.of("error", "ANALYZE failed: " + e.getMessage());
        }
    }
}