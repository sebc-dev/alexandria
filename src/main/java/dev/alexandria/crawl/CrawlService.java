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
        boolean followLinks = discovery.method() == PageDiscoveryService.DiscoveryMethod.LINK_CRAWL;

        LinkedHashSet<String> queue = seedQueue(rootUrl, discovery);
        Set<String> visited = new HashSet<>();
        List<CrawlResult> results = new ArrayList<>();

        log.info("Starting crawl of {} (discovery method: {}, seed URLs: {})",
                rootUrl, discovery.method(), queue.size());

        while (!queue.isEmpty() && results.size() < maxPages) {
            String normalized = dequeueAndNormalize(queue);
            if (!visited.add(normalized)) {
                continue;
            }
            log.info("Crawling [{}/{}]: {}", results.size() + 1, maxPages, normalized);
            processPage(normalized, rootUrl, followLinks, results, queue, visited);
        }

        log.info("Crawl complete: {} pages crawled from {}", results.size(), rootUrl);
        return results;
    }

    private LinkedHashSet<String> seedQueue(String rootUrl,
                                            PageDiscoveryService.DiscoveryResult discovery) {
        LinkedHashSet<String> queue = new LinkedHashSet<>();
        if (!discovery.urls().isEmpty()) {
            queue.addAll(discovery.urls());
        } else {
            queue.add(UrlNormalizer.normalize(rootUrl));
        }
        return queue;
    }

    private String dequeueAndNormalize(LinkedHashSet<String> queue) {
        String url = queue.iterator().next();
        queue.remove(url);
        return UrlNormalizer.normalize(url);
    }

    private void processPage(String url, String rootUrl, boolean followLinks,
                             List<CrawlResult> results, LinkedHashSet<String> queue,
                             Set<String> visited) {
        try {
            CrawlResult result = crawl4AiClient.crawl(url);
            if (result.success()) {
                results.add(result);
                if (followLinks) {
                    enqueueDiscoveredLinks(result, rootUrl, queue, visited);
                }
            } else {
                log.warn("Failed to crawl {}: {}", url, result.errorMessage());
            }
        } catch (Exception e) {
            log.error("Error crawling {}: {}", url, e.getMessage());
        }
    }

    private void enqueueDiscoveredLinks(CrawlResult result, String rootUrl,
                                        LinkedHashSet<String> queue, Set<String> visited) {
        result.internalLinks().stream()
                .map(UrlNormalizer::normalize)
                .filter(link -> UrlNormalizer.isSameSite(rootUrl, link))
                .filter(link -> !visited.contains(link))
                .forEach(queue::add);
    }
}
