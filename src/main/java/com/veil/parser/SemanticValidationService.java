package com.veil.parser;

import com.veil.ast.*;
import com.veil.parser.SemanticAnalyzer.SemanticError;

import org.springframework.stereotype.Service;

import java.util.List;

// Service layer for semantic validation.
@Service
public class SemanticValidationService {

    private final SemanticAnalyzer analyzer;

    public SemanticValidationService(SemanticAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    // Validates a SELECT statement and returns semantic errors.
    public List<SemanticError> validate(SelectStatement statement) {
        return analyzer.validate(statement);
    }

    // Validates a SQL string by parsing then analyzing.
    public List<SemanticError> validateSql(String sql) {
        List<com.veil.lexer.Token> tokens = new com.veil.lexer.Lexer(sql).tokenize();
        Statement stmt = new Parser(tokens).parse();
        if (!(stmt instanceof SelectStatement)) {
            throw new IllegalArgumentException("Only SELECT statements are supported for semantic validation");
        }
        return analyzer.validate((SelectStatement) stmt);
    }
}