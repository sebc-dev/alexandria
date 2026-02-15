package dev.alexandria.crawl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Crawl orchestrator that ties together page discovery and per-page crawling.
 * Tries sitemap.xml first for URL discovery. If no sitemap, falls back to
 * BFS link crawling starting from rootUrl.
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final Crawl4AiClient crawl4AiClient;
    private final PageDiscoveryService pageDiscoveryService;

    public CrawlService(Crawl4AiClient crawl4AiClient,
                        PageDiscoveryService pageDiscoveryService) {
        this.crawl4AiClient = crawl4AiClient;
        this.pageDiscoveryService = pageDiscoveryService;
    }

    /**
     * Crawl a documentation site from a root URL.
     * Tries sitemap.xml first for URL discovery. If no sitemap, falls back to
     * BFS link crawling starting from rootUrl.
     *
     * @param rootUrl the starting URL for the crawl
     * @param maxPages maximum number of pages to crawl (safety limit)
     * @return list of crawl results for each successfully crawled page
     */
    public List<CrawlResult> crawlSite(String rootUrl, int maxPages) {
        PageDiscoveryService.DiscoveryResult discovery = pageDiscoveryService.discoverUrls(rootUrl);

        LinkedHashSet<String> queue = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        List<CrawlResult> results = new ArrayList<>();
        boolean useLinkDiscovery = discovery.method() == PageDiscoveryService.DiscoveryMethod.LINK_CRAWL;

        if (!discovery.urls().isEmpty()) {
            queue.addAll(discovery.urls());
        } else {
            queue.add(UrlNormalizer.normalize(rootUrl));
        }

        log.info("Starting crawl of {} (discovery method: {}, seed URLs: {})",
                rootUrl, discovery.method(), queue.size());

        while (!queue.isEmpty() && results.size() < maxPages) {
            String url = queue.iterator().next();
            queue.remove(url);

            String normalized = UrlNormalizer.normalize(url);
            if (!visited.add(normalized)) {
                continue;
            }

            log.info("Crawling [{}/{}]: {}", results.size() + 1, maxPages, normalized);

            try {
                CrawlResult result = crawl4AiClient.crawl(normalized);
                if (result.success()) {
                    results.add(result);
                    // Only follow links if we are in link-crawl mode (no sitemap found)
                    if (useLinkDiscovery) {
                        result.internalLinks().stream()
                                .map(UrlNormalizer::normalize)
                                .filter(link -> UrlNormalizer.isSameSite(rootUrl, link))
                                .filter(link -> !visited.contains(link))
                                .forEach(queue::add);
                    }
                } else {
                    log.warn("Failed to crawl {}: {}", normalized, result.errorMessage());
                }
            } catch (Exception e) {
                log.error("Error crawling {}: {}", normalized, e.getMessage());
            }
        }

        log.info("Crawl complete: {} pages crawled from {}", results.size(), rootUrl);
        return results;
    }
}
