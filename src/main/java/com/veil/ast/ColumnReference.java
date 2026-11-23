package com.veil.ast;

// Simple column reference (qualified or unqualified): column, table.column, alias.column
public class ColumnReference implements Expression {

    private final String name;  // qualified or simple name

    public ColumnReference(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnReference)) return false;
        ColumnReference that = (ColumnReference) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}