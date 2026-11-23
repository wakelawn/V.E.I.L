package com.veil.service;

import com.veil.analysis.IndexAdvisor;
import com.veil.analysis.OptimizationEngine;
import com.veil.analysis.OptimizationFinding;
import com.veil.ast.SelectStatement;
import com.veil.ast.Statement;
import com.veil.core.database.DatabaseAdapter;
import com.veil.core.database.ExecutionPlanProvider;
import com.veil.explain.PlanSummary;
import com.veil.lexer.Lexer;
import com.veil.lexer.Token;
import com.veil.parser.Parser;
import com.veil.security.SqlValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryService {

	private final IndexAdvisor indexAdvisor;
	private final ExecutionPlanProvider planProvider;
	private final DatabaseAdapter databaseAdapter;
	private final SqlValidator sqlValidator;

	public QueryService(IndexAdvisor indexAdvisor,
                        ExecutionPlanProvider planProvider,
                        DatabaseAdapter databaseAdapter,
                        SqlValidator sqlValidator) {
		this.indexAdvisor = indexAdvisor;
		this.planProvider = planProvider;
		this.databaseAdapter = databaseAdapter;
		this.sqlValidator = sqlValidator;
	}

	public AnalyzeResponse analyze(String sql) {

		// Validate SQL before parsing: SELECT/WITH only, one statement,
		// bounded length.
		sqlValidator.validateSelectOnly(sql);

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


		// 5. OPTIMIZATION ENGINE (AST + plan-aware rules)
		OptimizationEngine engine = new OptimizationEngine(databaseAdapter);

		// Try to get the execution plan so plan-aware rules (ScanCost,
		// CompositeIndex, ExpensiveSort, AggregationCost, FilterSelectivity)
		// can detect expensive operations. If the database is unreachable,
		// fall back to AST-only analysis - analysis must never fail because
		// the plan could not be fetched.
		PlanSummary plan = null;
		try {
			String json = planProvider.getExecutionPlan(sql);
			plan = parsePlanJson(json);
		} catch (Exception ignored) {
			// Plan unavailable; continue with AST-only rules.
		}

		Map<String, Object> schema = buildSchema();

		List<OptimizationFinding> findings = engine.run(select, plan, schema);

		// 6. INDEX ANALYSIS (verifies the filter hint against real indexes)
		indexAdvisor.advise(select, findings);

		// plain strings kept for backward compatibility
		List<String> recommendations = new ArrayList<>();

		for (OptimizationFinding finding : findings) {
			recommendations.add(finding.getMessage());
		}


		// 7. RESPONSE
		return new AnalyzeResponse(tokens, statement.toString(),
				select.getTable().getTableName(), select.getColumns(),
				whereCondition, findings, recommendations);
	}

	// Parse EXPLAIN JSON into a PlanSummary. Best-effort: returns null on
	// any failure so the caller can fall back to AST-only analysis.
	private PlanSummary parsePlanJson(String json) {
		try {
			return com.veil.explain.ExplainService.parsePlanJson(json);
		} catch (Exception ignored) {
			return null;
		}
	}

	// Build a schema map from the database adapter for schema-aware rules.
	// Best-effort: returns empty map if the database is unreachable.
	private Map<String, Object> buildSchema() {
		Map<String, Object> schema = new HashMap<>();
		try {
			schema.put("tables", databaseAdapter.getTables());
			schema.put("columns", databaseAdapter.getColumns());
			schema.put("indexes", databaseAdapter.getIndexes());
		} catch (Exception ignored) {
			// Schema unavailable; continue with empty map.
		}
		return schema;
	}
}
