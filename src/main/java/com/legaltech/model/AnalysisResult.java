package com.legaltech.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The complete output produced by LegalAnalysisService for a given LegalCase.
 */
public class AnalysisResult {

    // ── Fields parsed directly from Claude's JSON response ──────────────────

    @JsonProperty("case_summary")
    private String caseSummary;

    @JsonProperty("case_type")
    private String caseType;

    @JsonProperty("parties_involved")
    private List<String> partiesInvolved;

    @JsonProperty("jurisdiction")
    private String jurisdiction;

    @JsonProperty("key_date")
    private String keyDate;

    @JsonProperty("key_clauses")
    private List<KeyClause> keyClauses;

    @JsonProperty("risks")
    private List<Risk> risks;

    @JsonProperty("overall_risk_score")
    private int overallRiskScore;   // 1–10

    @JsonProperty("key_points")
    private List<String> keyPoints;

    @JsonProperty("recommended_actions")
    private List<String> recommendedActions;

    // ── Metadata populated by LegalAnalysisService ──────────────────────────

    private LegalCase sourceCase;
    private LocalDateTime analyzedAt;
    private String modelUsed;
    private int inputTokens;
    private int outputTokens;

    public AnalysisResult() {}

    // ── Derived helpers ──────────────────────────────────────────────────────

    public String getRiskLabel() {
        if (overallRiskScore >= 8) return "CRITICAL";
        if (overallRiskScore >= 6) return "HIGH";
        if (overallRiskScore >= 4) return "MEDIUM";
        return "LOW";
    }

    public long countHighClauses() {
        if (keyClauses == null) return 0;
        return keyClauses.stream().filter(KeyClause::isHighSignificance).count();
    }

    public long countHighRisks() {
        if (risks == null) return 0;
        return risks.stream().filter(Risk::isHighSeverity).count();
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getCaseSummary() { return caseSummary; }
    public void setCaseSummary(String caseSummary) { this.caseSummary = caseSummary; }

    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }

    public List<String> getPartiesInvolved() { return partiesInvolved; }
    public void setPartiesInvolved(List<String> partiesInvolved) { this.partiesInvolved = partiesInvolved; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getKeyDate() { return keyDate; }
    public void setKeyDate(String keyDate) { this.keyDate = keyDate; }

    public List<KeyClause> getKeyClauses() { return keyClauses; }
    public void setKeyClauses(List<KeyClause> keyClauses) { this.keyClauses = keyClauses; }

    public List<Risk> getRisks() { return risks; }
    public void setRisks(List<Risk> risks) { this.risks = risks; }

    public int getOverallRiskScore() { return overallRiskScore; }
    public void setOverallRiskScore(int overallRiskScore) { this.overallRiskScore = overallRiskScore; }

    public List<String> getKeyPoints() { return keyPoints; }
    public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }

    public List<String> getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(List<String> recommendedActions) { this.recommendedActions = recommendedActions; }

    public LegalCase getSourceCase() { return sourceCase; }
    public void setSourceCase(LegalCase sourceCase) { this.sourceCase = sourceCase; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
}
