package com.veil.analysis;

import com.veil.ast.SelectStatement;

import java.util.ArrayList;
import java.util.List;

// the optimization engine: runs every rule against the parsed AST and
// collects the findings. It only reports what can be derived from the
// query itself - deterministic analysis, never invented facts
public class OptimizationEngine {

	private final List<OptimizationRule> rules = new ArrayList<>();

	public OptimizationEngine() {

		rules.add(new SelectStarRule());
		rules.add(new FilterIndexRule());
		rules.add(new NoFilterRule());
		rules.add(new SortRule());
	}

	public List<OptimizationFinding> run(SelectStatement select) {

		List<OptimizationFinding> findings = new ArrayList<>();

		for (OptimizationRule rule : rules) {
			rule.check(select, findings);
		}

		// an honest "all clear" instead of silence
		if (findings.isEmpty()) {
			findings.add(new OptimizationFinding(
					"NO_ISSUES",
					"low",
					"No issues found",
					"No optimization issues detected from the query structure.",
					null));
		}

		return findings;
	}
}
