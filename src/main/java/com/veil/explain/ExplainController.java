package com.veil.explain;

import com.veil.core.database.ExecutionPlanProvider;
import com.veil.security.SqlValidator;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/explain")
public class ExplainController {

    private final ExplainService explainService;
    private final ExecutionPlanProvider planProvider;
    private final SqlValidator sqlValidator;

    public ExplainController(ExplainService explainService,
                             ExecutionPlanProvider planProvider,
                             SqlValidator sqlValidator) {

        this.explainService = explainService;
        this.planProvider = planProvider;
        this.sqlValidator = sqlValidator;
    }

    @PostMapping
    public Map<String, Object> explain(@RequestBody Map<String, String> request) {

        String sql = request == null ? null : request.get("sql");

        sqlValidator.validateSelectOnly(sql);

        PlanSummary plan = explainService.explain(sql);

        return Map.of("sql", sql, "plan", plan);
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, String> request) {

        String sql = request == null ? null : request.get("sql");

        sqlValidator.validateSelectOnly(sql);

        try {
            String json = planProvider.getExecutionPlanAnalyze(sql);
            PlanSummary plan = explainService.explainFromJson(json);

            return Map.of("sql", sql, "plan", plan);
        } catch (UnsupportedOperationException e) {
            return Map.of("error", "EXPLAIN ANALYZE not supported by current provider");
        } catch (Exception e) {
            // Sanitized: never leak database or implementation details.
            return Map.of("error", "ANALYZE failed");
        }
    }
}
