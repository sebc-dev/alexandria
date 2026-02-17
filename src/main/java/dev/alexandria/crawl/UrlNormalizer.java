package dev.alexandria.crawl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that normalizes URLs for deduplication during crawling.
 * Removes fragments, tracking query params, normalizes trailing slashes and host casing.
 */
public final class UrlNormalizer {

    private static final Logger log = LoggerFactory.getLogger(UrlNormalizer.class);

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "source"
    );

    private UrlNormalizer() {
        // utility class
    }

    /**
     * Normalize a URL for deduplication:
     * - Remove fragments (#section)
     * - Remove common tracking query params (utm_*, ref, source)
     * - Remove trailing slash unless URL is just the domain root
     * - Lowercase scheme and host (path is case-sensitive)
     *
     * @param url the URL to normalize
     * @return normalized URL string, or the input unchanged if malformed
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            log.warn("Malformed URL, returning unchanged: {}", url);
            return url;
        }

        // Must have a scheme to normalize
        if (uri.getScheme() == null || uri.getHost() == null) {
            log.warn("URL missing scheme or host, returning unchanged: {}", url);
            return url;
        }

        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();
        String path = uri.getRawPath();
        String query = uri.getRawQuery();

        // Normalize path: remove trailing slash unless it's the root path
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // Filter out tracking query params
        String filteredQuery = filterQueryParams(query);

        // Rebuild URL without fragment
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != -1 && !isDefaultPort(scheme, port)) {
            sb.append(':').append(port);
        }
        sb.append(path);
        if (filteredQuery != null && !filteredQuery.isEmpty()) {
            sb.append('?').append(filteredQuery);
        }

        return sb.toString();
    }

    /**
     * Extract the base URL (scheme://host[:port]) from a full URL.
     * Non-default ports are preserved; default ports (80 for HTTP, 443 for HTTPS) are omitted.
     *
     * @param url the URL to extract the base from
     * @return the base URL, or the input unchanged if malformed
     */
    public static String normalizeToBase(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                log.warn("URL missing scheme or host, returning unchanged: {}", url);
                return url;
            }
            String scheme = uri.getScheme().toLowerCase();
            String host = uri.getHost().toLowerCase();
            int port = uri.getPort();
            if (port == -1 || isDefaultPort(scheme, port)) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (URISyntaxException e) {
            log.warn("Malformed URL, returning unchanged: {}", url);
            return url;
        }
    }

    /**
     * Check if a candidate URL has the same scheme+host+port as the root URL.
     * Used to filter out external links during crawling.
     *
     * @param rootUrl the root URL defining the site boundary
     * @param candidateUrl the URL to check
     * @return true if the candidate is on the same site
     */
    public static boolean isSameSite(String rootUrl, String candidateUrl) {
        try {
            URI root = new URI(rootUrl);
            URI candidate = new URI(candidateUrl);
            if (root.getScheme() == null || candidate.getScheme() == null
                    || root.getHost() == null || candidate.getHost() == null) {
                return false;
            }
        } catch (URISyntaxException e) {
            return false;
        }
        return normalizeToBase(rootUrl).equals(normalizeToBase(candidateUrl));
    }

    private static String filterQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String filtered = Arrays.stream(query.split("&"))
                .filter(param -> {
                    String key = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
                    return !TRACKING_PARAMS.contains(key.toLowerCase());
                })
                .sorted()
                .collect(Collectors.joining("&"));
        return filtered.isEmpty() ? null : filtered;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
    }
}
