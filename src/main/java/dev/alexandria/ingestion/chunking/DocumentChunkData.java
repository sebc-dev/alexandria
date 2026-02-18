package dev.alexandria.ingestion.chunking;

import java.util.Objects;

/**
 * Holds the text and metadata for a single chunk produced by the Markdown chunking engine.
 *
 * <p>{@code language} is {@code null} for prose chunks and populated for code chunks
 * (e.g. {@code "java"}, {@code "python"}, {@code "unknown"}).
 *
 * @param text        the chunk body text
 * @param sourceUrl   URL of the page this chunk was extracted from
 * @param sectionPath slash-separated heading hierarchy (e.g. "guide/configuration/routes")
 * @param contentType either {@link ContentType#PROSE} or {@link ContentType#CODE}
 * @param lastUpdated ISO-8601 timestamp of the source page
 * @param language    programming language for code chunks; null for prose
 */
public record DocumentChunkData(
        String text,
        String sourceUrl,
        String sectionPath,
        ContentType contentType,
        String lastUpdated,
        String language
) {
    public DocumentChunkData {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
        Objects.requireNonNull(sectionPath, "sectionPath must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
    }
}
