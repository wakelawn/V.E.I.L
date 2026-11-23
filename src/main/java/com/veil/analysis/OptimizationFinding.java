package com.veil.analysis;

// One optimization finding with evidence, confidence, and verification status.
public class OptimizationFinding {

    private String ruleId;
    private String severity;
    private String title;
    private String message;
    private String suggestionSql;

    // Evidence-based fields
    private String evidence;           // What in the query/plan triggered this
    private double confidence;         // 0.0 - 1.0
    private String evidenceLevel;      // AST_ONLY, PLAN_ESTIMATE, ACTUAL_METRICS
    private String verificationStatus; // UNVERIFIED, PLAN_VERIFIED, BENCHMARK_VERIFIED
    private String relatedPlanNode;    // Plan node type if applicable

    public OptimizationFinding() {
        this.confidence = 0.5;
        this.evidenceLevel = "AST_ONLY";
        this.verificationStatus = "UNVERIFIED";
    }

    public OptimizationFinding(String ruleId, String severity, String title,
            String message, String suggestionSql) {
        this();
        this.ruleId = ruleId;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.suggestionSql = suggestionSql;
    }

    // Original getters
    public String getRuleId() { return ruleId; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getSuggestionSql() { return suggestionSql; }

    // Evidence-based getters/setters
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = Math.max(0.0, Math.min(1.0, confidence)); }

    public String getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getRelatedPlanNode() { return relatedPlanNode; }
    public void setRelatedPlanNode(String relatedPlanNode) { this.relatedPlanNode = relatedPlanNode; }

    // Builder-style convenience
    public OptimizationFinding withEvidence(String evidence) { setEvidence(evidence); return this; }
    public OptimizationFinding withConfidence(double confidence) { setConfidence(confidence); return this; }
    public OptimizationFinding withEvidenceLevel(String level) { setEvidenceLevel(level); return this; }
    public OptimizationFinding withVerificationStatus(String status) { setVerificationStatus(status); return this; }
    public OptimizationFinding withRelatedPlanNode(String node) { setRelatedPlanNode(node); return this; }
}