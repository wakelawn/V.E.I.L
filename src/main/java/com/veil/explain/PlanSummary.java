package com.veil.explain;

public class PlanSummary {

	private String nodeType;
	private String relation;
	private String index;
	private double startupCost;
	private double totalCost;
	private long estimatedRows;

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
}