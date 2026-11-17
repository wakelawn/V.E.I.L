package com.veil.config;

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
		String message = e.getMostSpecificCause().getMessage();
		return ResponseEntity.badRequest().body(Map.of("error", "Database rejected the query: " + message));
	}

	// could not reach PostgreSQL at all (down / wrong credentials)
	@ExceptionHandler(CannotGetJdbcConnectionException.class)
	public ResponseEntity<Map<String, String>> onConnection(CannotGetJdbcConnectionException e) {
		String message = e.getMostSpecificCause().getMessage();
		return ResponseEntity.status(503).body(Map.of("error", "Database connection failed: " + message));
	}

	// a requested resource does not exist (e.g. unknown connection id)
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<Map<String, String>> onMissing(NoSuchElementException e) {
		return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
	}
}
