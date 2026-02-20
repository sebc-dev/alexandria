package dev.alexandria.crawl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory tracker for active crawl progress.
 *
 * <p>Maintains a {@link ConcurrentHashMap} of {@link CrawlProgress} snapshots keyed by source ID.
 * Each update atomically reads the current state, creates a new immutable record with the
 * updated field, and writes it back using {@code compute()}.
 *
 * <p>This is a singleton Spring bean. Progress data is transient (in-memory only) and lost
 * on restart. For persistent crawl status, use the {@link dev.alexandria.source.Source} entity.
 */
@Component
public class CrawlProgressTracker {

    private final ConcurrentHashMap<UUID, CrawlProgress> activeCrawls = new ConcurrentHashMap<>();

    /**
     * Start tracking a new crawl.
     *
     * @param sourceId     the source being crawled
     * @param initialTotal initial count of discovered URLs
     */
    public void startCrawl(UUID sourceId, int initialTotal) {
        activeCrawls.put(sourceId, new CrawlProgress(
                sourceId,
                CrawlProgress.Status.CRAWLING,
                0,
                0,
                initialTotal,
                0,
                List.of(),
                List.of(),
                Instant.now()
        ));
    }

    /**
     * Record that a page was successfully crawled.
     *
     * @param sourceId the source being crawled
     */
    public void recordPageCrawled(UUID sourceId) {
        activeCrawls.computeIfPresent(sourceId, (id, progress) ->
                new CrawlProgress(
                        progress.sourceId(),
                        progress.status(),
                        progress.pagesCrawled() + 1,
                        progress.pagesSkipped(),
                        progress.pagesTotal(),
                        progress.errors(),
                        progress.errorUrls(),
                        progress.filteredUrls(),
                        progress.startedAt()
                )
        );
    }

    /**
     * Record that a page was skipped due to unchanged content hash.
     *
     * @param sourceId the source being crawled
     */
    public void recordPageSkipped(UUID sourceId) {
        activeCrawls.computeIfPresent(sourceId, (id, progress) ->
                new CrawlProgress(
                        progress.sourceId(),
                        progress.status(),
                        progress.pagesCrawled(),
                        progress.pagesSkipped() + 1,
                        progress.pagesTotal(),
                        progress.errors(),
                        progress.errorUrls(),
                        progress.filteredUrls(),
                        progress.startedAt()
                )
        );
    }

    /**
     * Record that a page failed to crawl.
     *
     * @param sourceId the source being crawled
     * @param url      the URL that failed
     */
    public void recordError(UUID sourceId, String url) {
        activeCrawls.computeIfPresent(sourceId, (id, progress) -> {
            List<String> updatedErrors = new ArrayList<>(progress.errorUrls());
            updatedErrors.add(url);
            return new CrawlProgress(
                    progress.sourceId(),
                    progress.status(),
                    progress.pagesCrawled(),
                    progress.pagesSkipped(),
                    progress.pagesTotal(),
                    progress.errors() + 1,
                    updatedErrors,
                    progress.filteredUrls(),
                    progress.startedAt()
            );
        });
    }

    /**
     * Record that a URL was excluded by scope filtering.
     *
     * @param sourceId the source being crawled
     * @param url      the filtered URL
     */
    public void recordFiltered(UUID sourceId, String url) {
        activeCrawls.computeIfPresent(sourceId, (id, progress) -> {
            List<String> updatedFiltered = new ArrayList<>(progress.filteredUrls());
            updatedFiltered.add(url);
            return new CrawlProgress(
                    progress.sourceId(),
                    progress.status(),
                    progress.pagesCrawled(),
                    progress.pagesSkipped(),
                    progress.pagesTotal(),
                    progress.errors(),
                    progress.errorUrls(),
                    updatedFiltered,
                    progress.startedAt()
            );
        });
    }

    /**
     * Update the total discovered page count (may grow during BFS discovery).
     *
     * @param sourceId the source being crawled
     * @param total    new total count
     */
    public void updateTotal(UUID sourceId, int total) {
        activeCrawls.computeIfPresent(sourceId, (id, progress) ->
                new CrawlProgress(
                        progress.sourceId(),
                        progress.status(),
                        progress.pagesCrawled(),
                        progress.pagesSkipped(),
                        total,
                        progress.errors(),
                        progress.errorUrls(),
                        progress.filteredUrls(),
                        progress.startedAt()
                )
        );
    }

    /**
     * Mark a crawl as completed.
     *
     * @param sourceId the source that finished crawling
     */
    public void completeCrawl(UUID sourceId) {
        activeCrawls.computeIfPresent(sourceId, (id, progress) ->
                new CrawlProgress(
                        progress.sourceId(),
                        CrawlProgress.Status.COMPLETED,
                        progress.pagesCrawled(),
                        progress.pagesSkipped(),
                        progress.pagesTotal(),
                        progress.errors(),
                        progress.errorUrls(),
                        progress.filteredUrls(),
                        progress.startedAt()
                )
        );
    }

    /**
     * Mark a crawl as failed.
     *
     * @param sourceId the source whose crawl failed
     */
    public void failCrawl(UUID sourceId) {
        activeCrawls.computeIfPresent(sourceId, (id, progress) ->
                new CrawlProgress(
                        progress.sourceId(),
                        CrawlProgress.Status.FAILED,
                        progress.pagesCrawled(),
                        progress.pagesSkipped(),
                        progress.pagesTotal(),
                        progress.errors(),
                        progress.errorUrls(),
                        progress.filteredUrls(),
                        progress.startedAt()
                )
        );
    }

    /**
     * Get the current progress snapshot for a crawl.
     *
     * @param sourceId the source to check
     * @return progress snapshot, or empty if not tracking this source
     */
    public Optional<CrawlProgress> getProgress(UUID sourceId) {
        return Optional.ofNullable(activeCrawls.get(sourceId));
    }

    /**
     * Remove a crawl from tracking (cleanup after completion).
     *
     * @param sourceId the source to stop tracking
     */
    public void removeCrawl(UUID sourceId) {
        activeCrawls.remove(sourceId);
    }
}
