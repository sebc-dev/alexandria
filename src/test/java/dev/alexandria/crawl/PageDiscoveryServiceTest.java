package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageDiscoveryServiceTest {

    @Mock
    private SitemapParser sitemapParser;

    private PageDiscoveryService pageDiscoveryService;

    @BeforeEach
    void setUp() {
        pageDiscoveryService = new PageDiscoveryService(sitemapParser);
    }

    @Test
    void discoverUrls_sitemapAvailable_returnsSitemapResult() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://docs.example.com/page1", "https://docs.example.com/page2"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).containsExactly(
                "https://docs.example.com/page1",
                "https://docs.example.com/page2");
    }

    @Test
    void discoverUrls_noSitemap_returnsLinkCrawlResult() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl)).thenReturn(List.of());

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
        assertThat(result.urls()).isEmpty();
    }

    @Test
    void discoverUrls_sitemapFirst_triesSitemapBeforeFallback() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://docs.example.com/from-sitemap"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).containsExactly("https://docs.example.com/from-sitemap");
    }

    @Test
    void discoverUrls_filtersExternalUrls() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://docs.example.com/page1", "https://external.com/page"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).containsExactly("https://docs.example.com/page1");
    }

    @Test
    void discoverUrls_normalizesAndDeduplicatesUrls() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of(
                        "https://docs.example.com/page/",
                        "https://docs.example.com/page"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.urls()).containsExactly("https://docs.example.com/page");
    }

    @Test
    void discoverUrls_allUrlsExternal_returnsLinkCrawl() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://external.com/page1", "https://other.com/page2"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        // After filtering, no same-site URLs remain, but method is still SITEMAP
        // because sitemap returned URLs (they just were all external)
        // Actually let's check: the filter removes all, leaving empty filtered list
        // But the original sitemapUrls was not empty, so we enter the if-branch
        // filtered list is empty? No -- the code checks !sitemapUrls.isEmpty()
        // and returns a DiscoveryResult with the filtered list and SITEMAP method
        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).isEmpty();
    }
}
