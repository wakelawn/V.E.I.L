package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.core.database.DatabaseAdapter;
import com.veil.explain.PlanSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The optimization engine: runs every rule against the parsed AST and
// collects the findings. It only reports what can be derived from the
// query itself - deterministic analysis, never invented facts.
public class OptimizationEngine {

    private final List<OptimizationRule> rules = new ArrayList<>();

    public OptimizationEngine() {
        this(null);
    }

    public OptimizationEngine(DatabaseAdapter databaseAdapter) {
        // Core structural rules (AST-only)
        rules.add(new SelectStarRule());
        rules.add(new NoFilterRule());
        rules.add(new FilterIndexRule());
        rules.add(new SortRule());
        rules.add(new ProjectionRule());

        // Plan-aware rules (require execution plan)
        rules.add(new ScanCostRule());
        rules.add(new JoinStrategyRule());
        rules.add(new CardinalityMismatchRule());
        rules.add(new CompositeIndexRule());
        rules.add(new ExpensiveSortRule());
        rules.add(new AggregationCostRule());
        rules.add(new FilterSelectivityRule());

        // Schema-aware rules (require database adapter)
        if (databaseAdapter != null) {
            rules.add(new RedundantIndexRule(databaseAdapter));
        }
    }

    // Backward-compatible: runs only AST-based rules
    public List<OptimizationFinding> run(SelectStatement select) {
        return run(select, null, null);
    }

    // Full analysis with plan and schema
    public List<OptimizationFinding> run(SelectStatement select, PlanSummary plan, Map<String, Object> schema) {

        List<OptimizationFinding> findings = new ArrayList<>();

        for (OptimizationRule rule : rules) {
            if (rule.requiresPlan() && plan == null) {
                continue; // Skip plan-aware rules if no plan provided
            }
            rule.check(select, plan, schema, findings);
        }

        // An honest "all clear" instead of silence
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

    // Convenience: runs with plan only
    public List<OptimizationFinding> runWithPlan(SelectStatement select, PlanSummary plan) {
        return run(select, plan, null);
    }
}