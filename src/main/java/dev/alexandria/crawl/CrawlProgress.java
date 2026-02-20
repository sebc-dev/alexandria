package dev.alexandria.crawl;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of an active crawl's progress.
 *
 * <p>Created and updated by {@link CrawlProgressTracker} during crawl execution.
 * Each mutation produces a new record (value semantics for thread safety).
 *
 * @param sourceId     the source being crawled
 * @param status       current crawl status (CRAWLING, COMPLETED, FAILED)
 * @param pagesCrawled number of pages successfully crawled
 * @param pagesSkipped number of pages skipped due to unchanged content hash
 * @param pagesTotal   total discovered URLs (may grow during BFS)
 * @param errors       number of pages that failed to crawl
 * @param errorUrls    URLs that failed during crawl
 * @param filteredUrls URLs excluded by scope filtering
 * @param startedAt    when the crawl started
 */
public record CrawlProgress(
        java.util.UUID sourceId,
        Status status,
        int pagesCrawled,
        int pagesSkipped,
        int pagesTotal,
        int errors,
        List<String> errorUrls,
        List<String> filteredUrls,
        Instant startedAt
) {

    public CrawlProgress {
        errorUrls = errorUrls == null ? List.of() : List.copyOf(errorUrls);
        filteredUrls = filteredUrls == null ? List.of() : List.copyOf(filteredUrls);
    }

    /**
     * Crawl progress status, independent of {@link dev.alexandria.source.SourceStatus}
     * to avoid a package cycle between crawl and source packages.
     */
    public enum Status {
        CRAWLING,
        COMPLETED,
        FAILED
    }
}
