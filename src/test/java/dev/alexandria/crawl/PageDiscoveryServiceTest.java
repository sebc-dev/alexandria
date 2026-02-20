package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PageDiscoveryServiceTest {

    @Mock
    private SitemapParser sitemapParser;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    /** URL -> response body mapping for HTTP stubbing. Empty map = all return null. */
    private final Map<String, String> httpResponses = new HashMap<>();

    private PageDiscoveryService pageDiscoveryService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);

        var uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(restClient.get()).thenReturn(uriSpec);

        when(uriSpec.uri(any(String.class))).thenAnswer(invocation -> {
            String requestedUri = invocation.getArgument(0, String.class);
            String content = httpResponses.get(requestedUri);

            var headersSpec = mock(RestClient.RequestHeadersSpec.class);
            var responseSpec = mock(RestClient.ResponseSpec.class);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(String.class)).thenReturn(content);
            return headersSpec;
        });

        httpResponses.clear();
        pageDiscoveryService = new PageDiscoveryService(sitemapParser, restClientBuilder);
    }

    @Test
    void discoverUrlsSitemapAvailableReturnsSitemapResult() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://docs.example.com/page1", "https://docs.example.com/page2"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).containsExactly(
                "https://docs.example.com/page1",
                "https://docs.example.com/page2");
        assertThat(result.llmsFullContent()).isNull();
    }

    @Test
    void discoverUrlsNoSitemapReturnsLinkCrawlResult() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl)).thenReturn(List.of());

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
        assertThat(result.urls()).isEmpty();
        assertThat(result.llmsFullContent()).isNull();
    }

    @Test
    void discoverUrlsSitemapFirstTriesSitemapBeforeFallback() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://docs.example.com/from-sitemap"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).containsExactly("https://docs.example.com/from-sitemap");
    }

    @Test
    void discoverUrlsFiltersExternalUrls() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://docs.example.com/page1", "https://external.com/page"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).containsExactly("https://docs.example.com/page1");
    }

    @Test
    void discoverUrlsNormalizesAndDeduplicatesUrls() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of(
                        "https://docs.example.com/page/",
                        "https://docs.example.com/page"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.urls()).containsExactly("https://docs.example.com/page");
    }

    @Test
    void discoverUrlsAllUrlsExternalReturnsLinkCrawl() {
        String rootUrl = "https://docs.example.com";
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://external.com/page1", "https://other.com/page2"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).isEmpty();
    }

    @Test
    void llmsTxtFoundReturnsUrls() {
        String rootUrl = "https://docs.example.com";
        String llmsTxtContent = """
                # Example Docs

                > A documentation site

                ## Guides

                - [Getting Started](https://docs.example.com/start): Quick start guide
                - [API Reference](https://docs.example.com/api): Full API docs
                """;

        httpResponses.put("https://docs.example.com/llms.txt", llmsTxtContent);

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.LLMS_TXT);
        assertThat(result.urls()).containsExactly(
                "https://docs.example.com/start",
                "https://docs.example.com/api");
        assertThat(result.llmsFullContent()).isNull();
    }

    @Test
    void llmsFullTxtFoundReturnsContentAndUrls() {
        String rootUrl = "https://docs.example.com";
        String llmsFullContent = """
                # Example Docs

                This is a comprehensive documentation page with all the content.

                Some paragraph about getting started with the framework.

                Another paragraph about configuration options and best practices.

                See also [API Reference](https://docs.example.com/api) for more details.
                """;

        httpResponses.put("https://docs.example.com/llms-full.txt", llmsFullContent);

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.LLMS_FULL_TXT);
        assertThat(result.urls()).containsExactly("https://docs.example.com/api");
        assertThat(result.llmsFullContent()).isEqualTo(llmsFullContent);
    }

    @Test
    void llmsTxtNotFoundFallsToSitemap() {
        String rootUrl = "https://docs.example.com";
        // No httpResponses entries = all return null (llms.txt not found)
        when(sitemapParser.discoverFromSitemap(rootUrl))
                .thenReturn(List.of("https://docs.example.com/from-sitemap"));

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.SITEMAP);
        assertThat(result.urls()).containsExactly("https://docs.example.com/from-sitemap");
    }

    @Test
    void noLlmsTxtNoSitemapFallsToLinkCrawl() {
        String rootUrl = "https://docs.example.com";
        // No httpResponses entries = all return null (llms.txt not found)
        when(sitemapParser.discoverFromSitemap(rootUrl)).thenReturn(List.of());

        PageDiscoveryService.DiscoveryResult result = pageDiscoveryService.discoverUrls(rootUrl);

        assertThat(result.method()).isEqualTo(PageDiscoveryService.DiscoveryMethod.LINK_CRAWL);
        assertThat(result.urls()).isEmpty();
        assertThat(result.llmsFullContent()).isNull();
    }
}
