package dev.alexandria.crawl;

/**
 * Static utility for filtering URLs against a {@link CrawlScope}.
 * Block patterns take priority over allow patterns; external URLs are always rejected.
 */
public final class UrlScopeFilter {

    private UrlScopeFilter() {
        // utility class
    }

    /**
     * Check whether a URL is allowed by the given scope relative to the root URL.
     *
     * @param url the candidate URL to check
     * @param rootUrl the root URL defining the site boundary
     * @param scope the crawl scope with allow/block patterns
     * @return true if the URL passes scope filtering
     */
    public static boolean isAllowed(String url, String rootUrl, CrawlScope scope) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
