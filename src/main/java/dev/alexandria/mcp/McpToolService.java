package dev.alexandria.mcp;

import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

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
 * <p>Currently functional tools: {@code search_docs}, {@code list_sources}.
 * Stub tools for Phase 6: {@code add_source}, {@code remove_source},
 * {@code crawl_status}, {@code recrawl_source}.
 *
 * @see TokenBudgetTruncator
 * @see McpToolConfig
 */
@Service
public class McpToolService {

    private final SearchService searchService;
    private final SourceRepository sourceRepository;
    private final TokenBudgetTruncator truncator;

    public McpToolService(SearchService searchService,
                          SourceRepository sourceRepository,
                          TokenBudgetTruncator truncator) {
        this.searchService = searchService;
        this.sourceRepository = sourceRepository;
        this.truncator = truncator;
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
     * Adds a new documentation source URL for indexing (stub for Phase 6 orchestration).
     */
    @Tool(name = "add_source",
          description = "Add a new documentation source URL for indexing. The source is created in PENDING status.")
    public String addSource(
            @ToolParam(description = "URL of the documentation site to index") String url,
            @ToolParam(description = "Human-readable name for this source") String name) {
        try {
            if (url == null || url.isBlank()) {
                return "Error: URL must not be empty. Provide a documentation site URL.";
            }
            Source source = new Source(url, name);
            sourceRepository.save(source);
            return "Source '%s' added in PENDING status. Crawling will be available in a future update."
                    .formatted(name);
        } catch (Exception e) {
            return "Error adding source: " + e.getMessage();
        }
    }

    /**
     * Removes a documentation source and its indexed data by ID (stub for Phase 6 cascade).
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
     * Checks crawl status and index statistics for a source (stub for Phase 6 progress tracking).
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
            return ("Source: %s | Status: %s | Chunks: %d | Last crawled: %s. "
                    + "Detailed crawl progress will be available in a future update.")
                    .formatted(
                            source.getName(),
                            source.getStatus(),
                            source.getChunkCount(),
                            source.getLastCrawledAt() != null ? source.getLastCrawledAt().toString() : "never");
        } catch (IllegalArgumentException e) {
            return "Error: Invalid source ID format. Provide a valid UUID.";
        } catch (Exception e) {
            return "Error checking crawl status: " + e.getMessage();
        }
    }

    /**
     * Requests a re-crawl of an existing source (stub for Phase 6 crawl orchestration).
     */
    @Tool(name = "recrawl_source",
          description = "Request a re-crawl of an existing documentation source to refresh its indexed content.")
    public String recrawlSource(
            @ToolParam(description = "UUID of the source to re-crawl") String sourceId) {
        try {
            UUID uuid = parseUuid(sourceId);
            Optional<Source> found = sourceRepository.findById(uuid);
            if (found.isEmpty()) {
                return "Error: Source %s not found.".formatted(sourceId);
            }
            Source source = found.get();
            return "Recrawl requested for source '%s'. Recrawl functionality will be available in a future update."
                    .formatted(source.getName());
        } catch (IllegalArgumentException e) {
            return "Error: Invalid source ID format. Provide a valid UUID.";
        } catch (Exception e) {
            return "Error requesting recrawl: " + e.getMessage();
        }
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
}
