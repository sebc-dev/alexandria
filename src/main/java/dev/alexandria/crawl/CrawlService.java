package dev.alexandria.crawl;

import dev.alexandria.ingestion.IngestionService;
import dev.alexandria.ingestion.IngestionState;
import dev.alexandria.ingestion.IngestionStateRepository;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Crawl orchestrator that ties together page discovery and per-page crawling. Supports scope
 * filtering, depth tracking, incremental ingestion, llms-full.txt hybrid ingestion, progress
 * tracking, and deleted page cleanup.
 */
@Service
public class CrawlService {

  private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

  private final Crawl4AiClient crawl4AiClient;
  private final PageDiscoveryService pageDiscoveryService;
  private final CrawlProgressTracker progressTracker;
  private final IngestionService ingestionService;
  private final IngestionStateRepository ingestionStateRepository;
  private final SourceRepository sourceRepository;

  public CrawlService(
      Crawl4AiClient crawl4AiClient,
      PageDiscoveryService pageDiscoveryService,
      CrawlProgressTracker progressTracker,
      IngestionService ingestionService,
      IngestionStateRepository ingestionStateRepository,
      SourceRepository sourceRepository) {
    this.crawl4AiClient = crawl4AiClient;
    this.pageDiscoveryService = pageDiscoveryService;
    this.progressTracker = progressTracker;
    this.ingestionService = ingestionService;
    this.ingestionStateRepository = ingestionStateRepository;
    this.sourceRepository = sourceRepository;
  }

  /**
   * Crawl a documentation site from a root URL with default scope. Delegates to the full
   * scope-aware method.
   *
   * @param rootUrl the starting URL for the crawl
   * @param maxPages maximum number of pages to crawl (safety limit)
   * @return list of crawl results for each successfully crawled page
   */
  public List<CrawlResult> crawlSite(String rootUrl, int maxPages) {
    return crawlSite(null, rootUrl, CrawlScope.withDefaults(maxPages));
  }

  /**
   * Crawl a documentation site with scope filtering, depth tracking, and progress reporting. Tries
   * sitemap.xml / llms.txt first for URL discovery. If no discovery source, falls back to BFS link
   * crawling starting from rootUrl.
   *
   * @param sourceId the UUID of the source being crawled (null for legacy callers)
   * @param rootUrl the starting URL for the crawl
   * @param scope crawl scope with allow/block patterns, max depth, and page limits
   * @return list of crawl results for each successfully crawled page
   */
  public List<CrawlResult> crawlSite(UUID sourceId, String rootUrl, CrawlScope scope) {
    PageDiscoveryService.DiscoveryResult discovery = pageDiscoveryService.discoverUrls(rootUrl);
    boolean followLinks = discovery.method() == PageDiscoveryService.DiscoveryMethod.LINK_CRAWL;

    // Load Source once to cache version and name for metadata denormalization
    String sourceVersion = null;
    String sourceName = null;
    if (sourceId != null) {
      Optional<Source> sourceOpt = sourceRepository.findById(sourceId);
      if (sourceOpt.isPresent()) {
        sourceVersion = sourceOpt.get().getVersion();
        sourceName = sourceOpt.get().getName();
      }
    }

    // Handle llms-full.txt hybrid ingestion
    Set<String> llmsFullCoveredUrls = new HashSet<>();
    if (discovery.llmsFullContent() != null && sourceId != null) {
      log.info("Ingesting llms-full.txt content directly for {}", rootUrl);
      ingestionService.ingestPage(
          sourceId,
          discovery.llmsFullContent(),
          rootUrl,
          Instant.now().toString(),
          sourceVersion,
          sourceName);
      // All URLs from LLMS_FULL_TXT discovery are covered by the full content
      if (discovery.method() == PageDiscoveryService.DiscoveryMethod.LLMS_FULL_TXT) {
        llmsFullCoveredUrls.addAll(
            discovery.urls().stream().map(UrlNormalizer::normalize).toList());
      }
    }

    // Seed queue with depth tracking: URL -> depth
    LinkedHashMap<String, Integer> queue = seedQueue(rootUrl, discovery, scope);
    Set<String> visited = new HashSet<>();
    List<CrawlResult> results = new ArrayList<>();
    Set<String> crawledUrls = new HashSet<>();

    log.info(
        "Starting crawl of {} (discovery method: {}, seed URLs: {}, scope: maxDepth={}, maxPages={})",
        rootUrl,
        discovery.method(),
        queue.size(),
        scope.maxDepth(),
        scope.maxPages());

    if (sourceId != null) {
      progressTracker.startCrawl(sourceId, queue.size());
    }

    try {
      while (!queue.isEmpty() && results.size() < scope.maxPages()) {
        if (sourceId != null && progressTracker.isCancelled(sourceId)) {
          log.info("Crawl cancelled for source {}", sourceId);
          break;
        }
        Map.Entry<String, Integer> entry = dequeueFirst(queue);
        String normalized = UrlNormalizer.normalize(entry.getKey());
        int depth = entry.getValue();

        if (!visited.add(normalized)) {
          continue;
        }

        // Check max depth
        if (scope.maxDepth() != null && depth > scope.maxDepth()) {
          continue;
        }

        // Skip URLs covered by llms-full.txt
        if (llmsFullCoveredUrls.contains(normalized)) {
          log.debug("Skipping {} (covered by llms-full.txt)", normalized);
          if (sourceId != null) {
            progressTracker.recordPageSkipped(sourceId);
          }
          crawledUrls.add(normalized);
          continue;
        }

        log.info("Crawling [{}/{}]: {}", results.size() + 1, scope.maxPages(), normalized);
        processPage(
            sourceId,
            normalized,
            rootUrl,
            depth,
            followLinks,
            scope,
            results,
            queue,
            visited,
            crawledUrls,
            sourceVersion,
            sourceName);
      }

      // Post-crawl cleanup: remove orphaned pages
      if (sourceId != null) {
        cleanupDeletedPages(sourceId, crawledUrls);
        progressTracker.completeCrawl(sourceId);
      }
    } catch (Exception e) {
      if (sourceId != null) {
        progressTracker.failCrawl(sourceId);
      }
      throw e;
    }

    log.info("Crawl complete: {} pages crawled from {}", results.size(), rootUrl);
    return results;
  }

  private LinkedHashMap<String, Integer> seedQueue(
      String rootUrl, PageDiscoveryService.DiscoveryResult discovery, CrawlScope scope) {
    LinkedHashMap<String, Integer> queue = new LinkedHashMap<>();
    if (!discovery.urls().isEmpty()) {
      for (String url : discovery.urls()) {
        String normalized = UrlNormalizer.normalize(url);
        if (UrlScopeFilter.isAllowed(normalized, rootUrl, scope)) {
          queue.put(normalized, 0);
        }
      }
    }
    if (queue.isEmpty()) {
      queue.put(UrlNormalizer.normalize(rootUrl), 0);
    }
    return queue;
  }

  private Map.Entry<String, Integer> dequeueFirst(LinkedHashMap<String, Integer> queue) {
    var iterator = queue.entrySet().iterator();
    var entry = iterator.next();
    iterator.remove();
    return entry;
  }

  private void processPage(
      UUID sourceId,
      String url,
      String rootUrl,
      int depth,
      boolean followLinks,
      CrawlScope scope,
      List<CrawlResult> results,
      LinkedHashMap<String, Integer> queue,
      Set<String> visited,
      Set<String> crawledUrls,
      String version,
      String sourceName) {
    try {
      CrawlResult result = crawl4AiClient.crawl(url);
      if (result.success()) {
        results.add(result);
        crawledUrls.add(url);

        // Incremental ingestion with hash-based change detection
        if (sourceId != null) {
          IngestionService.IngestResult ingestResult =
              ingestIncremental(sourceId, url, result.markdown(), version, sourceName);
          if (ingestResult.skipped()) {
            progressTracker.recordPageSkipped(sourceId);
          } else {
            progressTracker.recordPageCrawled(sourceId);
          }
        }

        if (followLinks) {
          enqueueDiscoveredLinks(sourceId, result, rootUrl, depth, scope, queue, visited);
        }
      } else {
        log.warn("Failed to crawl {}: {}", url, result.errorMessage());
        if (sourceId != null) {
          progressTracker.recordError(sourceId, url);
        }
      }
    } catch (Exception e) {
      log.error("Error crawling {}: {}", url, e.getMessage());
      if (sourceId != null) {
        progressTracker.recordError(sourceId, url);
      }
    }
  }

  /**
   * Perform incremental ingestion for a single page: compare content hash, skip if unchanged,
   * delete old chunks and re-ingest if changed or new.
   */
  private IngestionService.IngestResult ingestIncremental(
      UUID sourceId, String normalizedUrl, String markdown, String version, String sourceName) {
    String newHash = ContentHasher.sha256(markdown);

    Optional<IngestionState> existingState =
        ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, normalizedUrl);

    if (existingState.isPresent() && existingState.get().getContentHash().equals(newHash)) {
      log.debug("Content unchanged for {}, skipping ingestion", normalizedUrl);
      return new IngestionService.IngestResult(0, true, false);
    }

    // Delete old chunks before re-ingesting
    ingestionService.deleteChunksForUrl(normalizedUrl);

    // Chunk and embed
    int chunkCount =
        ingestionService.ingestPage(
            sourceId, markdown, normalizedUrl, Instant.now().toString(), version, sourceName);

    // Update or create ingestion state
    if (existingState.isPresent()) {
      IngestionState state = existingState.get();
      state.setContentHash(newHash);
      state.setLastIngestedAt(Instant.now());
      ingestionStateRepository.save(state);
    } else {
      ingestionStateRepository.save(new IngestionState(sourceId, normalizedUrl, newHash));
    }

    log.debug(
        "Ingested {} chunks for {} ({})",
        chunkCount,
        normalizedUrl,
        existingState.isPresent() ? "updated" : "new");
    return new IngestionService.IngestResult(chunkCount, false, true);
  }

  private void enqueueDiscoveredLinks(
      UUID sourceId,
      CrawlResult result,
      String rootUrl,
      int parentDepth,
      CrawlScope scope,
      LinkedHashMap<String, Integer> queue,
      Set<String> visited) {
    int newUrlCount = 0;
    for (String link : result.internalLinks()) {
      String normalized = UrlNormalizer.normalize(link);
      if (!UrlNormalizer.isSameSite(rootUrl, normalized)) {
        continue;
      }
      if (visited.contains(normalized) || queue.containsKey(normalized)) {
        continue;
      }
      if (!UrlScopeFilter.isAllowed(normalized, rootUrl, scope)) {
        log.debug("Filtered URL by scope: {}", normalized);
        if (sourceId != null) {
          progressTracker.recordFiltered(sourceId, normalized);
        }
        continue;
      }
      queue.put(normalized, parentDepth + 1);
      newUrlCount++;
    }
    if (newUrlCount > 0 && sourceId != null) {
      progressTracker.updateTotal(sourceId, queue.size() + visited.size());
    }
  }

  /**
   * Remove chunks and ingestion state for pages that no longer exist in the crawled site. Only runs
   * when there are pre-existing ingestion states (incremental recrawl).
   */
  private void cleanupDeletedPages(UUID sourceId, Set<String> crawledUrls) {
    List<IngestionState> allStates = ingestionStateRepository.findAllBySourceId(sourceId);
    if (allStates.isEmpty()) {
      return;
    }

    List<String> orphanedUrls =
        allStates.stream()
            .map(IngestionState::getPageUrl)
            .filter(pageUrl -> !crawledUrls.contains(pageUrl))
            .toList();

    if (orphanedUrls.isEmpty()) {
      return;
    }

    log.info("Cleaning up {} deleted pages for source {}", orphanedUrls.size(), sourceId);
    for (String orphanUrl : orphanedUrls) {
      ingestionService.deleteChunksForUrl(orphanUrl);
    }
    ingestionStateRepository.deleteAllBySourceIdAndPageUrlNotIn(sourceId, crawledUrls);
  }
}
