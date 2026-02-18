package dev.alexandria.ingestion.prechunked;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.alexandria.ingestion.chunking.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Inner JSON record for a single pre-chunked chunk with all 5 metadata fields.
 *
 * <p>{@code language} is nullable: null for prose chunks, populated for code chunks.
 *
 * @param text        the chunk body text
 * @param sourceUrl   URL of the page this chunk belongs to
 * @param sectionPath slash-separated heading hierarchy
 * @param contentType either {@link ContentType#PROSE} or {@link ContentType#CODE}
 * @param lastUpdated ISO-8601 timestamp
 * @param language    programming language for code chunks; null for prose
 */
public record PreChunkedChunk(
        @NotBlank String text,
        @NotBlank @JsonProperty("source_url") String sourceUrl,
        @NotBlank @JsonProperty("section_path") String sectionPath,
        @NotNull @JsonProperty("content_type") ContentType contentType,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")
        @JsonProperty("last_updated") String lastUpdated,
        String language
) {}
