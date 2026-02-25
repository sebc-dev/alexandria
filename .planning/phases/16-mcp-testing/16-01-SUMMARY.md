---
phase: 16-mcp-testing
plan: 01
subsystem: testing
tags: [mcp, snapshot-testing, spring-ai, jackson, gradle]

# Dependency graph
requires:
  - phase: none
    provides: "McpToolService with 7 @Tool-annotated methods already existed"
provides:
  - "MCP tool schema snapshot test catching unintended contract changes"
  - "Versioned reference JSON for 7 MCP tool schemas"
  - "McpSchemaSnapshotGenerator utility for annotation-based schema extraction"
  - "updateMcpSnapshot Gradle task for reference file regeneration"
affects: [16-mcp-testing, mcp]

# Tech tracking
tech-stack:
  added: []
  patterns: [snapshot-testing, schema-freeze-via-reference-file]

key-files:
  created:
    - src/test/java/dev/alexandria/mcp/McpSchemaSnapshotGenerator.java
    - src/test/java/dev/alexandria/mcp/McpToolSchemaSnapshotTest.java
    - src/test/resources/mcp/tools-schema.json
  modified:
    - build.gradle.kts

key-decisions:
  - "Mockito mocks for McpToolService constructor params since only @Tool annotation scanning occurs, not runtime invocation"
  - "Jackson ObjectMapper with ORDER_MAP_ENTRIES_BY_KEYS + INDENT_OUTPUT for deterministic, human-readable reference JSON"
  - "Structural JSON comparison via readTree() so whitespace formatting differences do not cause false failures"

patterns-established:
  - "Snapshot testing: freeze API contracts in versioned reference files, regenerate via Gradle task"
  - "Schema generator pattern: MethodToolCallbackProvider extracts @Tool annotations without Spring context"

requirements-completed: [MCPT-01]

# Metrics
duration: 6min
completed: 2026-02-23
---

# Phase 16 Plan 01: MCP Tool Schema Snapshot Test Summary

**Snapshot test freezing 7 MCP tool schemas (names, descriptions, inputSchema) against versioned reference JSON with readable diff on failure**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-23T21:56:07Z
- **Completed:** 2026-02-23T22:02:01Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- McpSchemaSnapshotGenerator extracts tool schemas from @Tool annotations via MethodToolCallbackProvider without Spring context
- Reference JSON file with all 7 MCP tools sorted alphabetically with sorted keys and 2-space indent
- 3 snapshot tests: tool count guard (7), name set verification, full schema structural comparison
- Readable diff messages showing added/removed/modified tools with field-level detail
- updateMcpSnapshot Gradle task for one-command reference file regeneration

## Task Commits

Each task was committed atomically:

1. **Task 1: Create schema generator utility and reference JSON file** - `2dc0441` (feat)
2. **Task 2: Create snapshot test and Gradle updateMcpSnapshot task** - `8460cb4` (feat)

**Plan metadata:** (pending docs commit)

## Files Created/Modified
- `src/test/java/dev/alexandria/mcp/McpSchemaSnapshotGenerator.java` - Utility extracting tool schemas from @Tool annotations via reflection
- `src/test/java/dev/alexandria/mcp/McpToolSchemaSnapshotTest.java` - 3 unit tests: count guard, name set, full schema comparison
- `src/test/resources/mcp/tools-schema.json` - Versioned reference file for 7 MCP tool schemas
- `build.gradle.kts` - Added updateMcpSnapshot JavaExec Gradle task

## Decisions Made
- Used Mockito mocks for all 7 McpToolService constructor parameters since MethodToolCallbackProvider only reads annotations, not runtime method invocations
- Jackson ObjectMapper configured with ORDER_MAP_ENTRIES_BY_KEYS and INDENT_OUTPUT for deterministic, human-readable JSON output
- Structural JSON comparison via Jackson readTree() rather than string equality to avoid whitespace sensitivity
- Test uses Set comparison for tool names and indexed-by-name maps for field-level diff generation

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Snapshot test runs as part of `./quality.sh test` (381 total tests, 3 new)
- Reference file versioned in Git for explicit change tracking via PR diffs
- Ready for Phase 16 Plan 02 (MCP round-trip integration tests)

## Self-Check: PASSED

- All 3 created files verified on disk
- Both task commits (2dc0441, 8460cb4) verified in git log
- 381 unit tests passing (3 new snapshot tests)

---
*Phase: 16-mcp-testing*
*Completed: 2026-02-23*
