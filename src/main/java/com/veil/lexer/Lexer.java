package com.veil.lexer;

import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private final String input;
    private int position = 0;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {

        List<Token> tokens = new ArrayList<>();

        while (position < input.length()) {

            char current = input.charAt(position);

            // for whitespace
            if (Character.isWhitespace(current)) {
                position++;
                continue;
            }

            // *
            if (current == '*') {
                tokens.add(new Token(TokenType.STAR, "*"));
                position++;
                continue;
            }

            // = (single-character operator, needs no lookahead)
            if (current == '=') {
                tokens.add(new Token(TokenType.EQUALS, "="));
                position++;
                continue;
            }

            // < and <= and <> (two-character operators need lookahead)
            if (current == '<') {

                position++;

                if (position < input.length()
                        && input.charAt(position) == '=') {

                    tokens.add(new Token(TokenType.LESS_OR_EQUAL, "<="));
                    position++;

                } else if (position < input.length()
                        && input.charAt(position) == '>') {

                    tokens.add(new Token(TokenType.NOT_EQUALS, "<>"));
                    position++;

                } else {

                    tokens.add(new Token(TokenType.LESS, "<"));
                }

                continue;
            }

            // > and >= (same lookahead idea)
            if (current == '>') {

                position++;

                if (position < input.length()
                        && input.charAt(position) == '=') {

                    tokens.add(new Token(TokenType.GREATER_OR_EQUAL, ">="));
                    position++;

                } else {

                    tokens.add(new Token(TokenType.GREATER, ">"));
                }

                continue;
            }

            // != (the ! must be followed by =, otherwise it is an error)
            if (current == '!') {

                position++;

                if (position < input.length()
                        && input.charAt(position) == '=') {

                    tokens.add(new Token(TokenType.NOT_EQUALS, "!="));
                    position++;

                } else {

                    throw new LexException(
                            "Unknown character: ! (expected !=)");
                }

                continue;
            }

            // ,
            if (current == ',') {
                tokens.add(new Token(TokenType.COMMA, ","));
                position++;
                continue;
            }

            // ;
            if (current == ';') {
                tokens.add(new Token(TokenType.SEMICOLON, ";"));
                position++;
                continue;
            }

            // ( and )
            if (current == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                position++;
                continue;
            }
            if (current == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                position++;
                continue;
            }

            // . (for qualified names like u.id)
            if (current == '.') {
                tokens.add(new Token(TokenType.DOT, "."));
                position++;
                continue;
            }

            // words / identifiers / keywords
            if (Character.isLetter(current) || current == '_') {

                StringBuilder word = new StringBuilder();

                while (position < input.length()) {

                    char c = input.charAt(position);

                    if (Character.isLetterOrDigit(c) || c == '_') {
                        word.append(c);
                        position++;
                    } else {
                        break;
                    }
                }

                String value = word.toString();

                switch (value.toUpperCase()) {

                    case "SELECT" ->
                        tokens.add(new Token(TokenType.SELECT, value));

                    case "FROM" ->
                        tokens.add(new Token(TokenType.FROM, value));

                    case "WHERE" ->
                        tokens.add(new Token(TokenType.WHERE, value));

                    case "AND" ->
                        tokens.add(new Token(TokenType.AND, value));

                    case "OR" ->
                        tokens.add(new Token(TokenType.OR, value));

                    case "JOIN" ->
                        tokens.add(new Token(TokenType.JOIN, value));

                    case "ON" ->
                        tokens.add(new Token(TokenType.ON, value));

                    case "AS" ->
                        tokens.add(new Token(TokenType.AS, value));

                    case "ORDER" ->
                        tokens.add(new Token(TokenType.ORDER, value));

                    case "BY" ->
                        tokens.add(new Token(TokenType.BY, value));

                    case "ASC" ->
                        tokens.add(new Token(TokenType.ASC, value));

                    case "DESC" ->
                        tokens.add(new Token(TokenType.DESC, value));

                    case "LIMIT" ->
                        tokens.add(new Token(TokenType.LIMIT, value));

                    case "GROUP" ->
                        tokens.add(new Token(TokenType.GROUP, value));

                    case "HAVING" ->
                        tokens.add(new Token(TokenType.HAVING, value));

                    case "DISTINCT" ->
                        tokens.add(new Token(TokenType.DISTINCT, value));

                    case "COUNT" ->
                        tokens.add(new Token(TokenType.COUNT, value));

                    case "SUM" ->
                        tokens.add(new Token(TokenType.SUM, value));

                    case "AVG" ->
                        tokens.add(new Token(TokenType.AVG, value));

                    case "MIN" ->
                        tokens.add(new Token(TokenType.MIN, value));

                    case "MAX" ->
                        tokens.add(new Token(TokenType.MAX, value));

                    default ->
                        tokens.add(new Token(TokenType.IDENTIFIER, value));
                }

                continue;
            }

            // numbers
            if (Character.isDigit(current)) {

                StringBuilder number = new StringBuilder();

                while (position < input.length()
                        && Character.isDigit(input.charAt(position))) {

                    number.append(input.charAt(position));
                    position++;
                }

                tokens.add(
                    new Token(TokenType.NUMBER, number.toString())
                );

                continue;
            }

            // string
            if (current == '\'') {

                position++;

                StringBuilder string = new StringBuilder();

                while (position < input.length()
                        && input.charAt(position) != '\'') {

                    string.append(input.charAt(position));
                    position++;
                }

                if (position < input.length()) {
                    position++;
                }

                tokens.add(
                    new Token(TokenType.STRING, string.toString())
                );

                continue;
            }

            throw new LexException(
                "Unknown character: " + current
            );
        }

        // EOF - end of file
        tokens.add(new Token(TokenType.EOF, ""));

        return tokens;
    }
}