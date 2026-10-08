package com.veil;

import com.veil.ast.SelectStatement;
import com.veil.ast.Statement;
import com.veil.lexer.Lexer;
import com.veil.lexer.Token;
import com.veil.parser.Parser;

import java.util.List;

public class ParserTest {

	public static void main(String[] args) {

		// String sql = "SELECT name FROM users;";
		String sql = "SELECT name FROM projects WHERE id = 1;";

		// 1. Lexing
		Lexer lexer = new Lexer(sql);

		List<Token> tokens = lexer.tokenize();

		System.out.println("TOKENS:");

		for (Token token : tokens) {
			System.out.println(token);
		}

		// 2. Parsing
		Parser parser = new Parser(tokens);

		Statement statement = parser.parse();

		// 3. AST
		System.out.println();
		System.out.println("AST:");
		System.out.println(statement);

		SelectStatement select = (SelectStatement) statement;

		System.out.println();
		System.out.println("Table: " + select.getTable().getTableName());

		System.out.println("Columns: " + select.getColumns());

		System.out.println("WHERE: " + select.getWhereCondition());
	}
}