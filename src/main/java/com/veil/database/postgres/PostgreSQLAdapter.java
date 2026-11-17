package com.veil.database.postgres;

import com.veil.core.database.DatabaseAdapter;
import com.veil.core.database.DatabaseCapabilities;
import com.veil.core.database.DatabaseType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PostgreSQLAdapter implements DatabaseAdapter {

    private final JdbcTemplate jdbcTemplate;

    private static final DatabaseCapabilities CAPABILITIES =
            new DatabaseCapabilities(
                    true,  // SQL support
                    true,  // EXPLAIN support
                    true,  // Index support
                    true,  // Transaction support
                    true   // Schema inspection
            );

    public PostgreSQLAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public DatabaseCapabilities getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean testConnection() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );

        return result != null && result == 1;
    }

    @Override
    public String getDatabaseName() {
        return jdbcTemplate.queryForObject(
                "SELECT current_database()",
                String.class
        );
    }

    @Override
    public List<Map<String, Object>> getTables() {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """);
    }

    @Override
    public List<Map<String, Object>> getIndexes() {
        return jdbcTemplate.queryForList("""
                SELECT indexname, tablename
                FROM pg_indexes
                WHERE schemaname = 'public'
                ORDER BY tablename, indexname
                """);
    }

    @Override
    public List<Map<String, Object>> getIndexesForTable(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = ?
                ORDER BY indexname
                """, tableName);
    }
}