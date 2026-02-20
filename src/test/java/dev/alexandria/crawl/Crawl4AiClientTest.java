package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class Crawl4AiClientTest {

  @Mock private RestClient restClient;

  @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;

  @Mock private RestClient.RequestBodySpec requestBodySpec;

  @Mock private RestClient.ResponseSpec responseSpec;

  private Crawl4AiClient crawl4AiClient;

  @BeforeEach
  void setUp() {
    crawl4AiClient = new Crawl4AiClient(restClient);
  }

  private void stubRestClientChain() {
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri("/crawl")).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(Crawl4AiRequest.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
  }

  @Test
  void crawlSuccessfulResponseReturnsCrawlResultWithMarkdown() {
    stubRestClientChain();
    var markdown = new Crawl4AiMarkdown("# Raw", "# Fit content", null, null);
    var pageResult =
        new Crawl4AiPageResult(
            "https://docs.example.com",
            true,
            "200",
            markdown,
            Map.of(
                "internal",
                List.of(new Crawl4AiLink("https://docs.example.com/page2", "Page 2", null))),
            null);
    var response = new Crawl4AiResponse(true, List.of(pageResult));
    when(responseSpec.body(Crawl4AiResponse.class)).thenReturn(response);

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.success()).isTrue();
    assertThat(result.url()).isEqualTo("https://docs.example.com");
    assertThat(result.markdown()).isEqualTo("# Fit content");
    assertThat(result.internalLinks()).containsExactly("https://docs.example.com/page2");
  }

  @Test
  void crawlPrefersFitMarkdownOverRaw() {
    stubRestClientChain();
    var markdown = new Crawl4AiMarkdown("# Raw markdown", "# Fit markdown", null, null);
    var pageResult =
        new Crawl4AiPageResult("https://docs.example.com", true, "200", markdown, Map.of(), null);
    when(responseSpec.body(Crawl4AiResponse.class))
        .thenReturn(new Crawl4AiResponse(true, List.of(pageResult)));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.markdown()).isEqualTo("# Fit markdown");
  }

  @Test
  void crawlFitMarkdownBlankFallsBackToRawMarkdown() {
    stubRestClientChain();
    var markdown = new Crawl4AiMarkdown("# Raw markdown", "   ", null, null);
    var pageResult =
        new Crawl4AiPageResult("https://docs.example.com", true, "200", markdown, Map.of(), null);
    when(responseSpec.body(Crawl4AiResponse.class))
        .thenReturn(new Crawl4AiResponse(true, List.of(pageResult)));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.markdown()).isEqualTo("# Raw markdown");
  }

  @Test
  void crawlFitMarkdownNullFallsBackToRawMarkdown() {
    stubRestClientChain();
    var markdown = new Crawl4AiMarkdown("# Raw", null, null, null);
    var pageResult =
        new Crawl4AiPageResult("https://docs.example.com", true, "200", markdown, Map.of(), null);
    when(responseSpec.body(Crawl4AiResponse.class))
        .thenReturn(new Crawl4AiResponse(true, List.of(pageResult)));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.markdown()).isEqualTo("# Raw");
  }

  @Test
  void crawlMarkdownObjectNullReturnsNullMarkdown() {
    stubRestClientChain();
    var pageResult =
        new Crawl4AiPageResult("https://docs.example.com", true, "200", null, Map.of(), null);
    when(responseSpec.body(Crawl4AiResponse.class))
        .thenReturn(new Crawl4AiResponse(true, List.of(pageResult)));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.success()).isTrue();
    assertThat(result.markdown()).isNull();
  }

  @Test
  void crawlResponseNotSuccessReturnsFailed() {
    stubRestClientChain();
    var pageResult =
        new Crawl4AiPageResult(
            "https://docs.example.com", false, "500", null, Map.of(), "Internal Server Error");
    when(responseSpec.body(Crawl4AiResponse.class))
        .thenReturn(new Crawl4AiResponse(true, List.of(pageResult)));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Internal Server Error");
  }

  @Test
  void crawlEmptyResultsReturnsFailed() {
    stubRestClientChain();
    when(responseSpec.body(Crawl4AiResponse.class))
        .thenReturn(new Crawl4AiResponse(true, List.of()));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("no results");
  }

  @Test
  void crawlNullResponseReturnsFailed() {
    stubRestClientChain();
    when(responseSpec.body(Crawl4AiResponse.class)).thenReturn(null);

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.success()).isFalse();
  }

  @Test
  void crawlResponseLevelNotSuccessReturnsFailed() {
    stubRestClientChain();
    when(responseSpec.body(Crawl4AiResponse.class))
        .thenReturn(new Crawl4AiResponse(false, List.of()));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.success()).isFalse();
  }

  @Test
  void crawlRestClientExceptionReturnsFailed() {
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri("/crawl")).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(Crawl4AiRequest.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

    CrawlResult result = crawl4AiClient.crawl("https://docs.example.com");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Connection refused");
    assertThat(result.url()).isEqualTo("https://docs.example.com");
  }
}
