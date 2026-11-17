package com.veil.explain;

// one line of the plan's cost breakdown: how much of the plan's
// total cost a single node's own work represents
public class CostEntry {

	private String nodeType;
	private String relation;
	private String category;
	private double selfCost;
	private double sharePercent;

	public CostEntry() {
	}

	public CostEntry(String nodeType, String relation, String category,
			double selfCost, double sharePercent) {

		this.nodeType = nodeType;
		this.relation = relation;
		this.category = category;
		this.selfCost = selfCost;
		this.sharePercent = sharePercent;
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

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public double getSelfCost() {
		return selfCost;
	}

	public void setSelfCost(double selfCost) {
		this.selfCost = selfCost;
	}

	public double getSharePercent() {
		return sharePercent;
	}

	public void setSharePercent(double sharePercent) {
		this.sharePercent = sharePercent;
	}
}
