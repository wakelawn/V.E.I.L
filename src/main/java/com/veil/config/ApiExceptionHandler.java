package com.veil.config;

import com.veil.ai.AiUnavailableException;
import com.veil.ai.AiUpstreamException;
import com.veil.lexer.LexException;
import com.veil.parser.ParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

// returns parse/validation errors as HTTP 400 with a readable message
// so the frontend error box can show them instead of a raw 500
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(LexException.class)
	public ResponseEntity<Map<String, String>> onLex(LexException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(ParseException.class)
	public ResponseEntity<Map<String, String>> onParse(ParseException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> onBadArgument(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}

	// database rejected the SQL itself (e.g. unknown table/column)
	@ExceptionHandler(BadSqlGrammarException.class)
	public ResponseEntity<Map<String, String>> onBadSql(BadSqlGrammarException e) {
		// Sanitize: don't leak database schema details
		return ResponseEntity.badRequest().body(Map.of("error", "Database rejected the query. Check table/column names."));
	}

	// could not reach PostgreSQL at all (down / wrong credentials)
	@ExceptionHandler(CannotGetJdbcConnectionException.class)
	public ResponseEntity<Map<String, String>> onConnection(CannotGetJdbcConnectionException e) {
		// Sanitize: don't leak connection details
		return ResponseEntity.status(503).body(Map.of("error", "Database connection failed. Check server status."));
	}

	// NVIDIA NIM has no API key configured
	@ExceptionHandler(AiUnavailableException.class)
	public ResponseEntity<Map<String, String>> onAiUnavailable(AiUnavailableException e) {
		return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
	}

	// NVIDIA NIM was reachable but the upstream request failed
	@ExceptionHandler(AiUpstreamException.class)
	public ResponseEntity<Map<String, String>> onAiUpstream(AiUpstreamException e) {
		return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
	}

	// a requested resource does not exist (e.g. unknown connection id)
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<Map<String, String>> onMissing(NoSuchElementException e) {
		return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
	}
}
