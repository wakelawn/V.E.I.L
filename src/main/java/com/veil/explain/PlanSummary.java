package com.veil.explain;

import java.util.ArrayList;
import java.util.List;

public class PlanSummary {

	private String nodeType;
	private String relation;
	private String index;
	private double startupCost;
	private double totalCost;
	private long estimatedRows;

	// batch 1 pull up for strat and width
	private String strategy;
	private String scanDirection;
	private double estimatedWidth;
	private boolean parallelAware;
	private List<PlanSummary> children = new ArrayList<>();
	private List<String> recommendations = new ArrayList<>();

	// extra node details used by deeper plan analysis
	private String filter;
	private String sortKey;

	// ========== ACTUAL EXECUTION METRICS (from EXPLAIN ANALYZE) ==========
	private long actualRows;
	private double actualStartupTime;   // ms
	private double actualTotalTime;     // ms
	private long actualLoops;
	private long rowsRemovedByFilter;
	private long sharedHitBlocks;
	private long sharedReadBlocks;
	private long sharedDirtiedBlocks;
	private long sharedWrittenBlocks;
	private long localHitBlocks;
	private long localReadBlocks;
	private long localDirtiedBlocks;
	private long localWrittenBlocks;
	private long tempReadBlocks;
	private long tempWrittenBlocks;
	private double planningTime;        // ms
	private double executionTime;       // ms
	// =====================================================================

	// cost breakdown (set on the root only)
	private List<CostEntry> costBreakdown = new ArrayList<>();

	// batch 2 pull up for counts
	private int planNodeCount;
	private int scanNodeCount;
	private int indexScanCount;
	private int sequentialScanCount;
	private String planSummary;

	public PlanSummary() {
	}

	public String getNodeType() {
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	public String getRelation() {
		return relation;
	}

	public void setRelation(String relation) {
		this.relation = relation;
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public double getStartupCost() {
		return startupCost;
	}

	public void setStartupCost(double startupCost) {
		this.startupCost = startupCost;
	}

	public double getTotalCost() {
		return totalCost;
	}

	public void setTotalCost(double totalCost) {
		this.totalCost = totalCost;
	}

	public long getEstimatedRows() {
		return estimatedRows;
	}

	public void setEstimatedRows(long estimatedRows) {
		this.estimatedRows = estimatedRows;
	}

	// ========== ACTUAL METRICS GETTERS/SETTERS ==========
	public long getActualRows() {
		return actualRows;
	}

	public void setActualRows(long actualRows) {
		this.actualRows = actualRows;
	}

	public double getActualStartupTime() {
		return actualStartupTime;
	}

	public void setActualStartupTime(double actualStartupTime) {
		this.actualStartupTime = actualStartupTime;
	}

	public double getActualTotalTime() {
		return actualTotalTime;
	}

	public void setActualTotalTime(double actualTotalTime) {
		this.actualTotalTime = actualTotalTime;
	}

	public long getActualLoops() {
		return actualLoops;
	}

	public void setActualLoops(long actualLoops) {
		this.actualLoops = actualLoops;
	}

	public long getRowsRemovedByFilter() {
		return rowsRemovedByFilter;
	}

	public void setRowsRemovedByFilter(long rowsRemovedByFilter) {
		this.rowsRemovedByFilter = rowsRemovedByFilter;
	}

	public long getSharedHitBlocks() {
		return sharedHitBlocks;
	}

	public void setSharedHitBlocks(long sharedHitBlocks) {
		this.sharedHitBlocks = sharedHitBlocks;
	}

	public long getSharedReadBlocks() {
		return sharedReadBlocks;
	}

	public void setSharedReadBlocks(long sharedReadBlocks) {
		this.sharedReadBlocks = sharedReadBlocks;
	}

	public long getSharedDirtiedBlocks() {
		return sharedDirtiedBlocks;
	}

	public void setSharedDirtiedBlocks(long sharedDirtiedBlocks) {
		this.sharedDirtiedBlocks = sharedDirtiedBlocks;
	}

	public long getSharedWrittenBlocks() {
		return sharedWrittenBlocks;
	}

	public void setSharedWrittenBlocks(long sharedWrittenBlocks) {
		this.sharedWrittenBlocks = sharedWrittenBlocks;
	}

	public long getLocalHitBlocks() {
		return localHitBlocks;
	}

	public void setLocalHitBlocks(long localHitBlocks) {
		this.localHitBlocks = localHitBlocks;
	}

	public long getLocalReadBlocks() {
		return localReadBlocks;
	}

	public void setLocalReadBlocks(long localReadBlocks) {
		this.localReadBlocks = localReadBlocks;
	}

	public long getLocalDirtiedBlocks() {
		return localDirtiedBlocks;
	}

	public void setLocalDirtiedBlocks(long localDirtiedBlocks) {
		this.localDirtiedBlocks = localDirtiedBlocks;
	}

	public long getLocalWrittenBlocks() {
		return localWrittenBlocks;
	}

	public void setLocalWrittenBlocks(long localWrittenBlocks) {
		this.localWrittenBlocks = localWrittenBlocks;
	}

	public long getTempReadBlocks() {
		return tempReadBlocks;
	}

	public void setTempReadBlocks(long tempReadBlocks) {
		this.tempReadBlocks = tempReadBlocks;
	}

	public long getTempWrittenBlocks() {
		return tempWrittenBlocks;
	}

	public void setTempWrittenBlocks(long tempWrittenBlocks) {
		this.tempWrittenBlocks = tempWrittenBlocks;
	}

	public double getPlanningTime() {
		return planningTime;
	}

	public void setPlanningTime(double planningTime) {
		this.planningTime = planningTime;
	}

	public double getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(double executionTime) {
		this.executionTime = executionTime;
	}
	// =====================================================

	public String getStrategy() {
		return strategy;
	}

	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	public String getScanDirection() {
		return scanDirection;
	}

	public void setScanDirection(String scanDirection) {
		this.scanDirection = scanDirection;
	}

	public double getEstimatedWidth() {
		return estimatedWidth;
	}

	public void setEstimatedWidth(double estimatedWidth) {
		this.estimatedWidth = estimatedWidth;
	}

	public boolean isParallelAware() {
		return parallelAware;
	}

	public void setParallelAware(boolean parallelAware) {
		this.parallelAware = parallelAware;
	}

	public String getFilter() {
		return filter;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public String getSortKey() {
		return sortKey;
	}

	public void setSortKey(String sortKey) {
		this.sortKey = sortKey;
	}

	public List<CostEntry> getCostBreakdown() {
		return costBreakdown;
	}

	public void setCostBreakdown(List<CostEntry> costBreakdown) {
		this.costBreakdown = costBreakdown;
	}

	public List<PlanSummary> getChildren() {
		return children;
	}

	public void setChildren(List<PlanSummary> children) {
		this.children = children;
	}

	public List<String> getRecommendations() {
		return recommendations;
	}

	public void setRecommendations(List<String> recommendations) {
		this.recommendations = recommendations;
	}

	public int getPlanNodeCount() {
		return planNodeCount;
	}

	public void setPlanNodeCount(int planNodeCount) {
		this.planNodeCount = planNodeCount;
	}

	public int getScanNodeCount() {
		return scanNodeCount;
	}

	public void setScanNodeCount(int scanNodeCount) {
		this.scanNodeCount = scanNodeCount;
	}

	public int getIndexScanCount() {
		return indexScanCount;
	}

	public void setIndexScanCount(int indexScanCount) {
		this.indexScanCount = indexScanCount;
	}

	public int getSequentialScanCount() {
		return sequentialScanCount;
	}

	public void setSequentialScanCount(int sequentialScanCount) {
		this.sequentialScanCount = sequentialScanCount;
	}

	public String getPlanSummary() {
		return planSummary;
	}

	public void setPlanSummary(String planSummary) {
		this.planSummary = planSummary;
	}

	// ========== HELPER METHODS FOR ACTUAL METRICS ==========

	public boolean hasActualMetrics() {
		return actualRows > 0 || actualTotalTime > 0 || actualLoops > 0;
	}

	public double getEstimatedVsActualRatio() {
		if (estimatedRows <= 0 || actualRows <= 0) return 0.0;
		return (double) actualRows / (double) estimatedRows;
	}

	public boolean hasCardinalityMismatch() {
		double ratio = getEstimatedVsActualRatio();
		return ratio > 0 && (ratio > 10.0 || ratio < 0.1); // 10x or 10x off
	}

	public double getTotalBufferReads() {
		return sharedReadBlocks + localReadBlocks + tempReadBlocks;
	}

	public double getTotalBufferWrites() {
		return sharedWrittenBlocks + localWrittenBlocks + tempWrittenBlocks;
	}

	public double getActualTimePerLoop() {
		if (actualLoops <= 0) return 0.0;
		return actualTotalTime / actualLoops;
	}
	// =====================================================
}