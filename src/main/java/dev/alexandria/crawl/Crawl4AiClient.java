package dev.alexandria.crawl;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * REST client for the Crawl4AI Python sidecar that converts web pages to Markdown.
 *
 * <p>Uses headless Chromium via Crawl4AI with a {@code PruningContentFilter} for
 * boilerplate removal. Prefers {@code fit_markdown} (filtered) over {@code raw_markdown}.
 * Returns a {@link CrawlResult} with success/failure status for graceful pipeline handling.
 *
 * @see CrawlResult
 * @see Crawl4AiConfig
 */
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
     */
    public CrawlResult crawl(String url) {
        Crawl4AiRequest request = buildRequest(url);

        Crawl4AiResponse response;
        try {
            response = restClient.post()
                    .uri("/crawl")
                    .body(request)
                    .retrieve()
                    .body(Crawl4AiResponse.class);
        } catch (RestClientException e) {
            log.warn("Crawl4AI request failed for {}: {}", url, e.getMessage());
            return new CrawlResult(url, null, List.of(), false, e.getMessage());
        }

        if (response == null || !response.success() || response.results().isEmpty()) {
            return new CrawlResult(url, null, List.of(), false,
                    "Crawl4AI returned no results for " + url);
        }

        Crawl4AiPageResult page = response.results().getFirst();
        if (!page.success()) {
            return new CrawlResult(url, null, List.of(), false, page.error_message());
        }

        // Prefer fit_markdown (boilerplate-removed) over raw_markdown
        String markdown = page.markdown() != null && page.markdown().fit_markdown() != null
                && !page.markdown().fit_markdown().isBlank()
                ? page.markdown().fit_markdown()
                : (page.markdown() != null ? page.markdown().raw_markdown() : null);

        return new CrawlResult(url, markdown, page.internalLinkHrefs(), true, null);
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
