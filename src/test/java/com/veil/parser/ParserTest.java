package com.veil.parser;

import com.veil.ast.SelectStatement;
import com.veil.ast.Statement;
import com.veil.ast.WhereCondition;
import com.veil.lexer.Lexer;
import com.veil.lexer.Token;
import com.veil.parser.Parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserTest {

	@Test
	void parsesSelectWithWhere() {

		String sql = "SELECT name FROM projects WHERE id = 1;";

		List<Token> tokens = new Lexer(sql).tokenize();

		Statement statement = new Parser(tokens).parse();

		SelectStatement select = (SelectStatement) statement;

		assertEquals(List.of("name"), select.getColumns());
		assertEquals("projects", select.getTable().getTableName());

		WhereCondition where = select.getWhereCondition();

		assertEquals("id", where.getColumn());
		assertEquals("=", where.getOperator());
		assertEquals("1", where.getValue());
	}

	@Test
	void parsesSelectStarWithoutWhere() {

		String sql = "SELECT * FROM projects;";

		List<Token> tokens = new Lexer(sql).tokenize();

		Statement statement = new Parser(tokens).parse();

		SelectStatement select = (SelectStatement) statement;

		assertEquals(List.of("*"), select.getColumns());
		assertEquals("projects", select.getTable().getTableName());
		assertNull(select.getWhereCondition());
	}

	@Test
	void parsesMultipleColumns() {

		String sql = "SELECT id, name FROM projects;";

		List<Token> tokens = new Lexer(sql).tokenize();

		SelectStatement select = (SelectStatement) new Parser(tokens).parse();

		assertEquals(List.of("id", "name"), select.getColumns());
	}

	@Test
	void parsesStringCondition() {

		String sql = "SELECT name FROM projects WHERE name = 'VEIL';";

		List<Token> tokens = new Lexer(sql).tokenize();

		SelectStatement select = (SelectStatement) new Parser(tokens).parse();

		assertEquals("VEIL", select.getWhereCondition().getValue());
	}

	@Test
	void throwsOnMissingSelect() {

		List<Token> tokens = new Lexer("DROP TABLE users;").tokenize();

		assertThrows(ParseException.class, () -> new Parser(tokens).parse());
	}

	@Test
	void throwsOnMissingFrom() {

		List<Token> tokens = new Lexer("SELECT name;").tokenize();

		assertThrows(ParseException.class, () -> new Parser(tokens).parse());
	}
}
