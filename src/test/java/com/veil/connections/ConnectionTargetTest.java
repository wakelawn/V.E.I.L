package com.veil.connections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionTargetTest {

    private final ConnectionTarget target = new ConnectionTarget(new ConnectionSettings());

    @Test
    void acceptsOrdinaryDetails() {
        assertDoesNotThrow(() -> target.validate(
                "Production", "db.example.com", 5432, "veil_prod", "veil"));
    }

    @Test
    void hostCannotSmuggleUrlParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> target.validateHost("db.example.com/other"));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateHost("db.example.com?sslmode=disable"));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateHost("db.example.com;options=-c"));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateHost(""));
    }

    @Test
    void databaseNameCannotSmuggleUrlParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> target.validateDatabaseName("veil?sslmode=disable"));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateDatabaseName("veil;drop"));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateDatabaseName("veil/other"));
    }

    @Test
    void urlCarriesTimeoutsTlsAndApplicationName() {
        String url = target.jdbcUrl("db.example.com", 6543, "analytics");

        assertEquals(
                "jdbc:postgresql://db.example.com:6543/analytics"
                        + "?connectTimeout=5000&socketTimeout=30000"
                        + "&sslmode=prefer&ApplicationName=VEIL",
                url);
    }

    @Test
    void rejectsOversizedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> target.validateHost("a".repeat(256)));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateDatabaseName("a".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateName("a".repeat(101)));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateUsername("a".repeat(64)));
    }

    @Test
    void rejectsControlCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> target.validateUsername("veil\nuser"));
        assertThrows(IllegalArgumentException.class,
                () -> target.validateName("name\u0007"));
    }

    @Test
    void rejectsPortsOutsideTheValidRange() {
        assertThrows(IllegalArgumentException.class, () -> target.validatePort(0));
        assertThrows(IllegalArgumentException.class, () -> target.validatePort(65536));
        assertTrue(assertDoesNotThrow(() -> {
            target.validatePort(5432);
            return true;
        }));
    }

    @Test
    void settingsRejectInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(0, 30, 4, "prefer", true, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(5, 30, 4, "sometimes", true, 100));

        ConnectionSettings capped = new ConnectionSettings(5, 30, 999, "require", false, 10);
        assertEquals(32, capped.getPoolSize());
        assertEquals("require", capped.getSslMode());
        assertFalse(capped.isReadOnly());
        assertEquals(10, capped.getMaxSavedConnections());
    }
}
