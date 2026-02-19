package dev.alexandria.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a documentation source to be crawled and indexed.
 *
 * <p>Each source tracks a root URL (e.g. {@code https://docs.spring.io}) along with
 * crawl status, chunk count, and timestamps. The lifecycle follows the state machine
 * defined by {@link SourceStatus}: PENDING → CRAWLING → INDEXED (or ERROR).
 *
 * <p>Maps to the {@code sources} table managed by Flyway migrations.
 *
 * @see SourceStatus
 * @see SourceRepository
 */
@Entity
@Table(name = "sources")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String url;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceStatus status = SourceStatus.PENDING;

    @Column(name = "last_crawled_at")
    private Instant lastCrawledAt;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Source() {
        // JPA requires no-arg constructor
    }

    /**
     * Creates a new source in {@link SourceStatus#PENDING} state with zero chunks.
     *
     * @param url  the root URL of the documentation site (must be unique)
     * @param name a human-readable label for this source
     */
    public Source(String url, String name) {
        this.url = url;
        this.name = name;
        this.chunkCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public void setStatus(SourceStatus status) {
        this.status = status;
    }

    public Instant getLastCrawledAt() {
        return lastCrawledAt;
    }

    public void setLastCrawledAt(Instant lastCrawledAt) {
        this.lastCrawledAt = lastCrawledAt;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
