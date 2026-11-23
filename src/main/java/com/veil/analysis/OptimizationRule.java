package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;
import java.util.Map;

// A single optimization rule with access to query AST, execution plan, and schema.
// Implementations can be AST-only or plan-aware for evidence-based findings.
public interface OptimizationRule {

    // Basic check using only the AST (backward compatible)
    default void check(SelectStatement select, List<OptimizationFinding> findings) {
        check(select, null, null, findings);
    }

    // Full check with execution plan and schema for evidence-based analysis
    void check(SelectStatement select, PlanSummary plan, Map<String, Object> schema, List<OptimizationFinding> findings);

    // Unique identifier for this rule
    String getRuleId();

    // Human-readable name
    default String getName() { return getRuleId(); }

    // Whether this rule requires a plan for meaningful analysis
    default boolean requiresPlan() { return false; }
}