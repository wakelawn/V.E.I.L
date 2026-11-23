package com.veil.analysis;

// Represents a recommended index with full metadata.
public class IndexRecommendation {

    private String tableName;
    private String indexName;
    private String indexType;           // BTREE, HASH, GIN, GiST, etc.
    private String columns;             // column list: "col1, col2 DESC"
    private String whereClause;         // for partial indexes
    private String expression;          // for expression indexes
    private String sql;                 // full CREATE INDEX statement
    private String reason;              // why this index is recommended
    private String severity;            // high, medium, low
    private double confidence;          // 0.0 - 1.0
    private boolean isPartial;          // true if WHERE clause present
    private boolean isExpression;       // true if expression index
    private boolean isHypothetical;     // true if using HypoPG
    private double estimatedBenefit;    // 0.0 - 1.0

    // Getters and setters
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }

    public String getColumns() { return columns; }
    public void setColumns(String columns) { this.columns = columns; }

    public String getWhereClause() { return whereClause; }
    public void setWhereClause(String whereClause) { this.whereClause = whereClause; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = Math.max(0.0, Math.min(1.0, confidence)); }

    public boolean isPartial() { return isPartial; }
    public void setPartial(boolean partial) { isPartial = partial; }

    public boolean isExpression() { return isExpression; }
    public void setExpression(boolean expression) { isExpression = expression; }

    public boolean isHypothetical() { return isHypothetical; }
    public void setHypothetical(boolean hypothetical) { isHypothetical = hypothetical; }

    public double getEstimatedBenefit() { return estimatedBenefit; }
    public void setEstimatedBenefit(double estimatedBenefit) { this.estimatedBenefit = Math.max(0.0, Math.min(1.0, estimatedBenefit)); }

    // Builder-style
    public IndexRecommendation withTableName(String v) { setTableName(v); return this; }
    public IndexRecommendation withIndexName(String v) { setIndexName(v); return this; }
    public IndexRecommendation withIndexType(String v) { setIndexType(v); return this; }
    public IndexRecommendation withColumns(String v) { setColumns(v); return this; }
    public IndexRecommendation withWhereClause(String v) { setWhereClause(v); return this; }
    public IndexRecommendation withExpression(String v) { setExpression(v); return this; }
    public IndexRecommendation withSql(String v) { setSql(v); return this; }
    public IndexRecommendation withReason(String v) { setReason(v); return this; }
    public IndexRecommendation withSeverity(String v) { setSeverity(v); return this; }
    public IndexRecommendation withConfidence(double v) { setConfidence(v); return this; }
    public IndexRecommendation withPartial(boolean v) { setPartial(v); return this; }
    public IndexRecommendation withExpressionIndex(boolean v) { setExpression(v); return this; }
    public IndexRecommendation withHypothetical(boolean v) { setHypothetical(v); return this; }
    public IndexRecommendation withEstimatedBenefit(double v) { setEstimatedBenefit(v); return this; }
}