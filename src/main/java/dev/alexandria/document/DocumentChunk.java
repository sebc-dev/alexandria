package dev.alexandria.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A single indexed chunk of documentation stored in pgvector.
 *
 * <p>Each chunk holds the text content, JSONB metadata (source URL, section path, content type,
 * language), and a reference to the originating {@link dev.alexandria.source.Source}. The embedding
 * vector is managed by LangChain4j's {@code PgVectorEmbeddingStore} and is not mapped as a JPA
 * field.
 *
 * <p>Maps to the {@code document_chunks} table managed by Flyway migrations.
 *
 * @see DocumentChunkRepository
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "embedding_id")
  private @Nullable UUID id;

  @Column(nullable = false, columnDefinition = "TEXT")
  private @Nullable String text;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  private @Nullable String metadata;

  @Column(name = "source_id")
  private @Nullable UUID sourceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private @Nullable Instant createdAt;

  protected DocumentChunk() {
    // JPA requires no-arg constructor
  }

  /**
   * Creates a new document chunk.
   *
   * @param text the chunk text content
   * @param metadata JSONB metadata string (source_url, section_path, content_type, language)
   * @param sourceId the UUID of the originating {@link dev.alexandria.source.Source}, or {@code
   *     null}
   */
  public DocumentChunk(String text, @Nullable String metadata, @Nullable UUID sourceId) {
    this.text = text;
    this.metadata = metadata;
    this.sourceId = sourceId;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

  public @Nullable UUID getId() {
    return id;
  }

  public @Nullable String getText() {
    return text;
  }

  public @Nullable String getMetadata() {
    return metadata;
  }

  public @Nullable UUID getSourceId() {
    return sourceId;
  }

  public @Nullable Instant getCreatedAt() {
    return createdAt;
  }
}
