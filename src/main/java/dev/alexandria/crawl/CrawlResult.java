package dev.alexandria.crawl;

import java.util.List;

/**
 * Result of crawling a single URL: Markdown content, discovered links, and success status.
 */
public record CrawlResult(
        String url,
        String markdown,
        List<String> internalLinks,
        boolean success,
        String errorMessage
) {
    public CrawlResult {
        internalLinks = internalLinks == null ? List.of() : List.copyOf(internalLinks);
    }
}
