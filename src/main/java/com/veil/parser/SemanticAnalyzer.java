package com.veil.parser;

import com.veil.ast.*;
import com.veil.core.database.DatabaseAdapter;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Validates a parsed SELECT statement against the live database schema.
// Checks table/column existence, ambiguous references, GROUP BY validity,
// aggregate semantics, and join conditions.
@Component
public class SemanticAnalyzer {

    private final DatabaseAdapter databaseAdapter;

    // Schema cache: table_name -> List<ColumnInfo>
    private final Map<String, List<ColumnInfo>> schemaCache = new ConcurrentHashMap<>();

    // Cache timestamp for invalidation
    private long cacheTimestamp = 0;

    private static final long CACHE_TTL_MS = 60_000; // 1 minute

    public SemanticAnalyzer(DatabaseAdapter databaseAdapter) {
        this.databaseAdapter = databaseAdapter;
    }

    // Validates a SELECT statement and returns list of semantic errors.
    // Returns empty list if valid.
    public List<SemanticError> validate(SelectStatement statement) {

        List<SemanticError> errors = new ArrayList<>();

        // Refresh cache if stale
        ensureCacheFresh();

        // 1. Validate FROM table
        validateTableReference(statement.getTable(), errors);

        // 2. Validate JOIN tables
        for (JoinClause join : statement.getJoins()) {
            validateTableReference(join.getTable(), errors);
        }

        // Build column resolution context (table aliases -> real table names)
        Map<String, String> aliasToTable = buildAliasMap(statement);

        // 3. Validate SELECT items
        for (SelectItem item : statement.getSelectItems()) {
            validateExpression(item.getExpression(), aliasToTable, errors, item.hasAlias() ? item.getAlias() : null);
        }

        // 4. Validate WHERE condition
        if (statement.getWhereCondition() != null) {
            validateCondition(statement.getWhereCondition(), aliasToTable, errors);
        }

        // 5. Validate JOIN conditions
        for (JoinClause join : statement.getJoins()) {
            validateCondition(join.getCondition(), aliasToTable, errors);
        }

        // 6. Validate GROUP BY
        if (statement.getGroupBy() != null) {
            validateGroupBy(statement, aliasToTable, errors);
        }

        // 7. Validate HAVING
        if (statement.getGroupBy() != null && statement.getGroupBy().getHavingCondition() != null) {
            validateCondition(statement.getGroupBy().getHavingCondition(), aliasToTable, errors);
        }

        // 8. Validate ORDER BY
        for (OrderByItem orderBy : statement.getOrderBy()) {
            validateColumnReference(orderBy.getColumn(), aliasToTable, errors, "ORDER BY");
        }

        return errors;
    }

    private void ensureCacheFresh() {
        long now = System.currentTimeMillis();
        if (now - cacheTimestamp > CACHE_TTL_MS || schemaCache.isEmpty()) {
            refreshCache();
            cacheTimestamp = now;
        }
    }

    private void refreshCache() {
        schemaCache.clear();
        try {
            List<Map<String, Object>> columns = databaseAdapter.getColumns();
            for (Map<String, Object> row : columns) {
                String tableName = (String) row.get("table_name");
                String columnName = (String) row.get("column_name");
                String dataType = (String) row.get("data_type");

                schemaCache.computeIfAbsent(tableName, k -> new ArrayList<>())
                    .add(new ColumnInfo(columnName, dataType));
            }
        } catch (Exception e) {
            // Cache stays empty; validation will report missing metadata
        }
    }

    private Map<String, String> buildAliasMap(SelectStatement statement) {
        Map<String, String> map = new HashMap<>();
        // FROM table
        TableReference from = statement.getTable();
        if (from.getAlias() != null) {
            map.put(from.getAlias(), from.getTableName());
        }
        map.put(from.getTableName(), from.getTableName()); // also allow unqualified

        // JOIN tables
        for (JoinClause join : statement.getJoins()) {
            TableReference t = join.getTable();
            if (t.getAlias() != null) {
                map.put(t.getAlias(), t.getTableName());
            }
            map.put(t.getTableName(), t.getTableName());
        }
        return map;
    }

    private void validateTableReference(TableReference table, List<SemanticError> errors) {
        String tableName = table.getTableName();
        if (!schemaCache.containsKey(tableName)) {
            errors.add(new SemanticError(
                "Table '" + tableName + "' does not exist",
                "TABLE_NOT_FOUND",
                tableName));
        }
    }

    private void validateExpression(Expression expr, Map<String, String> aliasToTable,
                                    List<SemanticError> errors, String alias) {

        if (expr instanceof ColumnReference) {
            validateColumnReference(((ColumnReference) expr).getName(), aliasToTable, errors, "SELECT");
        } else if (expr instanceof AggregateExpression) {
            AggregateExpression agg = (AggregateExpression) expr;
            // Validate aggregate argument
            if (!"*".equals(agg.getArgument())) {
                validateColumnReference(agg.getArgument(), aliasToTable, errors, agg.getFunction());
            }
            // Check function name
            String func = agg.getFunction().toUpperCase();
            if (!Set.of("COUNT", "SUM", "AVG", "MIN", "MAX").contains(func)) {
                errors.add(new SemanticError(
                    "Unsupported aggregate function: " + func,
                    "INVALID_FUNCTION",
                    func));
            }
        } else if (expr instanceof ColumnReference) {
            validateColumnReference(((ColumnReference) expr).getName(), aliasToTable, errors, "EXPRESSION");
        }
    }

    private void validateCondition(Condition condition, Map<String, String> aliasToTable,
                                   List<SemanticError> errors) {

        if (condition instanceof WhereCondition) {
            WhereCondition wc = (WhereCondition) condition;
            validateColumnReference(wc.getColumn(), aliasToTable, errors, "WHERE");
            // Could also validate value type compatibility here
        } else if (condition instanceof LogicalCondition) {
            LogicalCondition lc = (LogicalCondition) condition;
            validateCondition(lc.getLeft(), aliasToTable, errors);
            validateCondition(lc.getRight(), aliasToTable, errors);
        }
    }

    private void validateGroupBy(SelectStatement statement, Map<String, String> aliasToTable,
                                 List<SemanticError> errors) {

        GroupByClause groupBy = statement.getGroupBy();
        if (groupBy == null) return;

        // Collect all GROUP BY columns
        Set<String> groupByColumns = new HashSet<>(groupBy.getColumns());

        // For each SELECT item, if it's not an aggregate, it must be in GROUP BY
        for (SelectItem item : statement.getSelectItems()) {
            Expression expr = item.getExpression();
            if (!(expr instanceof AggregateExpression)) {
                String columnName = expressionToColumnName(expr);
                if (columnName != null && !groupByColumns.contains(columnName)) {
                    errors.add(new SemanticError(
                        "Column '" + columnName + "' must appear in the GROUP BY clause or be used in an aggregate function",
                        "GROUP_BY_VIOLATION",
                        columnName));
                }
            }
        }

        // Validate GROUP BY columns exist
        for (String col : groupBy.getColumns()) {
            validateColumnReference(col, aliasToTable, errors, "GROUP BY");
        }
    }

    private void validateColumnReference(String columnRef, Map<String, String> aliasToTable,
                                         List<SemanticError> errors, String context) {

        // Handle qualified: table.column or alias.column
        String[] parts = columnRef.split("\\.", 2);
        if (parts.length == 2) {
            String qualifier = parts[0];
            String column = parts[1];
            String tableName = aliasToTable.get(qualifier);
            if (tableName == null) {
                errors.add(new SemanticError(
                    "Unknown table or alias '" + qualifier + "' in " + context,
                    "UNKNOWN_TABLE_ALIAS",
                    qualifier));
                return;
            }
            validateColumnExists(tableName, column, errors, context);
        } else {
            // Unqualified column - check all tables in scope
            String column = parts[0];
            List<String> matchingTables = new ArrayList<>();
            for (String tableName : aliasToTable.values()) {
                if (schemaCache.containsKey(tableName) &&
                    schemaCache.get(tableName).stream().anyMatch(c -> c.name.equalsIgnoreCase(column))) {
                    matchingTables.add(tableName);
                }
            }
            if (matchingTables.isEmpty()) {
                errors.add(new SemanticError(
                    "Column '" + column + "' does not exist in any table in " + context,
                    "COLUMN_NOT_FOUND",
                    column));
            } else if (matchingTables.size() > 1) {
                errors.add(new SemanticError(
                    "Column '" + column + "' is ambiguous in " + context +
                    " (found in: " + String.join(", ", matchingTables) + ")",
                    "AMBIGUOUS_COLUMN",
                    column));
            }
        }
    }

    private void validateColumnExists(String tableName, String columnName,
                                      List<SemanticError> errors, String context) {

        List<ColumnInfo> columns = schemaCache.get(tableName);
        if (columns == null) {
            errors.add(new SemanticError(
                "Table '" + tableName + "' not found in schema",
                "TABLE_NOT_FOUND",
                tableName));
            return;
        }
        boolean exists = columns.stream().anyMatch(c -> c.name.equalsIgnoreCase(columnName));
        if (!exists) {
            errors.add(new SemanticError(
                "Column '" + columnName + "' does not exist in table '" + tableName + "' (" + context + ")",
                "COLUMN_NOT_FOUND",
                columnName));
        }
    }

    private String expressionToColumnName(Expression expr) {
        if (expr instanceof ColumnReference) {
            return ((ColumnReference) expr).getName();
        }
        if (expr instanceof AggregateExpression) {
            return null; // aggregates don't need to be in GROUP BY
        }
        return expr.toString();
    }

    // Invalidate cache (call after DDL changes)
    public void invalidateCache() {
        schemaCache.clear();
        cacheTimestamp = 0;
    }

    // Simple column metadata holder
    private static class ColumnInfo {
        final String name;
        final String dataType;

        ColumnInfo(String name, String dataType) {
            this.name = name;
            this.dataType = dataType;
        }
    }

    // Structured semantic error
    public static class SemanticError {
        private final String message;
        private final String code;
        private final String detail;

        public SemanticError(String message, String code, String detail) {
            this.message = message;
            this.code = code;
            this.detail = detail;
        }

        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getDetail() { return detail; }
    }
}