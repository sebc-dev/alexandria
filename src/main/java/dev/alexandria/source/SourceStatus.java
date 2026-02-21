package dev.alexandria.source;

/**
 * Lifecycle states for a {@link Source}.
 *
 * <p>Normal flow: {@code PENDING → CRAWLING → INDEXED}. Re-crawl flow: {@code INDEXED → UPDATING →
 * INDEXED}. Any state may transition to {@code ERROR} on failure.
 */
public enum SourceStatus {
  /** Newly created, awaiting first crawl. */
  PENDING,
  /** Currently being crawled by the Crawl4AI sidecar. */
  CRAWLING,
  /** Successfully crawled and chunks indexed in pgvector. */
  INDEXED,
  /** Re-crawling to refresh existing content. */
  UPDATING,
  /** Crawl or ingestion failed; see logs for details. */
  ERROR
}
