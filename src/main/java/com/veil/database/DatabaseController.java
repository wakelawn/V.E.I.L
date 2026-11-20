package com.veil.database;

import com.veil.core.database.DatabaseAdapter;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/database")
public class DatabaseController {

    private final DatabaseAdapter databaseAdapter;

    public DatabaseController(DatabaseAdapter databaseAdapter) {
        this.databaseAdapter = databaseAdapter;
    }

    @GetMapping
    public Map<String, Object> getDatabaseMetadata() {

        return Map.of(
                "database", databaseAdapter.getDatabaseName(),
                "tables", databaseAdapter.getTables(),
                "columns", databaseAdapter.getColumns(),
                "indexes", databaseAdapter.getIndexes()
        );
    }

    @GetMapping("/health")
    public Map<String, Object> getDatabaseHealth() {

        boolean connected = databaseAdapter.testConnection();

        return Map.of(
                "databaseType",
                databaseAdapter.getDatabaseType().name(),
                "connected",
                connected
        );
    }
}