package com.legaltech.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a significant legal clause identified within a case document.
 */
public class KeyClause {

    @JsonProperty("clause_type")
    private String clauseType;

    @JsonProperty("clause_text")
    private String clauseText;

    @JsonProperty("significance")
    private String significance;   // HIGH | MEDIUM | LOW

    @JsonProperty("explanation")
    private String explanation;

    @JsonProperty("section")
    private String section;

    public KeyClause() {}

    public String getClauseType() { return clauseType; }
    public void setClauseType(String clauseType) { this.clauseType = clauseType; }

    public String getClauseText() { return clauseText; }
    public void setClauseText(String clauseText) { this.clauseText = clauseText; }

    public String getSignificance() { return significance; }
    public void setSignificance(String significance) { this.significance = significance; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    /** Returns true when this clause carries HIGH significance. */
    public boolean isHighSignificance() {
        return "HIGH".equalsIgnoreCase(significance);
    }
}
