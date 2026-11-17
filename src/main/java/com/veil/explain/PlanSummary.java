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
}