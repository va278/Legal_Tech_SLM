package com.legaltech.model;

import java.time.LocalDateTime;

/**
 * Represents a legal case or document submitted for analysis.
 */
public class LegalCase {

    private String caseName;
    private String caseText;
    private String sourceFilePath;
    private LocalDateTime submittedAt;

    public LegalCase() {}

    public LegalCase(String caseName, String caseText) {
        this.caseName = caseName;
        this.caseText = caseText;
        this.submittedAt = LocalDateTime.now();
    }

    public LegalCase(String caseName, String caseText, String sourceFilePath) {
        this(caseName, caseText);
        this.sourceFilePath = sourceFilePath;
    }

    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }

    public String getCaseText() { return caseText; }
    public void setCaseText(String caseText) { this.caseText = caseText; }

    public String getSourceFilePath() { return sourceFilePath; }
    public void setSourceFilePath(String sourceFilePath) { this.sourceFilePath = sourceFilePath; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public int getWordCount() {
        if (caseText == null || caseText.isBlank()) return 0;
        return caseText.trim().split("\\s+").length;
    }
}
