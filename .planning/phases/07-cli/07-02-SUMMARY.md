---
phase: 07-cli
plan: 02
subsystem: api
tags: [spring-shell, cli, commands]

# Dependency graph
requires:
  - phase: 07-01
    provides: Spring Shell dependency and repository statistics methods
provides:
  - CLI commands: index, search, status, clear
  - Exception resolver for exit codes
  - Unit tests for CLI commands
affects: [07-03]

# Tech tracking
tech-stack:
  added: []
  patterns: [cli-command-pattern, exception-resolver-pattern]

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/api/cli/AlexandriaCommands.java
    - src/main/java/fr/kalifazzia/alexandria/api/cli/CliExceptionResolver.java
    - src/test/java/fr/kalifazzia/alexandria/api/cli/AlexandriaCommandsTest.java
  modified:
    - pom.xml

key-decisions:
  - "Commands use @Command annotation (Spring Shell 3.4 style, not deprecated @ShellMethod)"
  - "clear requires --force flag for safety (no interactive prompts)"
  - "search limits to 1-20 results (reasonable CLI bounds)"
  - "Exit codes: 0=success, 1=user error, 2=I/O error"
  - "Tests mock port interfaces only (Java 25 Mockito compatibility)"

patterns-established:
  - "CLI commands delegate to core services"
  - "Validation throws IllegalArgumentException for user errors"
  - "CommandExceptionResolver maps exceptions to exit codes"

# Metrics
duration: 5min
completed: 2026-01-20
---

# Phase 7 Plan 2: CLI Commands Summary

**Four CLI commands (index, search, status, clear) with proper exit codes and validation**

## Performance

- **Duration:** 5 min
- **Started:** 2026-01-20T20:06:37Z
- **Completed:** 2026-01-20T20:11:18Z
- **Tasks:** 3
- **Files created:** 3

## Accomplishments
- Implemented all four CLI commands: index, search, status, clear
- Added CliExceptionResolver for proper exit codes
- Created unit tests covering validation and output formatting

## Task Commits

Each task was committed atomically:

1. **Task 1: Create AlexandriaCommands** - `3e461ce` (feat)
2. **Task 2: Create CliExceptionResolver** - `03046f0` (feat)
3. **Task 3: Create unit tests** - `c93259c` (test)

## Files Created/Modified
- `src/main/java/fr/kalifazzia/alexandria/api/cli/AlexandriaCommands.java` - CLI commands (index, search, status, clear)
- `src/main/java/fr/kalifazzia/alexandria/api/cli/CliExceptionResolver.java` - Exit code mapping
- `src/test/java/fr/kalifazzia/alexandria/api/cli/AlexandriaCommandsTest.java` - Unit tests
- `pom.xml` - Added -XX:+EnableDynamicAgentLoading for Mockito

## Decisions Made
- Commands use @Command/@Option annotations (Spring Shell 3.4 style, not deprecated annotations)
- clear command requires --force flag for safety (no interactive prompts in non-interactive mode)
- search command limits results to 1-20 (reasonable CLI bounds)
- Exit codes follow convention: 0=success, 1=user error (IllegalArgumentException), 2=I/O error
- Unit tests only mock port interfaces, not concrete service classes (Java 25 Mockito compatibility)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Java 25 Mockito compatibility**
- **Found during:** Task 3 (Unit tests)
- **Issue:** Mockito cannot mock concrete classes (IngestionService, SearchService) on Java 25 due to ByteBuddy instrumentation restrictions
- **Fix:** Refactored tests to only mock port interfaces; tests with null services where not needed
- **Files modified:** src/test/java/.../AlexandriaCommandsTest.java, pom.xml
- **Verification:** All 8 tests pass
- **Committed in:** c93259c

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Test approach modified for Java 25 compatibility. Full service integration can be tested via integration tests.

## Issues Encountered
- SearchResult record has different constructor order than assumed in plan - adapted test code to match actual record structure

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All four CLI requirements (CLI-01 to CLI-04) satisfied
- Commands delegate to existing services
- Ready for 07-03: Integration testing and documentation

---
*Phase: 07-cli*
*Completed: 2026-01-20*
