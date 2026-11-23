package com.veil.ast;

import java.util.List;

// GROUP BY clause with optional HAVING condition.
public class GroupByClause {

    private final List<String> columns;
    private final Condition havingCondition;

    public GroupByClause(List<String> columns, Condition havingCondition) {
        this.columns = columns;
        this.havingCondition = havingCondition;
    }

    public List<String> getColumns() {
        return columns;
    }

    public Condition getHavingCondition() {
        return havingCondition;
    }

    @Override
    public String toString() {
        String result = "GROUP BY " + String.join(", ", columns);
        if (havingCondition != null) {
            result += " HAVING " + havingCondition;
        }
        return result;
    }
}