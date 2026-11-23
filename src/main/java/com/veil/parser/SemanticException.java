package com.veil.parser;

// Semantic validation error with location information.
public class SemanticException extends RuntimeException {

    private final int line;
    private final int column;

    public SemanticException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public SemanticException(String message) {
        this(message, -1, -1);
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}