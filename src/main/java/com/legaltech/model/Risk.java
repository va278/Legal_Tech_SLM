package com.legaltech.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a legal risk identified within a case document.
 */
public class Risk {

    @JsonProperty("risk_id")
    private String riskId;

    @JsonProperty("category")
    private String category;   // Financial | Legal | Operational | Reputational | Compliance

    @JsonProperty("severity")
    private String severity;   // HIGH | MEDIUM | LOW

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("affected_clause")
    private String affectedClause;

    @JsonProperty("recommendation")
    private String recommendation;

    public Risk() {}

    public String getRiskId() { return riskId; }
    public void setRiskId(String riskId) { this.riskId = riskId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAffectedClause() { return affectedClause; }
    public void setAffectedClause(String affectedClause) { this.affectedClause = affectedClause; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public boolean isHighSeverity() {
        return "HIGH".equalsIgnoreCase(severity);
    }

    /** Returns a numeric weight for sorting (HIGH=3, MEDIUM=2, LOW=1). */
    public int getSeverityWeight() {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "HIGH"   -> 3;
            case "MEDIUM" -> 2;
            default       -> 1;
        };
    }
}
