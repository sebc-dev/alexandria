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

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "embedding_id")
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentChunk() {
        // JPA requires no-arg constructor
    }

    public DocumentChunk(String text, String metadata, UUID sourceId) {
        this.text = text;
        this.metadata = metadata;
        this.sourceId = sourceId;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getMetadata() {
        return metadata;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
