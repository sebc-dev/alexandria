# Phase 9: Source Management Completion - Research

**Researched:** 2026-02-20
**Domain:** Gap closure — JPA/JDBC batch updates, PostgreSQL system catalog queries, MCP tool wiring
**Confidence:** HIGH

## Summary

Phase 9 is gap closure work, not new capability development. The five gaps are well-understood defects discovered during the v1.5 milestone audit: (1) `source_id` FK is never populated on `document_chunks` because LangChain4j's `PgVectorEmbeddingStore.addAll()` bypasses JPA and does direct JDBC inserts without touching the `source_id` column; (2) `remove_source` cascade delete is broken as a consequence of gap 1; (3) `chunk_count` on Source is set to `results.size()` (page count, not chunk count); (4) `last_crawled_at` exists but is not exposed in MCP tool output formatted as ISO date; (5) `index_statistics` MCP tool does not exist.

All fixes are within the existing stack (Spring Data JPA, native SQL queries, McpToolService methods). No new libraries or infrastructure are needed. The critical complexity lies in the `source_id` FK population: since LangChain4j owns the INSERT, we must do a post-insert batch UPDATE using the UUIDs returned by `addAll()`. This requires adding a new native query to `DocumentChunkRepository` and passing `sourceId` through the ingestion pipeline.

**Primary recommendation:** Fix the source_id FK population first (it unblocks cascade delete and accurate chunk_count), then build the MCP tool changes on top of corrected data.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Staleness / Freshness indicator**: No stale/fresh label — just expose `last_crawled_at` as ISO date in MCP responses. `last_crawled_at` already exists on Source entity (TIMESTAMPTZ column in V1 migration). Expose in both `list_sources` and `crawl_status` MCP tool responses.
- **index_statistics MCP tool**: Global statistics only (no per-source breakdown). No input parameters — simple call returns all stats. Metrics: total chunks, total sources, embedding dimensions (384), estimated storage size, last activity timestamp. Tool name: `index_statistics`.
- **Cascade delete (remove_source)**: Suppression directe — no confirmation parameter. Feedback includes chunk count: "Source 'X' removed (N chunks deleted)." If crawl in progress: stop and proceed with deletion. Orphan chunks (source_id IS NULL): delete via Flyway migration.
- **chunk_count precision**: Real-time COUNT(*) query on document_chunks by source_id (not denormalized column). Total + breakdown by content_type: "chunks: 1247 (892 prose, 355 code)". Sources with zero chunks show `chunk_count: 0`.

### Claude's Discretion
- Exact SQL for storage size estimation (pg_relation_size vs pg_total_relation_size)
- How to stop an in-progress crawl during source deletion (virtual thread interruption strategy)
- Query optimization for COUNT with content_type grouping
- updateSourceNameMetadata() call placement during recrawl

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SRC-01 | User can add a documentation URL as a source via MCP tool `add_source` | Already functional from Phase 7 (McpToolService.addSource). Phase 9 formally verifies and closes this requirement. |
| SRC-02 | User can list all configured sources with status, last crawl time, and chunk count via MCP tool `list_sources` | Requires: (a) chunk_count fix to real COUNT(*), (b) content_type breakdown, (c) `last_crawled_at` ISO date exposure. See Architecture Patterns #1, #2, #5. |
| SRC-03 | User can remove a source and all its indexed data (cascade delete) via MCP tool `remove_source` | Requires: (a) source_id FK population fix, (b) orphan cleanup via Flyway V4, (c) enhanced remove_source with chunk count feedback, (d) stop active crawl. See Architecture Patterns #1, #3. |
| SRC-04 | User can see freshness status of each source (time since last crawl, staleness indicator) | Decision: expose `last_crawled_at` as ISO date (no qualitative label). See Architecture Pattern #5. |
| SRC-05 | User can view index statistics via MCP tool | New `index_statistics` tool: total chunks, total sources, embedding dimensions, storage size, last activity. See Architecture Patterns #4, Code Examples #4. |
</phase_requirements>

## Standard Stack

### Core (already in project — no additions)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | 3.5.7 (via Boot) | Native queries for batch UPDATE, COUNT, pg stats | Already used for DocumentChunkRepository, IngestionStateRepository |
| LangChain4j PgVector | 1.11.0-beta19 | EmbeddingStore.addAll() returns List<String> of generated UUIDs | Existing dependency; return value is the hook for source_id update |
| Spring AI MCP | 1.0.3 | @Tool annotation for new index_statistics tool | Existing MCP tooling pattern |
| Flyway | 11.8.2 | V4 migration for orphan chunk cleanup | Existing migration infrastructure |
| PostgreSQL 16 + pgvector 0.8 | Via Docker | pg_total_relation_size(), COUNT, JSONB queries | Existing infrastructure |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Testcontainers | 1.21.1 | Integration tests for native queries | Verify source_id update, cascade delete, stats queries |

### Alternatives Considered
None needed — this is gap closure within the existing stack.

## Architecture Patterns

### Pattern 1: Post-Insert source_id Batch Update (Fixes SRC-03 root cause)

**What:** LangChain4j's `PgVectorEmbeddingStore.addAll(embeddings, segments)` does direct JDBC INSERTs into `document_chunks` with columns `(embedding_id, embedding, text, metadata)`. It never touches the `source_id` column, leaving it NULL. The fix is a post-insert batch UPDATE.

**Why it works this way:** LangChain4j's default `MetadataStorageConfig` uses `COMBINED_JSON` mode with a single `metadata JSONB` column. Its INSERT statement (line 608-616 in PgVectorEmbeddingStore.java) includes only `embedding_id`, `embedding`, `text`, and metadata handler columns. The `source_id` column is custom to Alexandria's schema (added in V1 migration) and is invisible to LangChain4j.

**The fix:** `addAll()` returns `List<String>` of generated UUIDs. After each batch call, execute a native UPDATE:

```sql
UPDATE document_chunks SET source_id = :sourceId
WHERE embedding_id = ANY(:embeddingIds)
```

**Where to apply:** In `IngestionService.storeChunks()` — modify it to accept `sourceId` and call `DocumentChunkRepository.updateSourceIdBatch()` after each `addAll()` call.

**Propagation chain:** `sourceId` must flow from `CrawlService.ingestIncremental()` through `IngestionService.ingestPage()` to `storeChunks()`. Currently `ingestPage()` has no `sourceId` parameter — add a new overload.

**Confidence:** HIGH — verified by reading LangChain4j 1.11.0-beta19 source code directly. The `addAll(List<Embedding>, List<TextSegment>)` default method at EmbeddingStore interface line 67 calls `addAll(ids, embeddings, embedded)` and returns the `ids` list.

### Pattern 2: Real-time Chunk Count with Content Type Breakdown

**What:** Replace the denormalized `source.getChunkCount()` (which stores page count) with a real-time COUNT(*) query grouped by content_type from the JSONB metadata.

**SQL approach:**
```sql
SELECT
    COALESCE(metadata->>'content_type', 'unknown') AS content_type,
    COUNT(*) AS cnt
FROM document_chunks
WHERE source_id = :sourceId
GROUP BY metadata->>'content_type'
```

**Output format:** "chunks: 1247 (892 prose, 355 code)"

**Where to add:** New method on `DocumentChunkRepository` — `List<Object[]> countBySourceIdGroupedByContentType(UUID sourceId)`. A helper method in McpToolService (or a small DTO) formats the breakdown.

**Optimization note:** With the `idx_document_chunks_source_id` B-tree index already in place (V1 migration), the COUNT(*) + GROUP BY will use an index scan on source_id. For typical documentation sources (hundreds to low thousands of chunks), this is sub-millisecond.

**Confidence:** HIGH — the B-tree index exists, PostgreSQL JSONB extraction is well-understood.

### Pattern 3: Cascade Delete with Active Crawl Interruption

**What:** Enhanced `remove_source` that: (a) checks for active crawl and interrupts it, (b) counts chunks before deletion, (c) deletes source (ON DELETE CASCADE handles chunks + ingestion_state), (d) returns feedback message.

**Crawl interruption strategy:** The crawl runs on a virtual thread dispatched in `McpToolService.dispatchCrawl()`. To stop it:

1. **Option A — CrawlProgressTracker cancellation flag:** Add a `cancelCrawl(sourceId)` method that sets a status to CANCELLED. CrawlService checks `progressTracker.isCancelled(sourceId)` in its while-loop. This is clean and non-disruptive.

2. **Option B — Thread.interrupt() on virtual thread:** Store the virtual thread reference in CrawlProgressTracker. Call `thread.interrupt()` before deletion. Virtual threads handle interruption well, but this requires tracking the Thread object.

**Recommendation:** Option A (cancellation flag) — simpler, testable, no Thread reference management. CrawlService already checks `progressTracker.getProgress()` in a loop. Add `isCancelled()` check at the start of each loop iteration. When cancelled, the crawl loop breaks, the progress tracker is cleaned up, and removal proceeds.

**Chunk count feedback:** Before `sourceRepository.deleteById(uuid)`, query the real count:
```sql
SELECT COUNT(*) FROM document_chunks WHERE source_id = :sourceId
```
Then format: "Source 'Spring Boot' removed (1,247 chunks deleted)."

**Orphan cleanup:** Flyway V4 migration:
```sql
DELETE FROM document_chunks WHERE source_id IS NULL;
```
This handles any historical orphans from before the source_id fix.

**Confidence:** HIGH — ON DELETE CASCADE is already defined in V1 schema. The cancellation pattern is straightforward.

### Pattern 4: index_statistics MCP Tool

**What:** New `@Tool` method in McpToolService with no parameters, returning global index statistics.

**Queries needed (all in DocumentChunkRepository):**

1. **Total chunks:** `SELECT COUNT(*) FROM document_chunks`
2. **Total sources:** Use `sourceRepository.count()` (inherited from JpaRepository)
3. **Embedding dimensions:** Hardcoded 384 (from EmbeddingConfig, bge-small-en-v1.5-q)
4. **Estimated storage size:** Use `pg_total_relation_size` which includes the table, TOAST data, and all indexes (HNSW, GIN, B-tree):
   ```sql
   SELECT pg_total_relation_size('document_chunks')
   ```
   This returns bytes. Format as human-readable (e.g., "245.3 MB").

   **pg_relation_size vs pg_total_relation_size:**
   - `pg_relation_size('document_chunks')` — main table fork only (heap data)
   - `pg_total_relation_size('document_chunks')` — table + TOAST + ALL indexes
   - **Recommendation:** Use `pg_total_relation_size` — it gives the true disk footprint including the HNSW and GIN indexes, which are the bulk of the storage for an embedding store.

5. **Last activity timestamp:** `SELECT MAX(last_crawled_at) FROM sources`

**Output format:**
```
Index Statistics:
- Total chunks: 4,521
- Total sources: 3
- Embedding dimensions: 384 (bge-small-en-v1.5-q)
- Storage size: 245.3 MB
- Last activity: 2026-02-20T14:30:00Z
```

**MCP tool limit:** Currently MCP-05 specifies "maximum 6 tools". Adding `index_statistics` makes 7. The CONTEXT.md and ROADMAP.md explicitly define this as a Phase 9 requirement, so MCP-05's tool count is effectively updated to 7.

**Confidence:** HIGH — standard PostgreSQL catalog functions, trivially testable.

### Pattern 5: last_crawled_at ISO Date Exposure

**What:** Currently `last_crawled_at` is already in `listSources()` and `formatCompletedSummary()`, but it calls `source.getLastCrawledAt().toString()` which outputs `Instant.toString()` (already ISO-8601 format). The change is cosmetic — ensure the output is consistently formatted and explicitly present.

**Where to change:**
1. `McpToolService.listSources()` — already shows `last crawled: {date}` but uses `source.getChunkCount()` (wrong). Fix chunk count and keep the date.
2. `McpToolService.formatCompletedSummary()` (called by `crawlStatus()`) — already shows `Last crawled: {date}`.
3. `McpToolService.formatActiveProgress()` — does NOT show last_crawled_at (intentional — it's actively crawling).

**Note:** `Instant.toString()` in Java 21 produces ISO-8601 format like `2026-02-20T14:30:00Z`, which is exactly what the user wants. No formatting changes needed beyond ensuring the field appears in both tool responses.

**Confidence:** HIGH — trivial change, mostly verification that existing code already does what's needed.

### Pattern 6: updateSourceNameMetadata() Wiring

**What:** `DocumentChunkRepository.updateSourceNameMetadata()` exists but is never called. It should be called when a source's name changes during recrawl (similar to how `updateVersionMetadata()` is called in `recrawlSource()`).

**Where to add:** In `McpToolService.recrawlSource()`, alongside the existing version update logic (lines 262-266). If the source name changes, call `documentChunkRepository.updateSourceNameMetadata(source.getUrl(), newName)`.

**But wait:** The current `recrawlSource` tool has no `name` parameter. The source name only changes if it was auto-detected from the crawled site. Looking at the code, `sourceName` is read from `Source.getName()` in `CrawlService.crawlSite()` (line 86) and passed to `ingestionService.ingestPage()`. The name is set at `addSource()` time and never updated.

**Recommendation:** Add a `name` parameter to `recrawlSource` (like the existing `version` parameter). When provided and different from the current name, update the Source entity and batch-update chunk metadata. This mirrors the existing `version` update pattern exactly.

**Confidence:** HIGH — the repository method already exists and is tested.

### Anti-Patterns to Avoid
- **Denormalized chunk_count on Source entity:** The user decision explicitly says "real-time COUNT(*) query". Do NOT update `source.setChunkCount()` in `dispatchCrawl()` anymore — remove it or keep it as a rough estimate, but the MCP output must use the real query.
- **Transactional wrapping of LangChain4j calls:** `PgVectorEmbeddingStore` manages its own JDBC connections outside Spring's transaction manager. Do NOT annotate `storeChunks()` with `@Transactional` — it will not work. The post-insert UPDATE must be its own operation.
- **Filtering source_id by metadata instead of column:** Now that source_id will be populated, queries should use the `source_id` FK column (indexed by `idx_document_chunks_source_id`), NOT `metadata->>'source_name'`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Storage size estimation | Manual TOAST + index size calculation | `pg_total_relation_size('document_chunks')` | PostgreSQL built-in, includes all forks and indexes |
| Chunk count per source | Maintaining a denormalized counter | `COUNT(*) WHERE source_id = :id GROUP BY content_type` | Always accurate, fast with B-tree index |
| UUID batch collection | Custom UUID tracking code | Return value of `embeddingStore.addAll(embeddings, segments)` | LangChain4j already returns the generated UUIDs |
| Human-readable byte formatting | Manual division + suffix logic | `pg_size_pretty(pg_total_relation_size(...))` or simple Java helper | PostgreSQL has this built-in; or a 5-line Java method |

**Key insight:** Every gap in this phase has an existing mechanism (FK columns, SQL functions, repository methods, return values) that just needs to be wired correctly. No new capabilities need to be built.

## Common Pitfalls

### Pitfall 1: LangChain4j Transaction Isolation
**What goes wrong:** Attempting to wrap `embeddingStore.addAll()` + `documentChunkRepository.updateSourceId()` in a single `@Transactional` block. The LangChain4j store uses its own JDBC connection from the DataSource, not Spring's transaction-managed connection.
**Why it happens:** Natural instinct is to make insert + update atomic.
**How to avoid:** Accept eventual consistency. Call `addAll()`, capture returned IDs, then call the JPA native UPDATE in a separate transaction. If the UPDATE fails, the chunks exist with NULL source_id — same as current state, no worse.
**Warning signs:** `@Transactional` on IngestionService methods that call embeddingStore.

### Pitfall 2: dispatchCrawl Virtual Thread Not Completing Before Delete
**What goes wrong:** User calls `remove_source` while a crawl is actively inserting chunks. The DELETE CASCADE fires, then the crawl inserts new chunks with NULL source_id afterward.
**Why it happens:** Virtual thread is async, no coordination between MCP tools.
**How to avoid:** Implement the cancellation flag pattern. In `remove_source`: (1) cancel any active crawl via progressTracker, (2) wait briefly for the crawl loop to exit, (3) proceed with deletion. The brief wait is important — give the virtual thread time to check the cancellation flag.
**Warning signs:** Orphan chunks appearing after source deletion.

### Pitfall 3: COUNT(*) Performance on Large Tables
**What goes wrong:** Worrying about COUNT(*) performance and building a caching layer.
**Why it happens:** PostgreSQL COUNT(*) has a reputation for being slow on very large tables.
**How to avoid:** For Alexandria's scale (low thousands of chunks per source, single-digit sources), COUNT(*) with an indexed column is sub-millisecond. The B-tree index on source_id makes this an index-only scan. Do NOT over-engineer this.
**Warning signs:** Adding caching, denormalized counters, or async count updates.

### Pitfall 4: Flyway Migration Order
**What goes wrong:** Orphan cleanup migration (V4) runs before the source_id population fix is deployed.
**Why it happens:** Flyway runs migrations on startup, before any crawl-time fixes apply.
**How to avoid:** This is actually correct behavior — V4 cleans up historical orphans. New chunks ingested after the code fix will have source_id populated. The Flyway migration and the code fix work together but independently.
**Warning signs:** None — this is the intended approach per user decision.

### Pitfall 5: MCP Tool Count Exceeding Documented Limit
**What goes wrong:** Adding `index_statistics` makes 7 tools, violating MCP-05 ("maximum 6 tools").
**Why it happens:** MCP-05 was defined before Phase 9 was planned.
**How to avoid:** Update MCP-05 in REQUIREMENTS.md from 6 to 7 tools. The Phase 9 roadmap explicitly calls for this tool.
**Warning signs:** ArchUnit or documentation drift.

## Code Examples

### 1. Post-Insert source_id Batch Update (DocumentChunkRepository)

```java
// Source: Alexandria codebase pattern (native query like updateVersionMetadata)
@Modifying
@Transactional
@Query(value = """
        UPDATE document_chunks
        SET source_id = :sourceId
        WHERE embedding_id = ANY(CAST(:embeddingIds AS uuid[]))
        """, nativeQuery = true)
void updateSourceIdBatch(@Param("sourceId") UUID sourceId,
                         @Param("embeddingIds") String[] embeddingIds);
```

**Usage in IngestionService.storeChunks():**
```java
private void storeChunks(List<DocumentChunkData> chunks, UUID sourceId) {
    // ... existing batching logic ...
    List<String> ids = embeddingStore.addAll(embeddings, batch);
    if (sourceId != null) {
        documentChunkRepository.updateSourceIdBatch(
            sourceId,
            ids.toArray(String[]::new));
    }
}
```

### 2. Chunk Count with Content Type Breakdown (DocumentChunkRepository)

```java
@Query(value = """
        SELECT COALESCE(metadata->>'content_type', 'unknown') AS content_type,
               COUNT(*) AS cnt
        FROM document_chunks
        WHERE source_id = :sourceId
        GROUP BY metadata->>'content_type'
        """, nativeQuery = true)
List<Object[]> countBySourceIdGroupedByContentType(@Param("sourceId") UUID sourceId);
```

**Formatting helper:**
```java
// Returns "chunks: 1247 (892 prose, 355 code)"
private String formatChunkCount(UUID sourceId) {
    List<Object[]> rows = documentChunkRepository
        .countBySourceIdGroupedByContentType(sourceId);
    if (rows.isEmpty()) return "chunks: 0";

    long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
    String breakdown = rows.stream()
        .map(r -> "%d %s".formatted(((Number) r[1]).longValue(), r[0]))
        .collect(Collectors.joining(", "));
    return "chunks: %d (%s)".formatted(total, breakdown);
}
```

### 3. Total Chunk Count and Storage Size (DocumentChunkRepository)

```java
@Query(value = "SELECT COUNT(*) FROM document_chunks", nativeQuery = true)
long countAllChunks();

@Query(value = "SELECT pg_total_relation_size('document_chunks')", nativeQuery = true)
long getStorageSizeBytes();
```

### 4. index_statistics MCP Tool (McpToolService)

```java
@Tool(name = "index_statistics",
      description = "View global index statistics: total chunks, sources, storage size, "
                  + "embedding dimensions, and last activity timestamp.")
public String indexStatistics() {
    try {
        long totalChunks = documentChunkRepository.countAllChunks();
        long totalSources = sourceRepository.count();
        long storageSizeBytes = documentChunkRepository.getStorageSizeBytes();
        // Last activity = most recent last_crawled_at across all sources
        Instant lastActivity = sourceRepository.findMaxLastCrawledAt();

        return """
                Index Statistics:
                - Total chunks: %,d
                - Total sources: %d
                - Embedding dimensions: 384 (bge-small-en-v1.5-q)
                - Storage size: %s
                - Last activity: %s""".formatted(
                totalChunks, totalSources,
                formatBytes(storageSizeBytes),
                lastActivity != null ? lastActivity.toString() : "never");
    } catch (Exception e) {
        return "Error retrieving index statistics: " + e.getMessage();
    }
}
```

### 5. Cancellation Flag in CrawlProgressTracker

```java
// In CrawlProgressTracker — add cancellation support
private final Set<UUID> cancelledCrawls = ConcurrentHashMap.newKeySet();

public void cancelCrawl(UUID sourceId) {
    cancelledCrawls.add(sourceId);
}

public boolean isCancelled(UUID sourceId) {
    return cancelledCrawls.contains(sourceId);
}

// In completeCrawl/failCrawl — clean up
cancelledCrawls.remove(sourceId);
```

```java
// In CrawlService.crawlSite() while-loop — check cancellation
while (!queue.isEmpty() && results.size() < scope.maxPages()) {
    if (sourceId != null && progressTracker.isCancelled(sourceId)) {
        log.info("Crawl cancelled for source {}", sourceId);
        break;
    }
    // ... existing loop body ...
}
```

### 6. Enhanced remove_source (McpToolService)

```java
@Tool(name = "remove_source",
      description = "Remove a documentation source and all its indexed data by source ID.")
public String removeSource(@ToolParam(description = "UUID of the source to remove") String sourceId) {
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
            // Brief wait for crawl loop to observe cancellation
            Thread.sleep(500);
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
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| source.getChunkCount() (page count proxy) | COUNT(*) with content_type GROUP BY | Phase 9 | Accurate chunk counts with breakdown |
| source_id always NULL (LangChain4j bypass) | Post-insert batch UPDATE using returned UUIDs | Phase 9 | CASCADE delete works; FK integrity |
| No index_statistics tool | Global stats MCP tool | Phase 9 | Closes SRC-05 |
| sourceRepository.deleteById() (silent, no feedback) | Count-then-delete with formatted feedback | Phase 9 | User sees "1,247 chunks deleted" |

## Open Questions

1. **UUID Array Parameter Binding**
   - What we know: Spring Data JPA native queries with array parameters need careful typing. `String[]` mapped to PostgreSQL `uuid[]` requires either `CAST` in SQL or proper JDBC type registration.
   - What's unclear: Whether `@Param("embeddingIds") String[]` directly maps to `ANY(CAST(:embeddingIds AS uuid[]))` in Spring Data JPA native queries.
   - Recommendation: Test in integration test. Fallback: use `IN` clause with chunking for smaller batches, or use `@Query` with `entityManager.createNativeQuery()` for explicit type control.

2. **Thread.sleep(500) in remove_source**
   - What we know: The brief wait gives the virtual thread time to check the cancellation flag.
   - What's unclear: Whether 500ms is always sufficient. The crawl loop body (HTTP call to Crawl4AI) can take seconds per page.
   - Recommendation: The sleep is a best-effort optimization. Even without it, the worst case is a few more chunks inserted with proper source_id (since the FK fix also applies). After DELETE CASCADE, these chunks are cleaned up too. The sleep just reduces unnecessary work.

## Sources

### Primary (HIGH confidence)
- LangChain4j PgVectorEmbeddingStore.java (v1.11.0-beta19 sources, extracted from Gradle cache) — verified INSERT columns (line 608-616), verified addAll() return type
- LangChain4j EmbeddingStore.java interface (v1.11.0 core sources) — verified `addAll(List<Embedding>, List<TextSegment>)` returns `List<String>`
- Alexandria codebase (all files referenced are in the working tree at commit 8b9dc9a)
- PostgreSQL 16 documentation — `pg_total_relation_size()` catalog function

### Secondary (MEDIUM confidence)
- v1.5-MILESTONE-AUDIT.md — gap identification and evidence

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, all within existing stack
- Architecture: HIGH — all patterns verified against actual source code (LangChain4j internals, existing JPA queries)
- Pitfalls: HIGH — pitfalls are based on verified code analysis, not speculation
- Code examples: MEDIUM — UUID array parameter binding needs integration test validation (Open Question #1)

**Research date:** 2026-02-20
**Valid until:** 2026-03-20 (stable domain, no moving parts)
