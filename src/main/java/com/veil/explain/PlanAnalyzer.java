package com.veil.explain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

// walks a finished plan tree and adds plan-level insights:
// 1. the bottleneck: the node doing the most of its OWN work.
//    a parent's total cost always includes its children, so the real
//    measure of a node's own work is totalCost minus its children's
//    totalCost (the "self cost")
// 2. sequential scans that also apply a filter - the classic case
//    where an index may help on large tables
// 3. cardinality mismatches (estimated vs actual rows)
// 4. join strategy analysis
// 5. sort analysis
// 5. parallel execution insights
public class PlanAnalyzer {

    private static final double BOTTLENECK_MIN_SHARE = 30.0;
    private static final double CARDINALITY_MISMATCH_THRESHOLD = 10.0; // 10x
    private static final double SORT_SPILL_THRESHOLD = 1024 * 1024; // 1MB sort spill

    public void analyze(PlanSummary root) {

        if (root == null) {
            return;
        }

        // with a single node the plan is its own bottleneck - no insight
        if (root.getPlanNodeCount() <= 1) {
            addSeqScanFilterInsight(root);
            return;
        }

        addBottleneckInsight(root);
        addSeqScanFilterInsights(root);
        addCardinalityMismatchInsights(root);
        addJoinStrategyInsights(root);
        addSortInsights(root);
        addParallelInsights(root);
        addActualMetricsInsights(root);
    }

    // self cost = own work only (total minus what the children already include)
    private double selfCost(PlanSummary node) {

        double childrenCost = 0.0;

        for (PlanSummary child : node.getChildren()) {
            childrenCost += child.getTotalCost();
        }

        return node.getTotalCost() - childrenCost;
    }

    private void addBottleneckInsight(PlanSummary root) {

        PlanSummary bottleneck = null;
        double bestSelfCost = 0.0;

        for (PlanSummary node : flatten(root)) {

            double self = selfCost(node);

            if (self > bestSelfCost) {
                bestSelfCost = self;
                bottleneck = node;
            }
        }

        if (bottleneck == null || bestSelfCost <= 0) {
            return;
        }

        double share = bestSelfCost / root.getTotalCost() * 100.0;

        // only worth reporting when one node really dominates
        if (share < BOTTLENECK_MIN_SHARE) {
            return;
        }

        String target = bottleneck.getRelation() != null
                && !"NotAvailable".equals(bottleneck.getRelation())
                ? " on " + bottleneck.getRelation()
                : "";

        bottleneck.getRecommendations().add(
                "Plan bottleneck: " + bottleneck.getNodeType() + target
                        + " does " + Math.round(bestSelfCost * 100.0) / 100.0
                        + " of the " + Math.round(root.getTotalCost() * 100.0) / 100.0
                        + " total plan cost (~" + Math.round(share)
                        + "%). Optimizing this node helps the whole query most.");
    }

    private void addSeqScanFilterInsights(PlanSummary root) {

        for (PlanSummary node : flatten(root)) {
            addSeqScanFilterInsight(node);
        }
    }

    private void addSeqScanFilterInsight(PlanSummary node) {

        if (!"Seq Scan".equals(node.getNodeType())
                || node.getFilter() == null) {
            return;
        }

        String relation = node.getRelation();
        if (relation == null || "NotAvailable".equals(relation)) {
            relation = "table";
        }

        String msg = "Sequential scan on '" + relation
                + "' also applies a filter (" + node.getFilter()
                + "). If this table is large and only a few rows "
                + "match, an index on the filtered column may help.";

        // If actual metrics show high rows removed, emphasize
        if (node.getRowsRemovedByFilter() > 0) {
            msg += " Actual execution removed " + node.getRowsRemovedByFilter()
                    + " rows after filtering.";
        }

        node.getRecommendations().add(msg);
    }

    // ========== CARDINALITY MISMATCH ANALYSIS ==========
    private void addCardinalityMismatchInsights(PlanSummary root) {

        for (PlanSummary node : flatten(root)) {

            if (!node.hasActualMetrics()) {
                continue;
            }

            double ratio = node.getEstimatedVsActualRatio();

            if (ratio <= 0) continue;

            // Significant over-estimation (planner thought more rows would come out)
            if (ratio < 0.1) { // actual is 10x+ less than estimated
                node.getRecommendations().add(
                        "Cardinality mismatch: planner estimated " + node.getEstimatedRows()
                                + " rows but actual was " + node.getActualRows()
                                + " (~" + String.format("%.0f", 1.0 / ratio) + "x overestimate). "
                                + "This may cause poor join choices. Consider ANALYZE or better statistics.");
            }
            // Significant under-estimation (planner thought fewer rows)
            else if (ratio > 10.0) { // actual is 10x+ more than estimated
                node.getRecommendations().add(
                        "Cardinality mismatch: planner estimated " + node.getEstimatedRows()
                                + " rows but actual was " + node.getActualRows()
                                + " (~" + String.format("%.0f", ratio) + "x underestimate). "
                                + "This can cause nested loop joins to run much longer than planned. "
                                + "Consider ANALYZE or increasing statistics target.");
            }
        }
    }
    // =====================================================

    // ========== JOIN STRATEGY ANALYSIS ==========
    private void addJoinStrategyInsights(PlanSummary root) {

        for (PlanSummary node : flatten(root)) {

            String nodeType = node.getNodeType();
            if (nodeType == null) continue;

            if ("Nested Loop".equals(nodeType)) {
                analyzeNestedLoop(node);
            } else if ("Hash Join".equals(nodeType)) {
                analyzeHashJoin(node);
            } else if ("Merge Join".equals(nodeType)) {
                analyzeMergeJoin(node);
            }
        }
    }

    private void analyzeNestedLoop(PlanSummary node) {
        // Nested Loop is good for small outer relations
        // Check if outer side has high row count
        List<PlanSummary> children = node.getChildren();
        if (children.size() >= 2) {
            // In nested loop, first child is outer, second is inner
            PlanSummary outer = children.get(0);
            PlanSummary inner = children.get(1);

            long outerRows = outer.getActualRows() > 0 ? outer.getActualRows() : outer.getEstimatedRows();
            long innerRows = inner.getActualRows() > 0 ? inner.getActualRows() : inner.getEstimatedRows();

            if (outerRows > 10000) {
                node.getRecommendations().add(
                        "Nested Loop with large outer relation (" + outerRows + " rows). "
                                + "Consider Hash Join or enable parallel query. "
                                + "Check if join condition and indexes are optimal.");
            }

            if (innerRows > 1000 && outerRows > 1000) {
                node.getRecommendations().add(
                        "Nested Loop with both sides large. Hash Join would likely be faster. "
                                + "Verify join condition selectivity and consider enabling hash join (enable_hashjoin=on).");
            }
        }
    }

    private void analyzeHashJoin(PlanSummary node) {
        // Hash Join is generally good for large relations
        // Check for Hash Condition and potential spill
        if (node.getActualRows() > 0 && node.getEstimatedRows() > 0) {
            double ratio = (double) node.getActualRows() / node.getEstimatedRows();
            if (ratio > 5.0) {
                node.getRecommendations().add(
                        "Hash Join actual rows (" + node.getActualRows()
                                + ") significantly exceed estimate (" + node.getEstimatedRows()
                                + "). Hash table may have spilled to disk. "
                                + "Consider increasing work_mem or improving statistics.");
            }
        }
    }

    private void analyzeMergeJoin(PlanSummary node) {
        // Merge Join requires sorted inputs
        // Check if explicit sorts are present
        List<PlanSummary> children = node.getChildren();
        int sortChildren = 0;
        for (PlanSummary child : children) {
            if ("Sort".equals(child.getNodeType())) {
                sortChildren++;
            }
        }
        if (sortChildren > 0) {
            node.getRecommendations().add(
                    "Merge Join uses " + sortChildren + " explicit sort(s). "
                            + "If inputs were already sorted (e.g., by index), these sorts could be avoided. "
                            + "Consider indexes on join columns matching the sort order.");
        }
    }
    // =====================================================

    // ========== SORT ANALYSIS ==========
    private void addSortInsights(PlanSummary root) {

        for (PlanSummary node : flatten(root)) {

            if (!"Sort".equals(node.getNodeType())) {
                continue;
            }

            String sortKey = node.getSortKey();
            long estimatedRows = node.getEstimatedRows();
            long actualRows = node.getActualRows();
            double totalCost = node.getTotalCost();

            String msg = "Sort operation";
            if (sortKey != null && !"NotAvailable".equals(sortKey)) {
                msg += " by " + sortKey;
            }

            if (actualRows > 0 && actualRows > estimatedRows * 2) {
                msg += ". Actual rows (" + actualRows + ") much higher than estimated (" + estimatedRows + ") - sort may spill to disk.";
            } else if (estimatedRows > 10000) {
                msg += ". Large sort (" + estimatedRows + " estimated rows) - ensure work_mem is sufficient to avoid disk spill.";
            }

            // Check for Sort Method (if available in actual metrics)
            if (node.hasActualMetrics()) {
                // In real EXPLAIN ANALYZE, Sort Method would be available
                // This is a placeholder for when that data is parsed
            }

            node.getRecommendations().add(msg);
        }
    }
    // ===================================

    // ========== PARALLEL EXECUTION ANALYSIS ==========
    private void addParallelInsights(PlanSummary root) {

        boolean hasParallel = false;
        int parallelNodes = 0;

        for (PlanSummary node : flatten(root)) {
            if (node.isParallelAware()) {
                hasParallel = true;
                parallelNodes++;
            }
        }

        if (hasParallel) {
            root.getRecommendations().add(
                    "Parallel query execution detected (" + parallelNodes + " parallel-aware node(s)). "
                            + "Verify that parallel workers are utilized effectively. "
                            + "Check max_parallel_workers_per_gather setting and table parallel_workers storage parameter.");
        }

        // Check for Gather/Gather Merge (parallel -> serial transition)
        for (PlanSummary node : flatten(root)) {
            String nodeType = node.getNodeType();
            if ("Gather".equals(nodeType) || "Gather Merge".equals(nodeType)) {
                node.getRecommendations().add(
                        "Gather/Gather Merge node: parallel results combined into single process. "
                                + "This is a serialization point. Consider if parallelism benefit outweighs overhead.");
            }
        }
    }
    // ===================================================

    // ========== ACTUAL METRICS INSIGHTS ==========
    private void addActualMetricsInsights(PlanSummary root) {

        if (!root.hasActualMetrics()) {
            return; // No EXPLAIN ANALYZE data
        }

        // Total execution time
        if (root.getExecutionTime() > 0) {
            root.getRecommendations().add(
                    "Total execution time: " + Math.round(root.getExecutionTime() * 100.0) / 100.0 + " ms "
                            + "(planning: " + Math.round(root.getPlanningTime() * 100.0) / 100.0 + " ms).");
        }

        // Find nodes with highest actual time per loop
        PlanSummary slowest = null;
        double maxTimePerLoop = 0.0;

        for (PlanSummary node : flatten(root)) {
            if (!node.hasActualMetrics()) continue;
            double timePerLoop = node.getActualTimePerLoop();
            if (timePerLoop > maxTimePerLoop) {
                maxTimePerLoop = timePerLoop;
                slowest = node;
            }
        }

        if (slowest != null && maxTimePerLoop > 1.0) { // > 1ms per loop
            String relation = slowest.getRelation();
            if (relation == null || "NotAvailable".equals(relation)) {
                relation = "node";
            }
            root.getRecommendations().add(
                    "Slowest plan node: " + slowest.getNodeType()
                            + " on " + relation
                            + " takes ~" + Math.round(maxTimePerLoop * 100.0) / 100.0
                            + " ms per loop (loops: " + slowest.getActualLoops() + ").");
        }

        // Buffer usage summary
        long totalReads = 0;
        long totalWrites = 0;
        for (PlanSummary node : flatten(root)) {
            totalReads += node.getTotalBufferReads();
            totalWrites += node.getTotalBufferWrites();
        }

        if (totalReads > 0 || totalWrites > 0) {
            root.getRecommendations().add(
                    "Buffer I/O: " + totalReads + " reads, " + totalWrites + " writes. "
                            + "High reads may indicate missing indexes or insufficient shared_buffers.");
        }

        // Temp space usage (spill to disk)
        long totalTempReads = 0;
        long totalTempWrites = 0;
        for (PlanSummary node : flatten(root)) {
            totalTempReads += node.getTempReadBlocks();
            totalTempWrites += node.getTempWrittenBlocks();
        }

        if (totalTempReads > 0 || totalTempWrites > 0) {
            root.getRecommendations().add(
                    "Temporary disk usage: " + totalTempReads + " blocks read, " + totalTempWrites + " blocks written. "
                            + "Indicates work_mem too small for sorts/hashes. Consider increasing work_mem.");
        }
    }
    // depth-first list of every node in the tree
    private List<PlanSummary> flatten(PlanSummary node) {

        List<PlanSummary> out = new ArrayList<>();
        out.add(node);

        for (PlanSummary child : node.getChildren()) {
            out.addAll(flatten(child));
        }

        return out;
    }
}