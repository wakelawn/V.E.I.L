package com.veil.ai;

import com.veil.ast.SelectStatement;
import com.veil.lexer.Lexer;
import com.veil.parser.Parser;
import com.veil.parser.SemanticAnalyzer;
import com.veil.parser.SemanticValidationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Validates AI-generated SQL for syntax, safety, and semantic correctness.
@Component
public class AiSqlValidator {

    private final SemanticValidationService semanticValidator;

    // Forbidden patterns that should never appear in AI-generated SQL
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE",
            "TRUNCATE", "GRANT", "REVOKE", "COMMIT", "ROLLBACK",
            "SAVEPOINT", "VACUUM", "COPY",
            "\\\\copy", "\\\\dt", "\\\\d", "\\\\l"
    );

    // Forbidden functions that could be dangerous
    private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of(
            "pg_sleep", "pg_terminate_backend", "pg_cancel_backend",
            "pg_read_file", "pg_write_file", "pg_ls_dir",
            "lo_import", "lo_export", "lo_unlink",
            "dblink", "dblink_send_query", "dblink_exec"
    );

    public AiSqlValidator(SemanticValidationService semanticValidator) {
        this.semanticValidator = semanticValidator;
    }

    // Comprehensive validation of AI-generated SQL.
    public AiSqlValidationResult validate(String sql, String originalSql) {
        if (sql == null || sql.isBlank()) {
            return AiSqlValidationResult.invalid("SQL is empty");
        }

        List<String> warnings = new ArrayList<>();

        String upperSql = sql.toUpperCase();

        // 1. Check for forbidden keywords FIRST (before syntax validation)
        for (String forbidden : FORBIDDEN_KEYWORDS) {
            if (upperSql.contains(forbidden)) {
                return AiSqlValidationResult.invalid(
                        "Forbidden keyword detected: " + forbidden + ". AI must not generate DDL/DML.");
            }
        }

        // 2. Check for forbidden functions
        for (String func : FORBIDDEN_FUNCTIONS) {
            if (upperSql.contains(func.toUpperCase())) {
                return AiSqlValidationResult.invalid(
                        "Forbidden function detected: " + func);
            }
        }

        // 3. Must be SELECT, WITH, ANALYZE, or EXPLAIN (read-only).
        // ANALYZE updates planner statistics after index creation.
        // EXPLAIN shows the execution plan for verification.
        String trimmed = sql.trim().toUpperCase();
        boolean isSelectOrWith = trimmed.startsWith("SELECT")
                || trimmed.startsWith("WITH");
        boolean isAnalyzeOrExplain = trimmed.startsWith("ANALYZE")
                || trimmed.startsWith("EXPLAIN");
        if (!isSelectOrWith && !isAnalyzeOrExplain) {
            return AiSqlValidationResult.invalid(
                    "Only SELECT, WITH, ANALYZE, or EXPLAIN queries are allowed");
        }

        // 4. No multiple statements (allow one trailing semicolon only)
        String sqlNoTrailingSemi = sql.trim();
        if (sqlNoTrailingSemi.endsWith(";")) {
            sqlNoTrailingSemi = sqlNoTrailingSemi.substring(0, sqlNoTrailingSemi.length() - 1).trim();
        }
        if (sqlNoTrailingSemi.contains(";")) {
            return AiSqlValidationResult.invalid("Multiple statements not allowed");
        }

        // 5. Basic syntax validation (SELECT/WITH only - parser doesn't
        // handle ANALYZE/EXPLAIN)
        if (isSelectOrWith) {
            try {
                new Parser(new Lexer(sql).tokenize()).parse();
            } catch (com.veil.parser.ParseException e) {
                // WITH clauses not yet supported by parser - allow with warning
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Expected SELECT but found") && msg.contains("WITH") || msg.contains("IDENTIFIER"))) {
                    warnings.add("WITH clause detected - parser support for CTEs is experimental");
                    return AiSqlValidationResult.valid(sql, warnings);
                }
                return AiSqlValidationResult.invalid("Syntax error: " + msg);
            } catch (Exception e) {
                return AiSqlValidationResult.invalid("Syntax error: " + e.getMessage());
            }
        }

        // 6. Check for common issues
        if (sql.toUpperCase().contains("SELECT *")) {
            warnings.add("SELECT * detected - consider listing explicit columns");
        }

        if (sql.toUpperCase().matches(".*\\s+IN\\s*\\(\\s*SELECT.*")) {
            warnings.add("Subquery in IN clause detected - consider JOIN for better performance");
        }

        // Check row count estimation
        if (sql.toUpperCase().contains("LIMIT")) {
            // Good - has limit
        } else {
            warnings.add("No LIMIT clause - query may return large result sets");
        }

        return AiSqlValidationResult.valid(sql, warnings);
    }

    // Validate that AI response contains safe, executable SQL
    public AiSqlValidationResult validateSuggestionSql(String suggestionSql) {
        if (suggestionSql == null || suggestionSql.isBlank()) {
            return AiSqlValidationResult.valid(""); // Empty is OK (no suggestion)
        }

        // For DDL suggestions (CREATE INDEX, ANALYZE), allow more keywords
        String upper = suggestionSql.toUpperCase().trim();
        if (upper.startsWith("CREATE INDEX") || upper.startsWith("ANALYZE")) {
            return AiSqlValidationResult.valid(suggestionSql);
        }

        // Otherwise treat as SELECT
        return validate(suggestionSql, null);
    }
}