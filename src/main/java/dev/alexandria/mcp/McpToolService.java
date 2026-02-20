package dev.alexandria.mcp;

import dev.alexandria.crawl.CrawlProgress;
import dev.alexandria.crawl.CrawlProgressTracker;
import dev.alexandria.crawl.CrawlResult;
import dev.alexandria.crawl.CrawlScope;
import dev.alexandria.crawl.CrawlService;
import dev.alexandria.ingestion.IngestionService;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import dev.alexandria.source.SourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP adapter exposing Alexandria capabilities as tool methods for Claude Code.
 *
 * <p>Each method is annotated with {@code @Tool} and registered via
 * {@link McpToolConfig} as an MCP tool callable through the stdio or SSE transport.
 * Tool methods follow the structured error pattern: all exceptions are caught and
 * returned as descriptive error strings, never thrown.
 *
 * <p>Functional tools: {@code search_docs}, {@code list_sources}, {@code add_source},
 * {@code remove_source}, {@code crawl_status}, {@code recrawl_source}.
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

    public McpToolService(SearchService searchService,
                          SourceRepository sourceRepository,
                          TokenBudgetTruncator truncator,
                          CrawlService crawlService,
                          CrawlProgressTracker progressTracker,
                          IngestionService ingestionService) {
        this.searchService = searchService;
        this.sourceRepository = sourceRepository;
        this.truncator = truncator;
        this.crawlService = crawlService;
        this.progressTracker = progressTracker;
        this.ingestionService = ingestionService;
    }

    /**
     * Searches indexed documentation by semantic query with token budget enforcement.
     */
    @Tool(name = "search_docs",
          description = "Search indexed documentation by semantic query. "
                      + "Returns relevant excerpts with source URLs and section paths for citation.")
    public String searchDocs(
            @ToolParam(description = "Search query text") String query,
            @ToolParam(description = "Maximum number of results (1-50, default 10)") Integer maxResults) {
        try {
            if (query == null || query.isBlank()) {
                return "Error: Query must not be empty. Provide a search query string.";
            }
            int max = clampMaxResults(maxResults);
            List<SearchResult> results = searchService.search(new SearchRequest(query, max));

            if (results.isEmpty()) {
                return "No results found for query: " + query;
            }

            return truncator.truncate(results);
        } catch (Exception e) {
            return "Error searching documentation: " + e.getMessage();
        }
    }

    /**
     * Lists all indexed documentation sources with status and statistics.
     */
    @Tool(name = "list_sources",
          description = "List all indexed documentation sources with status, last crawl time, and chunk count.")
    public String listSources() {
        try {
            List<Source> sources = sourceRepository.findAll();
            if (sources.isEmpty()) {
                return "No documentation sources configured. Use add_source to add one.";
            }

            StringBuilder sb = new StringBuilder();
            for (Source source : sources) {
                sb.append(String.format("- %s (%s): %s | %d chunks | last crawled: %s%n",
                        source.getName(),
                        source.getUrl(),
                        source.getStatus(),
                        source.getChunkCount(),
                        source.getLastCrawledAt() != null ? source.getLastCrawledAt().toString() : "never"));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error listing sources: " + e.getMessage();
        }
    }

    /**
     * Adds a new documentation source URL for indexing with optional scope controls.
     * Creates the source, saves it, and triggers an async crawl via virtual thread.
     */
    @Tool(name = "add_source",
          description = "Add a documentation source URL for crawling and indexing. "
                      + "Optionally specify scope controls: allow/block URL patterns (glob), max depth, max pages.")
    public String addSource(
            @ToolParam(description = "URL of the documentation site to index") String url,
            @ToolParam(description = "Human-readable name for this source") String name,
            @ToolParam(description = "Comma-separated glob patterns for allowed URL paths (e.g., '/docs/**,/api/**'). Empty = allow all.", required = false) String allowPatterns,
            @ToolParam(description = "Comma-separated glob patterns for blocked URL paths (e.g., '/archive/**,/old/**'). Empty = block none.", required = false) String blockPatterns,
            @ToolParam(description = "Maximum crawl depth from root URL. Null = unlimited.", required = false) Integer maxDepth,
            @ToolParam(description = "Maximum number of pages to crawl (default: 500).", required = false) Integer maxPages,
            @ToolParam(description = "Manual llms.txt URL if auto-detection fails.", required = false) String llmsTxtUrl) {
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
     * Removes a documentation source and its indexed data by ID.
     */
    @Tool(name = "remove_source",
          description = "Remove a documentation source and its indexed data by source ID.")
    public String removeSource(
            @ToolParam(description = "UUID of the source to remove") String sourceId) {
        try {
            UUID uuid = parseUuid(sourceId);
            sourceRepository.deleteById(uuid);
            return "Source %s removed.".formatted(sourceId);
        } catch (IllegalArgumentException e) {
            return "Error: Invalid source ID format. Provide a valid UUID.";
        } catch (Exception e) {
            return "Error removing source: " + e.getMessage();
        }
    }

    /**
     * Checks crawl status: real-time progress for active crawls, summary for completed sources.
     */
    @Tool(name = "crawl_status",
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
     * Triggers a recrawl of an existing documentation source.
     * Incremental by default (only re-processes changed pages). Use full=true for complete re-processing.
     * Scope overrides are one-time and do not persist to the Source entity.
     */
    @Tool(name = "recrawl_source",
          description = "Trigger a recrawl of an existing documentation source. "
                      + "Incremental by default (only re-processes changed pages). Use full=true to force complete re-processing.")
    public String recrawlSource(
            @ToolParam(description = "UUID of the source to re-crawl") String sourceId,
            @ToolParam(description = "Force full recrawl (re-process all pages, not just changed ones)", required = false) Boolean full,
            @ToolParam(description = "Override allow patterns for this crawl run (comma-separated globs)", required = false) String allowPatterns,
            @ToolParam(description = "Override block patterns for this crawl run (comma-separated globs)", required = false) String blockPatterns,
            @ToolParam(description = "Override max depth for this crawl run", required = false) Integer maxDepth,
            @ToolParam(description = "Override max pages for this crawl run", required = false) Integer maxPages) {
        try {
            UUID uuid = parseUuid(sourceId);
            Optional<Source> found = sourceRepository.findById(uuid);
            if (found.isEmpty()) {
                return "Error: Source %s not found.".formatted(sourceId);
            }
            Source source = found.get();

            if (source.getStatus() == SourceStatus.CRAWLING || source.getStatus() == SourceStatus.UPDATING) {
                return "Error: Source '%s' is already being crawled (status: %s). Wait for it to finish."
                        .formatted(source.getName(), source.getStatus());
            }

            boolean isFullRecrawl = Boolean.TRUE.equals(full);

            if (isFullRecrawl) {
                ingestionService.clearIngestionState(uuid);
            }

            CrawlScope scope = buildRecrawlScope(source, allowPatterns, blockPatterns, maxDepth, maxPages);

            source.setStatus(SourceStatus.UPDATING);
            sourceRepository.save(source);

            dispatchCrawl(uuid, source.getUrl(), scope, isFullRecrawl);

            return "Recrawl started for '%s' (%s mode). Check progress with crawl_status."
                    .formatted(source.getName(), isFullRecrawl ? "full" : "incremental");
        } catch (IllegalArgumentException e) {
            return "Error: Invalid source ID format. Provide a valid UUID.";
        } catch (Exception e) {
            return "Error requesting recrawl: " + e.getMessage();
        }
    }

    /**
     * Dispatches an async crawl via virtual thread. Extracted for testability.
     */
    void dispatchCrawl(UUID sourceId, String url, CrawlScope scope, boolean isFullRecrawl) {
        Thread.startVirtualThread(() -> {
            try {
                List<CrawlResult> results = crawlService.crawlSite(sourceId, url, scope);
                Source freshSource = sourceRepository.findById(sourceId).orElseThrow();
                freshSource.setStatus(SourceStatus.INDEXED);
                freshSource.setLastCrawledAt(Instant.now());
                freshSource.setChunkCount(results.size());
                sourceRepository.save(freshSource);
            } catch (Exception e) {
                log.error("Crawl failed for source {}: {}", sourceId, e.getMessage(), e);
                sourceRepository.findById(sourceId).ifPresent(freshSource -> {
                    freshSource.setStatus(SourceStatus.ERROR);
                    sourceRepository.save(freshSource);
                });
            }
        });
    }

    private CrawlScope buildRecrawlScope(Source source, String allowPatterns, String blockPatterns,
                                          Integer maxDepth, Integer maxPages) {
        CrawlScope defaultScope = CrawlScope.fromSource(source);
        return new CrawlScope(
                allowPatterns != null ? parseCommaSeparated(allowPatterns) : defaultScope.allowPatterns(),
                blockPatterns != null ? parseCommaSeparated(blockPatterns) : defaultScope.blockPatterns(),
                maxDepth != null ? maxDepth : defaultScope.maxDepth(),
                maxPages != null ? maxPages : defaultScope.maxPages()
        );
    }

    private String formatActiveProgress(Source source, CrawlProgress progress) {
        Duration elapsed = Duration.between(progress.startedAt(), Instant.now());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Source: %s (%s)%n", source.getName(), source.getUrl()));
        sb.append(String.format("Status: %s%n", progress.status()));
        sb.append(String.format("Progress: %d/%d pages crawled, %d skipped, %d errors%n",
                progress.pagesCrawled(), progress.pagesTotal(), progress.pagesSkipped(), progress.errors()));
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
        return String.format("Source: %s (%s)%nStatus: %s%nChunks: %d%nLast crawled: %s",
                source.getName(),
                source.getUrl(),
                source.getStatus(),
                source.getChunkCount(),
                source.getLastCrawledAt() != null ? source.getLastCrawledAt().toString() : "never");
    }

    private int clampMaxResults(Integer maxResults) {
        if (maxResults == null || maxResults < 1) {
            return 10;
        }
        return Math.min(maxResults, 50);
    }

    private UUID parseUuid(String sourceId) {
        return UUID.fromString(sourceId);
    }

    private static List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
