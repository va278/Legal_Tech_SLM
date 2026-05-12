package com.legaltech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Low-level HTTP client for the Anthropic Messages API.
 *
 * Uses prompt caching on the system prompt (ephemeral cache_control) so that
 * repeated analyses of different documents within the same session avoid
 * re-processing the large system prompt.
 */
public class ClaudeApiClient {

    private static final String API_URL    = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int    TIMEOUT_SEC = 120;

    private static final String SYSTEM_PROMPT = """
            You are an expert legal analyst and attorney with decades of experience in contract law,
            litigation, regulatory compliance, employment law, intellectual property, and legal risk
            assessment. Your task is to meticulously analyze legal documents and return structured,
            actionable insights in strict JSON format.

            When analyzing a legal document you MUST:
            1. Identify the document/case type (Contract, Litigation, Regulatory, Employment, IP,
               Real Estate, Criminal, or Other).
            2. Identify all named parties.
            3. Detect the governing jurisdiction if stated.
            4. Extract every significant legal clause, categorizing each as one of:
               Indemnification, Limitation of Liability, Termination, Payment Terms,
               IP Assignment, Confidentiality / NDA, Dispute Resolution, Force Majeure,
               Warranty / Representation, Non-Compete / Non-Solicitation, Governing Law,
               Data Protection, Change of Control, or Other.
            5. Assess legal risks, rating each as HIGH / MEDIUM / LOW and assigning it to one of:
               Financial, Legal, Operational, Reputational, or Compliance.
            6. Produce an overall risk score from 1 (minimal risk) to 10 (extreme risk).
            7. Summarize the case in 2–3 sentences.
            8. List the top key points a lawyer or client should know immediately.
            9. List concrete recommended actions to mitigate the identified risks.

            CRITICAL: Respond ONLY with a single, valid JSON object matching EXACTLY this schema.
            Do NOT wrap it in markdown code fences. Do NOT add commentary before or after the JSON.

            {
              "case_summary":        "<2-3 sentence summary>",
              "case_type":           "<type>",
              "parties_involved":    ["<party1>", "<party2>"],
              "jurisdiction":        "<jurisdiction or 'Not specified'>",
              "key_date":            "<key date or date range or 'Not specified'>",
              "key_clauses": [
                {
                  "clause_type":   "<type>",
                  "clause_text":   "<verbatim or paraphrased text>",
                  "significance":  "HIGH|MEDIUM|LOW",
                  "explanation":   "<why this matters legally>",
                  "section":       "<section/page reference or 'Not specified'>"
                }
              ],
              "risks": [
                {
                  "risk_id":        "R001",
                  "category":       "Financial|Legal|Operational|Reputational|Compliance",
                  "severity":       "HIGH|MEDIUM|LOW",
                  "title":          "<short risk title>",
                  "description":    "<detailed description>",
                  "affected_clause":"<which clause creates this risk>",
                  "recommendation": "<specific mitigation action>"
                }
              ],
              "overall_risk_score":   7,
              "key_points":          ["<point1>", "<point2>"],
              "recommended_actions": ["<action1>", "<action2>"]
            }
            """;

    private final HttpClient    httpClient;
    private final ObjectMapper  mapper;
    private final String        apiKey;
    private final String        model;

    public ClaudeApiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model  = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Sends the legal document text to Claude and returns the raw text of
     * Claude's response (the JSON analysis string).
     */
    public ApiResponse analyze(String legalText) throws IOException, InterruptedException {
        String requestBody = buildRequestBody(legalText);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("Content-Type",       "application/json")
                .header("x-api-key",          apiKey)
                .header("anthropic-version",   API_VERSION)
                .header("anthropic-beta",      "prompt-caching-2024-07-31")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Anthropic API error " + response.statusCode()
                    + ": " + extractErrorMessage(response.body()));
        }

        return parseApiResponse(response.body());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String buildRequestBody(String legalText) throws IOException {
        // Escape the legal text for embedding into JSON
        String escapedText = mapper.writeValueAsString(legalText);
        // writeValueAsString wraps in quotes; strip them for inline use
        escapedText = escapedText.substring(1, escapedText.length() - 1);

        return """
                {
                  "model": "%s",
                  "max_tokens": 4096,
                  "system": [
                    {
                      "type": "text",
                      "text": %s,
                      "cache_control": {"type": "ephemeral"}
                    }
                  ],
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {
                          "type": "text",
                          "text": "<legal_document>\\n%s\\n</legal_document>\\n\\nAnalyze this legal document and respond with the JSON object described in your instructions."
                        }
                      ]
                    }
                  ]
                }
                """.formatted(
                model,
                mapper.writeValueAsString(SYSTEM_PROMPT),
                escapedText);
    }

    private ApiResponse parseApiResponse(String rawJson) throws IOException {
        JsonNode root = mapper.readTree(rawJson);

        // Extract the text content from content[0].text
        String content = root.path("content").get(0).path("text").asText();

        // Extract token usage
        JsonNode usage = root.path("usage");
        int inputTokens  = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        String modelUsed = root.path("model").asText(model);

        return new ApiResponse(content, inputTokens, outputTokens, modelUsed);
    }

    private String extractErrorMessage(String errorBody) {
        try {
            JsonNode node = mapper.readTree(errorBody);
            return node.path("error").path("message").asText(errorBody);
        } catch (Exception e) {
            return errorBody;
        }
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    public record ApiResponse(
            String responseText,
            int    inputTokens,
            int    outputTokens,
            String modelUsed) {}
}
