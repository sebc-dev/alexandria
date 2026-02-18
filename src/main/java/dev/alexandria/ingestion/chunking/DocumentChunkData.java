package dev.alexandria.ingestion.chunking;

/**
 * Holds the text and metadata for a single chunk produced by the Markdown chunking engine.
 *
 * <p>{@code contentType} is either {@code "prose"} or {@code "code"}.
 * {@code language} is {@code null} for prose chunks and populated for code chunks
 * (e.g. {@code "java"}, {@code "python"}, {@code "unknown"}).
 *
 * @param text        the chunk body text
 * @param sourceUrl   URL of the page this chunk was extracted from
 * @param sectionPath slash-separated heading hierarchy (e.g. "guide/configuration/routes")
 * @param contentType either "prose" or "code"
 * @param lastUpdated ISO-8601 timestamp of the source page
 * @param language    programming language for code chunks; null for prose
 */
public record DocumentChunkData(
        String text,
        String sourceUrl,
        String sectionPath,
        String contentType,
        String lastUpdated,
        String language
) {}
