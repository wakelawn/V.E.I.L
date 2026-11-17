package com.veil.ast;

// an AND/OR node in the WHERE condition tree.
// left and right can be comparisons or deeper logical nodes,
// which is how precedence (AND binds tighter than OR) is represented
public class LogicalCondition implements Condition {

	private final String operator;
	private final Condition left;
	private final Condition right;

	public LogicalCondition(String operator, Condition left, Condition right) {

		this.operator = operator;
		this.left = left;
		this.right = right;
	}

	public String getOperator() {
		return operator;
	}

	public Condition getLeft() {
		return left;
	}

	public Condition getRight() {
		return right;
	}

	@Override
	public String toString() {
		return "(" + left + " " + operator + " " + right + ")";
	}
}
