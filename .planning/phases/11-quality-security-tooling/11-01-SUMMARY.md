---
phase: 11-quality-security-tooling
plan: 01
subsystem: tooling
tags: [errorprone, spotless, google-java-format, pre-commit-hook, code-quality]

# Dependency graph
requires: []
provides:
  - Error Prone compile-time bug detection (ERROR-level fails build)
  - Spotless google-java-format enforcement on all Java sources
  - Pre-commit hook auto-formatting staged Java files
  - CI spotlessCheck step catching bypassed formatting
  - Centralized Error Prone suppression file at config/errorprone/bugpatterns.txt
affects: [11-02, 11-03, 11-04]

# Tech tracking
tech-stack:
  added: [error-prone-2.36.0, spotless-7.0.3, google-java-format, net.ltgt.errorprone-4.2.0]
  patterns: [centralized-errorprone-suppressions, pre-commit-auto-format, git-blame-ignore-revs]

key-files:
  created:
    - config/errorprone/bugpatterns.txt
    - .git-blame-ignore-revs
    - scripts/pre-commit
    - scripts/install-hooks.sh
  modified:
    - build.gradle.kts
    - gradle/libs.versions.toml
    - .github/workflows/ci.yml
    - src/main/java/**/*.java (77 files reformatted)

key-decisions:
  - "ratchetFrom disabled in git worktrees due to JGit incompatibility; full spotlessCheck runs instead"
  - "NullAway version placeholder added (0.12.6) but not configured; deferred to Plan 02"
  - "Pre-commit hook shared via scripts/install-hooks.sh for worktree-compatible installation"

patterns-established:
  - "Error Prone suppressions: add to config/errorprone/bugpatterns.txt, not inline @SuppressWarnings"
  - "Code formatting: google-java-format via Spotless, auto-applied by pre-commit hook"
  - "Formatting commits: record SHA in .git-blame-ignore-revs for clean git blame"

requirements-completed: [QUAL-01, QUAL-03]

# Metrics
duration: 7min
completed: 2026-02-20
---

# Phase 11 Plan 01: Error Prone + Spotless Summary

**Error Prone 2.36.0 compile-time bug detection and Spotless google-java-format enforcement with pre-commit hook and CI gate**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-20T20:05:50Z
- **Completed:** 2026-02-20T20:13:39Z
- **Tasks:** 2
- **Files modified:** 84

## Accomplishments
- Error Prone configured as compiler plugin catching ERROR-level bugs at compile time
- Spotless enforces google-java-format across all 77 Java source files
- Big-bang format applied with dedicated commit tracked in .git-blame-ignore-revs
- Pre-commit hook auto-formats staged Java files before every commit
- CI build job runs spotlessCheck to catch formatting bypassed via --no-verify
- Centralized Error Prone suppression file ready for false positive management

## Task Commits

Each task was committed atomically:

1. **Task 1: Configure Error Prone and Spotless Gradle plugins** - `e88ce0d` (chore)
2. **Task 2a: Big-bang google-java-format** - `5bc5487` (style - dedicated formatting commit)
3. **Task 2b: Pre-commit hook, .git-blame-ignore-revs, CI step** - `b4fce00` (feat)

## Files Created/Modified
- `build.gradle.kts` - Error Prone + Spotless plugin configuration with worktree-safe ratchetFrom
- `gradle/libs.versions.toml` - Version catalog entries for errorprone, spotless, nullaway
- `config/errorprone/bugpatterns.txt` - Centralized suppression file (empty, no false positives found)
- `.git-blame-ignore-revs` - Git blame skip list for formatting commit 5bc5487
- `scripts/pre-commit` - Pre-commit hook running spotlessApply
- `scripts/install-hooks.sh` - Worktree-compatible hook installer
- `.github/workflows/ci.yml` - Added spotlessCheck step after compilation
- `src/**/*.java` (77 files) - Reformatted with google-java-format

## Decisions Made
- **ratchetFrom workaround**: JGit 6.10 cannot resolve `.git` files in git worktrees, so ratchetFrom is conditionally enabled only when `.git` is a directory. In worktrees, Spotless checks all files (acceptable since all code is formatted after big-bang). CI (standard checkout) will use ratchetFrom normally.
- **NullAway version only**: Added version 0.12.6 to version catalog as placeholder. Library entry and build configuration deferred to Plan 02.
- **Hook installation**: Created `scripts/install-hooks.sh` using `git rev-parse --git-path hooks` for worktree compatibility instead of hardcoding `.git/hooks/`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing import for Error Prone Gradle DSL**
- **Found during:** Task 1 (Error Prone configuration)
- **Issue:** `options.errorprone {}` DSL requires `import net.ltgt.gradle.errorprone.errorprone` which was not in the plan
- **Fix:** Added the import at the top of build.gradle.kts
- **Files modified:** build.gradle.kts
- **Verification:** `./gradlew classes` passes
- **Committed in:** e88ce0d (Task 1 commit)

**2. [Rule 3 - Blocking] JGit worktree incompatibility with ratchetFrom**
- **Found during:** Task 1 (Spotless configuration)
- **Issue:** JGit 6.10 bundled with Spotless 7.0.3 cannot find git repository when `.git` is a worktree file (not a directory). `ratchetFrom("origin/master")` fails with "Cannot find git repository in any parent directory"
- **Fix:** Conditional ratchetFrom: enabled only when `.git` is a directory (standard repo), skipped in worktrees. Full spotlessCheck still runs on all files.
- **Files modified:** build.gradle.kts
- **Verification:** `./gradlew spotlessCheck` passes in worktree environment
- **Committed in:** e88ce0d (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes necessary to make the build work. No scope creep. ratchetFrom limitation is cosmetic (optimization only); all enforcement mechanisms work correctly.

## Issues Encountered
- Error Prone found only WARNING-level issues (StringCaseLocaleUsage, NonApiType, UnusedVariable, HidingField, CanonicalDuration) across all source sets. No ERROR-level violations, so no source fixes or suppression entries were needed. Warnings are informational and do not block the build.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Error Prone + Spotless foundation is in place for Plan 02 (NullAway configuration)
- All existing code is formatted and compiles cleanly with Error Prone
- Pre-commit hook ensures ongoing formatting compliance
- CI gate prevents unformatted code from merging

## Self-Check: PASSED

All 8 files verified present. All 3 commits verified in git log.

---
*Phase: 11-quality-security-tooling*
*Completed: 2026-02-20*
