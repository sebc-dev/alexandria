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
import java.util.Arrays;
import java.util.List;
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

    @Column(name = "allow_patterns")
    private String allowPatterns;

    @Column(name = "block_patterns")
    private String blockPatterns;

    @Column(name = "max_depth")
    private Integer maxDepth;

    @Column(name = "max_pages")
    private Integer maxPages;

    @Column(name = "llms_txt_url")
    private String llmsTxtUrl;

    @Column(name = "version")
    private String version;

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
        this.maxPages = 500;
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

    public String getAllowPatterns() {
        return allowPatterns;
    }

    public void setAllowPatterns(String allowPatterns) {
        this.allowPatterns = allowPatterns;
    }

    public String getBlockPatterns() {
        return blockPatterns;
    }

    public void setBlockPatterns(String blockPatterns) {
        this.blockPatterns = blockPatterns;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Integer getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(Integer maxPages) {
        this.maxPages = maxPages;
    }

    public String getLlmsTxtUrl() {
        return llmsTxtUrl;
    }

    public void setLlmsTxtUrl(String llmsTxtUrl) {
        this.llmsTxtUrl = llmsTxtUrl;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Parses the comma-separated allow patterns into a list.
     *
     * @return list of allow glob patterns, or empty list if none configured
     */
    public List<String> getAllowPatternList() {
        return parseCommaSeparated(allowPatterns);
    }

    /**
     * Parses the comma-separated block patterns into a list.
     *
     * @return list of block glob patterns, or empty list if none configured
     */
    public List<String> getBlockPatternList() {
        return parseCommaSeparated(blockPatterns);
    }

    private static List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
