package com.veil.lexer;

public enum TokenType {

    SELECT,
    FROM,
    WHERE,

    IDENTIFIER,
    NUMBER,
    STRING,

    EQUALS,
    NOT_EQUALS,
    LESS,
    LESS_OR_EQUAL,
    GREATER,
    GREATER_OR_EQUAL,
    COMMA,
    STAR,
    SEMICOLON,

    EOF
}