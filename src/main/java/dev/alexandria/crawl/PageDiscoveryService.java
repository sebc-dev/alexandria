package dev.alexandria.crawl;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Coordinates URL discovery for a site. Tries sitemap.xml first via SitemapParser,
 * falls back to signaling the caller to use recursive link crawling.
 */
@Service
public class PageDiscoveryService {

    private final SitemapParser sitemapParser;

    public PageDiscoveryService(SitemapParser sitemapParser) {
        this.sitemapParser = sitemapParser;
    }

    /**
     * Discover URLs for a site. Tries sitemap.xml first, falls back to empty
     * (caller will use recursive link crawling).
     *
     * @param rootUrl the root URL of the site to discover
     * @return discovered URLs, or empty list (caller should seed with rootUrl for link crawling)
     */
    public DiscoveryResult discoverUrls(String rootUrl) {
        List<String> sitemapUrls = sitemapParser.discoverFromSitemap(rootUrl);
        if (!sitemapUrls.isEmpty()) {
            List<String> filtered = sitemapUrls.stream()
                    .filter(url -> UrlNormalizer.isSameSite(rootUrl, url))
                    .map(UrlNormalizer::normalize)
                    .distinct()
                    .toList();
            return new DiscoveryResult(filtered, DiscoveryMethod.SITEMAP);
        }
        return new DiscoveryResult(List.of(), DiscoveryMethod.LINK_CRAWL);
    }

    public enum DiscoveryMethod { SITEMAP, LINK_CRAWL }

    public record DiscoveryResult(List<String> urls, DiscoveryMethod method) {}
}
