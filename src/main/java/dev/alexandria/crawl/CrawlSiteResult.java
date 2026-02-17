package dev.alexandria.crawl;

import java.util.List;

public record CrawlSiteResult(
        List<CrawlResult> successPages,
        List<String> failedUrls
) {}
