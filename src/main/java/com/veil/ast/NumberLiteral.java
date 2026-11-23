package com.veil.ast;

// Numeric literal (integer or decimal)
public class NumberLiteral implements Expression {

    private final String value;

    public NumberLiteral(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}