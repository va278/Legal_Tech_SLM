package com.legaltech.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legaltech.model.AnalysisResult;
import com.legaltech.model.KeyClause;
import com.legaltech.model.Risk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Formats an {@link AnalysisResult} into human-readable console output,
 * a JSON file, or a plain-text report file.
 */
public class ReportGenerator {

    private static final String DIVIDER     = "═".repeat(70);
    private static final String SUB_DIVIDER = "─".repeat(70);
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper jsonMapper;

    public ReportGenerator() {
        this.jsonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Prints a full formatted report to stdout. */
    public void printConsoleReport(AnalysisResult r) {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println("  LEGAL CASE ANALYSIS REPORT");
        System.out.println(DIVIDER);

        printCaseOverview(r);
        printRiskScoreBanner(r);
        printKeyPoints(r);
        printKeyClauses(r);
        printRisks(r);
        printRecommendedActions(r);
        printFooter(r);

        System.out.println(DIVIDER);
        System.out.println();
    }

    /** Serializes the full AnalysisResult to a JSON file. */
    public void saveJsonReport(AnalysisResult r, String outputPath) throws IOException {
        String json = jsonMapper.writeValueAsString(r);
        Path path = Paths.get(outputPath);
        Files.writeString(path, json, StandardCharsets.UTF_8);
        System.out.println("[INFO] JSON report saved → " + path.toAbsolutePath());
    }

    /** Writes a plain-text report to a file (same content as console output). */
    public void saveTextReport(AnalysisResult r, String outputPath) throws IOException {
        String content = buildTextReport(r);
        Path path = Paths.get(outputPath);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        System.out.println("[INFO] Text report saved → " + path.toAbsolutePath());
    }

    /** Returns the full analysis as a pretty-printed JSON string. */
    public String toJson(AnalysisResult r) throws IOException {
        return jsonMapper.writeValueAsString(r);
    }

    // ── Console / text formatting ─────────────────────────────────────────────

    private void printCaseOverview(AnalysisResult r) {
        System.out.println();
        System.out.printf("  Case Name  : %s%n",
                r.getSourceCase() != null ? r.getSourceCase().getCaseName() : "Unknown");
        System.out.printf("  Case Type  : %s%n", nullSafe(r.getCaseType()));
        System.out.printf("  Jurisdiction: %s%n", nullSafe(r.getJurisdiction()));
        System.out.printf("  Key Date   : %s%n", nullSafe(r.getKeyDate()));

        if (r.getPartiesInvolved() != null && !r.getPartiesInvolved().isEmpty()) {
            System.out.printf("  Parties    : %s%n", String.join(", ", r.getPartiesInvolved()));
        }

        System.out.println();
        System.out.println("  SUMMARY");
        System.out.println(SUB_DIVIDER);
        wordWrap(nullSafe(r.getCaseSummary()), 68, "  ");
        System.out.println();
    }

    private void printRiskScoreBanner(AnalysisResult r) {
        int score = r.getOverallRiskScore();
        String label = r.getRiskLabel();
        String bar   = buildRiskBar(score);

        System.out.println(SUB_DIVIDER);
        System.out.printf("  OVERALL RISK SCORE: %d/10  [%s]  %s%n", score, label, bar);
        System.out.printf("  High-significance clauses: %d  |  High-severity risks: %d%n",
                r.countHighClauses(), r.countHighRisks());
        System.out.println(SUB_DIVIDER);
        System.out.println();
    }

    private void printKeyPoints(AnalysisResult r) {
        List<String> points = r.getKeyPoints();
        if (points == null || points.isEmpty()) return;

        System.out.println("  KEY POINTS");
        System.out.println(SUB_DIVIDER);
        for (int i = 0; i < points.size(); i++) {
            System.out.printf("  %2d. ", i + 1);
            wordWrap(points.get(i), 64, "      ");
        }
        System.out.println();
    }

    private void printKeyClauses(AnalysisResult r) {
        List<KeyClause> clauses = r.getKeyClauses();
        if (clauses == null || clauses.isEmpty()) return;

        // Sort: HIGH first, then MEDIUM, then LOW
        List<KeyClause> sorted = clauses.stream()
                .sorted(Comparator.comparingInt(c ->
                        signifWeight(((KeyClause) c).getSignificance())).reversed())
                .toList();

        System.out.println("  KEY CLAUSES  (" + sorted.size() + " identified)");
        System.out.println(SUB_DIVIDER);

        for (int i = 0; i < sorted.size(); i++) {
            KeyClause c = sorted.get(i);
            System.out.printf("  [%d] %s  (%s)%n", i + 1,
                    nullSafe(c.getClauseType()), signifTag(c.getSignificance()));
            if (c.getSection() != null && !c.getSection().isBlank()
                    && !"Not specified".equalsIgnoreCase(c.getSection())) {
                System.out.printf("      Section: %s%n", c.getSection());
            }
            System.out.print("      Text: ");
            wordWrap(truncate(nullSafe(c.getClauseText()), 200), 60, "            ");
            System.out.print("      Why it matters: ");
            wordWrap(nullSafe(c.getExplanation()), 58, "                    ");
            System.out.println();
        }
    }

    private void printRisks(AnalysisResult r) {
        List<Risk> risks = r.getRisks();
        if (risks == null || risks.isEmpty()) return;

        List<Risk> sorted = risks.stream()
                .sorted(Comparator.comparingInt(Risk::getSeverityWeight).reversed())
                .toList();

        System.out.println("  IDENTIFIED RISKS  (" + sorted.size() + " total)");
        System.out.println(SUB_DIVIDER);

        for (Risk risk : sorted) {
            System.out.printf("  %s [%s] %s — %s%n",
                    nullSafe(risk.getRiskId()),
                    severityTag(risk.getSeverity()),
                    nullSafe(risk.getCategory()),
                    nullSafe(risk.getTitle()));
            System.out.print("       Description: ");
            wordWrap(nullSafe(risk.getDescription()), 57, "                   ");
            if (risk.getAffectedClause() != null && !risk.getAffectedClause().isBlank()) {
                System.out.printf("       Affected by: %s%n", risk.getAffectedClause());
            }
            System.out.print("       Action: ");
            wordWrap(nullSafe(risk.getRecommendation()), 61, "               ");
            System.out.println();
        }
    }

    private void printRecommendedActions(AnalysisResult r) {
        List<String> actions = r.getRecommendedActions();
        if (actions == null || actions.isEmpty()) return;

        System.out.println("  RECOMMENDED ACTIONS");
        System.out.println(SUB_DIVIDER);
        for (int i = 0; i < actions.size(); i++) {
            System.out.printf("  %2d. ", i + 1);
            wordWrap(actions.get(i), 64, "      ");
        }
        System.out.println();
    }

    private void printFooter(AnalysisResult r) {
        System.out.println(SUB_DIVIDER);
        if (r.getAnalyzedAt() != null) {
            System.out.printf("  Analyzed: %s  |  Model: %s%n",
                    r.getAnalyzedAt().format(DT_FMT), nullSafe(r.getModelUsed()));
        }
        System.out.printf("  Tokens used: %d input / %d output%n",
                r.getInputTokens(), r.getOutputTokens());
    }

    // ── Text-file builder (mirrors console output as plain text) ─────────────

    private String buildTextReport(AnalysisResult r) {
        StringBuilder sb = new StringBuilder();
        // Capture console output by redirecting calls to the StringBuilder
        appendLine(sb, DIVIDER);
        appendLine(sb, "  LEGAL CASE ANALYSIS REPORT");
        appendLine(sb, DIVIDER);
        appendLine(sb, "");
        if (r.getSourceCase() != null)
            appendLine(sb, "  Case Name  : " + r.getSourceCase().getCaseName());
        appendLine(sb, "  Case Type  : " + nullSafe(r.getCaseType()));
        appendLine(sb, "  Jurisdiction: " + nullSafe(r.getJurisdiction()));
        appendLine(sb, "  Key Date   : " + nullSafe(r.getKeyDate()));
        if (r.getPartiesInvolved() != null && !r.getPartiesInvolved().isEmpty())
            appendLine(sb, "  Parties    : " + String.join(", ", r.getPartiesInvolved()));
        appendLine(sb, "");
        appendLine(sb, "  SUMMARY");
        appendLine(sb, SUB_DIVIDER);
        appendLine(sb, "  " + nullSafe(r.getCaseSummary()));
        appendLine(sb, "");
        appendLine(sb, "  OVERALL RISK SCORE: " + r.getOverallRiskScore()
                + "/10  [" + r.getRiskLabel() + "]");
        appendLine(sb, "  High-significance clauses: " + r.countHighClauses()
                + "  |  High-severity risks: " + r.countHighRisks());
        appendLine(sb, "");

        // Key Points
        if (r.getKeyPoints() != null) {
            appendLine(sb, "  KEY POINTS");
            appendLine(sb, SUB_DIVIDER);
            for (int i = 0; i < r.getKeyPoints().size(); i++)
                appendLine(sb, "  " + (i + 1) + ". " + r.getKeyPoints().get(i));
            appendLine(sb, "");
        }

        // Clauses
        if (r.getKeyClauses() != null) {
            appendLine(sb, "  KEY CLAUSES");
            appendLine(sb, SUB_DIVIDER);
            for (KeyClause c : r.getKeyClauses()) {
                appendLine(sb, "  • [" + nullSafe(c.getSignificance()) + "] "
                        + nullSafe(c.getClauseType()));
                appendLine(sb, "    Text: " + truncate(nullSafe(c.getClauseText()), 300));
                appendLine(sb, "    Why: " + nullSafe(c.getExplanation()));
                appendLine(sb, "");
            }
        }

        // Risks
        if (r.getRisks() != null) {
            appendLine(sb, "  RISKS");
            appendLine(sb, SUB_DIVIDER);
            for (Risk risk : r.getRisks()) {
                appendLine(sb, "  " + nullSafe(risk.getRiskId()) + " [" + nullSafe(risk.getSeverity())
                        + "] " + nullSafe(risk.getTitle()));
                appendLine(sb, "    Category: " + nullSafe(risk.getCategory()));
                appendLine(sb, "    " + nullSafe(risk.getDescription()));
                appendLine(sb, "    Action: " + nullSafe(risk.getRecommendation()));
                appendLine(sb, "");
            }
        }

        // Actions
        if (r.getRecommendedActions() != null) {
            appendLine(sb, "  RECOMMENDED ACTIONS");
            appendLine(sb, SUB_DIVIDER);
            for (int i = 0; i < r.getRecommendedActions().size(); i++)
                appendLine(sb, "  " + (i + 1) + ". " + r.getRecommendedActions().get(i));
            appendLine(sb, "");
        }

        appendLine(sb, DIVIDER);
        return sb.toString();
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    private static void wordWrap(String text, int width, String indent) {
        if (text == null || text.isBlank()) { System.out.println(); return; }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        boolean first = true;
        for (String word : words) {
            if (line.length() + word.length() + 1 > width) {
                System.out.println(first ? line : indent + line);
                line = new StringBuilder(word);
                first = false;
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) System.out.println(first ? line : indent + line);
    }

    private static String buildRiskBar(int score) {
        int filled = Math.max(0, Math.min(10, score));
        return "[" + "█".repeat(filled) + "░".repeat(10 - filled) + "]";
    }

    private static String signifTag(String s) {
        return switch (s == null ? "" : s.toUpperCase()) {
            case "HIGH"   -> "!! HIGH";
            case "MEDIUM" -> " ! MED ";
            default       -> "   LOW ";
        };
    }

    private static int signifWeight(String s) {
        return switch (s == null ? "" : s.toUpperCase()) {
            case "HIGH"   -> 3;
            case "MEDIUM" -> 2;
            default       -> 1;
        };
    }

    private static String severityTag(String s) {
        return switch (s == null ? "" : s.toUpperCase()) {
            case "HIGH"   -> "HIGH  ";
            case "MEDIUM" -> "MEDIUM";
            default       -> "LOW   ";
        };
    }

    private static String nullSafe(String s) { return s != null ? s : "N/A"; }

    private static String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "…" : s;
    }

    private static void appendLine(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }
}
