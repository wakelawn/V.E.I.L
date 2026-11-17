package com.veil.ast;

public class TableReference {

    private final String tableName;
    private final String alias;

    public TableReference(String tableName) {
        this(tableName, null);
    }

    public TableReference(String tableName, String alias) {

        this.tableName = tableName;
        this.alias = alias;
    }

    public String getTableName() {
        return tableName;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public String toString() {
        return alias != null ? tableName + " " + alias : tableName;
    }
}
