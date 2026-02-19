package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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

    private CrawlService crawlService;

    @BeforeEach
    void setUp() {
        crawlService = new CrawlService(crawl4AiClient, pageDiscoveryService);
    }

    @Test
    void crawlSite_happyPath_returnsSinglePageResult() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/guide"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Guide", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/guide");
        assertThat(results.getFirst().markdown()).isEqualTo("# Guide");
    }

    @Test
    void crawlSite_linkCrawlMode_followsInternalLinks() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
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
    void crawlSite_sitemapMode_doesNotFollowLinks() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/page1"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/page1"))
                .thenReturn(new CrawlResult("https://docs.example.com/page1",
                        "# Page 1", List.of("https://docs.example.com/page2"), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        verify(crawl4AiClient, never()).crawl("https://docs.example.com/page2");
    }

    @Test
    void crawlSite_maxPagesLimit_stopsCrawlingAtLimit() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/p1",
                        "https://docs.example.com/p2",
                        "https://docs.example.com/p3"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP);
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
    void crawlSite_failedCrawlResult_skippedAndContinues() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/fail",
                        "https://docs.example.com/ok"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP);
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
    void crawlSite_exceptionDuringCrawl_skippedAndContinues() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of("https://docs.example.com/error",
                        "https://docs.example.com/ok"),
                PageDiscoveryService.DiscoveryMethod.SITEMAP);
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
    void crawlSite_duplicateUrls_onlyCrawledOnce() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        // Root page returns a link that will normalize to the same URL as root
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
    void crawlSite_urlNormalization_fragmentsStripped() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        // Root page returns links with fragments that normalize to the same page
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/",
                        "# Home",
                        List.of("https://docs.example.com/guide#intro",
                                "https://docs.example.com/guide#details"),
                        true, null));
        when(crawl4AiClient.crawl("https://docs.example.com/guide"))
                .thenReturn(new CrawlResult("https://docs.example.com/guide", "# Guide", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        // guide#intro and guide#details normalize to the same URL, only one crawl
        assertThat(results).hasSize(2);
    }

    @Test
    void crawlSite_emptyDiscoveryUrls_usesNormalizedRootUrl() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
        when(pageDiscoveryService.discoverUrls(rootUrl)).thenReturn(discovery);
        when(crawl4AiClient.crawl("https://docs.example.com/"))
                .thenReturn(new CrawlResult("https://docs.example.com/", "# Home", List.of(), true, null));

        List<CrawlResult> results = crawlService.crawlSite(rootUrl, 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().url()).isEqualTo("https://docs.example.com/");
    }

    @Test
    void crawlSite_linkCrawlMode_filtersExternalLinks() {
        String rootUrl = "https://docs.example.com";
        var discovery = new PageDiscoveryService.DiscoveryResult(
                List.of(),
                PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
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
}
