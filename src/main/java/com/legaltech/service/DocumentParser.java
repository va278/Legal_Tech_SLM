package com.legaltech.service;

import com.legaltech.model.LegalCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Reads legal documents from a file path, stdin, or a raw string and
 * produces a {@link LegalCase} ready for analysis.
 *
 * Supported file types: .txt, .md, .text (plain-text only).
 * PDF / DOCX support can be added by wiring in Apache PDFBox / POI.
 */
public class DocumentParser {

    private static final int MAX_CHARS = 200_000; // ~50k tokens — well within Claude's context

    private DocumentParser() {}

    /**
     * Reads a legal document from the given file path.
     *
     * @param filePath path to a .txt / .md file
     * @return populated LegalCase
     */
    public static LegalCase fromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        if (!Files.isReadable(path)) {
            throw new IOException("File is not readable: " + filePath);
        }

        String extension = getExtension(path.getFileName().toString()).toLowerCase();
        if (!extension.equals("txt") && !extension.equals("md") && !extension.equals("text")) {
            throw new IOException("Unsupported file type '." + extension
                    + "'. Supported: .txt, .md, .text");
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        content = sanitize(content);

        String caseName = path.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        return new LegalCase(caseName, content, filePath);
    }

    /**
     * Wraps an already-loaded text string into a LegalCase.
     *
     * @param text     raw legal text
     * @param caseName descriptive name for the case
     * @return populated LegalCase
     */
    public static LegalCase fromText(String text, String caseName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Legal text must not be blank.");
        }
        return new LegalCase(caseName, sanitize(text));
    }

    /**
     * Reads from stdin until EOF (Ctrl+Z on Windows, Ctrl+D on Unix).
     */
    public static LegalCase fromStdin(String caseName) throws IOException {
        System.out.println("Paste the legal document text below, then press Enter + Ctrl+Z (Windows) or Ctrl+D (Unix) to finish:");
        System.out.println();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            if (content.isBlank()) {
                throw new IOException("No text was provided via stdin.");
            }
            return new LegalCase(caseName, sanitize(content));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String sanitize(String text) {
        // Normalize line endings and strip null bytes
        text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\0", "");
        // Truncate if absurdly large
        if (text.length() > MAX_CHARS) {
            System.err.println("[WARN] Document truncated to " + MAX_CHARS + " characters to fit model context.");
            text = text.substring(0, MAX_CHARS);
        }
        return text.strip();
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1)
                : "";
    }
}
