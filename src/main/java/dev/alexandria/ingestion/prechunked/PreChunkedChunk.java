package dev.alexandria.ingestion.prechunked;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Inner JSON record for a single pre-chunked chunk with all 5 metadata fields.
 *
 * <p>{@code language} is nullable: null for prose chunks, populated for code chunks.
 *
 * @param text        the chunk body text
 * @param sourceUrl   URL of the page this chunk belongs to
 * @param sectionPath slash-separated heading hierarchy
 * @param contentType either "prose" or "code"
 * @param lastUpdated ISO-8601 timestamp
 * @param language    programming language for code chunks; null for prose
 */
public record PreChunkedChunk(
        @NotBlank String text,
        @NotBlank @JsonProperty("source_url") String sourceUrl,
        @NotBlank @JsonProperty("section_path") String sectionPath,
        @NotBlank @JsonProperty("content_type") @Pattern(regexp = "prose|code") String contentType,
        @NotBlank @JsonProperty("last_updated") String lastUpdated,
        String language
) {}
