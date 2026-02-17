package dev.alexandria.crawl;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class Crawl4AiClient {

    private static final Logger log = LoggerFactory.getLogger(Crawl4AiClient.class);

    private final RestClient restClient;

    public Crawl4AiClient(@Qualifier("crawl4AiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Crawl a single URL via Crawl4AI sidecar.
     * Uses PruningContentFilter for boilerplate removal and headless Chromium for JS rendering.
     * Retries on transient RestClientException with exponential backoff.
     */
    @Retryable(
            retryFor = RestClientException.class,
            maxAttemptsExpression = "${alexandria.crawl4ai.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${alexandria.crawl4ai.retry.delay-ms}",
                    multiplierExpression = "${alexandria.crawl4ai.retry.multiplier}"
            )
    )
    public CrawlResult crawl(String url) {
        Crawl4AiRequest request = buildRequest(url);

        Crawl4AiResponse response = restClient.post()
                .uri("/crawl")
                .body(request)
                .retrieve()
                .body(Crawl4AiResponse.class);

        if (response == null || !response.success() || response.results().isEmpty()) {
            return new CrawlResult(url, null, List.of(), false,
                    "Crawl4AI returned no results for " + url);
        }

        Crawl4AiPageResult page = response.results().getFirst();
        if (!page.success()) {
            return new CrawlResult(url, null, List.of(), false, page.errorMessage());
        }

        return new CrawlResult(url, extractMarkdown(page.markdown()),
                page.internalLinkHrefs(), true, null);
    }

    @Recover
    CrawlResult recoverCrawl(RestClientException e, String url) {
        log.warn("Crawl4AI request failed after retries for {}: {}", url, e.getMessage());
        return new CrawlResult(url, null, List.of(), false, e.getMessage());
    }

    /**
     * Prefer fitMarkdown (boilerplate-removed) over rawMarkdown.
     */
    private String extractMarkdown(Crawl4AiMarkdown markdown) {
        if (markdown == null) {
            return null;
        }
        if (markdown.fitMarkdown() != null && !markdown.fitMarkdown().isBlank()) {
            return markdown.fitMarkdown();
        }
        return markdown.rawMarkdown();
    }

    private Crawl4AiRequest buildRequest(String url) {
        return new Crawl4AiRequest(
                List.of(url),
                Map.of("type", "BrowserConfig", "params", Map.of("headless", true)),
                Map.of("type", "CrawlerRunConfig", "params", Map.of(
                        "cache_mode", "bypass",
                        "word_count_threshold", 50,
                        "excluded_tags", List.of("nav", "footer", "header"),
                        "markdown_generator", Map.of(
                                "type", "DefaultMarkdownGenerator",
                                "params", Map.of(
                                        "content_filter", Map.of(
                                                "type", "PruningContentFilter",
                                                "params", Map.of("threshold", 0.48, "min_word_threshold", 20)
                                        )
                                )
                        )
                ))
        );
    }
}
