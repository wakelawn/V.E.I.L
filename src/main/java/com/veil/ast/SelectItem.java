package com.veil.ast;

import java.util.List;
import java.util.Objects;

// Represents a single item in the SELECT clause: an expression with optional alias.
public class SelectItem {

    private final Expression expression;
    private final String alias;

    public SelectItem(Expression expression, String alias) {
        this.expression = expression;
        this.alias = alias;
    }

    // Convenience for simple column references (backward compatibility)
    public SelectItem(String column) {
        this(new ColumnReference(column), null);
    }

    public SelectItem(String column, String alias) {
        this(new ColumnReference(column), alias);
    }

    public Expression getExpression() {
        return expression;
    }

    public String getAlias() {
        return alias;
    }

    public boolean hasAlias() {
        return alias != null && !alias.isBlank();
    }

    @Override
    public String toString() {
        if (hasAlias()) {
            return expression + " AS " + alias;
        }
        return expression.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SelectItem)) return false;
        SelectItem that = (SelectItem) o;
        return Objects.equals(expression, that.expression) && Objects.equals(alias, that.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, alias);
    }
}