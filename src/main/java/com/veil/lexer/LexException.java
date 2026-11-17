package com.veil.lexer;

// thrown when the lexer meets a character it cannot tokenize
// extends RuntimeException so existing behavior is unchanged
public class LexException extends RuntimeException {

	public LexException(String message) {
		super(message);
	}
}
