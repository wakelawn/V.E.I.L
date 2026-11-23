package com.veil.security;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Validates SQL input before it reaches the parser or the database.
 *
 * <p>VEIL only ever runs read-only {@code EXPLAIN} against the target
 * database, so every SQL string accepted by an endpoint must be a single
 * {@code SELECT} (or {@code WITH}) statement. This class centralises that
 * check so the analyze, explain and benchmark endpoints all enforce the same
 * rules.</p>
 */
@Component
public class SqlValidator {

    /** Maximum accepted SQL length in characters. Guards against DoS via
     *  oversized lexer/parser input. */
    private static final int MAX_SQL_LENGTH = 64 * 1024;

    /**
     * Validates that {@code sql} is a single read-only SELECT/WITH statement.
     *
     * @throws IllegalArgumentException when the SQL is blank, too long,
     *                                  not a SELECT/WITH, or contains multiple
     *                                  statements separated by semicolons.
     */
    public void validateSelectOnly(String sql) {

        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }

        if (sql.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException(
                    "SQL query too long (max " + MAX_SQL_LENGTH + " characters)");
        }

        String query = sql.trim();

        // Remove one trailing semicolon before checking for more statements.
        if (query.endsWith(";")) {
            query = query.substring(0, query.length() - 1).trim();
        }

        String upper = query.toUpperCase(Locale.ROOT);

        // Allow SELECT and WITH queries only.
        boolean startsWithSelect = upper.startsWith("SELECT ")
                || upper.startsWith("SELECT\n")
                || upper.startsWith("SELECT\t")
                || upper.equals("SELECT");
        boolean startsWithWith = upper.startsWith("WITH ")
                || upper.startsWith("WITH\n")
                || upper.startsWith("WITH\t")
                || upper.equals("WITH");

        if (!startsWithSelect && !startsWithWith) {
            throw new IllegalArgumentException(
                    "Only SELECT or WITH queries are supported");
        }

        // Reject multiple statements.
        if (query.contains(";")) {
            throw new IllegalArgumentException(
                    "Multiple SQL statements are not allowed");
        }
    }
}
