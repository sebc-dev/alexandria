---
phase: 12-integration-test-coverage
plan: 01
subsystem: testing
tags: [jacoco, maven, coverage, integration-tests, failsafe]

# Dependency graph
requires:
  - phase: 11-jacoco-unit-coverage
    provides: JaCoCo plugin base configuration with prepare-agent and report goals
provides:
  - JaCoCo integration test coverage (prepare-agent-integration, report-integration)
  - Merged coverage report combining unit + integration test data
  - Three separate reports: jacoco/, jacoco-it/, jacoco-merged/
affects: [12-02-ci-integration, sonarcloud, coverage-badges]

# Tech tracking
tech-stack:
  added: []
  patterns: [JaCoCo merge goal for combined reports, argLine late evaluation for Failsafe]

key-files:
  created: []
  modified: [pom.xml]

key-decisions:
  - "Bind merge goal to post-integration-test phase (default is generate-resources)"
  - "Bind report-merged to verify phase for ordering after merge"
  - "Use same argLine pattern for Failsafe as Surefire"

patterns-established:
  - "Merged coverage: merge goal + report goal with custom dataFile"
  - "Failsafe argLine: @{argLine} -XX:+EnableDynamicAgentLoading"

# Metrics
duration: 2min
completed: 2026-01-23
---

# Phase 12 Plan 01: JaCoCo Integration Test Coverage Summary

**JaCoCo integration test coverage with prepare-agent-integration, report-integration, merge, and merged report goals**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-23T05:07:59Z
- **Completed:** 2026-01-23T05:10:24Z
- **Tasks:** 3
- **Files modified:** 1

## Accomplishments

- JaCoCo configured to generate jacoco.exec (unit), jacoco-it.exec (IT), and jacoco-merged.exec
- Three separate HTML reports: jacoco/, jacoco-it/, jacoco-merged/
- Failsafe plugin configured with argLine for JaCoCo instrumentation
- All coverage artifacts verified to generate on `mvn verify`

## Task Commits

Each task was committed atomically:

1. **Task 1: Add JaCoCo Integration Test Goals** - `dac3893` (feat)
2. **Task 2: Add argLine to Failsafe Plugin** - `84fff85` (feat)
3. **Task 3: Verify Integration Coverage Generation** - no commit (verification only)

## Files Created/Modified

- `pom.xml` - Added prepare-agent-integration, report-integration, merge-results, report-merged executions; added argLine to Failsafe plugin

## Decisions Made

1. **Bind merge goal to post-integration-test phase** - Default merge phase is generate-resources (before tests). Explicit binding ensures both exec files exist before merge.

2. **Bind report-merged to verify phase** - Ensures merge completes before merged report generation.

3. **Same argLine pattern for Failsafe** - Used identical `@{argLine} -XX:+EnableDynamicAgentLoading` pattern as Surefire for consistency and JaCoCo + Mockito compatibility.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Integration tests fail due to missing Apache AGE extension**

During verification, integration tests failed because the Testcontainers PostgreSQL image doesn't include the Apache AGE extension. This is existing tech debt documented in STATE.md.

However, the JaCoCo configuration works correctly:
- All three `.exec` files generated (jacoco.exec, jacoco-it.exec, jacoco-merged.exec)
- All three report directories contain HTML, XML, and CSV files
- Coverage data captured even for failing tests

The test failures are unrelated to this plan's objective (JaCoCo configuration).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- JaCoCo integration test coverage ready for CI integration (Plan 02)
- Three report types available for upload to GitHub Actions artifacts
- XML reports at `target/site/jacoco*/jacoco.xml` for SonarCloud integration

---
*Phase: 12-integration-test-coverage*
*Completed: 2026-01-23*
