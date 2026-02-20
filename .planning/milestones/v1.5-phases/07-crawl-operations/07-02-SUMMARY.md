---
phase: 07-crawl-operations
plan: 02
subsystem: crawl
tags: [llms-txt, url-extraction, markdown-parsing, regex]

# Dependency graph
requires:
  - phase: 03-web-crawling
    provides: "crawl package structure, UrlNormalizer static utility pattern"
provides:
  - "LlmsTxtParser static utility for llms.txt/llms-full.txt parsing"
  - "LlmsTxtResult record for typed parse results"
  - "URL extraction from markdown link syntax"
  - "Content classification heuristic (link index vs full content)"
affects: [07-crawl-operations, crawl, ingestion]

# Tech tracking
tech-stack:
  added: []
  patterns: [static-utility-with-regex, record-as-result-type, link-index-heuristic]

key-files:
  created:
    - src/main/java/dev/alexandria/crawl/LlmsTxtParser.java
    - src/test/java/dev/alexandria/crawl/LlmsTxtParserTest.java
  modified: []

key-decisions:
  - "Link index threshold at 0.3 ratio for isLlmsTxtContent heuristic"
  - "Regex-based parsing: Pattern [text](url) handles both dash-prefixed and bare links"
  - "LlmsTxtResult as nested record in LlmsTxtParser (not separate file)"

patterns-established:
  - "Static utility with nested result record: LlmsTxtParser.parse() returns LlmsTxtResult"
  - "Content classification heuristic: ratio of link lines to content lines distinguishes file types"

requirements-completed: [CRWL-11]

# Metrics
duration: 5min
completed: 2026-02-20
---

# Phase 7 Plan 02: LlmsTxtParser Summary

**Static utility parsing llms.txt markdown link format with heuristic to distinguish link index from full content**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-20T09:30:59Z
- **Completed:** 2026-02-20T09:36:10Z
- **Tasks:** 1 (TDD: RED + GREEN)
- **Files modified:** 2

## Accomplishments
- LlmsTxtParser extracts URLs from standard llms.txt markdown link format (with/without dashes, with/without descriptions)
- Content classification heuristic distinguishes llms.txt (link index) from llms-full.txt (raw content)
- 11 comprehensive unit tests covering all edge cases (null, empty, no links, blockquotes, multiple sections)
- Follows established static utility pattern (same as UrlNormalizer, ContentHasher)

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: TDD failing tests** - `cba21d0` (test)
2. **Task 1 GREEN: LlmsTxtParser implementation** - `b44494b` (feat)

_TDD task: RED (failing tests) then GREEN (passing implementation). No REFACTOR needed._

## Files Created/Modified
- `src/main/java/dev/alexandria/crawl/LlmsTxtParser.java` - Static utility for llms.txt parsing: parseUrls(), isLlmsTxtContent(), parse() methods with nested LlmsTxtResult record
- `src/test/java/dev/alexandria/crawl/LlmsTxtParserTest.java` - 11 unit tests covering standard format, descriptions, headers, blockquotes, bare links, edge cases, and content classification

## Decisions Made
- Link index threshold at 0.3: content with >= 30% of non-blank, non-header lines containing markdown links is classified as llms.txt (link index). This correctly classifies the test cases (llms.txt has ~100% link lines, llms-full.txt has < 30%)
- Regex pattern `\[([^\]]+)\]\(([^)]+)\)` extracts markdown links from any position on a line, supporting both `- [Title](URL)` and `[Title](URL)` variants
- LlmsTxtResult as nested record in LlmsTxtParser rather than separate file, keeping the API self-contained
- parseUrls skips headers (#), blockquotes (>), and blank lines per the llms.txt specification

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- LlmsTxtParser ready for integration into PageDiscoveryService as highest-priority URL discovery strategy
- parse() method provides the classification needed for hybrid llms-full.txt ingestion (rawContent for direct chunking, urls for crawl gap-filling)

## Self-Check: PASSED

- [x] LlmsTxtParser.java exists (159 lines)
- [x] LlmsTxtParserTest.java exists (251 lines, exceeds 50-line minimum)
- [x] Commit cba21d0 exists (RED phase)
- [x] Commit b44494b exists (GREEN phase)
- [x] parseUrls method present
- [x] Pattern.compile used for regex
- [x] 142 total tests pass (BUILD SUCCESSFUL), 0 failures

---
*Phase: 07-crawl-operations*
*Completed: 2026-02-20*
