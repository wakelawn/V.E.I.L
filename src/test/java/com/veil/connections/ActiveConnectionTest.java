package com.veil.connections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveConnectionTest {

    private final ConnectionSettings settings = new ConnectionSettings();
    private final ActiveConnection active = new ActiveConnection(
            new ConnectionTarget(settings), settings);

    @AfterEach
    void shutDown() {
        active.shutdown();
    }

    private SavedConnection saved(String name) {
        SavedConnection saved = new SavedConnection(
                name, "127.0.0.1", 5432, "veil_test", "veil", "secret");
        saved.setId(1);
        return saved;
    }

    @Test
    void activatePublishesTemplateAndState() {
        SavedConnection connection = saved("local");

        active.activate(connection);

        assertTrue(active.isActive());
        assertSame(connection, active.get());
        assertNotNull(active.jdbcTemplate());
    }

    @Test
    void activatingAnotherConnectionReplacesThePool() {
        active.activate(saved("first"));
        JdbcTemplate first = active.jdbcTemplate();

        SavedConnection second = saved("second");
        second.setId(2);
        active.activate(second);

        assertTrue(active.jdbcTemplate() != first);
        assertNotSame(first, active.jdbcTemplate());
        assertSame(second, active.get());
    }

    @Test
    void deactivateClearsState() {
        active.activate(saved("local"));

        active.deactivate();

        assertFalse(active.isActive());
        assertNull(active.get());
        assertNull(active.jdbcTemplate());
    }

    @Test
    void activateRejectsInvalidDetails() {
        SavedConnection bad = new SavedConnection(
                "bad", "host?sslmode=disable", 5432, "veil", "veil", "secret");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> active.activate(bad));
        assertFalse(active.isActive());
    }
}
