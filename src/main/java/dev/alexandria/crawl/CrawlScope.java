package dev.alexandria.crawl;

import java.util.List;

/**
 * Immutable scope configuration for crawl URL filtering.
 * Defines allow/block glob patterns, maximum crawl depth, and page limits.
 */
public record CrawlScope(
        List<String> allowPatterns,
        List<String> blockPatterns,
        Integer maxDepth,
        int maxPages
) {

    public CrawlScope {
        allowPatterns = allowPatterns == null ? List.of() : List.copyOf(allowPatterns);
        blockPatterns = blockPatterns == null ? List.of() : List.copyOf(blockPatterns);
    }

    /**
     * Creates a default scope with no pattern restrictions.
     *
     * @param maxPages maximum number of pages to crawl
     * @return scope with empty allow/block patterns and unlimited depth
     */
    public static CrawlScope withDefaults(int maxPages) {
        return new CrawlScope(List.of(), List.of(), null, maxPages);
    }
}
