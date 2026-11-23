package com.veil.lexer;

public enum TokenType {

    SELECT,
    FROM,
    WHERE,
    AND,
    OR,
    JOIN,
    ON,
    AS,
    ORDER,
    BY,
    ASC,
    DESC,
    LIMIT,
    GROUP,
    HAVING,
    DISTINCT,
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,

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
    DOT,
    LPAREN,
    RPAREN,

    EOF
}