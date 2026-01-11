package dev.alexandria.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Metadata associated with a document chunk for RAG storage and retrieval.
 *
 * @param sourceUri URI identifying the original document source
 * @param documentHash Hash of the entire source document for change detection
 * @param chunkIndex Zero-based index of this chunk within the document
 * @param breadcrumbs Hierarchical path within the document (e.g., "Docs > Introduction")
 * @param documentTitle Human-readable title of the source document
 * @param contentHash Hash of this chunk's content for deduplication
 * @param createdAt Timestamp when this chunk was created/ingested
 * @param documentType Type of the source document (e.g., "markdown", "html")
 * @param fileSize Size of the source file in bytes
 * @param fileModifiedAt Last modification timestamp of the source file
 */
public record ChunkMetadata(
    String sourceUri,
    String documentHash,
    int chunkIndex,
    String breadcrumbs,
    String documentTitle,
    String contentHash,
    Instant createdAt,
    String documentType,
    long fileSize,
    Instant fileModifiedAt) {

  /** Compact constructor with validation. */
  public ChunkMetadata {
    if (sourceUri == null || sourceUri.isBlank()) {
      throw new IllegalArgumentException("sourceUri cannot be null or blank");
    }
    if (chunkIndex < 0) {
      throw new IllegalArgumentException("chunkIndex must be >= 0");
    }
    if (fileSize < 0) {
      throw new IllegalArgumentException("fileSize must be >= 0");
    }
  }

  /**
   * Creates a logical URI combining the source URI with the chunk index. Useful for referencing a
   * specific chunk within a document.
   *
   * @return the logical URI in format "sourceUri#chunk-N"
   */
  public String toLogicalUri() {
    return sourceUri + "#chunk-" + chunkIndex;
  }

  /**
   * Computes a deterministic SHA-256 hash of the given content. Uses NFKC Unicode normalization to
   * ensure equivalent Unicode sequences produce the same hash.
   *
   * @param content the content to hash
   * @return lowercase hexadecimal representation of the SHA-256 hash
   */
  public static String computeHash(final String content) {
    if (content == null) {
      throw new IllegalArgumentException("Content cannot be null");
    }
    final String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
