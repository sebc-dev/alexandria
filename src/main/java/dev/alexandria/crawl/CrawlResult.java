package dev.alexandria.crawl;

import java.util.List;

public record CrawlResult(
        String url,
        String markdown,
        List<String> internalLinks,
        boolean success,
        String errorMessage
) {}
