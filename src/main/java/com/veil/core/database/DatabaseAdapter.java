package com.veil.core.database;

import java.util.List;
import java.util.Map;

// common contract for database-specific implementations
public interface DatabaseAdapter {

    DatabaseType getDatabaseType();

    DatabaseCapabilities getCapabilities();

    boolean testConnection();

    String getDatabaseName();

    List<Map<String, Object>> getTables();

    List<Map<String, Object>> getColumns();

    List<Map<String, Object>> getIndexes();

    // indexes of one table, including their definitions
    // (used by index analysis to see which columns are covered)
    List<Map<String, Object>> getIndexesForTable(String tableName);

}