package com.veil.ast;

public class TableReference {

    private final String tableName;

    public TableReference(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public String toString() {
        return tableName;
    }
}