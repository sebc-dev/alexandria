package dev.alexandria.ingestion.chunking;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Holds the text and metadata for a single chunk produced by the Markdown chunking engine.
 *
 * <p>{@code language} is {@code null} for prose chunks and populated for code chunks (e.g. {@code
 * "java"}, {@code "python"}, {@code "unknown"}).
 *
 * @param text the chunk body text
 * @param sourceUrl URL of the page this chunk was extracted from
 * @param sectionPath slash-separated heading hierarchy (e.g. "guide/configuration/routes")
 * @param contentType either {@link ContentType#PROSE} or {@link ContentType#CODE}
 * @param lastUpdated ISO-8601 timestamp of the source page
 * @param language programming language for code chunks; null for prose
 * @param version documentation version label; null if not versioned
 * @param sourceName human-readable source name; null if not set
 * @param chunkType "parent" for parent chunks, "child" for child chunks; null for backward
 *     compatibility with legacy callers
 * @param parentId deterministic parent identifier for child chunks (format: {@code
 *     {sourceUrl}#{sectionPath}}); null for parent chunks and legacy callers
 */
public record DocumentChunkData(
    String text,
    String sourceUrl,
    String sectionPath,
    ContentType contentType,
    String lastUpdated,
    @Nullable String language,
    @Nullable String version,
    @Nullable String sourceName,
    @Nullable String chunkType,
    @Nullable String parentId) {
  public DocumentChunkData {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
    Objects.requireNonNull(sectionPath, "sectionPath must not be null");
    Objects.requireNonNull(contentType, "contentType must not be null");
    Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
    if ("parent".equals(chunkType) && parentId != null) {
      throw new IllegalArgumentException("Parent chunks must not have a parentId");
    }
  }

  /**
   * Converts chunk metadata to a langchain4j {@link Metadata} instance with snake_case keys used by
   * the EmbeddingStore.
   */
  public Metadata toMetadata() {
    Metadata metadata =
        Metadata.from("source_url", sourceUrl)
            .put("section_path", sectionPath)
            .put("content_type", contentType.value())
            .put("last_updated", lastUpdated);
    if (language != null) {
      metadata.put("language", language);
    }
    if (version != null) {
      metadata.put("version", version);
    }
    if (sourceName != null) {
      metadata.put("source_name", sourceName);
    }
    if (chunkType != null) {
      metadata.put("chunk_type", chunkType);
    }
    if (parentId != null) {
      metadata.put("parent_id", parentId);
    }
    return metadata;
  }

  /** Converts this chunk to a langchain4j {@link TextSegment} ready for embedding. */
  public TextSegment toTextSegment() {
    return TextSegment.from(text, toMetadata());
  }
}
