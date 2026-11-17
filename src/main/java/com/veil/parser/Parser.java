package com.veil.parser;

import com.veil.ast.Condition;
import com.veil.ast.JoinClause;
import com.veil.ast.LogicalCondition;
import com.veil.ast.OrderByItem;
import com.veil.ast.SelectStatement;
import com.veil.ast.Statement;
import com.veil.ast.TableReference;
import com.veil.ast.WhereCondition;
import com.veil.lexer.Token;
import com.veil.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

// recursive descent parser:
// every grammar rule below becomes one method, and the call chain
// parseOr -> parseAnd -> parseComparison encodes precedence
// (AND binds tighter than OR)
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

		TableReference table = parseTableReference();

		List<JoinClause> joins = parseJoins();

		Condition whereCondition = null;

		if (match(TokenType.WHERE)) {
			whereCondition = parseOr();
		}

		List<OrderByItem> orderBy = parseOrderBy();

		Long limit = null;

		if (match(TokenType.LIMIT)) {
			limit = Long.valueOf(consume(TokenType.NUMBER).getValue());
		}

		// semicolon
		match(TokenType.SEMICOLON);

		consume(TokenType.EOF);

		return new SelectStatement(columns, table, whereCondition,
				joins, orderBy, limit);
	}

	// SELECT * | SELECT column[, column]... (qualified names allowed: u.id)
	private List<String> parseColumns() {

		List<String> columns = new ArrayList<>();

		if (match(TokenType.STAR)) {
			columns.add("*");
			return columns;
		}

		columns.add(parseColumnReference());

		while (match(TokenType.COMMA)) {
			columns.add(parseColumnReference());
		}

		return columns;
	}

	// column or qualified column: u.id
	private String parseColumnReference() {

		String name = consume(TokenType.IDENTIFIER).getValue();

		if (match(TokenType.DOT)) {
			name += "." + consume(TokenType.IDENTIFIER).getValue();
		}

		return name;
	}

	// table name with optional alias: users, users u, users AS u
	private TableReference parseTableReference() {

		String tableName = consume(TokenType.IDENTIFIER).getValue();
		String alias = parseOptionalAlias();

		return new TableReference(tableName, alias);
	}

	// an alias is only present when the next token is a plain identifier
	// (keywords like WHERE/JOIN/ORDER stop it naturally)
	private String parseOptionalAlias() {

		if (match(TokenType.AS)) {
			return consume(TokenType.IDENTIFIER).getValue();
		}

		if (peek().getType() == TokenType.IDENTIFIER) {
			return consume(TokenType.IDENTIFIER).getValue();
		}

		return null;
	}

	// zero or more: JOIN table [alias] ON condition
	private List<JoinClause> parseJoins() {

		List<JoinClause> joins = new ArrayList<>();

		while (peek().getType() == TokenType.JOIN) {

			consume(TokenType.JOIN);

			TableReference table = parseTableReference();

			consume(TokenType.ON);

			joins.add(new JoinClause(table, parseOr()));
		}

		return joins;
	}

	// lowest precedence: OR combines two AND-groups
	private Condition parseOr() {

		Condition left = parseAnd();

		while (peek().getType() == TokenType.OR) {

			consume(TokenType.OR);
			left = new LogicalCondition("OR", left, parseAnd());
		}

		return left;
	}

	// higher precedence: AND combines two comparisons
	private Condition parseAnd() {

		Condition left = parseComparison();

		while (peek().getType() == TokenType.AND) {

			consume(TokenType.AND);
			left = new LogicalCondition("AND", left, parseComparison());
		}

		return left;
	}

	// one leaf: column op value
	private Condition parseComparison() {

		String column = parseColumnReference();

		Token operatorToken = peek();

		if (operatorToken.getType() != TokenType.EQUALS
				&& operatorToken.getType() != TokenType.NOT_EQUALS
				&& operatorToken.getType() != TokenType.LESS
				&& operatorToken.getType() != TokenType.LESS_OR_EQUAL
				&& operatorToken.getType() != TokenType.GREATER
				&& operatorToken.getType() != TokenType.GREATER_OR_EQUAL) {

			throw new ParseException(
					"Expected comparison operator but found "
							+ operatorToken.getType());
		}

		position++;

		Token valueToken = peek();

		if (valueToken.getType() != TokenType.NUMBER
				&& valueToken.getType() != TokenType.STRING
				&& valueToken.getType() != TokenType.IDENTIFIER) {

			throw new ParseException(
					"Expected value in WHERE condition but found "
							+ valueToken.getType());
		}

		position++;

		String value = valueToken.getValue();

		if (peek().getType() == TokenType.DOT) {
			position++;
			value += "." + consume(TokenType.IDENTIFIER).getValue();
		}

		return new WhereCondition(column,
				operatorToken.getValue(), value);
	}

	// ORDER BY column [ASC|DESC][, column ...]
	private List<OrderByItem> parseOrderBy() {

		List<OrderByItem> items = new ArrayList<>();

		if (!match(TokenType.ORDER)) {
			return items;
		}

		consume(TokenType.BY);

		do {
			String column = parseColumnReference();

			boolean descending = false;

			if (match(TokenType.DESC)) {
				descending = true;
			} else {
				match(TokenType.ASC);
			}

			items.add(new OrderByItem(column, descending));

		} while (match(TokenType.COMMA));

		return items;
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
