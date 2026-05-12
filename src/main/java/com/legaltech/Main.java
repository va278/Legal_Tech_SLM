package com.legaltech;

import com.legaltech.model.AnalysisResult;
import com.legaltech.model.LegalCase;
import com.legaltech.report.ReportGenerator;
import com.legaltech.service.DocumentParser;
import com.legaltech.service.LegalAnalysisService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Entry point for the Legal Case Analyzer CLI.
 *
 * Usage:
 *   java -jar legal-case-analyzer.jar [options]
 *
 * Options:
 *   -f <path>        Analyze a .txt / .md file
 *   -t "<text>"      Analyze inline text (quote the argument)
 *   -n "<name>"      Case name (optional, derived from filename if -f is used)
 *   -o <path>        Save output to a file (.json or .txt, default: console only)
 *   -m <model>       Claude model ID (default: claude-sonnet-4-6)
 *   -k <key>         Anthropic API key (overrides ANTHROPIC_API_KEY env var)
 *   --json           Output as JSON to console (instead of formatted report)
 *   --help           Show this help message
 *
 * Environment:
 *   ANTHROPIC_API_KEY   Your Anthropic API key (required unless -k is passed)
 *
 * Examples:
 *   java -jar legal-case-analyzer.jar -f contract.txt
 *   java -jar legal-case-analyzer.jar -f lease.txt -o lease_report.json
 *   java -jar legal-case-analyzer.jar -f nda.txt -o nda_report.txt --json
 *   java -jar legal-case-analyzer.jar -t "Party A agrees to indemnify..." -n "Quick NDA"
 */
public class Main {

    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    public static void main(String[] args) {
        // Parse CLI arguments
        CliArgs cli;
        try {
            cli = CliArgs.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] " + e.getMessage());
            printUsage();
            System.exit(1);
            return;
        }

        if (cli.showHelp()) {
            printUsage();
            return;
        }

        // Resolve API key
        String apiKey = cli.apiKey() != null ? cli.apiKey() : System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[ERROR] No API key found. Set the ANTHROPIC_API_KEY environment");
            System.err.println("        variable or pass -k <key> on the command line.");
            System.exit(1);
            return;
        }

        // Load the legal document
        LegalCase legalCase;
        try {
            legalCase = loadCase(cli);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[ERROR] Could not load document: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("[INFO] Analyzing case: " + legalCase.getCaseName());

        // Run analysis
        LegalAnalysisService service = new LegalAnalysisService(apiKey, cli.model());
        AnalysisResult result;
        try {
            result = service.analyze(legalCase);
        } catch (IOException e) {
            System.err.println("[ERROR] Analysis failed: " + e.getMessage());
            System.exit(1);
            return;
        } catch (InterruptedException e) {
            System.err.println("[ERROR] Analysis was interrupted.");
            Thread.currentThread().interrupt();
            System.exit(1);
            return;
        }

        // Generate output
        ReportGenerator reporter = new ReportGenerator();

        try {
            if (cli.jsonOutput()) {
                System.out.println(reporter.toJson(result));
            } else {
                reporter.printConsoleReport(result);
            }

            if (cli.outputPath() != null) {
                if (cli.outputPath().endsWith(".json")) {
                    reporter.saveJsonReport(result, cli.outputPath());
                } else {
                    reporter.saveTextReport(result, cli.outputPath());
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to write output file: " + e.getMessage());
            System.exit(1);
        }
    }

    // ── Document loading ──────────────────────────────────────────────────────

    private static LegalCase loadCase(CliArgs cli) throws IOException {
        if (cli.filePath() != null) {
            return DocumentParser.fromFile(cli.filePath());
        }
        if (cli.inlineText() != null) {
            String name = cli.caseName() != null ? cli.caseName() : "Inline Case";
            return DocumentParser.fromText(cli.inlineText(), name);
        }
        // Interactive stdin mode
        String name = cli.caseName() != null ? cli.caseName() : "Interactive Case";
        return DocumentParser.fromStdin(name);
    }

    // ── Help ─────────────────────────────────────────────────────────────────

    private static void printUsage() {
        System.out.println("""

                Legal Case Analyzer — AI-powered legal document analysis
                ══════════════════════════════════════════════════════════

                USAGE
                  java -jar legal-case-analyzer.jar [options]

                OPTIONS
                  -f <path>      Analyze a .txt or .md file
                  -t "<text>"    Analyze a text snippet (quote the argument)
                  -n "<name>"    Override the case name
                  -o <path>      Save report to file (.txt or .json extension)
                  -m <model>     Claude model (default: claude-sonnet-4-6)
                  -k <key>       Anthropic API key (overrides env var)
                  --json         Print JSON output to console
                  --help         Show this help

                ENVIRONMENT
                  ANTHROPIC_API_KEY   Your Anthropic API key

                EXAMPLES
                  java -jar legal-case-analyzer.jar -f contract.txt
                  java -jar legal-case-analyzer.jar -f nda.txt -o report.json
                  java -jar legal-case-analyzer.jar -t "Clause text here" -n "My Case"
                  java -jar legal-case-analyzer.jar -f lease.txt --json
                """);
    }

    // ── CLI argument record ───────────────────────────────────────────────────

    record CliArgs(
            String  filePath,
            String  inlineText,
            String  caseName,
            String  outputPath,
            String  model,
            String  apiKey,
            boolean jsonOutput,
            boolean showHelp) {

        static CliArgs parse(String[] rawArgs) {
            Iterator<String> it = Arrays.asList(rawArgs).iterator();

            String  filePath   = null;
            String  inlineText = null;
            String  caseName   = null;
            String  outputPath = null;
            String  model      = DEFAULT_MODEL;
            String  apiKey     = null;
            boolean jsonOutput = false;
            boolean showHelp   = false;

            while (it.hasNext()) {
                String arg = it.next();
                switch (arg) {
                    case "-f"     -> filePath   = requireNext(it, "-f");
                    case "-t"     -> inlineText = requireNext(it, "-t");
                    case "-n"     -> caseName   = requireNext(it, "-n");
                    case "-o"     -> outputPath = requireNext(it, "-o");
                    case "-m"     -> model      = requireNext(it, "-m");
                    case "-k"     -> apiKey     = requireNext(it, "-k");
                    case "--json" -> jsonOutput = true;
                    case "--help", "-h" -> showHelp = true;
                    default       -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }

            if (!showHelp && filePath == null && inlineText == null) {
                // stdin mode — acceptable, fall through
            }

            return new CliArgs(filePath, inlineText, caseName, outputPath,
                               model, apiKey, jsonOutput, showHelp);
        }

        private static String requireNext(Iterator<String> it, String flag) {
            if (!it.hasNext()) {
                throw new IllegalArgumentException("Flag " + flag + " requires an argument.");
            }
            return it.next();
        }
    }
}
