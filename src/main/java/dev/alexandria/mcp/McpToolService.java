package dev.alexandria.mcp;

import dev.alexandria.crawl.CrawlProgress;
import dev.alexandria.crawl.CrawlProgressTracker;
import dev.alexandria.crawl.CrawlResult;
import dev.alexandria.crawl.CrawlScope;
import dev.alexandria.crawl.CrawlService;
import dev.alexandria.document.DocumentChunkRepository;
import dev.alexandria.ingestion.IngestionService;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import dev.alexandria.source.SourceStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP adapter exposing Alexandria capabilities as tool methods for Claude Code.
 *
 * <p>Each method is annotated with {@code @Tool} and registered via {@link McpToolConfig} as an MCP
 * tool callable through the stdio or SSE transport. Tool methods follow the structured error
 * pattern: all exceptions are caught and returned as descriptive error strings, never thrown.
 *
 * <p>Functional tools: {@code search_docs}, {@code list_sources}, {@code add_source}, {@code
 * remove_source}, {@code crawl_status}, {@code recrawl_source}, {@code index_statistics}.
 *
 * @see TokenBudgetTruncator
 * @see McpToolConfig
 */
@Service
public class McpToolService {

  private static final Logger log = LoggerFactory.getLogger(McpToolService.class);

  private final SearchService searchService;
  private final SourceRepository sourceRepository;
  private final TokenBudgetTruncator truncator;
  private final CrawlService crawlService;
  private final CrawlProgressTracker progressTracker;
  private final IngestionService ingestionService;
  private final DocumentChunkRepository documentChunkRepository;

  public McpToolService(
      SearchService searchService,
      SourceRepository sourceRepository,
      TokenBudgetTruncator truncator,
      CrawlService crawlService,
      CrawlProgressTracker progressTracker,
      IngestionService ingestionService,
      DocumentChunkRepository documentChunkRepository) {
    this.searchService = searchService;
    this.sourceRepository = sourceRepository;
    this.truncator = truncator;
    this.crawlService = crawlService;
    this.progressTracker = progressTracker;
    this.ingestionService = ingestionService;
    this.documentChunkRepository = documentChunkRepository;
  }

  /**
   * Searches indexed documentation by semantic query with token budget enforcement. Supports
   * metadata filters (source, section path, version, content type) and reranking score threshold
   * for precision control.
   */
  @Tool(
      name = "search_docs",
      description =
          "Search indexed documentation by semantic query. "
              + "Returns relevant excerpts with source URLs, section paths, and reranking scores for citation. "
              + "Supports metadata filters for narrowing results.")
  public String searchDocs(
      @ToolParam(description = "Search query text") @Nullable String query,
      @ToolParam(description = "Maximum number of results (1-50, default 10)")
          @Nullable Integer maxResults,
      @ToolParam(description = "Filter by source name", required = false) @Nullable String source,
      @ToolParam(
              description = "Filter by section path prefix, e.g. 'API Reference'",
              required = false)
          @Nullable String sectionPath,
      @ToolParam(description = "Filter by version tag, e.g. 'React 19'", required = false)
          @Nullable String version,
      @ToolParam(
              description = "Filter by content type: PROSE, CODE, or MIXED (all)",
              required = false)
          @Nullable String contentType,
      @ToolParam(
              description =
                  "Minimum reranking confidence score (0.0-1.0). Results below this threshold are excluded.",
              required = false)
          @Nullable Double minScore,
      @ToolParam(
              description =
                  "RRF k parameter for reciprocal rank fusion (default 60). Higher values reduce the impact of rank differences.",
              required = false)
          @Nullable Integer rrfK) {
    try {
      if (query == null || query.isBlank()) {
        return "Error: Query must not be empty. Provide a search query string.";
      }
      if (rrfK != null) {
        log.debug("rrfK={} accepted but applied at store-level configuration only", rrfK);
      }
      int max = clampMaxResults(maxResults);
      List<SearchResult> results =
          searchService.search(
              new SearchRequest(
                  query, max, source, sectionPath, version, contentType, minScore, rrfK));

      if (results.isEmpty()) {
        return buildEmptyResultMessage(query, source, sectionPath, version, contentType);
      }

      return truncator.truncate(results);
    } catch (Exception e) {
      return "Error searching documentation: " + e.getMessage();
    }
  }

  /** Lists all indexed documentation sources with status and statistics. */
  @Tool(
      name = "list_sources",
      description =
          "List all indexed documentation sources with status, last crawl time, and chunk count.")
  public String listSources() {
    try {
      List<Source> sources = sourceRepository.findAll();
      if (sources.isEmpty()) {
        return "No documentation sources configured. Use add_source to add one.";
      }

      StringBuilder sb = new StringBuilder();
      for (Source source : sources) {
        sb.append(
            String.format(
                "- %s (%s): %s | chunks: %s | last crawled: %s%n",
                source.getName(),
                source.getUrl(),
                source.getStatus(),
                formatChunkCount(source.getId()),
                source.getLastCrawledAt() != null
                    ? source.getLastCrawledAt().toString()
                    : "never"));
      }
      return sb.toString();
    } catch (Exception e) {
      return "Error listing sources: " + e.getMessage();
    }
  }

  /**
   * Adds a new documentation source URL for indexing with optional scope controls. Creates the
   * source, saves it, and triggers an async crawl via virtual thread.
   */
  @Tool(
      name = "add_source",
      description =
          "Add a documentation source URL for crawling and indexing. "
              + "Optionally specify scope controls: allow/block URL patterns (glob), max depth, max pages.")
  public String addSource(
      @ToolParam(description = "URL of the documentation site to index") @Nullable String url,
      @ToolParam(description = "Human-readable name for this source") String name,
      @ToolParam(
              description =
                  "Comma-separated glob patterns for allowed URL paths (e.g., '/docs/**,/api/**'). Empty = allow all.",
              required = false)
          @Nullable String allowPatterns,
      @ToolParam(
              description =
                  "Comma-separated glob patterns for blocked URL paths (e.g., '/archive/**,/old/**'). Empty = block none.",
              required = false)
          @Nullable String blockPatterns,
      @ToolParam(
              description = "Maximum crawl depth from root URL. Null = unlimited.",
              required = false)
          @Nullable Integer maxDepth,
      @ToolParam(description = "Maximum number of pages to crawl (default: 500).", required = false)
          @Nullable Integer maxPages,
      @ToolParam(description = "Manual llms.txt URL if auto-detection fails.", required = false)
          @Nullable String llmsTxtUrl,
      @ToolParam(
              description = "Version label for this source (e.g., 'React 19', '3.5')",
              required = false)
          @Nullable String version) {
    try {
      if (url == null || url.isBlank()) {
        return "Error: URL must not be empty. Provide a documentation site URL.";
      }
      Source source = new Source(url, name);
      source.setAllowPatterns(allowPatterns);
      source.setBlockPatterns(blockPatterns);
      source.setMaxDepth(maxDepth);
      source.setMaxPages(maxPages != null ? maxPages : 500);
      source.setLlmsTxtUrl(llmsTxtUrl);
      source.setVersion(version);
      source.setStatus(SourceStatus.CRAWLING);
      sourceRepository.save(source);

      UUID sourceId = source.getId();
      CrawlScope scope = CrawlScope.fromSource(source);

      dispatchCrawl(sourceId, url, scope, false);

      return "Source '%s' created (ID: %s). Crawl started. Check progress with crawl_status."
          .formatted(name, source.getId());
    } catch (Exception e) {
      return "Error adding source: " + e.getMessage();
    }
  }

  /**
   * Removes a documentation source and its indexed data by ID. Cancels any active crawl, counts
   * chunks before deletion, and returns feedback with chunk count.
   */
  @Tool(
      name = "remove_source",
      description = "Remove a documentation source and its indexed data by source ID.")
  public String removeSource(
      @ToolParam(description = "UUID of the source to remove") String sourceId) {
    try {
      UUID uuid = parseUuid(sourceId);
      Optional<Source> found = sourceRepository.findById(uuid);
      if (found.isEmpty()) {
        return "Error: Source %s not found.".formatted(sourceId);
      }
      Source source = found.get();

      // Cancel active crawl if running
      if (source.getStatus() == SourceStatus.CRAWLING
          || source.getStatus() == SourceStatus.UPDATING) {
        progressTracker.cancelCrawl(uuid);
        try {
          Thread.sleep(500); // Brief wait for crawl loop to observe cancellation
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt(); // Restore interrupt flag, continue to deletion
        }
      }

      // Count chunks before deletion for feedback
      long chunkCount = documentChunkRepository.countBySourceId(uuid);

      // Delete source (ON DELETE CASCADE handles chunks + ingestion_state)
      sourceRepository.deleteById(uuid);
      progressTracker.removeCrawl(uuid);

      return "Source '%s' removed (%,d chunks deleted).".formatted(source.getName(), chunkCount);
    } catch (IllegalArgumentException e) {
      return "Error: Invalid source ID format. Provide a valid UUID.";
    } catch (Exception e) {
      return "Error removing source: " + e.getMessage();
    }
  }

  /** Checks crawl status: real-time progress for active crawls, summary for completed sources. */
  @Tool(
      name = "crawl_status",
      description = "Check the crawl status and index statistics for a documentation source by ID.")
  public String crawlStatus(
      @ToolParam(description = "UUID of the source to check") String sourceId) {
    try {
      UUID uuid = parseUuid(sourceId);
      Optional<Source> found = sourceRepository.findById(uuid);
      if (found.isEmpty()) {
        return "Error: Source %s not found.".formatted(sourceId);
      }
      Source source = found.get();

      Optional<CrawlProgress> progress = progressTracker.getProgress(uuid);
      if (progress.isPresent()) {
        return formatActiveProgress(source, progress.get());
      }

      return formatCompletedSummary(source);
    } catch (IllegalArgumentException e) {
      return "Error: Invalid source ID format. Provide a valid UUID.";
    } catch (Exception e) {
      return "Error checking crawl status: " + e.getMessage();
    }
  }

  /**
   * Triggers a recrawl of an existing documentation source. Incremental by default (only
   * re-processes changed pages). Use full=true for complete re-processing. Scope overrides are
   * one-time and do not persist to the Source entity.
   */
  @Tool(
      name = "recrawl_source",
      description =
          "Trigger a recrawl of an existing documentation source. "
              + "Incremental by default (only re-processes changed pages). Use full=true to force complete re-processing.")
  public String recrawlSource(
      @ToolParam(description = "UUID of the source to re-crawl") String sourceId,
      @ToolParam(
              description = "Force full recrawl (re-process all pages, not just changed ones)",
              required = false)
          @Nullable Boolean full,
      @ToolParam(
              description = "Override allow patterns for this crawl run (comma-separated globs)",
              required = false)
          @Nullable String allowPatterns,
      @ToolParam(
              description = "Override block patterns for this crawl run (comma-separated globs)",
              required = false)
          @Nullable String blockPatterns,
      @ToolParam(description = "Override max depth for this crawl run", required = false)
          @Nullable Integer maxDepth,
      @ToolParam(description = "Override max pages for this crawl run", required = false)
          @Nullable Integer maxPages,
      @ToolParam(description = "Update the version label for this source", required = false)
          @Nullable String version,
      @ToolParam(description = "Update the display name for this source", required = false)
          @Nullable String name) {
    try {
      UUID uuid = parseUuid(sourceId);
      Optional<Source> found = sourceRepository.findById(uuid);
      if (found.isEmpty()) {
        return "Error: Source %s not found.".formatted(sourceId);
      }
      Source source = found.get();

      if (source.getStatus() == SourceStatus.CRAWLING
          || source.getStatus() == SourceStatus.UPDATING) {
        return "Error: Source '%s' is already being crawled (status: %s). Wait for it to finish."
            .formatted(source.getName(), source.getStatus());
      }

      boolean isFullRecrawl = Boolean.TRUE.equals(full);

      if (isFullRecrawl) {
        ingestionService.clearIngestionState(uuid);
      }

      String sourceUrl = Objects.requireNonNull(source.getUrl());

      // Update version before crawl so new chunks get the updated version
      if (version != null && !version.equals(source.getVersion())) {
        source.setVersion(version);
        sourceRepository.save(source);
        documentChunkRepository.updateVersionMetadata(sourceUrl, version);
      }

      // Update source name metadata if name changed
      if (name != null && !name.equals(source.getName())) {
        source.setName(name);
        sourceRepository.save(source);
        documentChunkRepository.updateSourceNameMetadata(sourceUrl, name);
      }

      CrawlScope scope =
          buildRecrawlScope(source, allowPatterns, blockPatterns, maxDepth, maxPages);

      source.setStatus(SourceStatus.UPDATING);
      sourceRepository.save(source);

      dispatchCrawl(uuid, sourceUrl, scope, isFullRecrawl);

      return "Recrawl started for '%s' (%s mode). Check progress with crawl_status."
          .formatted(source.getName(), isFullRecrawl ? "full" : "incremental");
    } catch (IllegalArgumentException e) {
      return "Error: Invalid source ID format. Provide a valid UUID.";
    } catch (Exception e) {
      return "Error requesting recrawl: " + e.getMessage();
    }
  }

  /** Dispatches an async crawl via virtual thread. Extracted for testability. */
  void dispatchCrawl(@Nullable UUID sourceId, String url, CrawlScope scope, boolean isFullRecrawl) {
    Thread.startVirtualThread(
        () -> {
          try {
            List<CrawlResult> results = crawlService.crawlSite(sourceId, url, scope);
            Source freshSource = sourceRepository.findById(sourceId).orElseThrow();
            freshSource.setStatus(SourceStatus.INDEXED);
            freshSource.setLastCrawledAt(Instant.now());
            freshSource.setChunkCount(results.size());
            sourceRepository.save(freshSource);
          } catch (Exception e) {
            log.error("Crawl failed for source {}: {}", sourceId, e.getMessage(), e);
            sourceRepository
                .findById(sourceId)
                .ifPresent(
                    freshSource -> {
                      freshSource.setStatus(SourceStatus.ERROR);
                      sourceRepository.save(freshSource);
                    });
          }
        });
  }

  /**
   * Returns global index statistics: total chunks, sources, storage size, embedding dimensions, and
   * last activity timestamp.
   */
  @Tool(
      name = "index_statistics",
      description =
          "View global index statistics: total chunks, sources, storage size, "
              + "embedding dimensions, and last activity timestamp.")
  public String indexStatistics() {
    try {
      long totalChunks = documentChunkRepository.countAllChunks();
      long totalSources = sourceRepository.count();
      long storageSizeBytes = documentChunkRepository.getStorageSizeBytes();
      Instant lastActivity = sourceRepository.findMaxLastCrawledAt();

      return """
                    Index Statistics:
                    - Total chunks: %,d
                    - Total sources: %d
                    - Embedding dimensions: 384 (bge-small-en-v1.5-q)
                    - Storage size: %s
                    - Last activity: %s"""
          .formatted(
              totalChunks,
              totalSources,
              formatBytes(storageSizeBytes),
              lastActivity != null ? lastActivity.toString() : "never");
    } catch (Exception e) {
      return "Error retrieving index statistics: " + e.getMessage();
    }
  }

  private String formatChunkCount(@Nullable UUID sourceId) {
    if (sourceId == null) {
      return "0";
    }
    List<Object[]> grouped = documentChunkRepository.countBySourceIdGroupedByContentType(sourceId);
    if (grouped.isEmpty()) {
      return "0";
    }
    long total = 0;
    List<String> parts = new ArrayList<>();
    for (Object[] row : grouped) {
      String contentType = (String) row[0];
      long count = ((Number) row[1]).longValue();
      total += count;
      parts.add(count + " " + contentType);
    }
    if (parts.size() == 1) {
      return String.valueOf(total);
    }
    return total + " (" + String.join(", ", parts) + ")";
  }

  private static String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024) return "%.1f KB".formatted(kb);
    double mb = kb / 1024.0;
    if (mb < 1024) return "%.1f MB".formatted(mb);
    double gb = mb / 1024.0;
    return "%.1f GB".formatted(gb);
  }

  private CrawlScope buildRecrawlScope(
      Source source,
      @Nullable String allowPatterns,
      @Nullable String blockPatterns,
      @Nullable Integer maxDepth,
      @Nullable Integer maxPages) {
    CrawlScope defaultScope = CrawlScope.fromSource(source);
    return new CrawlScope(
        allowPatterns != null ? parseCommaSeparated(allowPatterns) : defaultScope.allowPatterns(),
        blockPatterns != null ? parseCommaSeparated(blockPatterns) : defaultScope.blockPatterns(),
        maxDepth != null ? maxDepth : defaultScope.maxDepth(),
        maxPages != null ? maxPages : defaultScope.maxPages());
  }

  private String formatActiveProgress(Source source, CrawlProgress progress) {
    Duration elapsed = Duration.between(progress.startedAt(), Instant.now());

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Source: %s (%s)%n", source.getName(), source.getUrl()));
    sb.append(String.format("Status: %s%n", progress.status()));
    sb.append(
        String.format(
            "Progress: %d/%d pages crawled, %d skipped, %d errors%n",
            progress.pagesCrawled(),
            progress.pagesTotal(),
            progress.pagesSkipped(),
            progress.errors()));
    sb.append(String.format("Elapsed: %dm %ds%n", elapsed.toMinutes(), elapsed.toSecondsPart()));

    if (!progress.errorUrls().isEmpty()) {
      sb.append(String.format("Error URLs:%n"));
      for (String errorUrl : progress.errorUrls()) {
        sb.append(String.format("  - %s%n", errorUrl));
      }
    }

    if (!progress.filteredUrls().isEmpty()) {
      sb.append(String.format("Filtered URLs (excluded by scope):%n"));
      for (String filteredUrl : progress.filteredUrls()) {
        sb.append(String.format("  - %s%n", filteredUrl));
      }
    }

    return sb.toString();
  }

  private String formatCompletedSummary(Source source) {
    return String.format(
        "Source: %s (%s)%nStatus: %s%nChunks: %s%nLast crawled: %s",
        source.getName(),
        source.getUrl(),
        source.getStatus(),
        formatChunkCount(source.getId()),
        source.getLastCrawledAt() != null ? source.getLastCrawledAt().toString() : "never");
  }

  private int clampMaxResults(@Nullable Integer maxResults) {
    if (maxResults == null || maxResults < 1) {
      return 10;
    }
    return Math.min(maxResults, 50);
  }

  private UUID parseUuid(String sourceId) {
    return UUID.fromString(sourceId);
  }

  private String buildEmptyResultMessage(
      String query,
      @Nullable String source,
      @Nullable String sectionPath,
      @Nullable String version,
      @Nullable String contentType) {
    boolean hasFilters =
        source != null || sectionPath != null || version != null || contentType != null;

    if (!hasFilters) {
      return "No results found for query: " + query;
    }

    List<String> activeFilters = new ArrayList<>();
    if (source != null) {
      activeFilters.add("source='" + source + "'");
    }
    if (sectionPath != null) {
      activeFilters.add("sectionPath='" + sectionPath + "'");
    }
    if (version != null) {
      activeFilters.add("version='" + version + "'");
    }
    if (contentType != null) {
      activeFilters.add("contentType='" + contentType + "'");
    }

    List<String> availableVersions = documentChunkRepository.findDistinctVersions();
    List<String> availableSources = documentChunkRepository.findDistinctSourceNames();

    return "No results for query '%s' with filters [%s]. Available versions: %s. Available sources: %s."
        .formatted(query, String.join(", ", activeFilters), availableVersions, availableSources);
  }

  private static List<String> parseCommaSeparated(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }
}
