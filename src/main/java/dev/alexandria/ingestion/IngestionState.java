package dev.alexandria.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks incremental crawl state for a single page within a {@link dev.alexandria.source.Source}.
 *
 * <p>Each record stores a content hash of the last ingested version of a page,
 * enabling the ingestion pipeline to skip unchanged pages on re-crawl.
 * The unique constraint on {@code (source_id, page_url)} ensures one state record per page per source.
 *
 * <p>Maps to the {@code ingestion_state} table managed by Flyway migrations.
 */
@Entity
@Table(name = "ingestion_state", uniqueConstraints =
    @UniqueConstraint(columnNames = {"source_id", "page_url"})
)
public class IngestionState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "page_url", nullable = false)
    private String pageUrl;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "last_ingested_at", nullable = false)
    private Instant lastIngestedAt;

    protected IngestionState() {
        // JPA requires no-arg constructor
    }

    /**
     * Creates a new ingestion state record.
     *
     * @param sourceId    the UUID of the owning source
     * @param pageUrl     the canonical URL of the crawled page
     * @param contentHash a hash (e.g. SHA-256) of the page content at ingestion time
     */
    public IngestionState(UUID sourceId, String pageUrl, String contentHash) {
        this.sourceId = sourceId;
        this.pageUrl = pageUrl;
        this.contentHash = contentHash;
    }

    @PrePersist
    protected void onCreate() {
        this.lastIngestedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getLastIngestedAt() {
        return lastIngestedAt;
    }

    public void setLastIngestedAt(Instant lastIngestedAt) {
        this.lastIngestedAt = lastIngestedAt;
    }
}
