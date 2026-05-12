package com.legaltech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legaltech.model.AnalysisResult;
import com.legaltech.model.LegalCase;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Orchestrates the end-to-end legal analysis pipeline:
 *   1. Calls ClaudeApiClient with the document text.
 *   2. Strips any markdown fences from the response.
 *   3. Deserializes the JSON into an AnalysisResult.
 *   4. Attaches metadata (model, tokens, timestamp).
 */
public class LegalAnalysisService {

    private static final int MAX_RETRIES = 2;

    private final ClaudeApiClient apiClient;
    private final ObjectMapper    mapper;

    public LegalAnalysisService(String apiKey, String model) {
        this.apiClient = new ClaudeApiClient(apiKey, model);
        this.mapper    = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * Analyzes the given legal case and returns a fully populated AnalysisResult.
     *
     * @param legalCase the parsed document to analyze
     * @return structured analysis including summary, clauses, and risks
     * @throws IOException          on network or API error
     * @throws InterruptedException if the HTTP call is interrupted
     */
    public AnalysisResult analyze(LegalCase legalCase) throws IOException, InterruptedException {
        System.out.println("[INFO] Sending document to Claude (" + legalCase.getWordCount() + " words)...");

        ClaudeApiClient.ApiResponse apiResponse = callWithRetry(legalCase.getCaseText());

        System.out.println("[INFO] Response received. Input tokens: " + apiResponse.inputTokens()
                + " | Output tokens: " + apiResponse.outputTokens());

        AnalysisResult result = parseAnalysisJson(apiResponse.responseText());

        // Attach metadata
        result.setSourceCase(legalCase);
        result.setAnalyzedAt(LocalDateTime.now());
        result.setModelUsed(apiResponse.modelUsed());
        result.setInputTokens(apiResponse.inputTokens());
        result.setOutputTokens(apiResponse.outputTokens());

        return result;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private ClaudeApiClient.ApiResponse callWithRetry(String text)
            throws IOException, InterruptedException {

        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return apiClient.analyze(text);
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    System.err.println("[WARN] API call failed (attempt " + attempt + "): "
                            + e.getMessage() + ". Retrying...");
                    Thread.sleep(2000L * attempt);
                }
            }
        }
        throw lastException;
    }

    private AnalysisResult parseAnalysisJson(String rawText) throws IOException {
        String json = stripMarkdownFences(rawText).strip();

        // Guard: ensure we have a JSON object
        if (!json.startsWith("{")) {
            int brace = json.indexOf('{');
            if (brace >= 0) {
                json = json.substring(brace);
            } else {
                throw new IOException(
                        "Claude did not return a JSON object. Raw response:\n" + rawText);
            }
        }

        try {
            return mapper.readValue(json, AnalysisResult.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse Claude's JSON response: " + e.getMessage()
                    + "\n--- Raw JSON ---\n" + json, e);
        }
    }

    /**
     * Removes ```json ... ``` or ``` ... ``` markdown wrappers that Claude
     * may produce despite being instructed not to.
     */
    private String stripMarkdownFences(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            // Drop the opening fence line
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            // Drop the closing fence
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.lastIndexOf("```"));
            }
        }
        return trimmed.strip();
    }
}
