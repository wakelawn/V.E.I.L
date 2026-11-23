package com.veil.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    @Test
    void acceptsSimpleSelect() {
        assertDoesNotThrow(() -> validator.validateSelectOnly("SELECT id FROM projects"));
    }

    @Test
    void acceptsSelectWithWhereAndLimit() {
        assertDoesNotThrow(() -> validator.validateSelectOnly(
                "SELECT name FROM queries WHERE status = 'done' ORDER BY id LIMIT 10;"));
    }

    @Test
    void acceptsSelectStar() {
        assertDoesNotThrow(() -> validator.validateSelectOnly("SELECT * FROM projects;"));
    }

    @Test
    void acceptsWithQuery() {
        assertDoesNotThrow(() -> validator.validateSelectOnly(
                "WITH cte AS (SELECT id FROM projects) SELECT * FROM cte;"));
    }

    @Test
    void rejectsEmptySql() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> validator.validateSelectOnly(""));
        assertEquals("SQL query cannot be empty", e.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("   "));
    }

    @Test
    void rejectsNonSelect() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("DELETE FROM users"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("UPDATE projects SET name = 'x'"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("INSERT INTO queries (query_text) VALUES ('x')"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("DROP TABLE projects"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("CREATE TABLE t (id int)"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("ALTER TABLE projects ADD COLUMN x int"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("TRUNCATE queries"));
    }

    @Test
    void rejectsMultipleStatements() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("SELECT 1; SELECT 2;"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("SELECT id FROM projects; DROP TABLE users;"));
    }

    @Test
    void rejectsSqlInjectionAttempts() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("SELECT id FROM projects; --"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateSelectOnly("SELECT id FROM projects; DELETE FROM users; --"));
    }

    @Test
    void rejectsOversizedSql() {
        String tooLong = "SELECT " + "a".repeat(70_000) + " FROM projects";
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> validator.validateSelectOnly(tooLong));
        assertTrue(e.getMessage().contains("too long"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateSelectOnly(null));
    }
}
