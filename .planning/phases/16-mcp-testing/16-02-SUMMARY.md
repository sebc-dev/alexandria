---
phase: 16-mcp-testing
plan: 02
subsystem: testing
tags: [mcp, sse, integration-test, json-rpc, testcontainers, pgvector]

# Dependency graph
requires:
  - phase: 16-mcp-testing (plan 01)
    provides: McpToolService with 7 @Tool methods
provides:
  - Round-trip integration tests for all 7 MCP tools via SSE transport
  - SQL seed data with pgvector embeddings for MCP test scenarios
  - MCP SDK client dependency on integrationTest classpath
affects: [mcp, search, source, ingestion]

# Tech tracking
tech-stack:
  added: [io.modelcontextprotocol.sdk:mcp:0.10.0 (integrationTest)]
  patterns: [McpSyncClient round-trip testing over SSE, @Sql seed data with pre-computed embeddings]

key-files:
  created:
    - src/integrationTest/java/dev/alexandria/mcp/McpRoundTripIT.java
    - src/integrationTest/resources/mcp/seed-data.sql
  modified:
    - build.gradle.kts

key-decisions:
  - "Random normalized 384-dim vectors for seed data (tests MCP transport, not search quality)"
  - "PER_CLASS test lifecycle with shared McpSyncClient for efficiency"
  - "System.nanoTime() in add_source URLs to avoid unique constraint conflicts across test runs"

patterns-established:
  - "MCP round-trip test pattern: McpSyncClient -> SSE -> Spring Boot -> service -> DB -> response"
  - "SQL seed data with pre-computed vector embeddings for MCP integration tests"

requirements-completed: [MCPT-02]

# Metrics
duration: 6min
completed: 2026-02-23
---

# Phase 16 Plan 02: MCP Round-Trip Tests Summary

**20 integration tests exercising all 7 MCP tools via McpSyncClient over SSE transport with pgvector seed data**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-23T21:56:41Z
- **Completed:** 2026-02-23T22:02:54Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- All 7 MCP tools (search_docs, list_sources, add_source, remove_source, crawl_status, recrawl_source, index_statistics) verified via full JSON-RPC round-trip over SSE
- 20 tests covering happy paths and error cases for each tool
- SQL seed data with 3 sources and 6 document chunks with 384-dim normalized embeddings
- MCP SDK client dependency configured on integrationTest classpath

## Task Commits

Each task was committed atomically:

1. **Task 1: Create SQL seed data and add MCP client dependency** - `2dc0441` (feat)
2. **Task 2: Create McpRoundTripIT integration test class** - `67de918` (feat)

## Files Created/Modified
- `src/integrationTest/java/dev/alexandria/mcp/McpRoundTripIT.java` - 20 round-trip tests for all 7 MCP tools via McpSyncClient over SSE
- `src/integrationTest/resources/mcp/seed-data.sql` - Seed data with 3 sources, 6 chunks, 384-dim embeddings
- `build.gradle.kts` - Added MCP SDK 0.10.0 to integrationTest dependencies

## Decisions Made
- Used random normalized 384-dim vectors instead of real BGE embeddings in seed data; round-trip tests verify MCP transport contract, not search quality
- Used PER_CLASS test lifecycle with shared McpSyncClient (single SSE connection) for test efficiency
- Used System.nanoTime() suffix in add_source test URLs to avoid unique constraint conflicts when @Sql reseeds between tests
- Used fixed UUIDs (00000000-0000-0000-0000-00000000000X) for predictable assertions in seed data

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- MCP round-trip test infrastructure established for future MCP tool additions
- All integration tests pass (48 total including existing + 20 new MCP tests)

## Self-Check: PASSED

- All 3 files exist (McpRoundTripIT.java, seed-data.sql, 16-02-SUMMARY.md)
- Both task commits verified (2dc0441, 67de918)
- McpRoundTripIT.java: 341 lines (exceeds min 200)
- seed-data.sql: contains INSERT INTO sources
- build.gradle.kts: contains modelcontextprotocol dependency

---
*Phase: 16-mcp-testing*
*Completed: 2026-02-23*
