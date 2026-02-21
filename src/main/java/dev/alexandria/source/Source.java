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
import org.jspecify.annotations.Nullable;

/**
 * Represents a documentation source to be crawled and indexed.
 *
 * <p>Each source tracks a root URL (e.g. {@code https://docs.spring.io}) along with crawl status,
 * chunk count, and timestamps. The lifecycle follows the state machine defined by {@link
 * SourceStatus}: PENDING → CRAWLING → INDEXED (or ERROR).
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
  private @Nullable UUID id;

  @Column(nullable = false, unique = true)
  private @Nullable String url;

  private @Nullable String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SourceStatus status = SourceStatus.PENDING;

  @Column(name = "last_crawled_at")
  private @Nullable Instant lastCrawledAt;

  @Column(name = "chunk_count", nullable = false)
  private int chunkCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private @Nullable Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private @Nullable Instant updatedAt;

  @Column(name = "allow_patterns")
  private @Nullable String allowPatterns;

  @Column(name = "block_patterns")
  private @Nullable String blockPatterns;

  @Column(name = "max_depth")
  private @Nullable Integer maxDepth;

  @Column(name = "max_pages")
  private @Nullable Integer maxPages;

  @Column(name = "llms_txt_url")
  private @Nullable String llmsTxtUrl;

  @Column(name = "version")
  private @Nullable String version;

  protected Source() {
    // JPA requires no-arg constructor
  }

  /**
   * Creates a new source in {@link SourceStatus#PENDING} state with zero chunks.
   *
   * @param url the root URL of the documentation site (must be unique)
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

  public @Nullable UUID getId() {
    return id;
  }

  public @Nullable String getUrl() {
    return url;
  }

  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public SourceStatus getStatus() {
    return status;
  }

  public void setStatus(SourceStatus status) {
    this.status = status;
  }

  public @Nullable Instant getLastCrawledAt() {
    return lastCrawledAt;
  }

  public void setLastCrawledAt(@Nullable Instant lastCrawledAt) {
    this.lastCrawledAt = lastCrawledAt;
  }

  public int getChunkCount() {
    return chunkCount;
  }

  public void setChunkCount(int chunkCount) {
    this.chunkCount = chunkCount;
  }

  public @Nullable Instant getCreatedAt() {
    return createdAt;
  }

  public @Nullable Instant getUpdatedAt() {
    return updatedAt;
  }

  public @Nullable String getAllowPatterns() {
    return allowPatterns;
  }

  public void setAllowPatterns(@Nullable String allowPatterns) {
    this.allowPatterns = allowPatterns;
  }

  public @Nullable String getBlockPatterns() {
    return blockPatterns;
  }

  public void setBlockPatterns(@Nullable String blockPatterns) {
    this.blockPatterns = blockPatterns;
  }

  public @Nullable Integer getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(@Nullable Integer maxDepth) {
    this.maxDepth = maxDepth;
  }

  public @Nullable Integer getMaxPages() {
    return maxPages;
  }

  public void setMaxPages(@Nullable Integer maxPages) {
    this.maxPages = maxPages;
  }

  public @Nullable String getLlmsTxtUrl() {
    return llmsTxtUrl;
  }

  public void setLlmsTxtUrl(@Nullable String llmsTxtUrl) {
    this.llmsTxtUrl = llmsTxtUrl;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(@Nullable String version) {
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

  private static List<String> parseCommaSeparated(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
