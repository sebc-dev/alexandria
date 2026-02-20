---
phase: 11-quality-security-tooling
plan: 02
subsystem: tooling
tags: [nullaway, jspecify, null-safety, error-prone, compile-time-checks]

# Dependency graph
requires:
  - phase: 11-01
    provides: Error Prone compile-time bug detection framework
provides:
  - NullAway compile-time null safety enforcement at ERROR level
  - JSpecify @NullMarked on all 10 packages under dev.alexandria
  - @Nullable annotations on all nullable parameters, returns, and fields
  - Build fails on null safety violations (NullAway ERROR severity)
affects: [11-03, 11-04]

# Tech tracking
tech-stack:
  added: [nullaway-0.12.6, jspecify-1.0.0]
  patterns: [jspecify-null-marked-packages, nullable-annotation-convention, nullaway-jspecify-mode]

key-files:
  created:
    - src/main/java/dev/alexandria/package-info.java
    - src/main/java/dev/alexandria/config/package-info.java
    - src/main/java/dev/alexandria/crawl/package-info.java
    - src/main/java/dev/alexandria/document/package-info.java
    - src/main/java/dev/alexandria/ingestion/package-info.java
    - src/main/java/dev/alexandria/ingestion/chunking/package-info.java
    - src/main/java/dev/alexandria/ingestion/prechunked/package-info.java
    - src/main/java/dev/alexandria/mcp/package-info.java
    - src/main/java/dev/alexandria/search/package-info.java
    - src/main/java/dev/alexandria/source/package-info.java
  modified:
    - build.gradle.kts
    - gradle/libs.versions.toml
    - src/main/java/**/*.java (27 files annotated)
    - src/test/java/**/*.java (13 test files updated)
    - src/integrationTest/java/**/*.java (3 integration test files updated)

key-decisions:
  - "NullAway at ERROR severity to fail builds on null safety violations, not just warn"
  - "JPA entity fields (id, createdAt, etc.) marked @Nullable since they are uninitialized before JPA save"
  - "Mockito test classes use @SuppressWarnings(NullAway.Init) for framework-initialized fields"
  - "SourceBuilder generates random UUID for test entities via reflection to simulate JPA save behavior"
  - "Third-party @Nullable returns handled with Objects.requireNonNull or Objects.requireNonNullElse"

patterns-established:
  - "@NullMarked on every package: new packages must include package-info.java with @NullMarked"
  - "@Nullable for nullable boundaries: all nullable params, returns, and fields use org.jspecify.annotations.Nullable"
  - "JPA entity nullable fields: JPA-managed fields use @Nullable on the field declaration (private @Nullable UUID id)"
  - "Test Mockito fields: use @SuppressWarnings(NullAway.Init) at class level for @Mock/@InjectMocks/@Captor fields"
  - "Null defense tests: use @SuppressWarnings(NullAway) on specific test methods that intentionally pass null"

requirements-completed: [QUAL-02]

# Metrics
duration: 21min
completed: 2026-02-20
---

# Phase 11 Plan 02: NullAway + JSpecify Summary

**NullAway 0.12.6 compile-time null safety at ERROR level with JSpecify @NullMarked on all 10 packages and @Nullable annotations across 43 production files**

## Performance

- **Duration:** 21 min
- **Started:** 2026-02-20T20:16:26Z
- **Completed:** 2026-02-20T20:37:26Z
- **Tasks:** 2
- **Files modified:** 53

## Accomplishments
- NullAway configured as Error Prone check in JSpecify mode with ERROR severity
- All 10 packages annotated with @NullMarked via package-info.java
- Every nullable parameter, return type, and field annotated with @Nullable across the entire codebase
- Zero NullAway errors across main, test, and integration test source sets
- All 285 unit tests pass with no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Configure NullAway and JSpecify dependencies** - `0bf4589` (chore)
2. **Task 2: Add @NullMarked and @Nullable annotations** - `4bd402f` (feat)

## Files Created/Modified
- `build.gradle.kts` - NullAway errorprone dependency, JSpecify implementation, NullAway JSpecify mode + ERROR severity
- `gradle/libs.versions.toml` - jspecify 1.0.0 version and library entries
- `src/main/java/**/package-info.java` (10 files) - @NullMarked annotations on all packages
- `src/main/java/**/*.java` (27 files) - @Nullable annotations on nullable boundaries
- `src/test/java/**/*.java` (13 files) - @SuppressWarnings for Mockito init, null-safety test fixes
- `src/integrationTest/java/**/*.java` (3 files) - Objects.requireNonNull for Docker API and JPA ID access

## Decisions Made
- **NullAway ERROR severity**: Configured NullAway at ERROR level (not default WARNING) so null safety violations fail the build. This matches the success criteria requirement.
- **JPA entity @Nullable fields**: JPA-managed fields (id, createdAt, updatedAt) are marked @Nullable because they are null before JPA persist. Getters return @Nullable accordingly.
- **SourceBuilder reflection-based ID**: Added UUID generation to SourceBuilder via reflection to simulate JPA's @GeneratedValue behavior in tests. Without this, source.getId() returns null in unit tests, breaking formatChunkCount and other methods.
- **@SuppressWarnings("NullAway.Init") for Mockito**: Test classes using @Mock/@InjectMocks/@Captor use class-level @SuppressWarnings("NullAway.Init") since Mockito initializes these fields via the JUnit extension, not constructors.
- **Defensive null methods accept @Nullable**: Methods like LlmsTxtParser.parseUrls(), LanguageDetector.detect(), MarkdownChunker.chunk() that internally handle null parameters are annotated @Nullable to be honest about their contracts.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SourceBuilder missing UUID for test entities**
- **Found during:** Task 2 (test verification)
- **Issue:** SourceBuilder creates Source entities without ID (set by JPA in production). After marking getId() as @Nullable, formatChunkCount's null guard returns "0" early, breaking 6 McpToolServiceTest tests with UnnecessaryStubbingException.
- **Fix:** Added UUID generation to SourceBuilder with reflection-based field setter. Default generates random UUID; tests can override with `.id(null)` if needed.
- **Files modified:** src/test/java/dev/alexandria/fixture/SourceBuilder.java
- **Verification:** All 285 tests pass
- **Committed in:** 4bd402f (Task 2 commit)

**2. [Rule 1 - Bug] CrawlService null dereference on getContentHash()**
- **Found during:** Task 2 (NullAway compilation)
- **Issue:** `existingState.get().getContentHash().equals(newHash)` would NPE if contentHash is null (marked @Nullable for JPA entity field)
- **Fix:** Reversed comparison to `newHash.equals(existingState.get().getContentHash())` so the known non-null value calls equals
- **Files modified:** src/main/java/dev/alexandria/crawl/CrawlService.java
- **Verification:** Compiles clean, tests pass
- **Committed in:** 4bd402f (Task 2 commit)

**3. [Rule 1 - Bug] RerankerService unsafe metadata access**
- **Found during:** Task 2 (NullAway compilation)
- **Issue:** `segment.metadata().getString("source_url")` returns @Nullable String, passed to SearchResult constructor expecting non-null
- **Fix:** Used `Objects.requireNonNullElse(value, "")` to provide safe default
- **Files modified:** src/main/java/dev/alexandria/search/RerankerService.java
- **Verification:** Compiles clean, tests pass
- **Committed in:** 4bd402f (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (3 bugs)
**Impact on plan:** All fixes were genuine null safety improvements discovered by NullAway. The SourceBuilder fix was necessary for test compatibility with the new null-safe contracts. No scope creep.

## Issues Encountered
- NullAway in JSpecify mode treats all unannotated parameters as @NonNull, which is the correct default-non-null behavior. This caught several latent null safety issues in the codebase (CrawlService hash comparison, RerankerService metadata access).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- NullAway + JSpecify foundation is in place for all future development
- New packages must include package-info.java with @NullMarked
- All nullable boundaries must be annotated with @Nullable
- Build will fail on any null safety violation
- Ready for Plan 03 (next quality/security tooling step)

## Self-Check: PASSED

All 10 package-info.java files verified present. Both commits verified in git log.

---
*Phase: 11-quality-security-tooling*
*Completed: 2026-02-20*
