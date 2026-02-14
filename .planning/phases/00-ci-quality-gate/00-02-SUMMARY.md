---
phase: 00-ci-quality-gate
plan: 02
subsystem: infra
tags: [quality-sh, github-actions, ci-pipeline, sonarcloud, jacoco, pitest, spotbugs, supply-chain-security]

# Dependency graph
requires:
  - phase: 00-ci-quality-gate
    provides: "Gradle 9.3.1 build with Spring Boot 4.0.2 and all quality gate plugins"
provides:
  - "quality.sh local quality gate runner with 8 subcommands for Claude Code"
  - "GitHub Actions CI pipeline with 6 jobs (build, test, spotbugs, mutation, sonarcloud) using shared build artifact"
  - "Non-blocking CI: only test failures block merge"
  - "HTML report artifacts downloadable from GitHub Actions"
  - "SonarCloud integration configured via env vars (requires SONAR_TOKEN secret)"
  - "GitHub Actions pinned to commit SHAs for supply chain security"
affects: [01-foundation, 02-core-search, 03-web-crawling, 04-data-pipeline, 05-mcp-server, 06-docker, 07-hardening, 08-polish]

# Tech tracking
tech-stack:
  added: [github-actions, gradle-actions-v5, upload-artifact-v4, gradle-9.3.1, spring-boot-4.0.2, pitest-1.21.0, testcontainers-2.0.3]
  patterns: [parallel-ci-jobs, non-blocking-quality-gates, local-quality-script, sha-pinned-actions, shared-build-artifact, env-var-config]

key-files:
  created:
    - quality.sh
    - .github/workflows/ci.yml
  modified:
    - build.gradle.kts
    - .gitignore

key-decisions:
  - "Pinned GitHub Actions to commit SHAs for supply chain security"
  - "Shared build job in CI to avoid redundant compilation across parallel jobs"
  - "SonarCloud config via environment variables (not hardcoded placeholders)"
  - "Added integration test subcommand and --with-integration flag to quality.sh"
  - "Restored io.spring.dependency-management plugin for IDE compatibility"
  - "Upgraded stack: Gradle 9.3.1, Spring Boot 4.0.2, PIT 1.21.0, Testcontainers 2.0.3"

patterns-established:
  - "quality.sh is the local quality interface for Claude Code -- run targeted checks, get concise summaries"
  - "CI workflow pattern: test job blocks merge, all other quality jobs are non-blocking (continue-on-error: true)"
  - "All CI jobs publish HTML reports as downloadable GitHub Actions artifacts"
  - "GitHub Actions pinned to commit SHAs with version comments (e.g., @abc123 # v4)"
  - "SonarCloud config reads SONAR_PROJECT_KEY and SONAR_ORGANIZATION from environment variables"
  - "CI workflow concurrency: cancel-in-progress for duplicate runs on same branch"

# Metrics
duration: ~20min (including post-checkpoint refinements and stack upgrade)
completed: 2026-02-14
---

# Phase 0 Plan 2: Local Quality Script and CI Pipeline Summary

**quality.sh local runner with 8 subcommands and GitHub Actions CI with shared build + 5 parallel analysis jobs -- SHA-pinned actions, env-var SonarCloud config, test failures block merge**

## Performance

- **Duration:** ~20 min (initial execution + post-checkpoint refinements + stack upgrade)
- **Started:** 2026-02-14T12:47:28Z
- **Completed:** 2026-02-14
- **Tasks:** 3 (2 auto + 1 human-verify checkpoint, approved)
- **Files created:** 2, **modified:** 3

## Accomplishments
- quality.sh with 8 subcommands (test, integration, mutation, spotbugs, arch, coverage, all, help), --package targeting, --with-integration flag
- GitHub Actions CI workflow with shared build job + 5 parallel analysis/test jobs, SHA-pinned for supply chain security
- Stack upgraded to latest: Gradle 9.3.1, Spring Boot 4.0.2, PIT 1.21.0, Testcontainers 2.0.3
- SonarCloud configured via environment variables (SONAR_PROJECT_KEY, SONAR_ORGANIZATION) instead of hardcoded placeholders
- All quality gates verified working with the upgraded stack
- User approved checkpoint verification

## Task Commits

Core task commits:

1. **Task 1: Create quality.sh local quality gate script** - `bf4c9ba` (feat)
2. **Task 2: Create GitHub Actions CI workflow with parallel jobs** - `c639f37` (feat)
3. **Task 3: Verify CI pipeline end-to-end** - checkpoint approved by user

Post-checkpoint refinements and improvements:

4. `8c54236` - fix(quality): propagate test exit codes and remove redundant arch run
5. `3cfa649` - fix(quality): wire --package flag for PIT mutation testing
6. `b82a5dd` - fix(build): parameterize SonarCloud config via environment variables
7. `d1dcc73` - fix(ci): add workflow concurrency control
8. `6fd2feb` - perf(ci): add shared build job to avoid redundant compilation
9. `e57a99a` - feat(quality): add integration test subcommand and --with-integration flag
10. `b5674f9` - fix(quality): eliminate CI redundancy and improve report isolation
11. `56c4111` - chore: upgrade stack to latest versions (Gradle 9.3.1, Spring Boot 4.0.2)
12. `5836fc4` - security(ci): pin GitHub Actions to commit SHAs
13. `e5c17de` - fix(build): restore dependency-management plugin for IDE compatibility

## Files Created/Modified
- `quality.sh` - Local quality gate runner with 8 subcommands for Claude Code (test, integration, mutation, spotbugs, arch, coverage, all, help)
- `.github/workflows/ci.yml` - GitHub Actions CI pipeline with shared build + 5 parallel jobs, SHA-pinned actions, concurrency control
- `build.gradle.kts` - Upgraded to Spring Boot 4.0.2, PIT 1.21.0, Testcontainers 2.0.3, env-var SonarCloud config, dependency-management plugin restored
- `.gitignore` - Fixed duplicate gradle wrapper exclusion

## Decisions Made
- **SHA-pinned GitHub Actions:** All actions pinned to commit SHAs with version comments for supply chain security (e.g., `actions/checkout@34e1148...# v4`)
- **Shared build job:** CI workflow uses a dedicated `build` job that compiles all sources once; downstream jobs download the build artifact to avoid redundant compilation
- **Environment variable SonarCloud config:** `sonar.projectKey` and `sonar.organization` read from `SONAR_PROJECT_KEY` and `SONAR_ORGANIZATION` env vars (set as GitHub repository variables), not hardcoded in build.gradle.kts
- **Integration test subcommand:** Added `./quality.sh integration` and `--with-integration` flag for `all` command; integration tests are opt-in since they require Docker
- **Dependency-management plugin for IDE:** Restored `io.spring.dependency-management` plugin (v1.1.7) instead of manual `platform(SpringBootPlugin.BOM_COORDINATES)` for better IDE resolution support
- **Stack upgrade to latest:** Gradle 9.3.1, Spring Boot 4.0.2, PIT plugin 1.19.0-rc.3 with PIT core 1.21.0, Testcontainers 2.0.3
- **Concurrency control:** CI workflow uses `cancel-in-progress: true` with `github.workflow-github.ref` grouping to cancel duplicate runs
- **SonarCloud depends on test job:** SonarCloud job uses `needs: [test]` to reuse coverage artifacts from the test job

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test exit codes not propagated in quality.sh**
- **Found during:** Post-checkpoint refinement
- **Issue:** quality.sh used `|| true` on test commands, swallowing failures
- **Fix:** Propagate exit codes from test and coverage commands; only use `|| true` for non-blocking gates (spotbugs, mutation)
- **Files modified:** quality.sh
- **Commit:** `8c54236`

**2. [Rule 1 - Bug] PIT --package flag not wired through Gradle property**
- **Found during:** Post-checkpoint refinement
- **Issue:** quality.sh passed `-Ppitest.targetClasses` but build.gradle.kts didn't read the property
- **Fix:** Added `providers.gradleProperty("pitest.targetClasses")` in build.gradle.kts PIT config
- **Files modified:** build.gradle.kts, quality.sh
- **Commit:** `3cfa649`

**3. [Rule 2 - Missing Critical] SonarCloud hardcoded placeholder keys**
- **Found during:** Post-checkpoint refinement
- **Issue:** SonarCloud config had hardcoded PLACEHOLDER_PROJECT_KEY and PLACEHOLDER_ORGANIZATION
- **Fix:** Changed to read from environment variables via `providers.environmentVariable()`
- **Files modified:** build.gradle.kts
- **Commit:** `b82a5dd`

**4. [Rule 2 - Missing Critical] No CI concurrency control**
- **Found during:** Post-checkpoint refinement
- **Issue:** Multiple pushes could run full CI in parallel wastefully
- **Fix:** Added concurrency group with cancel-in-progress
- **Files modified:** .github/workflows/ci.yml
- **Commit:** `d1dcc73`

**5. [Rule 2 - Missing Critical] Redundant compilation across CI jobs**
- **Found during:** Post-checkpoint refinement
- **Issue:** Each CI job compiled all sources independently
- **Fix:** Added shared build job; downstream jobs download build artifacts
- **Files modified:** .github/workflows/ci.yml
- **Commit:** `6fd2feb`

**6. [Rule 2 - Missing Critical] No integration test subcommand in quality.sh**
- **Found during:** Post-checkpoint refinement
- **Issue:** quality.sh had no way to run integration tests independently
- **Fix:** Added `integration` subcommand and `--with-integration` flag for `all`
- **Files modified:** quality.sh
- **Commit:** `e57a99a`

**7. [Rule 2 - Missing Critical] GitHub Actions supply chain security**
- **Found during:** Post-checkpoint refinement
- **Issue:** Actions referenced by tag (v4) are mutable; could be compromised
- **Fix:** Pinned all actions to immutable commit SHAs with version comments
- **Files modified:** .github/workflows/ci.yml
- **Commit:** `5836fc4`

**8. [Rule 1 - Bug] SpotBugs report path mismatch**
- **Found during:** Post-checkpoint refinement
- **Issue:** quality.sh looked for `spotbugs.xml` but report was named by task (`spotbugsMain.xml`)
- **Fix:** Updated quality.sh to read `spotbugsMain.xml`
- **Files modified:** quality.sh
- **Commit:** `b5674f9`

---

**Total deviations:** 8 auto-fixed (3 bugs, 5 missing critical)
**Impact on plan:** All fixes improve correctness, security, and CI efficiency. No scope creep -- all within plan 02 deliverable scope.

## Issues Encountered
None -- all improvements were identified and resolved during post-checkpoint refinement.

## User Setup Required

SonarCloud integration requires manual setup before the sonarcloud CI job will work:
1. Create SonarCloud account at https://sonarcloud.io (free for open-source)
2. Import the Alexandria GitHub repository into SonarCloud
3. Add `SONAR_TOKEN` as a GitHub repository secret (Settings -> Secrets -> Actions)
4. Add `SONAR_PROJECT_KEY` and `SONAR_ORGANIZATION` as GitHub repository variables (Settings -> Variables -> Actions)
5. Create custom Quality Gate in SonarCloud (non-blocking coverage per project philosophy)

## Next Phase Readiness
- Phase 0 quality infrastructure is complete
- Claude Code can use `./quality.sh <command>` for fast, targeted quality checks in all subsequent phases
- CI pipeline will activate on first push to main or PR targeting main
- Stack upgraded to latest: Gradle 9.3.1, Spring Boot 4.0.2, ready for Phase 1 foundation work
- SonarCloud activation is optional and independent of other CI jobs

## Self-Check: PASSED

All created files verified present (quality.sh, .github/workflows/ci.yml). All task commits verified in git log. Stack upgrade and SHA pinning commits verified. SUMMARY.md exists at expected path.

---
*Phase: 00-ci-quality-gate*
*Completed: 2026-02-14*
