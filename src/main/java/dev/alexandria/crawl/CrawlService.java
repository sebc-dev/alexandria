package dev.alexandria.crawl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Crawl orchestrator that ties together page discovery and per-page crawling.
 * Tries sitemap.xml first for URL discovery. If no sitemap, falls back to
 * BFS link crawling starting from rootUrl.
 * Crawling is done concurrently in batches using virtual threads.
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final Crawl4AiClient crawl4AiClient;
    private final PageDiscoveryService pageDiscoveryService;
    private final int concurrency;

    public CrawlService(Crawl4AiClient crawl4AiClient,
                        PageDiscoveryService pageDiscoveryService,
                        Crawl4AiProperties properties) {
        this.crawl4AiClient = crawl4AiClient;
        this.pageDiscoveryService = pageDiscoveryService;
        this.concurrency = properties.crawlConcurrency();
    }

    /**
     * Crawl a documentation site from a root URL.
     * Tries sitemap.xml first for URL discovery. If no sitemap, falls back to
     * BFS link crawling starting from rootUrl.
     * Pages are crawled concurrently in batches for performance.
     *
     * @param rootUrl the starting URL for the crawl
     * @param maxPages maximum number of pages to crawl (safety limit)
     * @return result containing successfully crawled pages and failed URLs
     */
    public CrawlSiteResult crawlSite(String rootUrl, int maxPages) {
        PageDiscoveryService.DiscoveryResult discovery = pageDiscoveryService.discoverUrls(rootUrl);

        LinkedHashSet<String> queue = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        List<CrawlResult> results = new ArrayList<>();
        List<String> failedUrls = new ArrayList<>();
        boolean useLinkDiscovery = discovery.method() == PageDiscoveryService.DiscoveryMethod.LINK_CRAWL;

        if (!discovery.urls().isEmpty()) {
            queue.addAll(discovery.urls());
        } else {
            queue.add(UrlNormalizer.normalize(rootUrl));
        }

        log.info("Starting crawl of {} (discovery method: {}, seed URLs: {}, concurrency: {})",
                rootUrl, discovery.method(), queue.size(), concurrency);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            while (!queue.isEmpty() && results.size() < maxPages) {
                int remaining = maxPages - results.size();
                List<String> batch = takeBatch(queue, visited, Math.min(concurrency, remaining));

                if (batch.isEmpty()) {
                    break;
                }

                log.info("Crawling batch of {} URLs [{}/{}]", batch.size(), results.size() + 1, maxPages);

                List<Future<CrawlResult>> futures = batch.stream()
                        .map(url -> executor.submit(() -> crawl4AiClient.crawl(url)))
                        .toList();

                for (int i = 0; i < futures.size(); i++) {
                    String url = batch.get(i);
                    try {
                        CrawlResult result = futures.get(i).get();
                        if (result.success()) {
                            results.add(result);
                            if (useLinkDiscovery) {
                                result.internalLinks().stream()
                                        .map(UrlNormalizer::normalize)
                                        .filter(link -> UrlNormalizer.isSameSite(rootUrl, link))
                                        .filter(link -> !visited.contains(link))
                                        .forEach(queue::add);
                            }
                        } else {
                            log.warn("Failed to crawl {}: {}", url, result.errorMessage());
                            failedUrls.add(url);
                        }
                    } catch (Exception e) {
                        log.error("Error crawling {}: {}", url, e.getMessage());
                        failedUrls.add(url);
                    }
                }
            }
        }

        log.info("Crawl complete: {} pages crawled, {} failed from {}",
                results.size(), failedUrls.size(), rootUrl);
        return new CrawlSiteResult(results, failedUrls);
    }

    private List<String> takeBatch(LinkedHashSet<String> queue, Set<String> visited, int batchSize) {
        List<String> batch = new ArrayList<>(batchSize);
        Iterator<String> it = queue.iterator();
        while (it.hasNext() && batch.size() < batchSize) {
            String url = it.next();
            it.remove();
            String normalized = UrlNormalizer.normalize(url);
            if (visited.add(normalized)) {
                batch.add(normalized);
            }
        }
        return batch;
    }
}
