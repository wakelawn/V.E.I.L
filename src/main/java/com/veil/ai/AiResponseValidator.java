package com.veil.ai;

import com.veil.ast.SelectStatement;
import com.veil.parser.SemanticValidationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Validates AI response structure and content for safety and schema compliance.
@Component
public class AiResponseValidator {

    private final SemanticValidationService semanticValidator;

    public AiResponseValidator(SemanticValidationService semanticValidator) {
        this.semanticValidator = semanticValidator;
    }

    // Validates the structure and content of AI recommendation response.
    public ValidationResult validateRecommendation(AiRecommendation rec, SelectStatement originalSelect) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check required fields
        if (rec == null) {
            return ValidationResult.invalid(List.of("AI recommendation is null"));
        }

        if (rec.summary() == null || rec.summary().isBlank()) {
            warnings.add("Summary is empty");
        }

        if (rec.bottlenecks() == null) {
            warnings.add("No bottlenecks reported");
        }

        // Validate findings
        if (rec.findings() != null) {
            for (int i = 0; i < rec.findings().size(); i++) {
                AiRecommendation.Finding f = rec.findings().get(i);
                
                if (f.title() == null || f.title().isBlank()) {
                    errors.add("Finding " + i + ": title is required");
                }
                
                if (f.message() == null || f.message().isBlank()) {
                    warnings.add("Finding " + i + ": message is empty");
                }

                // Validate severity
                String sev = f.severity();
                if (sev != null && !Set.of("high", "medium", "low").contains(sev.toLowerCase())) {
                    warnings.add("Finding " + i + ": invalid severity '" + sev + "'");
                }

                // Validate suggestionSql if present
                if (f.suggestionSql() != null && !f.suggestionSql().isBlank()) {
                    // Check for dangerous DDL - only CREATE INDEX or ANALYZE allowed
                    String sql = f.suggestionSql().trim().toUpperCase();
                    if (!sql.startsWith("CREATE INDEX") && !sql.startsWith("ANALYZE")) {
                        return ValidationResult.invalid(
                                List.of("Finding " + i + ": suggestionSql must be CREATE INDEX or ANALYZE only"));
                    }
                }
            }
        }

        // Validate estimatedImprovement
        if (rec.estimatedImprovement() != null && !rec.estimatedImprovement().isBlank()) {
            String imp = rec.estimatedImprovement().toLowerCase();
            // Check for unrealistic claims
            if (imp.contains("100%") || imp.contains("1000%") || imp.contains("instant")) {
                return ValidationResult.invalid(List.of("Unrealistic improvement claim: " + rec.estimatedImprovement()));
            }
        }

        return errors.isEmpty() 
                ? ValidationResult.valid(warnings)
                : ValidationResult.invalid(errors);
    }

    // Validate AI chat response
    public ValidationResult validateChatResponse(String response) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (response == null || response.isBlank()) {
            return ValidationResult.invalid(List.of("Empty response"));
        }

        // Check for common issues
        if (response.length() > 10000) {
            warnings.add("Response is very long (" + response.length() + " chars)");
        }

        // Check for potential SQL injection in response
        String upper = response.toUpperCase();
        if (upper.contains("DROP TABLE") || upper.contains("DELETE FROM") || 
            upper.contains("TRUNCATE TABLE")) {
            warnings.add("Response contains potentially dangerous SQL keywords");
        }

        return ValidationResult.valid(warnings);
    }

    // Result class
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        private ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }

        public static ValidationResult valid(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
}