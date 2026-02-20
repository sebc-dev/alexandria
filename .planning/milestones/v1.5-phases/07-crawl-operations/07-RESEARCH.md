# Phase 7: Crawl Operations - Research

**Researched:** 2026-02-20
**Domain:** Crawl scope control, incremental crawling, progress tracking, llms.txt ingestion
**Confidence:** HIGH

## Summary

Phase 7 adds operational control to the existing crawling infrastructure. The codebase already has the foundational pieces: `CrawlService` manages BFS crawl orchestration, `IngestionService` handles chunking and embedding, `IngestionState` tracks per-page content hashes in the database, and `McpToolService` has stub implementations for `add_source`, `crawl_status`, and `recrawl_source`. The work is primarily about connecting these pieces with scope filtering, incremental change detection, progress tracking, and llms.txt discovery.

The key architectural insight is that Alexandria manages its own crawl loop (CrawlService does BFS page-by-page), calling Crawl4AI's REST API only for single-page rendering. This means max_depth, URL filtering, and scope controls are all implemented in Alexandria's Java code, not delegated to Crawl4AI. The incremental crawl system already has its database table (`ingestion_state`) and JPA entity (`IngestionState`) with `findBySourceIdAndPageUrl` -- the core infrastructure for SHA-256 change detection exists; it just needs to be wired into the crawl/ingestion flow.

**Primary recommendation:** Evolve CrawlService to accept scope parameters (patterns, maxDepth, maxPages), add a CrawlProgressTracker for real-time status, wire IngestionState into the ingestion pipeline for change detection, and add an LlmsTxtParser as a third URL discovery strategy in PageDiscoveryService.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Scope Controls
- Glob patterns for allowlist/blocklist (e.g., `/docs/**`, `!/docs/archive/**`)
- Scope defined at `add_source` time, overridable via `recrawl_source` params
- Max depth unlimited by default (no default cap)
- Max pages configurable per source
- URLs filtered by scope are logged (not silently skipped) -- available in `crawl_status` output

#### Incremental Crawl Behavior
- Content hash (SHA-256) per page determines change detection
- Unchanged pages (same hash): skip entirely -- no re-chunking, no re-embedding
- Changed pages (different hash): delete all old chunks for that page, re-chunk and re-embed from scratch (replace-all strategy)
- Deleted pages: detected by comparing crawled URLs against indexed URLs post-crawl -- chunks for missing pages are deleted
- No diff-based chunk comparison -- full replacement per page is simpler and reliable

#### Scheduling & Recrawl
- **No automatic/scheduled recrawls** -- only manual recrawl via `recrawl_source` MCP tool
- Recrawl is incremental by default, with a `full` flag to force complete re-processing
- Recrawl can override scope params (patterns, max_depth, max_pages) for that run, defaults to original source config

#### llms.txt Support
- Auto-detection: check `/llms.txt` and `/llms-full.txt` at domain root when adding a source
- User can manually provide llms.txt URL if auto-detection fails
- Discovery priority cascade: llms.txt > sitemap.xml > link crawl (each level supplements the previous)
- llms-full.txt: hybrid ingestion -- use as primary content source (ingest directly), then crawl any pages from llms.txt/sitemap/links not covered by llms-full.txt
- Handles incomplete llms-full.txt gracefully by filling gaps via crawling

### Claude's Discretion
- Progress reporting format and detail level in `crawl_status`
- Hash storage mechanism (column on existing table vs separate tracking table)
- Glob pattern matching library choice
- llms.txt parsing implementation details

### Deferred Ideas (OUT OF SCOPE)
- Automatic/scheduled recrawls (cron or interval-based) -- future phase or backlog
- Scope persistence updates (ability to permanently change a source's scope config after initial add)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CRWL-03 | User can control crawl scope (URL pattern allowlist/blocklist, max depth, max pages) | Glob pattern matching via `java.nio.file.PathMatcher` or Spring AntPathMatcher for URL path filtering; CrawlService already accepts maxPages; need to add depth tracking and pattern filtering |
| CRWL-06 | System performs incremental/delta crawls -- only re-processes pages whose content hash (SHA-256) has changed | `IngestionState` entity and `ingestion_state` table already exist with SHA-256 hash storage; `IngestionStateRepository.findBySourceIdAndPageUrl()` already defined; needs wiring into ingestion flow |
| CRWL-07 | User can schedule periodic recrawls (interval-based) | **DEFERRED per CONTEXT.md** -- only manual `recrawl_source` MCP tool in this phase |
| CRWL-09 | User can check crawl progress (pages crawled, pages remaining, errors) via MCP tool `crawl_status` | McpToolService.crawlStatus() stub exists; needs real-time progress tracking via in-memory state or DB |
| CRWL-10 | User can trigger a recrawl of an existing source via MCP tool `recrawl_source` | McpToolService.recrawlSource() stub exists; needs to invoke CrawlService with scope params and incremental logic |
| CRWL-11 | System can ingest llms.txt and llms-full.txt files as documentation sources and use them for page discovery | New LlmsTxtParser needed; integrates into PageDiscoveryService as highest-priority discovery method |
</phase_requirements>

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Java JDK | 21 | `java.security.MessageDigest` for SHA-256, `java.nio.file.PathMatcher` for glob matching | Zero-dependency -- JDK built-ins are sufficient for both hashing and glob matching |
| Spring Boot | 3.5.7 | Web framework, DI, RestClient for HTTP | Already the project foundation |
| LangChain4j | 1.11.0-beta19 | `EmbeddingStore.removeAll(Filter)` for chunk deletion by metadata | Already used; `removeAll(metadataKey("source_url").isEqualTo(...))` pattern proven in PreChunkedImporter |
| Spring Data JPA | (managed) | Repository queries for IngestionState, Source, DocumentChunk | Already used throughout |

### Supporting (no new dependencies needed)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.security.MessageDigest` | JDK 21 | SHA-256 content hashing | Hash page content for change detection |
| `java.nio.file.FileSystems.getDefault().getPathMatcher()` | JDK 21 | Glob pattern matching on URL paths | Filter URLs against allowlist/blocklist patterns |
| Spring `RestClient` | (managed) | HTTP GET for llms.txt/llms-full.txt fetching | Already configured via `RestClient.Builder` in SitemapParser |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| JDK `PathMatcher` for globs | hrakaroo/glob-library-java 0.9.0 | Faster (1.5x) but adds dependency for a non-critical path; URL filtering is not performance-sensitive |
| JDK `PathMatcher` for globs | Spring `AntPathMatcher` | Already on classpath; uses Ant-style patterns (`**`) instead of glob syntax, but less standard for user-facing patterns |
| JDK `MessageDigest` | Guava Hashing or Apache Commons DigestUtils | Convenience wrappers but add dependency for a 5-line utility method |

**No new dependencies required.** All capabilities are provided by JDK 21 built-ins and libraries already in the project.

## Architecture Patterns

### Current Package Structure (relevant to Phase 7)
```
dev.alexandria/
├── crawl/              # CrawlService, Crawl4AiClient, PageDiscoveryService, SitemapParser
├── ingestion/          # IngestionService, IngestionState, IngestionStateRepository
│   ├── chunking/       # MarkdownChunker, DocumentChunkData
│   └── prechunked/     # PreChunkedImporter
├── source/             # Source entity, SourceStatus enum, SourceRepository
├── document/           # DocumentChunk entity, DocumentChunkRepository
├── mcp/                # McpToolService (stubs for crawl_status, recrawl_source)
└── config/             # EmbeddingConfig, GlobalExceptionHandler
```

### Pattern 1: Scope Configuration as Records
**What:** Represent crawl scope as an immutable record that travels with crawl requests
**When to use:** Passing scope from MCP tools through to CrawlService

```java
// New record in crawl/ package
public record CrawlScope(
    List<String> allowPatterns,   // e.g. ["/docs/**"]
    List<String> blockPatterns,   // e.g. ["!/docs/archive/**"]
    Integer maxDepth,             // null = unlimited
    int maxPages                  // required, default from Source
) {
    public CrawlScope {
        allowPatterns = allowPatterns == null ? List.of() : List.copyOf(allowPatterns);
        blockPatterns = blockPatterns == null ? List.of() : List.copyOf(blockPatterns);
    }
}
```

### Pattern 2: URL Scope Filter (Pure Function)
**What:** Static utility that checks if a URL passes scope filters (allowlist, blocklist, same-site). Pure function, unit-testable without mocks.
**When to use:** Inside CrawlService loop before crawling each URL

```java
// In crawl/ package, similar to UrlNormalizer (static utility)
public final class UrlScopeFilter {

    public static boolean isAllowed(String url, String rootUrl, CrawlScope scope) {
        if (!UrlNormalizer.isSameSite(rootUrl, url)) return false;
        String path = extractPath(url);
        // Block patterns take priority (negation patterns starting with !)
        for (String pattern : scope.blockPatterns()) {
            if (matchGlob(path, pattern)) return false;
        }
        // If allowPatterns exist, URL must match at least one
        if (!scope.allowPatterns().isEmpty()) {
            return scope.allowPatterns().stream().anyMatch(p -> matchGlob(path, p));
        }
        return true;
    }

    private static boolean matchGlob(String path, String pattern) {
        PathMatcher matcher = FileSystems.getDefault()
            .getPathMatcher("glob:" + pattern);
        return matcher.matches(Path.of(path));
    }
}
```

### Pattern 3: Incremental Ingestion with Change Detection
**What:** Wrap IngestionService to compare content hashes before processing
**When to use:** During crawl-then-ingest flow

The existing `IngestionState` entity and repository already support this pattern. The flow:
1. Crawl page -> compute SHA-256 of markdown content
2. Look up `IngestionState` by (sourceId, pageUrl)
3. If hash matches -> skip (no re-chunking, no re-embedding)
4. If hash differs or no record -> delete old chunks for that page URL, re-chunk, re-embed, update hash
5. After crawl completes -> compare crawled URLs vs indexed URLs, delete orphaned chunks

### Pattern 4: CrawlProgress Tracking (In-Memory + DB)
**What:** Track crawl progress in a ConcurrentHashMap for real-time status, persist final state to Source entity
**When to use:** `crawl_status` MCP tool reads from in-memory tracker during crawl; reads from Source after completion

```java
// In crawl/ package
public record CrawlProgress(
    UUID sourceId,
    SourceStatus status,
    int pagesCrawled,
    int pagesSkipped,       // unchanged content hash
    int pagesTotal,         // discovered URLs count (may grow during BFS)
    int errors,
    List<String> errorUrls, // URLs that failed
    List<String> filteredUrls, // URLs excluded by scope
    Instant startedAt
) {}

// CrawlProgressTracker manages ConcurrentHashMap<UUID, CrawlProgress>
```

### Pattern 5: llms.txt Discovery Cascade
**What:** PageDiscoveryService tries llms.txt first, then sitemap.xml, then link crawl. Each level supplements previous.
**When to use:** When adding a source or recrawling

The current `PageDiscoveryService.discoverUrls()` tries sitemap first, falls back to link crawl. This extends to: llms.txt -> sitemap -> link crawl, with each level adding URLs not already discovered.

### Anti-Patterns to Avoid
- **Passing CrawlService the entire Source entity:** Pass only what's needed (URL, scope, sourceId). Keep the service testable.
- **Storing progress in the database for real-time updates:** Use in-memory `ConcurrentHashMap` for active crawl status (polled by MCP tool). DB updates only on crawl start/complete/error.
- **Implementing scheduled crawls "just in case":** Explicitly deferred. Only manual `recrawl_source`.
- **Building a generic pipeline framework for crawl stages:** Sequential method calls in a service class are sufficient (consistent with existing IngestionService pattern).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SHA-256 hashing | Custom hash implementation | `java.security.MessageDigest.getInstance("SHA-256")` | Standard, battle-tested, 5 lines of code |
| Glob pattern matching | Custom wildcard parser | `java.nio.file.FileSystems.getDefault().getPathMatcher("glob:...")` | JDK built-in, handles `*`, `**`, `?`, `[...]` |
| llms.txt Markdown parsing | Full Markdown parser for llms.txt | Simple line-by-line parsing with regex | llms.txt is trivially structured: H1, H2 headers, and `- [name](url)` links |
| Metadata-based chunk deletion | Custom SQL DELETE queries | `embeddingStore.removeAll(metadataKey("source_url").isEqualTo(url))` | Already proven in PreChunkedImporter |
| URL normalization | New normalizer | Existing `UrlNormalizer.normalize()` | Already handles fragments, tracking params, trailing slashes |

**Key insight:** The codebase already contains most building blocks. Phase 7 is primarily about orchestration and connecting existing pieces, not building new fundamental capabilities.

## Common Pitfalls

### Pitfall 1: PathMatcher and URL Paths
**What goes wrong:** `java.nio.file.PathMatcher` operates on `java.nio.file.Path` objects, which are filesystem paths. URL paths like `/docs/api/v2` work fine, but patterns with query strings or fragments will not match correctly.
**Why it happens:** PathMatcher was designed for file systems, not URLs. It handles `/` as separator correctly, but edge cases exist.
**How to avoid:** Extract only the path component from the URL before matching. Always normalize URLs first via `UrlNormalizer.normalize()` before scope filtering. Unit test edge cases: root path `/`, paths with dots, paths with encoded characters.
**Warning signs:** Patterns that "should match" failing in tests, or URLs with query strings not being filtered correctly.

### Pitfall 2: Race Conditions in Progress Tracking
**What goes wrong:** Crawl runs on virtual thread, MCP tool reads progress from another thread. Without thread safety, progress can show stale or inconsistent data.
**Why it happens:** Virtual threads in Spring Boot 3.5 mean multiple concurrent requests. `ConcurrentHashMap` is safe for individual operations but compound read-modify-write needs care.
**How to avoid:** Use `ConcurrentHashMap` with `AtomicReference<CrawlProgress>` or make `CrawlProgress` immutable and replace entirely via `ConcurrentHashMap.put()`. Avoid partial updates.
**Warning signs:** Tests that pass in isolation but fail under concurrent access, or progress showing 0 after crawl is complete.

### Pitfall 3: Orphaned Chunk Deletion During Incremental Crawl
**What goes wrong:** After a recrawl, pages that were removed from the site still have chunks in pgvector. Or worse, deletion happens mid-crawl before verifying all pages.
**Why it happens:** Deleted page detection requires comparing crawled URLs against indexed URLs, but this must happen after the entire crawl completes (not during).
**How to avoid:** Two-phase approach: (1) crawl all pages, tracking which URLs were visited, (2) after crawl completes, query `ingestion_state` for all page URLs belonging to this source, delete chunks for any URL not in the crawled set. User decision explicitly specifies this post-crawl approach.
**Warning signs:** Chunks remaining for deleted pages, or chunks being prematurely deleted during a partial crawl failure.

### Pitfall 4: llms-full.txt Hybrid Ingestion Complexity
**What goes wrong:** Ingesting llms-full.txt directly AND crawling individual pages creates duplicate chunks for pages covered by both.
**Why it happens:** llms-full.txt contains concatenated page content, but the system also crawls those same pages individually.
**How to avoid:** Track which page URLs are covered by llms-full.txt content. When crawling additional pages, skip any URL whose content is already ingested from llms-full.txt. The key is treating llms-full.txt as the primary source and only filling gaps via crawl.
**Warning signs:** Duplicate chunks in search results, or search quality degradation from duplicated content.

### Pitfall 5: EmbeddingStore.removeAll() Performance with Metadata Filters
**What goes wrong:** Deleting chunks by `source_url` metadata is O(n) on the entire embedding store if not indexed properly.
**Why it happens:** JSONB metadata queries without a proper index can be slow.
**How to avoid:** The `document_chunks` table already has an index on `source_id` (B-tree). For per-page deletion, use `source_url` metadata filter via LangChain4j's `removeAll(Filter)`. If performance becomes an issue, consider adding a GIN index on the metadata JSONB column. For now, the existing setup should be sufficient for typical documentation sites (hundreds to low thousands of pages).
**Warning signs:** Slow recrawl operations, especially the delete phase.

### Pitfall 6: Source Entity Schema Changes
**What goes wrong:** Adding new columns to the `sources` table (e.g., scope patterns, max_pages config) without a Flyway migration causes Hibernate validation failures or runtime errors.
**Why it happens:** `ddl-auto=none` means Hibernate does not create or modify schema -- Flyway is the source of truth.
**How to avoid:** Create a V2 Flyway migration for any schema changes. Add new columns with sensible defaults so existing data is not affected.
**Warning signs:** `SchemaManagementException` on startup, or null values in new columns for existing sources.

## Code Examples

### SHA-256 Content Hashing (JDK built-in)
```java
// Source: java.security.MessageDigest (JDK 21)
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ContentHasher {
    private ContentHasher() {}

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

### Glob Pattern Matching on URL Paths (JDK built-in)
```java
// Source: java.nio.file (JDK 21)
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

// Match URL path against glob pattern
String urlPath = "/docs/api/reference";
PathMatcher matcher = FileSystems.getDefault()
    .getPathMatcher("glob:/docs/**");
boolean matches = matcher.matches(Path.of(urlPath)); // true

// Blocklist pattern (convention: ! prefix stripped before matching)
PathMatcher blockMatcher = FileSystems.getDefault()
    .getPathMatcher("glob:/docs/archive/**");
boolean blocked = blockMatcher.matches(Path.of(urlPath)); // false
```

### LangChain4j Chunk Deletion by Metadata
```java
// Source: Already used in PreChunkedImporter.java
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

// Delete all chunks for a specific page URL
embeddingStore.removeAll(
    metadataKey("source_url").isEqualTo("https://docs.spring.io/boot/configuration")
);
```

### IngestionState Lookup for Change Detection
```java
// Source: Existing IngestionStateRepository.java
Optional<IngestionState> existing = ingestionStateRepository
    .findBySourceIdAndPageUrl(sourceId, normalizedUrl);

String newHash = ContentHasher.sha256(crawlResult.markdown());

if (existing.isPresent() && existing.get().getContentHash().equals(newHash)) {
    // Content unchanged -- skip re-processing
    return;
}

// Content changed or new page -- re-process
// 1. Delete old chunks for this page
embeddingStore.removeAll(metadataKey("source_url").isEqualTo(normalizedUrl));
// 2. Re-chunk and re-embed
ingestionService.ingestPage(crawlResult.markdown(), normalizedUrl, Instant.now().toString());
// 3. Update hash
if (existing.isPresent()) {
    existing.get().setContentHash(newHash);
    existing.get().setLastIngestedAt(Instant.now());
    ingestionStateRepository.save(existing.get());
} else {
    ingestionStateRepository.save(new IngestionState(sourceId, normalizedUrl, newHash));
}
```

### llms.txt Parsing (Line-by-Line)
```java
// Parse llms.txt format: extract URLs from markdown links
// Format: - [Title](https://url): Optional description
private static final Pattern LINK_PATTERN =
    Pattern.compile("^-\\s*\\[([^\\]]+)\\]\\(([^)]+)\\)");

public List<String> parseUrls(String llmsTxtContent) {
    return llmsTxtContent.lines()
        .map(LINK_PATTERN::matcher)
        .filter(Matcher::find)
        .map(m -> m.group(2))  // Extract URL
        .toList();
}
```

### CrawlService with Depth Tracking
```java
// Depth tracking during BFS crawl
// Current: queue is LinkedHashSet<String> (URL only)
// New: queue tracks (url, depth) pairs
record QueueEntry(String url, int depth) {}

// In the crawl loop:
while (!queue.isEmpty() && results.size() < scope.maxPages()) {
    QueueEntry entry = dequeue(queue);
    if (scope.maxDepth() != null && entry.depth() > scope.maxDepth()) {
        continue; // Skip URLs beyond max depth
    }
    // ... crawl and process ...
    if (followLinks) {
        for (String link : discoveredLinks) {
            queue.add(new QueueEntry(link, entry.depth() + 1));
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Crawl4AI deep_crawl_strategy for BFS | Alexandria manages own BFS loop, Crawl4AI for single-page rendering | Phase 3 (established pattern) | All scope/depth logic is in Java, not delegated to Crawl4AI |
| Full re-crawl every time | Incremental crawl with content hashing | Phase 7 (this phase) | Significantly reduces re-processing for large doc sites |
| No llms.txt support | llms.txt/llms-full.txt as discovery + content source | Phase 7 (this phase) | Aligns with emerging documentation standard |

**Key architectural fact:** Alexandria's CrawlService already does BFS link-crawling in Java, calling Crawl4AI's `/crawl` REST endpoint for single-page rendering only. This means max_depth, scope filtering, and page limits are all Alexandria-side concerns. Crawl4AI's deep_crawl_strategy (BFS/DFS) is a Python SDK feature that does not apply to the REST API usage.

## Existing Codebase Assets for Phase 7

### Already Built (ready to use)
| Asset | Location | What It Does | Phase 7 Usage |
|-------|----------|-------------|---------------|
| `IngestionState` entity | `ingestion/IngestionState.java` | Stores (sourceId, pageUrl, contentHash, lastIngestedAt) | Change detection for incremental crawl |
| `IngestionStateRepository` | `ingestion/IngestionStateRepository.java` | `findBySourceIdAndPageUrl()` | Look up existing hash for a page |
| `ingestion_state` table | `V1__initial_schema.sql` | DB table with UNIQUE(source_id, page_url) | Persist content hashes |
| `CrawlService.crawlSite()` | `crawl/CrawlService.java` | BFS crawl with maxPages limit | Extend with depth tracking and scope filtering |
| `PageDiscoveryService` | `crawl/PageDiscoveryService.java` | Sitemap-first, fallback to link crawl | Add llms.txt as highest-priority discovery |
| `SitemapParser` | `crawl/SitemapParser.java` | Fetches/parses sitemap.xml | Pattern to follow for llms.txt fetching |
| `UrlNormalizer` | `crawl/UrlNormalizer.java` | URL normalization and same-site check | Used before scope filtering |
| `McpToolService` stubs | `mcp/McpToolService.java` | `crawlStatus()`, `recrawlSource()`, `addSource()` stubs | Replace stubs with real implementations |
| `SourceStatus` enum | `source/SourceStatus.java` | PENDING, CRAWLING, INDEXED, UPDATING, ERROR | Already has UPDATING state for recrawls |
| `EmbeddingStore.removeAll(Filter)` | Used in `PreChunkedImporter.java` | Delete chunks by metadata filter | Delete old chunks for changed/deleted pages |
| `SourceBuilder` fixture | `fixture/SourceBuilder.java` | Test builder for Source entity | Extend with new fields (scope config) |

### Needs Building
| Component | Package | Purpose |
|-----------|---------|---------|
| `CrawlScope` record | `crawl/` | Immutable scope config (patterns, maxDepth, maxPages) |
| `UrlScopeFilter` | `crawl/` | Static utility to filter URLs against scope patterns |
| `ContentHasher` | `crawl/` or `ingestion/` | SHA-256 utility for content hashing |
| `LlmsTxtParser` | `crawl/` | Fetch and parse llms.txt/llms-full.txt |
| `CrawlProgressTracker` | `crawl/` | In-memory progress tracking (ConcurrentHashMap) |
| `CrawlOrchestrator` (or extend CrawlService) | `crawl/` | Orchestrate: discover -> crawl -> ingest -> cleanup |
| V2 Flyway migration | `db/migration/` | Add scope columns to sources table |
| Source entity updates | `source/Source.java` | New fields: allowPatterns, blockPatterns, maxPages, maxDepth |
| IngestionStateRepository extensions | `ingestion/` | `findAllBySourceId()`, `deleteBySourceIdAndPageUrlNotIn()` |
| DocumentChunkRepository extensions | `document/` | `deleteBySourceId()` or use EmbeddingStore filter |

## Discretion Recommendations

### Progress Reporting Format (Claude's Discretion)
**Recommendation:** Structured text format for `crawl_status` output:

```
Source: Spring Boot Docs (https://docs.spring.io/boot)
Status: CRAWLING
Progress: 42/120 pages crawled (35%)
Skipped: 15 pages (unchanged content)
Errors: 2 pages failed
  - https://docs.spring.io/boot/old-page: 404 Not Found
  - https://docs.spring.io/boot/broken: Connection timeout
Filtered: 5 URLs excluded by scope
  - https://docs.spring.io/boot/archive/v1: blocked by !/docs/archive/**
Started: 2026-02-20T10:30:00Z
Elapsed: 3m 45s
```

For completed crawls, show summary:
```
Source: Spring Boot Docs (https://docs.spring.io/boot)
Status: INDEXED
Last crawl: 2026-02-20T10:33:45Z
Pages: 103 crawled, 15 skipped (unchanged), 2 errors
Chunks: 1,247 total
```

### Hash Storage Mechanism (Claude's Discretion)
**Recommendation:** Use the existing `ingestion_state` table (separate tracking table). It already exists with the right schema: `(source_id, page_url, content_hash, last_ingested_at)` with a unique constraint on `(source_id, page_url)`. No need for a column on `document_chunks` -- the separate table cleanly separates concerns and is already built.

Additional repository methods needed:
- `List<IngestionState> findAllBySourceId(UUID sourceId)` -- for deleted page detection
- `void deleteAllBySourceId(UUID sourceId)` -- for full recrawl reset
- `void deleteAllBySourceIdAndPageUrlNotIn(UUID sourceId, Collection<String> pageUrls)` -- for orphaned page cleanup

### Glob Pattern Matching Library (Claude's Discretion)
**Recommendation:** Use JDK's `java.nio.file.PathMatcher` with `glob:` syntax. Zero dependencies, handles `*`, `**`, `?`, `[...]` correctly. The key insight: extract URL path before matching (strip scheme/host/query). This is a pure utility function, easily unit-tested.

Caveat: `PathMatcher` operates on `java.nio.file.Path` objects. URL paths work correctly as they use `/` as separator, same as POSIX file paths. Edge case to test: root path `/`, encoded characters like `%20`.

### llms.txt Parsing Implementation (Claude's Discretion)
**Recommendation:** Simple line-by-line parser with regex. The llms.txt format is intentionally simple Markdown:
- H1: project name (required)
- Blockquote: summary (optional)
- H2: section headers (optional)
- List items: `- [Title](URL): description` (the URLs we extract)

For llms-full.txt: Treat the entire file content as a single large Markdown document to be chunked and ingested directly (via MarkdownChunker). Track which URLs are covered by parsing any H2/H3 headers that match page titles to URLs from llms.txt.

**Parsing approach:** Regex for URL extraction (`Pattern.compile("^-\\s*\\[([^\\]]+)\\]\\(([^)]+)\\)")`), standard HTTP GET for fetching. Reuse `RestClient.Builder` pattern from `SitemapParser`.

## Open Questions

1. **Source schema extension for scope config**
   - What we know: Source entity needs new fields for allowPatterns, blockPatterns, maxPages, maxDepth, and optionally llmsTxtUrl
   - What's unclear: Whether to store patterns as comma-separated string, JSON array, or separate table
   - Recommendation: Store as JSON array in a single JSONB column (PostgreSQL native JSON support, no migration for each pattern change). Two columns: `scope_config JSONB` containing `{"allow": [...], "block": [...], "max_depth": N, "max_pages": N}`, or individual columns. Individual columns are simpler for JPA mapping.

2. **add_source triggering crawl orchestration**
   - What we know: add_source currently just saves a Source in PENDING status (stub)
   - What's unclear: Whether Phase 6 (Source Management) will have already connected add_source to CrawlService, or if Phase 7 must do this
   - Recommendation: Assume Phase 7 must handle the full orchestration: add_source -> discover pages -> crawl -> ingest -> update source status. If Phase 6 already handles basic crawl triggering, Phase 7 extends it with scope/incremental/llms.txt support.

3. **llms-full.txt page URL mapping**
   - What we know: llms-full.txt is a single concatenated Markdown file; we need to track which "pages" it covers
   - What's unclear: How to reliably map content sections in llms-full.txt back to individual page URLs
   - Recommendation: Use the URL list from llms.txt as the authoritative mapping. Pages listed in llms.txt are considered "covered" if their content appears in llms-full.txt. For ingestion, chunk the entire llms-full.txt as one document with the site root as source_url, and skip crawling pages whose URLs appear in the llms.txt listing. This is simpler than trying to split llms-full.txt into per-page segments.

4. **ConcurrentHashMap cleanup for progress tracking**
   - What we know: In-memory progress tracking needs cleanup after crawls complete
   - What's unclear: How long to keep completed crawl progress in memory
   - Recommendation: Keep completed crawl progress for 1 hour (configurable), then evict. This allows checking status shortly after completion. For historical data, the Source entity stores lastCrawledAt, chunkCount, and status.

## Sources

### Primary (HIGH confidence)
- Existing codebase: `CrawlService.java`, `IngestionState.java`, `McpToolService.java`, `V1__initial_schema.sql` -- direct code reading
- JDK 21 documentation: `java.security.MessageDigest`, `java.nio.file.PathMatcher`, `java.nio.file.FileSystems`
- LangChain4j PgVectorEmbeddingStore: `removeAll(Filter)` method verified in existing `PreChunkedImporter.java`

### Secondary (MEDIUM confidence)
- llms.txt specification: [llmstxt.org](https://llmstxt.org/) -- format is simple and stable
- Crawl4AI REST API: [docs.crawl4ai.com](https://docs.crawl4ai.com/api/parameters/) -- confirms deep crawling is Python SDK, REST is single-page
- SHA-256 in Java: [Baeldung guide](https://www.baeldung.com/sha-256-hashing-java) -- standard MessageDigest pattern

### Tertiary (LOW confidence)
- llms-full.txt specification: No formal spec exists at llmstxt.org. The "full" variant is a community convention (concatenated Markdown), not an official standard. Implementation should be flexible.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all JDK/existing libraries
- Architecture: HIGH -- extensions to existing patterns, not new architectural paradigms
- Pitfalls: HIGH -- identified from direct codebase analysis (known integration points)
- llms.txt parsing: MEDIUM -- format is simple but llms-full.txt hybrid ingestion has design decisions
- Progress tracking concurrency: MEDIUM -- virtual threads interaction needs careful testing

**Research date:** 2026-02-20
**Valid until:** 2026-03-20 (30 days -- stable domain, no fast-moving dependencies)
