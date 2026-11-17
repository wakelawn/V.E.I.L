package com.veil.analysis;

import com.veil.ast.SelectStatement;

import java.util.List;

// a single optimization rule: look at the AST and, if it sees a problem,
// add a finding to the shared list. Keeping each check in its own class
// makes the engine easy to extend (Phase 8 adds index analysis this way)
public interface OptimizationRule {

	void check(SelectStatement select, List<OptimizationFinding> findings);
}
