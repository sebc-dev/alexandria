---
phase: 07-cli
plan: 01
subsystem: infra
tags: [spring-shell, repository, cli]

# Dependency graph
requires:
  - phase: 06-mcp-server
    provides: MCP tools using search/ingestion services
provides:
  - Spring Shell 3.4.1 dependency for CLI commands
  - DocumentRepository count(), findLastUpdated(), deleteAll() methods
  - ChunkRepository count(), deleteAll() methods
  - GraphRepository clearAll() method
  - CLI profile with non-interactive mode
affects: [07-02, 07-03]

# Tech tracking
tech-stack:
  added: [spring-shell-starter-3.4.1]
  patterns: [repository-statistics-methods, cli-profile-configuration]

key-files:
  created: []
  modified:
    - pom.xml
    - src/main/resources/application.yml
    - src/main/java/fr/kalifazzia/alexandria/core/port/DocumentRepository.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/ChunkRepository.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/GraphRepository.java
    - src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcDocumentRepository.java
    - src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcChunkRepository.java
    - src/main/java/fr/kalifazzia/alexandria/infra/persistence/AgeGraphRepository.java

key-decisions:
  - "Spring Shell 3.4.1 for CLI (latest stable, Spring Boot 3.4 compatible)"
  - "CLI profile disables interactive mode for single-command execution"

patterns-established:
  - "Repository statistics methods: count() returns long, findLastUpdated() returns Optional<Instant>"
  - "Clear methods: deleteAll() for tables, clearAll() for graph (Chunks before Documents)"

# Metrics
duration: 2min
completed: 2026-01-20
---

# Phase 7 Plan 1: CLI Foundation Summary

**Spring Shell 3.4.1 dependency with repository statistics/clear methods for CLI commands**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-20T20:02:42Z
- **Completed:** 2026-01-20T20:05:03Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- Added Spring Shell 3.4.1 dependency for CLI command infrastructure
- Extended three repository interfaces with statistics and clear methods
- Implemented all new methods in JDBC/AGE adapters
- Configured CLI profile for non-interactive mode

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Spring Shell dependency and CLI profile** - `de88a71` (chore)
2. **Task 2: Extend repository interfaces** - `d97fb8f` (feat)
3. **Task 3: Implement repository methods in adapters** - `82bf961` (feat)

## Files Created/Modified
- `pom.xml` - Added spring-shell-starter 3.4.1 dependency
- `src/main/resources/application.yml` - Added CLI profile with interactive mode disabled
- `src/main/java/fr/kalifazzia/alexandria/core/port/DocumentRepository.java` - Added count(), findLastUpdated(), deleteAll()
- `src/main/java/fr/kalifazzia/alexandria/core/port/ChunkRepository.java` - Added count(), deleteAll()
- `src/main/java/fr/kalifazzia/alexandria/core/port/GraphRepository.java` - Added clearAll()
- `src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcDocumentRepository.java` - Implemented new interface methods
- `src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcChunkRepository.java` - Implemented new interface methods
- `src/main/java/fr/kalifazzia/alexandria/infra/persistence/AgeGraphRepository.java` - Implemented clearAll() with DETACH DELETE

## Decisions Made
- Spring Shell 3.4.1 selected (latest stable, compatible with Spring Boot 3.4.1)
- CLI profile configured with shell.interactive.enabled=false for single-command mode
- Graph clearAll() deletes Chunks first, then Documents (respects vertex dependencies)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Repository infrastructure ready for CLI commands
- Spring Shell available for command registration
- Ready for 07-02: Implement CLI commands (status, index, search, clear)

---
*Phase: 07-cli*
*Completed: 2026-01-20*
