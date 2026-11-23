package com.veil.ast;

// Represents an aggregate function call: COUNT(*), AVG(column), SUM(column), etc.
public class AggregateExpression implements Expression {

    private final String function;  // COUNT, SUM, AVG, MIN, MAX
    private final String argument;  // * or column name (qualified or not)
    private final boolean distinct;

    public AggregateExpression(String function, String argument, boolean distinct) {
        this.function = function.toUpperCase();
        this.argument = argument;
        this.distinct = distinct;
    }

    public String getFunction() {
        return function;
    }

    public String getArgument() {
        return argument;
    }

    public boolean isDistinct() {
        return distinct;
    }

    @Override
    public String toString() {
        String distinctStr = distinct ? "DISTINCT " : "";
        return function + "(" + distinctStr + argument + ")";
    }
}