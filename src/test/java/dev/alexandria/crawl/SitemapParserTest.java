package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@SuppressWarnings("NullAway.Init")
@ExtendWith(MockitoExtension.class)
class SitemapParserTest {

  @Mock private RestClient.Builder restClientBuilder;

  @Mock private RestClient restClient;

  @Mock private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

  private SitemapParser sitemapParser;

  /** Per-URL response stubs: maps URL -> byte[] content or null for exceptions. */
  private final Map<String, Object> urlResponses = new HashMap<>();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    lenient()
        .when(restClientBuilder.defaultHeader(anyString(), any(String[].class)))
        .thenReturn(restClientBuilder);
    lenient().when(restClientBuilder.build()).thenReturn(restClient);
    lenient()
        .when(restClient.get())
        .thenReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);

    // Route each URI call to its own mock chain based on registered responses
    lenient()
        .when(requestHeadersUriSpec.uri(anyString()))
        .thenAnswer(
            invocation -> {
              String url = invocation.getArgument(0);
              Object registered = urlResponses.get(url);

              @SuppressWarnings("unchecked")
              RestClient.RequestHeadersSpec<? extends RestClient.RequestHeadersSpec<?>>
                  headersSpec = mock(RestClient.RequestHeadersSpec.class);

              if (registered instanceof RestClientException ex) {
                when(headersSpec.retrieve()).thenThrow(ex);
              } else {
                RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
                when(headersSpec.retrieve()).thenReturn(respSpec);
                when(respSpec.body(byte[].class)).thenReturn((byte[]) registered);
              }
              return headersSpec;
            });

    urlResponses.clear();
    sitemapParser = new SitemapParser(restClientBuilder);
  }

  private void stubHttpGet(String url, byte @Nullable [] content) {
    urlResponses.put(url, content);
  }

  private void stubHttpGetException(String url) {
    urlResponses.put(url, new RestClientException("404 Not Found"));
  }

  @Test
  void discoverFromSitemapValidSitemapReturnsUrls() {
    String sitemapXml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url><loc>https://docs.example.com/page1</loc></url>
                    <url><loc>https://docs.example.com/page2</loc></url>
                </urlset>
                """;
    stubHttpGet(
        "https://docs.example.com/sitemap.xml", sitemapXml.getBytes(StandardCharsets.UTF_8));

    List<String> urls = sitemapParser.discoverFromSitemap("https://docs.example.com/docs/intro");

    assertThat(urls)
        .containsExactly("https://docs.example.com/page1", "https://docs.example.com/page2");
  }

  @Test
  void discoverFromSitemapSitemapIndexReturnsUrlsFromNestedSitemaps() {
    String sitemapIndex =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <sitemap><loc>https://docs.example.com/sitemap-docs.xml</loc></sitemap>
                </sitemapindex>
                """;
    String nestedSitemap =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url><loc>https://docs.example.com/guide</loc></url>
                </urlset>
                """;
    stubHttpGet(
        "https://docs.example.com/sitemap.xml", sitemapIndex.getBytes(StandardCharsets.UTF_8));
    stubHttpGet(
        "https://docs.example.com/sitemap-docs.xml",
        nestedSitemap.getBytes(StandardCharsets.UTF_8));

    List<String> urls = sitemapParser.discoverFromSitemap("https://docs.example.com");

    assertThat(urls).containsExactly("https://docs.example.com/guide");
  }

  @Test
  void discoverFromSitemapNoSitemapFoundReturnsEmptyList() {
    stubHttpGetException("https://docs.example.com/sitemap.xml");
    stubHttpGetException("https://docs.example.com/sitemap_index.xml");

    List<String> urls = sitemapParser.discoverFromSitemap("https://docs.example.com");

    assertThat(urls).isEmpty();
  }

  @Test
  void discoverFromSitemapFirstCandidateFailsTriesSecond() {
    String sitemapXml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url><loc>https://docs.example.com/found</loc></url>
                </urlset>
                """;
    stubHttpGetException("https://docs.example.com/sitemap.xml");
    stubHttpGet(
        "https://docs.example.com/sitemap_index.xml", sitemapXml.getBytes(StandardCharsets.UTF_8));

    List<String> urls = sitemapParser.discoverFromSitemap("https://docs.example.com");

    assertThat(urls).containsExactly("https://docs.example.com/found");
  }

  @Test
  void discoverFromSitemapMalformedXmlReturnsEmptyList() {
    byte[] malformedContent = "<not valid xml at all".getBytes(StandardCharsets.UTF_8);
    stubHttpGet("https://docs.example.com/sitemap.xml", malformedContent);
    stubHttpGetException("https://docs.example.com/sitemap_index.xml");

    List<String> urls = sitemapParser.discoverFromSitemap("https://docs.example.com");

    assertThat(urls).isEmpty();
  }

  @Test
  void discoverFromSitemapEmptyContentReturnsEmptyList() {
    stubHttpGet("https://docs.example.com/sitemap.xml", new byte[0]);
    stubHttpGetException("https://docs.example.com/sitemap_index.xml");

    List<String> urls = sitemapParser.discoverFromSitemap("https://docs.example.com");

    assertThat(urls).isEmpty();
  }

  @Test
  void discoverFromSitemapNullContentReturnsEmptyList() {
    stubHttpGet("https://docs.example.com/sitemap.xml", null);
    stubHttpGetException("https://docs.example.com/sitemap_index.xml");

    List<String> urls = sitemapParser.discoverFromSitemap("https://docs.example.com");

    assertThat(urls).isEmpty();
  }

  @Test
  void normalizeToBaseExtractsSchemeAndHost() {
    String result = sitemapParser.normalizeToBase("https://docs.spring.io/boot/reference/");

    assertThat(result).isEqualTo("https://docs.spring.io");
  }

  @Test
  void normalizeToBasePreservesNonDefaultPort() {
    String result = sitemapParser.normalizeToBase("http://localhost:8080/docs");

    assertThat(result).isEqualTo("http://localhost:8080");
  }

  @Test
  void normalizeToBaseOmitsDefaultHttpsPort() {
    String result = sitemapParser.normalizeToBase("https://example.com:443/path");

    assertThat(result).isEqualTo("https://example.com");
  }
}
