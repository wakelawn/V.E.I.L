package com.veil.parser;

import com.veil.ast.*;
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

        boolean distinct = match(TokenType.DISTINCT);

        List<SelectItem> selectItems = parseSelectItems();

        consume(TokenType.FROM);

        TableReference table = parseTableReference();

        List<JoinClause> joins = parseJoins();

        Condition whereCondition = null;

        if (match(TokenType.WHERE)) {
            whereCondition = parseOr();
        }

        GroupByClause groupBy = null;
        if (match(TokenType.GROUP)) {
            consume(TokenType.BY);
            groupBy = parseGroupBy();
        }

        List<OrderByItem> orderBy = parseOrderBy();

        Long limit = null;

        if (match(TokenType.LIMIT)) {
            limit = Long.valueOf(consume(TokenType.NUMBER).getValue());
        }

        // semicolon
        match(TokenType.SEMICOLON);

        consume(TokenType.EOF);

        return new SelectStatement(selectItems, table, whereCondition,
                joins, groupBy, orderBy, limit);
    }

    // SELECT [DISTINCT] select_item[, select_item]...
    private List<SelectItem> parseSelectItems() {

        List<SelectItem> items = new ArrayList<>();

        if (match(TokenType.STAR)) {
            items.add(new SelectItem("*"));
            return items;
        }

        items.add(parseSelectItem());

        while (match(TokenType.COMMA)) {
            items.add(parseSelectItem());
        }

        return items;
    }

    // select_item: expression [AS alias]
    private SelectItem parseSelectItem() {
        Expression expr = parseExpression();

        String alias = null;
        if (match(TokenType.AS)) {
            alias = consume(TokenType.IDENTIFIER).getValue();
        } else if (peek().getType() == TokenType.IDENTIFIER
                && !isKeyword(peek().getValue())) {
            // implicit alias (without AS)
            alias = consume(TokenType.IDENTIFIER).getValue();
        }

        return new SelectItem(expr, alias);
    }

    // expression: aggregate_function | column_reference | number | string | ...
    private Expression parseExpression() {
        Token current = peek();

        // Aggregate functions: COUNT(*), AVG(col), SUM(col), etc.
        if (isAggregateFunction(current.getType())) {
            return parseAggregateFunction();
        }

        // Number literal
        if (current.getType() == TokenType.NUMBER) {
            position++;
            return new NumberLiteral(current.getValue());
        }

        // String literal
        if (current.getType() == TokenType.STRING) {
            position++;
            return new StringLiteral(current.getValue());
        }

        // Column reference (possibly qualified)
        return parseColumnReferenceExpression();
    }

    private boolean isAggregateFunction(TokenType type) {
        return type == TokenType.COUNT
                || type == TokenType.SUM
                || type == TokenType.AVG
                || type == TokenType.MIN
                || type == TokenType.MAX;
    }

    // COUNT(*), AVG(col), SUM(col), MIN(col), MAX(col) [DISTINCT]
    private AggregateExpression parseAggregateFunction() {
        Token funcToken = consume(peek().getType()); // COUNT, SUM, AVG, MIN, MAX
        String function = funcToken.getValue().toUpperCase();

        consume(TokenType.LPAREN);

        boolean distinct = false;
        if (match(TokenType.DISTINCT)) {
            distinct = true;
        }

        String argument;
        if (match(TokenType.STAR)) {
            argument = "*";
        } else {
            // Column reference (possibly qualified)
            argument = parseColumnReference();
        }

        consume(TokenType.RPAREN);

        return new AggregateExpression(function, argument, distinct);
    }

    private Expression parseColumnReferenceExpression() {
        String name = consume(TokenType.IDENTIFIER).getValue();

        if (match(TokenType.DOT)) {
            name += "." + consume(TokenType.IDENTIFIER).getValue();
        }

        return new ColumnReference(name);
    }

    private GroupByClause parseGroupBy() {
        List<String> columns = new ArrayList<>();

        columns.add(parseColumnReference());

        while (match(TokenType.COMMA)) {
            columns.add(parseColumnReference());
        }

        Condition havingCondition = null;
        if (match(TokenType.HAVING)) {
            havingCondition = parseOr();
        }

        return new GroupByClause(columns, havingCondition);
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
    // (keywords like WHERE/JOIN/ORDER/GROUP/HAVING/LIMIT stop it naturally)
    private String parseOptionalAlias() {

        if (match(TokenType.AS)) {
            return consume(TokenType.IDENTIFIER).getValue();
        }

        if (peek().getType() == TokenType.IDENTIFIER && !isKeyword(peek().getValue())) {
            return consume(TokenType.IDENTIFIER).getValue();
        }

        return null;
    }

    private boolean isKeyword(String value) {
        String upper = value.toUpperCase();
        return upper.equals("WHERE") || upper.equals("JOIN") || upper.equals("ORDER")
                || upper.equals("GROUP") || upper.equals("HAVING")
                || upper.equals("LIMIT") || upper.equals("ON")
                || upper.equals("ASC") || upper.equals("DESC")
                || upper.equals("BY");
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

        Expression left = parseExpression();

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

        Expression right = parseExpression();

        return new WhereCondition(
                expressionToString(left),
                operatorToken.getValue(),
                expressionToString(right)
        );
    }

    private String expressionToString(Expression expr) {
        if (expr instanceof ColumnReference) {
            return ((ColumnReference) expr).getName();
        }
        if (expr instanceof NumberLiteral) {
            return ((NumberLiteral) expr).getValue();
        }
        if (expr instanceof StringLiteral) {
            return ((StringLiteral) expr).getValue();
        }
        if (expr instanceof AggregateExpression) {
            return expr.toString();
        }
        return expr.toString();
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