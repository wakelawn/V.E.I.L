package com.veil.ai;

import java.util.List;

// Validation result for AI-generated SQL.
public class AiSqlValidationResult {

    private final boolean valid;
    private final String errorMessage;
    private final String validatedSql;
    private final List<String> warnings;

    public AiSqlValidationResult(boolean valid, String errorMessage, String validatedSql, List<String> warnings) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.validatedSql = validatedSql;
        this.warnings = warnings;
    }

    public static AiSqlValidationResult valid(String sql) {
        return new AiSqlValidationResult(true, null, sql, List.of());
    }

    public static AiSqlValidationResult valid(String sql, List<String> warnings) {
        return new AiSqlValidationResult(true, null, sql, warnings);
    }

    public static AiSqlValidationResult invalid(String error) {
        return new AiSqlValidationResult(false, error, null, List.of());
    }

    public static AiSqlValidationResult invalid(String error, List<String> warnings) {
        return new AiSqlValidationResult(false, error, null, warnings);
    }

    public boolean isValid() { return valid; }
    public String getErrorMessage() { return errorMessage; }
    public String getValidatedSql() { return validatedSql; }
    public List<String> getWarnings() { return warnings; }
}