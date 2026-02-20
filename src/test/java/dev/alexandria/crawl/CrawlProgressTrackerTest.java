package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrawlProgressTrackerTest {

    private CrawlProgressTracker tracker;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        tracker = new CrawlProgressTracker();
        sourceId = UUID.randomUUID();
    }

    @Test
    void startCrawlCreatesInitialProgress() {
        tracker.startCrawl(sourceId, 10);

        CrawlProgress progress = tracker.getProgress(sourceId).orElseThrow();

        assertThat(progress.sourceId()).isEqualTo(sourceId);
        assertThat(progress.status()).isEqualTo(CrawlProgress.Status.CRAWLING);
        assertThat(progress.pagesCrawled()).isZero();
        assertThat(progress.pagesSkipped()).isZero();
        assertThat(progress.pagesTotal()).isEqualTo(10);
        assertThat(progress.errors()).isZero();
        assertThat(progress.errorUrls()).isEmpty();
        assertThat(progress.filteredUrls()).isEmpty();
        assertThat(progress.startedAt()).isNotNull();
    }

    @Test
    void recordPageCrawledIncrementsCount() {
        tracker.startCrawl(sourceId, 5);

        tracker.recordPageCrawled(sourceId);
        tracker.recordPageCrawled(sourceId);

        assertThat(tracker.getProgress(sourceId).orElseThrow().pagesCrawled()).isEqualTo(2);
    }

    @Test
    void recordPageSkippedIncrementsCount() {
        tracker.startCrawl(sourceId, 5);

        tracker.recordPageSkipped(sourceId);

        assertThat(tracker.getProgress(sourceId).orElseThrow().pagesSkipped()).isEqualTo(1);
    }

    @Test
    void recordErrorAddsUrlToList() {
        tracker.startCrawl(sourceId, 5);

        tracker.recordError(sourceId, "https://example.com/broken");

        CrawlProgress progress = tracker.getProgress(sourceId).orElseThrow();
        assertThat(progress.errors()).isEqualTo(1);
        assertThat(progress.errorUrls()).containsExactly("https://example.com/broken");
    }

    @Test
    void recordFilteredAddsUrlToList() {
        tracker.startCrawl(sourceId, 5);

        tracker.recordFiltered(sourceId, "https://example.com/blocked");

        CrawlProgress progress = tracker.getProgress(sourceId).orElseThrow();
        assertThat(progress.filteredUrls()).containsExactly("https://example.com/blocked");
    }

    @Test
    void completeCrawlSetsCompletedStatus() {
        tracker.startCrawl(sourceId, 5);

        tracker.completeCrawl(sourceId);

        assertThat(tracker.getProgress(sourceId).orElseThrow().status())
                .isEqualTo(CrawlProgress.Status.COMPLETED);
    }

    @Test
    void failCrawlSetsFailedStatus() {
        tracker.startCrawl(sourceId, 5);

        tracker.failCrawl(sourceId);

        assertThat(tracker.getProgress(sourceId).orElseThrow().status())
                .isEqualTo(CrawlProgress.Status.FAILED);
    }

    @Test
    void getProgressReturnsEmptyForUnknownSource() {
        assertThat(tracker.getProgress(UUID.randomUUID())).isEmpty();
    }

    @Test
    void removeCrawlCleansUpMemory() {
        tracker.startCrawl(sourceId, 5);

        tracker.removeCrawl(sourceId);

        assertThat(tracker.getProgress(sourceId)).isEmpty();
    }
}
