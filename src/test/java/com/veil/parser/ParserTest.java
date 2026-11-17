package com.veil.parser;

import com.veil.ast.JoinClause;
import com.veil.ast.LogicalCondition;
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

		// the WHERE slot holds a condition tree; a simple comparison
		// is a leaf node, so it can be cast directly
		WhereCondition where = (WhereCondition) select.getWhereCondition();

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

		WhereCondition where =
				(WhereCondition) select.getWhereCondition();

		assertEquals("VEIL", where.getValue());
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

	@Test
	void parsesAndConditionAsLogicalNode() {

		String sql = "SELECT name FROM projects WHERE id = 1 AND name = 'VEIL';";

		SelectStatement select =
				(SelectStatement) new Parser(new Lexer(sql).tokenize()).parse();

		LogicalCondition logical =
				(LogicalCondition) select.getWhereCondition();

		assertEquals("AND", logical.getOperator());

		WhereCondition left = (WhereCondition) logical.getLeft();
		WhereCondition right = (WhereCondition) logical.getRight();

		assertEquals("id", left.getColumn());
		assertEquals("name", right.getColumn());
	}

	@Test
	void parsesOrWithAndPrecedence() {

		// a = 1 OR b = 2 AND c = 3  means  OR(a=1, AND(b=2, c=3))
		// because AND binds tighter than OR
		String sql = "SELECT name FROM projects "
				+ "WHERE id = 1 OR id = 2 AND name = 'VEIL';";

		SelectStatement select =
				(SelectStatement) new Parser(new Lexer(sql).tokenize()).parse();

		LogicalCondition or = (LogicalCondition) select.getWhereCondition();
		LogicalCondition and = (LogicalCondition) or.getRight();

		assertEquals("OR", or.getOperator());
		assertEquals("AND", and.getOperator());
	}

	@Test
	void parsesComparisonOperators() {

		String[] sqls = {
				"SELECT name FROM t WHERE id <> 5;",
				"SELECT name FROM t WHERE id != 5;",
				"SELECT name FROM t WHERE id < 5;",
				"SELECT name FROM t WHERE id <= 5;",
				"SELECT name FROM t WHERE id > 5;",
				"SELECT name FROM t WHERE id >= 5;",
		};

		String[] operators = { "<>", "!=", "<", "<=", ">", ">=" };

		for (int i = 0; i < sqls.length; i++) {

			SelectStatement select = (SelectStatement)
					new Parser(new Lexer(sqls[i]).tokenize()).parse();

			WhereCondition where =
					(WhereCondition) select.getWhereCondition();

			assertEquals(operators[i], where.getOperator(),
					"failed for: " + sqls[i]);
		}
	}

	@Test
	void parsesQualifiedColumns() {

		String sql = "SELECT u.id, u.name FROM users u WHERE u.id = 1;";

		SelectStatement select =
				(SelectStatement) new Parser(new Lexer(sql).tokenize()).parse();

		assertEquals(List.of("u.id", "u.name"), select.getColumns());
		assertEquals("users", select.getTable().getTableName());
		assertEquals("u", select.getTable().getAlias());

		WhereCondition where =
				(WhereCondition) select.getWhereCondition();

		assertEquals("u.id", where.getColumn());
	}

	@Test
	void parsesOrderByAndLimit() {

		String sql = "SELECT name FROM projects "
				+ "ORDER BY name DESC, id ASC LIMIT 5;";

		SelectStatement select =
				(SelectStatement) new Parser(new Lexer(sql).tokenize()).parse();

		assertEquals(2, select.getOrderBy().size());
		assertEquals("name", select.getOrderBy().get(0).getColumn());
		assertEquals(true, select.getOrderBy().get(0).isDescending());
		assertEquals("id", select.getOrderBy().get(1).getColumn());
		assertEquals(false, select.getOrderBy().get(1).isDescending());

		assertEquals(5L, select.getLimit());
	}

	@Test
	void parsesJoinWithAliasesAndCondition() {

		String sql = "SELECT u.id FROM users u "
				+ "JOIN orders o ON u.id = o.user_id;";

		SelectStatement select =
				(SelectStatement) new Parser(new Lexer(sql).tokenize()).parse();

		assertEquals(1, select.getJoins().size());

		JoinClause join = select.getJoins().get(0);

		assertEquals("orders", join.getTable().getTableName());
		assertEquals("o", join.getTable().getAlias());

		WhereCondition on = (WhereCondition) join.getCondition();

		assertEquals("u.id", on.getColumn());
		assertEquals("=", on.getOperator());
		assertEquals("o.user_id", on.getValue());
	}

	@Test
	void throwsOnJoinWithoutOn() {

		List<Token> tokens =
				new Lexer("SELECT id FROM users JOIN orders;").tokenize();

		assertThrows(ParseException.class, () -> new Parser(tokens).parse());
	}
}
