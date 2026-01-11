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
   * Construit une URI logique identifiant ce chunk du document.
   *
   * @return l'URI logique au format "sourceUri#chunk-N", où N est l'index du chunk
   */
  public String toLogicalUri() {
    return sourceUri + "#chunk-" + chunkIndex;
  }

  /**
   * Calcule un hachage SHA‑256 déterministe d'une chaîne après normalisation Unicode NFKC.
   * La normalisation NFKC garantit que des séquences Unicode équivalentes produisent le même hachage.
   *
   * @param content la chaîne à hacher ; ne peut pas être null
   * @return la représentation hexadécimale en minuscules du hachage SHA‑256
   * @throws IllegalArgumentException si {@code content} est null
   * @throws IllegalStateException si l'algorithme SHA‑256 n'est pas disponible
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