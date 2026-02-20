package dev.alexandria.ingestion.prechunked;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Outer JSON envelope for pre-chunked import: a source URL and its chunks.
 *
 * @param sourceUrl the source URL used for replacement semantics (delete existing + insert new)
 * @param chunks the list of pre-chunked chunks to import (must not be empty)
 */
public record PreChunkedRequest(
    @NotBlank @JsonProperty("source_url") String sourceUrl,
    @NotEmpty @Valid List<PreChunkedChunk> chunks) {
  public PreChunkedRequest {
    chunks = chunks == null ? List.of() : List.copyOf(chunks);
  }
}
