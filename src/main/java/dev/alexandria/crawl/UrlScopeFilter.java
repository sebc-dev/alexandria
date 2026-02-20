package dev.alexandria.crawl;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

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
     * <p>
     * Filtering order:
     * <ol>
     *   <li>Reject if URL is malformed or not on the same site as rootUrl</li>
     *   <li>Reject if URL path matches any block pattern (block takes priority)</li>
     *   <li>If allow patterns exist, accept only if URL path matches at least one</li>
     *   <li>If no allow patterns, accept all same-site URLs</li>
     * </ol>
     *
     * @param url the candidate URL to check
     * @param rootUrl the root URL defining the site boundary
     * @param scope the crawl scope with allow/block patterns
     * @return true if the URL passes scope filtering
     */
    public static boolean isAllowed(String url, String rootUrl, CrawlScope scope) {
        String urlPath;
        try {
            urlPath = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (!UrlNormalizer.isSameSite(rootUrl, url)) {
            return false;
        }

        if (urlPath == null || urlPath.isEmpty()) {
            urlPath = "/";
        }

        Path path = Path.of(urlPath);

        for (String blockPattern : scope.blockPatterns()) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + blockPattern);
            if (matcher.matches(path)) {
                return false;
            }
        }

        if (!scope.allowPatterns().isEmpty()) {
            for (String allowPattern : scope.allowPatterns()) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + allowPattern);
                if (matcher.matches(path)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }
}
