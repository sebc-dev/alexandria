package dev.alexandria.ingestion.chunking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keyword-based heuristic for detecting the programming language of a code snippet.
 *
 * <p>Scores each candidate language by counting how many of its characteristic patterns
 * appear in the code. Returns the highest-scoring language if the score is at least 2,
 * otherwise returns {@code "unknown"}.
 */
public final class LanguageDetector {

    private static final Map<String, List<String>> LANGUAGE_PATTERNS = Map.ofEntries(
            Map.entry("java", List.of("public class ", "private ", "import java.",
                    "@Override", "System.out.", "public static void main")),
            Map.entry("python", List.of("def ", "import ", "from ", "self.",
                    "if __name__", "print(", "elif ")),
            Map.entry("javascript", List.of("const ", "let ", "function ", "=>",
                    "console.log", "require(", "module.exports")),
            Map.entry("typescript", List.of("interface ", ": string", ": number",
                    ": boolean", "export ", "import {", "type ")),
            Map.entry("yaml", List.of("apiVersion:", "kind:", "metadata:", "spec:", "---")),
            Map.entry("xml", List.of("<?xml", "<beans", "xmlns:", "<project", "<dependency")),
            Map.entry("sql", List.of("SELECT ", "INSERT INTO", "CREATE TABLE",
                    "ALTER TABLE", "WHERE ", "JOIN ", "FROM ")),
            Map.entry("bash", List.of("#!/bin/bash", "echo ", "export ", "if [", "fi", "done")),
            Map.entry("go", List.of("func ", "package ", "import (", "fmt.", "err != nil")),
            Map.entry("rust", List.of("fn ", "let mut ", "impl ", "pub fn", "use std::", "match "))
    );

    private LanguageDetector() {
        // static utility
    }

    /**
     * Detects the programming language of the given code snippet.
     *
     * @param code the source code to analyze
     * @return the detected language name, or {@code "unknown"} if no language scores high enough
     */
    public static String detect(String code) {
        if (code == null || code.isEmpty()) {
            return "unknown";
        }

        Map<String, Integer> scores = new HashMap<>();
        for (var entry : LANGUAGE_PATTERNS.entrySet()) {
            int score = 0;
            for (String pattern : entry.getValue()) {
                if (code.contains(pattern)) {
                    score++;
                }
            }
            if (score > 0) {
                scores.put(entry.getKey(), score);
            }
        }

        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }
}
