---
phase: 04-ingestion-pipeline
plan: 02
subsystem: ingestion
tags: [langchain4j, embedding-store, pgvector, bean-validation, batch-embedding, metadata-filter]

# Dependency graph
requires:
  - phase: 04-ingestion-pipeline
    plan: 01
    provides: "MarkdownChunker, DocumentChunkData, LanguageDetector"
  - phase: 02-core-search
    provides: "SearchService, EmbeddingStore<TextSegment> bean, pgvector schema"
  - phase: 03-web-crawling
    provides: "CrawlResult, CrawlSiteResult records"
provides:
  - "IngestionService: crawl result -> chunk -> embed -> store orchestrator"
  - "PreChunkedImporter: JSON import with Bean Validation and replacement semantics"
  - "PreChunkedRequest/PreChunkedChunk: DTOs with Jackson + Jakarta validation annotations"
  - "End-to-end pipeline: crawled Markdown becomes searchable via hybrid search"
affects: [05-mcp-server, 06-api-endpoints]

# Tech tracking
tech-stack:
  added: [spring-boot-starter-validation]
  patterns: [batch embedding with embedAll(), metadata-based deletion with removeAll(Filter), all-or-nothing validation]

key-files:
  created:
    - src/main/java/dev/alexandria/ingestion/IngestionService.java
    - src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedImporter.java
    - src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedRequest.java
    - src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedChunk.java
    - src/integrationTest/java/dev/alexandria/ingestion/IngestionServiceIT.java
    - src/integrationTest/java/dev/alexandria/ingestion/prechunked/PreChunkedImporterIT.java
  modified:
    - gradle/libs.versions.toml
    - build.gradle.kts

key-decisions:
  - "spring-boot-starter-validation added for Jakarta Bean Validation (not transitive from starter-web)"
  - "Batch embedding via embeddingModel.embedAll() for efficient processing (Pitfall 7)"
  - "Metadata-based deletion via removeAll(Filter) with metadataKey() for replacement import"
  - "@Transactional on importChunks for delete+insert atomicity"

patterns-established:
  - "TextSegment metadata: 5 snake_case keys (source_url, section_path, content_type, last_updated, language)"
  - "Language field only added to metadata when non-null (prose chunks have null language)"
  - "Pre-chunked import: all-or-nothing Bean Validation before any storage operations"
  - "Replacement semantics: removeAll by source_url filter before addAll"

# Metrics
duration: 4min
completed: 2026-02-18
---

# Phase 04 Plan 02: Ingestion Pipeline Orchestration Summary

**IngestionService wiring crawl-to-search pipeline via MarkdownChunker and batch embedding, plus PreChunkedImporter with Bean Validation and replacement semantics**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-18T05:20:35Z
- **Completed:** 2026-02-18T05:25:10Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- IngestionService orchestrates CrawlSiteResult -> MarkdownChunker -> embedAll -> addAll pipeline
- PreChunkedImporter validates JSON input (all-or-nothing), deletes existing chunks by source_url, stores replacements
- PreChunkedRequest/Chunk records with Jackson @JsonProperty and Jakarta @NotBlank/@Pattern annotations
- 8 integration tests verifying ingest-search roundtrip, metadata correctness, replacement semantics, and validation rejection against real pgvector via Testcontainers

## Task Commits

Each task was committed atomically:

1. **Task 1: IngestionService orchestrator and PreChunkedImporter with DTOs** - `f376baa` (feat)
2. **Task 2: Integration tests proving ingest-search roundtrip and pre-chunked import** - `880334c` (test)

## Files Created/Modified
- `gradle/libs.versions.toml` - Added spring-boot-starter-validation library entry
- `build.gradle.kts` - Added spring-boot-starter-validation implementation dependency
- `src/main/java/dev/alexandria/ingestion/IngestionService.java` - Pipeline orchestrator: crawl result -> chunk -> embed -> store (93 lines)
- `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedImporter.java` - JSON import with validation and replacement (83 lines)
- `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedRequest.java` - Outer JSON record: source_url + chunks list
- `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedChunk.java` - Inner JSON record: text + 5 metadata fields with Bean Validation
- `src/integrationTest/java/dev/alexandria/ingestion/IngestionServiceIT.java` - 4 integration tests for IngestionService (97 lines)
- `src/integrationTest/java/dev/alexandria/ingestion/prechunked/PreChunkedImporterIT.java` - 4 integration tests for PreChunkedImporter (140 lines)

## Decisions Made
- **spring-boot-starter-validation** added explicitly -- Jakarta Bean Validation is NOT transitive from spring-boot-starter-web (only javax.validation 1.1.0 from crawler-commons was on classpath)
- **Batch embedding** via `embeddingModel.embedAll(segments)` instead of one-by-one for efficient processing of multi-chunk pages (per Pitfall 7 in research)
- **@Transactional** on `importChunks()` for delete+insert atomicity in replacement import (per Pitfall 5 in research)
- **Metadata-based deletion** via `removeAll(metadataKey("source_url").isEqualTo(...))` for clean replacement semantics

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Full crawl-to-search pipeline operational: CrawlService -> IngestionService -> SearchService
- Pre-chunked import ready for AI-assisted chunking workflows
- Phase 4 complete -- all success criteria verified:
  - SC1 (heading chunking): MarkdownChunkerTest (Plan 01)
  - SC2 (configurable overlap): OVERRIDDEN by user decision -- heading path provides context
  - SC3 (metadata): IngestionServiceIT Test 2 (metadata roundtrip)
  - SC4 (code extraction): MarkdownChunkerTest + IngestionServiceIT Test 1
  - SC5 (pre-chunked import): PreChunkedImporterIT Tests 1-4
  - SC6 (end-to-end): IngestionServiceIT Test 1 (ingest -> search roundtrip with real pgvector)

## Self-Check: PASSED

All 7 files found. All 2 commits verified.

---
*Phase: 04-ingestion-pipeline*
*Completed: 2026-02-18*
