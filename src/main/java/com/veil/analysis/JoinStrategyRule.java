package com.veil.analysis;

import com.veil.ast.SelectStatement;
import com.veil.explain.PlanSummary;

import java.util.List;

// Rule: Join strategy analysis - Nested Loop vs Hash Join vs Merge Join.
public class JoinStrategyRule implements OptimizationRule {

    @Override
    public String getRuleId() { return "JOIN_STRATEGY"; }

    @Override
    public boolean requiresPlan() { return true; }

    @Override
    public void check(SelectStatement select, PlanSummary plan, java.util.Map<String, Object> schema, List<OptimizationFinding> findings) {

        if (plan == null) return;

        for (PlanSummary node : flatten(plan)) {
            String nodeType = node.getNodeType();
            if (nodeType == null) continue;

            if ("Nested Loop".equals(nodeType)) {
                analyzeNestedLoop(node, findings);
            } else if ("Hash Join".equals(nodeType)) {
                analyzeHashJoin(node, findings);
            } else if ("Merge Join".equals(nodeType)) {
                analyzeMergeJoin(node, findings);
            }
        }
    }

    private void analyzeNestedLoop(PlanSummary node, List<OptimizationFinding> findings) {
        List<PlanSummary> children = node.getChildren();
        if (children.size() < 2) return;

        PlanSummary outer = children.get(0);
        PlanSummary inner = children.get(1);

        long outerRows = outer.getActualRows() > 0 ? outer.getActualRows() : outer.getEstimatedRows();
        long innerRows = inner.getActualRows() > 0 ? inner.getActualRows() : inner.getEstimatedRows();

        // Large outer relation in Nested Loop
        if (outerRows > 10000) {
            findings.add(new OptimizationFinding(
                    "JOIN_STRATEGY",
                    "high",
                    "Nested Loop with large outer relation",
                    "Nested Loop outer side has " + outerRows + " rows. "
                            + "Hash Join would likely be faster for this cardinality. "
                            + "Consider adding indexes on join columns or using a Hash Join hint.",
                    "/* Consider: SET enable_hashjoin = on; */")
                    .withEvidence("Nested Loop outer rows: " + outerRows + ", inner rows: " + innerRows)
                    .withConfidence(0.85)
                    .withEvidenceLevel("PLAN_ESTIMATE")
                    .withVerificationStatus("UNVERIFIED")
                    .withRelatedPlanNode("Nested Loop"));
        }

        // Both sides large
        if (outerRows > 1000 && innerRows > 1000) {
            findings.add(new OptimizationFinding(
                    "JOIN_STRATEGY",
                    "medium",
                    "Nested Loop with large inputs on both sides",
                    "Both Nested Loop sides exceed 1000 rows (outer: " + outerRows
                            + ", inner: " + innerRows + "). Hash Join would typically "
                            + "be more efficient. Verify join condition selectivity.",
                    "/* Consider: SET enable_hashjoin = on; */")
                    .withEvidence("Nested Loop both sides > 1000 rows")
                    .withConfidence(0.8)
                    .withEvidenceLevel("PLAN_ESTIMATE")
                    .withVerificationStatus("UNVERIFIED")
                    .withRelatedPlanNode("Nested Loop"));
        }
    }

    private void analyzeHashJoin(PlanSummary node, List<OptimizationFinding> findings) {
        // Check for potential hash spill
        if (node.getActualRows() > 0 && node.getEstimatedRows() > 0) {
            double ratio = (double) node.getActualRows() / node.getEstimatedRows();
            if (ratio > 5.0) {
                findings.add(new OptimizationFinding(
                        "JOIN_STRATEGY",
                        "medium",
                        "Potential Hash Join spill to disk",
                        "Hash Join actual rows (" + node.getActualRows()
                                + ") exceed estimate (" + node.getEstimatedRows()
                                + ") by " + String.format("%.1f", ratio) + "x. "
                                + "Hash table may have spilled to disk. "
                                + "Consider increasing work_mem or improving statistics.",
                        null)
                        .withEvidence("Hash Join actual/estimate ratio: " + String.format("%.1f", ratio))
                        .withConfidence(0.8)
                        .withEvidenceLevel("ACTUAL_METRICS")
                        .withVerificationStatus("UNVERIFIED")
                        .withRelatedPlanNode("Hash Join"));
            }
        }
    }

    private void analyzeMergeJoin(PlanSummary node, List<OptimizationFinding> findings) {
        List<PlanSummary> children = node.getChildren();
        int sortChildren = 0;
        for (PlanSummary child : children) {
            if ("Sort".equals(child.getNodeType())) {
                sortChildren++;
            }
        }
        if (sortChildren > 0) {
            findings.add(new OptimizationFinding(
                    "JOIN_STRATEGY",
                    "low",
                    "Merge Join requires explicit sorts",
                    "Merge Join uses " + sortChildren + " explicit sort(s). "
                            + "If inputs were already sorted (e.g., by index on join columns), "
                            + "these sorts could be avoided. Consider indexes on join columns "
                            + "matching the sort order.",
                        null)
                    .withEvidence("Merge Join has " + sortChildren + " explicit sort child(ren)")
                    .withConfidence(0.75)
                    .withEvidenceLevel("PLAN_ESTIMATE")
                    .withVerificationStatus("UNVERIFIED")
                    .withRelatedPlanNode("Merge Join"));
        }
    }

    private List<PlanSummary> flatten(PlanSummary node) {
        java.util.List<PlanSummary> out = new java.util.ArrayList<>();
        out.add(node);
        if (node.getChildren() != null) {
            for (PlanSummary child : node.getChildren()) {
                out.addAll(flatten(child));
            }
        }
        return out;
    }
}