package com.veil.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LexerTest {

	@Test
	void tokenizesSelectFromWhere() {

		String sql = "SELECT name FROM users WHERE id = 10;";

		List<Token> tokens = new Lexer(sql).tokenize();

		assertEquals(TokenType.SELECT, tokens.get(0).getType());
		assertEquals(TokenType.IDENTIFIER, tokens.get(1).getType());
		assertEquals("name", tokens.get(1).getValue());

		assertEquals(TokenType.FROM, tokens.get(2).getType());

		assertEquals(TokenType.IDENTIFIER, tokens.get(3).getType());
		assertEquals("users", tokens.get(3).getValue());

		assertEquals(TokenType.WHERE, tokens.get(4).getType());

		assertEquals(TokenType.IDENTIFIER, tokens.get(5).getType());
		assertEquals("id", tokens.get(5).getValue());

		assertEquals(TokenType.EQUALS, tokens.get(6).getType());

		assertEquals(TokenType.NUMBER, tokens.get(7).getType());
		assertEquals("10", tokens.get(7).getValue());

		assertEquals(TokenType.SEMICOLON, tokens.get(8).getType());

		assertEquals(TokenType.EOF, tokens.get(9).getType());
	}

	@Test
	void tokenizesStar() {

		List<Token> tokens = new Lexer("SELECT * FROM t;").tokenize();

		assertEquals(TokenType.STAR, tokens.get(1).getType());
		assertEquals("*", tokens.get(1).getValue());
	}

	@Test
	void tokenizesStringLiteral() {

		List<Token> tokens =
				new Lexer("SELECT name FROM users WHERE id = 'abc';")
						.tokenize();

		Token string = tokens.get(7);

		assertEquals(TokenType.STRING, string.getType());
		assertEquals("abc", string.getValue());
	}

	@Test
	void tokenizesCaseInsensitiveKeywords() {

		List<Token> tokens = new Lexer("select * from t;").tokenize();

		assertEquals(TokenType.SELECT, tokens.get(0).getType());
		assertEquals(TokenType.FROM, tokens.get(2).getType());
	}

	@Test
	void throwsOnUnknownCharacter() {

		assertThrows(RuntimeException.class,
				() -> new Lexer("SELECT name FROM users WHERE id = (1);")
						.tokenize());
	}
}
