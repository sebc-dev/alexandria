package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.alexandria.ingestion.IngestionService;
import dev.alexandria.ingestion.IngestionState;
import dev.alexandria.ingestion.IngestionStateRepository;
import dev.alexandria.source.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlServiceTest {

    @Mock
    private Crawl4AiClient crawl4AiClient;

    @Mock
    private PageDiscoveryService pageDiscoveryService;

    @Mock
    private CrawlProgressTracker progressTracker;

    @Mock
    private IngestionService ingestionService;

    @Mock
    private IngestionStateRepository ingestionStateRepository;

    @Mock
    private SourceRepository sourceRepository;

    private CrawlService crawlService;

    @BeforeEach
    void setUp() {
        crawlService = new CrawlService(crawl4AiClient, pageDiscoveryService,
                progressTracker, ingestionService, ingestionStateRepository, sourceRepository);
    }

    // --- Existing behavior: BFS, maxPages, link following, sitemap mode ---

    @Test
    void crawlSiteHappyPathReturnsSinglePageResult() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/guide"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Guide", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/guide");
        assertThat(results.getFirst().markdown()).isEqualTo("# Guide");
    }

    @Test
    void crawlSiteLinkCrawlModeFollowsInternalLinks() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/",
                        "# Home", List.of("https://docs.example.com/guide"), true, null));
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide",
                        "# Guide", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).url()).isEqualTo("https://docs.example.com/");
        assertThat(results.get(1).url()).isEqualTo("https://docs.example.com/guide");
    }

    @Test
    void crawlSiteSitemapModeDoesNotFollowLinks() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/page1"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/page1"))
                .thenReturn(new CrawlResult("https://docs.example.com/page1",
                        "# Page 1", List.of("https://docs.example.com/page2"), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        verify(crawl4AiClient, never()).crawl("https://docs.example.com/page2");
    }

    @Test
    void crawlSiteMaxPagesLimitStopsCrawlingAtLimit() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/p1",
                        "https://docs.example.com/p2",
                        "https://docs.example.com/p3"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/p1"))
                .thenReturn(new CrawlResult("https://docs.example.com/p1", "# P1", List.of(), true, null));
        when(crawl4AiClient.crawl("https://docs.example.com/p2"))
                .thenReturn(new CrawlResult("https://docs.example.com/p2", "# P2", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 2);

        assertThat(results).hasSize(2);
        verify(crawl4AiClient, never()).crawl("https://docs.example.com/p3");
    }

    @Test
    void crawlSiteFailedCrawlResultSkippedAndContinues() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/fail",
                        "https://docs.example.com/ok"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/fail"))
                .thenReturn(new CrawlResult("https://docs.example.com/fail", null, List.of(), false, "404"));
        when(crawl4AiClient.crawl("https://docs.example.com/ok"))
                .thenReturn(new CrawlResult("https://docs.example.com/ok", "# OK", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/ok");
    }

    @Test
    void crawlSiteExceptionDuringCrawlSkippedAndContinues() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/error",
                        "https://docs.example.com/ok"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/error"))
                .thenThrow(new RuntimeException("Connection refused"));
        when(crawl4AiClient.crawl("https://docs.example.com/ok"))
                .thenReturn(new CrawlResult("https://docs.example.com/ok", "# OK", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/ok");
    }

    @Test
    void crawlSiteDuplicateUrlsOnlyCrawledOnce() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/",
                        "# Home",
                        List.of("https://docs.example.com/", "https://docs.example.com/guide"),
                        true, null));
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Guide", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(2);
    }

    @Test
    void crawlSiteUrlNormalizationFragmentsStripped() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/",
                        "# Home",
                        List.of("https://docs.example.com/guide#intro",
                                "https://docs.example.com/guide#details"),
                        true, null));
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Guide", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(2);
    }

    @Test
    void crawlSiteEmptyDiscoveryUrlsUsesNormalizedRootUrl() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/", "# Home", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/");
    }

    @Test
    void crawlSiteLinkCrawlModeFiltersExternalLinks() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/",
                        "# Home",
                        List.of("https://external.com/page", "https://docs.example.com/guide"),
                        true, null));
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Guide", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(2);
        verify(crawl4AiClient, never()).crawl("https://external.com/page");
    }

    // --- Scope filtering ---

    @Test
    void scopeFilterRejectsBlockedUrls() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = new CrawlScope(List.of(), List.of("/api/**"), null, 10);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/guide",
                        "https://docs.example.com/api/internal"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Guide", List.of(), true, null));
        when(ingestionStateRepository.findBySourceIdAndPageUrl(any(), anyString()))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestPage(any(UUID.class), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(1);
        when(ingestionStateRepository.findAllBySourceId(sourceId)).thenReturn(List.of());

        List<CrawlResult> results = crawlService.crawlSite(sourceId, rootUrl, scope);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/guide");
        verify(crawl4AiClient, never()).crawl("https://docs.example.com/api/internal");
    }

    @Test
    void scopeFilterAllowsMatchingUrls() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = new CrawlScope(List.of("/docs/**"), List.of(), null, 10);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/docs/guide",
                        "https://docs.example.com/blog/post"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/docs/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/docs/guide", "# Guide", List.of(), true, null));
        when(ingestionStateRepository.findBySourceIdAndPageUrl(any(), anyString()))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestPage(any(UUID.class), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(1);
        when(ingestionStateRepository.findAllBySourceId(sourceId)).thenReturn(List.of());

        List<CrawlResult> results = crawlService.crawlSite(sourceId, rootUrl, scope);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/docs/guide");
        verify(crawl4AiClient, never()).crawl("https://docs.example.com/blog/post");
    }

    // --- Depth tracking ---

    @Test
    void maxDepthLimitsUrlDiscovery() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = new CrawlScope(List.of(), List.of(), 1, 10);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        // Root (depth 0) -> /level1 (depth 1) -> /level2 (depth 2, should not be crawled)
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/",
                        "# Home", List.of("https://docs.example.com/level1"), true, null));
        when(crawl4AiClient.crawl("https://docs.example.com/level1"))
                .thenReturn(new CrawlResult("https://docs.example.com/level1",
                        "# Level 1", List.of("https://docs.example.com/level2"), true, null));
        when(ingestionStateRepository.findBySourceIdAndPageUrl(any(), anyString()))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestPage(any(UUID.class), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(1);
        when(ingestionStateRepository.findAllBySourceId(sourceId)).thenReturn(List.of());

        List<CrawlResult> results = crawlService.crawlSite(sourceId, rootUrl, scope);

        assertThat(results).hasSize(2);
        verify(crawl4AiClient, never()).crawl("https://docs.example.com/level2");
    }

    // --- Progress tracking ---

    @Test
    void progressTrackerUpdatedDuringCrawl() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = CrawlScope.withDefaults(10);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/page1"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/page1"))
                .thenReturn(new CrawlResult("https://docs.example.com/page1", "# Page", List.of(), true, null));
        when(ingestionStateRepository.findBySourceIdAndPageUrl(any(), anyString()))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestPage(any(UUID.class), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(1);
        when(ingestionStateRepository.findAllBySourceId(sourceId)).thenReturn(List.of());

        crawlService.crawlSite(sourceId, rootUrl, scope);

        verify(progressTracker).startCrawl(sourceId, 1);
        verify(progressTracker).recordPageCrawled(sourceId);
        verify(progressTracker).completeCrawl(sourceId);
    }

    // --- llms-full.txt hybrid ingestion ---

    @Test
    void llmsFullContentIngestedDirectlyAndCoveredUrlsSkipped() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = CrawlScope.withDefaults(10);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/guide", "https://docs.example.com/api"),
                PageDiscoveryService.DiscoveryMethod.LLMS_FULL_TXT, "# Full content\nAll docs here");
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(ingestionService.ingestPage(any(UUID.class), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(5);
        when(ingestionStateRepository.findAllBySourceId(sourceId)).thenReturn(List.of());

        List<CrawlResult> results = crawlService.crawlSite(sourceId, rootUrl, scope);

        // llms-full.txt content ingested directly
        verify(ingestionService).ingestPage(eq(sourceId), eq("# Full content\nAll docs here"), eq(rootUrl), anyString(), isNull(), isNull());
        // Covered URLs are skipped (not crawled)
        verify(crawl4AiClient, never()).crawl(anyString());
        // The URLs are still counted as visited
        assertThat(results).isEmpty();
        verify(progressTracker).completeCrawl(sourceId);
    }

    // --- Deleted page cleanup ---

    @Test
    void cleanupDeletedPagesRemovesOrphanedChunksAndState() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = CrawlScope.withDefaults(10);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/current"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/current"))
                .thenReturn(new CrawlResult("https://docs.example.com/current", "# Current", List.of(), true, null));
        when(ingestionStateRepository.findBySourceIdAndPageUrl(any(), anyString()))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestPage(any(UUID.class), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(1);

        // Simulate pre-existing state with an orphaned page
        var existingState = new IngestionState(sourceId, "https://docs.example.com/deleted", "oldhash");
        var currentState = new IngestionState(sourceId, "https://docs.example.com/current", "currenthash");
        when(ingestionStateRepository.findAllBySourceId(sourceId))
                .thenReturn(List.of(currentState, existingState));

        crawlService.crawlSite(sourceId, rootUrl, scope);

        // Orphaned page chunks should be deleted via ingestionService
        verify(ingestionService).deleteChunksForUrl("https://docs.example.com/deleted");
        verify(ingestionStateRepository).deleteAllBySourceIdAndPageUrlNotIn(eq(sourceId), any());
    }

    // --- Incremental ingestion (hash-based change detection in CrawlService) ---

    @Test
    void incrementalIngestionSkipsUnchangedContent() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = CrawlScope.withDefaults(10);
        String markdown = "# Guide";
        String hash = ContentHasher.sha256(markdown);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/guide"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", markdown, List.of(), true, null));
        // Existing state with same hash
        var existingState = new IngestionState(sourceId, "https://docs.example.com/guide", hash);
        when(ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, "https://docs.example.com/guide"))
                .thenReturn(Optional.of(existingState));
        when(ingestionStateRepository.findAllBySourceId(sourceId)).thenReturn(List.of(existingState));

        crawlService.crawlSite(sourceId, rootUrl, scope);

        // Should skip ingestion since hash matches
        verify(ingestionService, never()).ingestPage(any(), anyString(), anyString(), anyString(), any(), any());
        verify(progressTracker).recordPageSkipped(sourceId);
    }

    @Test
    void incrementalIngestionReprocessesChangedContent() {
        UUID sourceId = UUID.randomUUID();
        String rootUrl = "https://docs.example.com";
        var scope = CrawlScope.withDefaults(10);
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/guide"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP, null);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Updated Guide", List.of(), true, null));
        // Existing state with different hash
        var existingState = new IngestionState(sourceId, "https://docs.example.com/guide",
                ContentHasher.sha256("# Old Guide"));
        when(ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, "https://docs.example.com/guide"))
                .thenReturn(Optional.of(existingState));
        when(ingestionService.ingestPage(any(UUID.class), anyString(), anyString(), anyString(), isNull(), isNull())).thenReturn(2);
        when(ingestionStateRepository.findAllBySourceId(sourceId)).thenReturn(List.of(existingState));

        crawlService.crawlSite(sourceId, rootUrl, scope);

        // Should delete old chunks, re-ingest, and update state
        verify(ingestionService).deleteChunksForUrl("https://docs.example.com/guide");
        verify(ingestionService).ingestPage(eq(sourceId), eq("# Updated Guide"), eq("https://docs.example.com/guide"), anyString(), isNull(), isNull());
        verify(ingestionStateRepository).save(existingState);
        verify(progressTracker).recordPageCrawled(sourceId);
    }
}
