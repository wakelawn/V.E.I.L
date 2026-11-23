package com.veil.ai;

import com.veil.parser.Parser;
import com.veil.parser.SemanticValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiSqlValidatorTest {

    @Autowired
    private AiSqlValidator validator;

    @Test
    void validSelectPasses() {
        AiSqlValidationResult result = validator.validate("SELECT id FROM queries WHERE project_id = 1;", null);
        assertTrue(result.isValid());
    }

    @Test
    void selectStarWarns() {
        AiSqlValidationResult result = validator.validate("SELECT * FROM queries;", null);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("SELECT *")));
    }

    @Test
    void dropTableRejected() {
        AiSqlValidationResult result = validator.validate("DROP TABLE queries;", null);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Forbidden keyword"));
    }

    @Test
    void deleteRejected() {
        AiSqlValidationResult result = validator.validate("DELETE FROM queries WHERE id = 1;", null);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Forbidden keyword"));
    }

    @Test
    void updateRejected() {
        AiSqlValidationResult result = validator.validate("UPDATE queries SET status = 'done';", null);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Forbidden keyword"));
    }

    @Test
    void insertRejected() {
        AiSqlValidationResult result = validator.validate("INSERT INTO queries (id) VALUES (1);", null);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Forbidden keyword"));
    }

    @Test
    void createIndexAllowed() {
        AiSqlValidationResult result = validator.validateSuggestionSql("CREATE INDEX idx_q_status ON queries (status);");
        assertTrue(result.isValid());
    }

    @Test
    void multipleStatementsRejected() {
        AiSqlValidationResult result = validator.validate("SELECT 1; SELECT 2;", null);
        assertFalse(result.isValid());
    }

    @Test
    void withClauseAllowed() {
        AiSqlValidationResult result = validator.validate("WITH cte AS (SELECT 1) SELECT * FROM cte;", null);
        assertTrue(result.isValid());
    }

    @Test
    void limitRecommended() {
        AiSqlValidationResult result = validator.validate("SELECT * FROM queries;", null);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("LIMIT")));
    }
}