package com.veil.parser;

import com.veil.ast.SelectStatement;
import com.veil.ast.Statement;
import com.veil.ast.TableReference;
import com.veil.ast.WhereCondition;
import com.veil.lexer.Token;
import com.veil.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class Parser {

	private final List<Token> tokens;
	private int position = 0;

	public Parser(List<Token> tokens) {
		this.tokens = tokens;
	}

	public Statement parse() {

		consume(TokenType.SELECT);

		List<String> columns = parseColumns();

		consume(TokenType.FROM);

		String tableName = consume(TokenType.IDENTIFIER).getValue();

		WhereCondition whereCondition = null;

		if (match(TokenType.WHERE)) {
			whereCondition = parseWhereCondition();
		}

		// Optional semicolon
		match(TokenType.SEMICOLON);

		// Make sure nothing is left unparsed
		consume(TokenType.EOF);

		return new SelectStatement(columns, new TableReference(tableName),
				whereCondition);
	}

	private List<String> parseColumns() {

		List<String> columns = new ArrayList<>();

		// SELECT *
		if (match(TokenType.STAR)) {
			columns.add("*");
			return columns;
		}

		// SELECT column
		columns.add(consume(TokenType.IDENTIFIER).getValue());

		// SELECT column1, column2
		while (match(TokenType.COMMA)) {

			columns.add(consume(TokenType.IDENTIFIER).getValue());
		}

		return columns;
	}

	private WhereCondition parseWhereCondition() {

		String column = consume(TokenType.IDENTIFIER).getValue();

		String operator = consume(TokenType.EQUALS).getValue();

		Token valueToken = peek();

		if (valueToken.getType() != TokenType.NUMBER
				&& valueToken.getType() != TokenType.STRING
				&& valueToken.getType() != TokenType.IDENTIFIER) {

			throw new ParseException(
					"Expected value in WHERE condition but found "
							+ valueToken.getType());
		}

		position++;

		return new WhereCondition(column, operator, valueToken.getValue());
	}

	private Token consume(TokenType expected) {

		Token token = peek();

		if (token.getType() != expected) {

			throw new ParseException(
					"Expected " + expected + " but found " + token.getType());
		}

		position++;

		return token;
	}

	private boolean match(TokenType type) {

		if (peek().getType() == type) {
			position++;
			return true;
		}

		return false;
	}

	private Token peek() {

		if (position >= tokens.size()) {
			return new Token(TokenType.EOF, "");
		}

		return tokens.get(position);
	}
}