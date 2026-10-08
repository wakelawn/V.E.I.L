package com.veil;

import com.veil.lexer.Lexer;
import com.veil.lexer.Token;

import java.util.List;

public class LexerTest {

    public static void main(String[] args) {

        String sql = "SELECT name FROM users WHERE id = 10;";

        Lexer lexer = new Lexer(sql);

        List<Token> tokens = lexer.tokenize();

        for (Token token : tokens) {
            System.out.println(token);
        }
    }
}