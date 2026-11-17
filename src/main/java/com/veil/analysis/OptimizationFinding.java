package com.veil.analysis;

// one problem a rule found in the query, along with the advice we can
// honestly give from the AST alone (no database lookups, no invented facts)
public class OptimizationFinding {

	private String ruleId;
	private String severity;
	private String title;
	private String message;
	private String suggestionSql;

	public OptimizationFinding() {
	}

	public OptimizationFinding(String ruleId, String severity, String title,
			String message, String suggestionSql) {

		this.ruleId = ruleId;
		this.severity = severity;
		this.title = title;
		this.message = message;
		this.suggestionSql = suggestionSql;
	}

	public String getRuleId() {
		return ruleId;
	}

	public String getSeverity() {
		return severity;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public String getSuggestionSql() {
		return suggestionSql;
	}
}
