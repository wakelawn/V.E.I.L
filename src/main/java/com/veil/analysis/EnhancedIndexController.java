package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.core.database.DatabaseAdapter;
import com.veil.explain.PlanSummary;
import com.veil.parser.Parser;

import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

// REST endpoint for enhanced index recommendations
@RestController
@RequestMapping("/api/indexes")
public class EnhancedIndexController {

    private final EnhancedIndexAdvisor advisor;

    public EnhancedIndexController(EnhancedIndexAdvisor advisor) {
        this.advisor = advisor;
    }

    @PostMapping("/recommend")
    public Map<String, Object> recommend(@RequestBody Map<String, Object> request) {
        String sql = (String) request.get("sql");
        if (sql == null || sql.isBlank()) {
            return Map.of("error", "sql is required");
        }

        try {
            // Parse SQL
            List<com.veil.lexer.Token> tokens = new com.veil.lexer.Lexer(sql).tokenize();
            SelectStatement select = (SelectStatement) new Parser(tokens).parse();

            // Get plan if requested
            PlanSummary plan = null;
            Boolean withPlan = (Boolean) request.get("withPlan");
            if (withPlan != null && withPlan) {
                // Would need to call ExplainService - simplified for now
            }

            // Get plan from request if provided
            if (request.containsKey("plan")) {
                // Parse plan from request
            }

            // Get findings from optimization engine
            OptimizationEngine engine = new OptimizationEngine(
                    request.containsKey("withDb") ? (DatabaseAdapter) null : null);
            List<OptimizationFinding> findings = engine.runWithPlan(select, plan);

            // Get enhanced recommendations
            List<IndexRecommendation> recommendations = advisor.advise(select, findings, plan);

            List<Map<String, Object>> recs = recommendations.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tableName", r.getTableName());
                m.put("indexName", r.getIndexName());
                m.put("indexType", r.getIndexType());
                m.put("columns", r.getColumns());
                m.put("whereClause", r.getWhereClause());
                m.put("expression", r.getExpression());
                m.put("sql", r.getSql());
                m.put("reason", r.getReason());
                m.put("severity", r.getSeverity());
                m.put("confidence", r.getConfidence());
                m.put("isPartial", r.isPartial());
                m.put("isExpression", r.isExpression());
                m.put("isHypothetical", r.isHypothetical());
                m.put("estimatedBenefit", r.getEstimatedBenefit());
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sql", sql);
            response.put("findings", findings);
            response.put("recommendations", recs);
            return response;
        } catch (Exception e) {
            return Map.of("error", "Recommendation failed: " + e.getMessage());
        }
    }

    // Check HypoPG availability
    @GetMapping("/hypopg/status")
    public Map<String, Object> hypopgStatus() {
        // Would need to inject HypoPgService
        return Map.of("available", false, "message", "HypoPG status check not fully implemented");
    }
}