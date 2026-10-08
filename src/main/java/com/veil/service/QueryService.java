package com.veil.service;

import com.veil.ast.SelectStatement;
import com.veil.ast.Statement;
import com.veil.lexer.Lexer;
import com.veil.lexer.Token;
import com.veil.parser.Parser;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QueryService {

	public AnalyzeResponse analyze(String sql) {

		if (sql == null || sql.isBlank()) {
			throw new IllegalArgumentException("SQL query cannot be empty");
		}

		// 1. LEXER
		Lexer lexer = new Lexer(sql);

		List<Token> tokenObjects = lexer.tokenize();

		List<String> tokens = new ArrayList<>();

		for (Token token : tokenObjects) {
			tokens.add(token.toString());
		}

		// 2. PARSER
		Parser parser = new Parser(tokenObjects);

		Statement statement = parser.parse();

		// 3. AST
		if (!(statement instanceof SelectStatement)) {

			throw new IllegalArgumentException(
					"Only SELECT statements are currently supported.");
		}

		SelectStatement select = (SelectStatement) statement;


		// 4. WHERE
		String whereCondition = null;

		if (select.getWhereCondition() != null) {

			whereCondition = select.getWhereCondition().toString();
		}


		// 5. RECOMMENDATIONS
		List<String> recommendations = generateRecommendations(select);


		// 6. RESPONSE
		return new AnalyzeResponse(tokens, statement.toString(),
				select.getTable().getTableName(), select.getColumns(),
				whereCondition, recommendations);
	}

	private List<String> generateRecommendations(SelectStatement select) {

		List<String> recommendations = new ArrayList<>();

		// SELECT *
		if (select.getColumns().contains("*")) {

			recommendations.add(
					"Avoid SELECT * when only specific columns are required.");
		}

		// WHERE
		if (select.getWhereCondition() != null) {

			String column = select.getWhereCondition().getColumn();

			recommendations.add("WHERE condition detected on column '" + column
					+ "'. Consider an index if this column is frequently filtered.");
		}

		// Nothing detected
		if (recommendations.isEmpty()) {

			recommendations.add("No basic optimization issues detected.");
		}

		return recommendations;
	}
}