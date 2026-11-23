package com.veil.ai;

import com.veil.parser.Parser;
import com.veil.parser.SemanticAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SqlEquivalenceCheckerTest {

    @Autowired
    private SqlEquivalenceChecker checker;

    @Test
    void identicalQueriesEquivalent() {
        String sql = "SELECT id FROM queries WHERE project_id = 1;";
        var result = checker.checkEquivalence(sql, sql);
        assertEquals("EQUIVALENT", result.getStatus());
    }

    @Test
    void whitespaceDifferencesEquivalent() {
        String sql1 = "SELECT id FROM queries WHERE project_id = 1;";
        String sql2 = "SELECT  id  FROM  queries  WHERE  project_id = 1 ;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("EQUIVALENT", result.getStatus());
    }

    @Test
    void caseInsensitiveEquivalent() {
        String sql1 = "SELECT id FROM queries WHERE project_id = 1;";
        String sql2 = "select id from queries where project_id = 1;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("EQUIVALENT", result.getStatus());
    }

    @Test
    void differentWhereClauseNotEquivalent() {
        String sql1 = "SELECT id FROM queries WHERE project_id = 1;";
        String sql2 = "SELECT id FROM queries WHERE project_id = 2;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("NOT_EQUIVALENT", result.getStatus());
    }

    @Test
    void differentColumnListNotEquivalent() {
        String sql1 = "SELECT id FROM queries WHERE project_id = 1;";
        String sql2 = "SELECT id, name FROM queries WHERE project_id = 1;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("NOT_EQUIVALENT", result.getStatus());
    }

    @Test
    void differentTableNotEquivalent() {
        String sql1 = "SELECT id FROM queries WHERE project_id = 1;";
        String sql2 = "SELECT id FROM projects WHERE id = 1;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("NOT_EQUIVALENT", result.getStatus());
    }

    @Test
    void withClauseEquivalent() {
        String sql1 = "WITH cte AS (SELECT 1) SELECT * FROM cte;";
        String sql2 = "WITH cte AS (SELECT 1) SELECT * FROM cte;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("EQUIVALENT", result.getStatus());
    }

    @Test
    void differentLimitNotEquivalent() {
        String sql1 = "SELECT id FROM queries LIMIT 10;";
        String sql2 = "SELECT id FROM queries LIMIT 20;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("NOT_EQUIVALENT", result.getStatus());
    }

    @Test
    void differentOrderByNotEquivalent() {
        String sql1 = "SELECT id FROM queries ORDER BY created_at ASC;";
        String sql2 = "SELECT id FROM queries ORDER BY created_at DESC;";
        var result = checker.checkEquivalence(sql1, sql2);
        assertEquals("NOT_EQUIVALENT", result.getStatus());
    }
}