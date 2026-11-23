package com.veil.parser;

import com.veil.ast.*;
import com.veil.parser.SemanticAnalyzer.SemanticError;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// REST endpoint for semantic SQL validation.
@RestController
@RequestMapping("/api/validate")
public class SemanticValidationController {

    private final SemanticValidationService service;

    public SemanticValidationController(SemanticValidationService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> validate(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.isBlank()) {
            return Map.of("valid", false, "errors", List.of(
                Map.of("message", "SQL is required", "code", "MISSING_SQL")));
        }

        try {
            List<SemanticError> errors = service.validateSql(sql);
            return Map.of(
                "valid", errors.isEmpty(),
                "errors", errors);
        } catch (Exception e) {
            return Map.of("valid", false, "errors", List.of(
                Map.of("message", e.getMessage(), "code", "PARSE_ERROR")));
        }
    }
}