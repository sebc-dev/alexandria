---
phase: 04-ingestion-pipeline
plan: 01
subsystem: ingestion
tags: [commonmark-java, markdown, chunking, ast, language-detection, tdd]

# Dependency graph
requires:
  - phase: 01-foundation-infrastructure
    provides: "Spring Boot project structure, Gradle build, Java 21 toolchain"
provides:
  - "MarkdownChunker: AST-based heading splitter with code extraction"
  - "DocumentChunkData: record with chunk text + 5 metadata fields"
  - "LanguageDetector: keyword heuristic for 10 programming languages"
affects: [04-02-ingestion-pipeline, 05-mcp-server]

# Tech tracking
tech-stack:
  added: [commonmark-java 0.27.1, commonmark-ext-gfm-tables 0.27.1]
  patterns: [AST-based Markdown chunking, source span text extraction, keyword-based language detection]

key-files:
  created:
    - src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java
    - src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java
    - src/main/java/dev/alexandria/ingestion/chunking/LanguageDetector.java
    - src/test/java/dev/alexandria/ingestion/chunking/MarkdownChunkerTest.java
    - src/test/java/dev/alexandria/ingestion/chunking/LanguageDetectorTest.java
  modified:
    - gradle/libs.versions.toml
    - build.gradle.kts

key-decisions:
  - "commonmark-java 0.27.1 over flexmark-java (actively maintained, cleaner API)"
  - "IncludeSourceSpans.BLOCKS for preserving original Markdown formatting in prose chunks"
  - "Heading text excluded from prose chunk when section has only code blocks"
  - "TablesExtension added to both Parser and TextContentRenderer for full GFM table support"

patterns-established:
  - "Source span extraction: enable IncludeSourceSpans.BLOCKS on parser, read original lines by line index"
  - "Section path slugification: lowercase, non-alphanumeric to hyphens, collapse, trim"
  - "Code block language: fence info string first token, fallback to LanguageDetector.detect()"
  - "Separate heading tracking from content nodes to avoid empty prose chunks"

# Metrics
duration: 6min
completed: 2026-02-18
---

# Phase 04 Plan 01: Markdown Chunking Engine Summary

**AST-based Markdown chunker with commonmark-java 0.27.1, heading splitting at H1/H2/H3, code block extraction, and keyword-based language detection for 10 languages**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-18T05:11:11Z
- **Completed:** 2026-02-18T05:17:47Z
- **Tasks:** 2 (1 auto + 1 TDD feature with RED/GREEN/REFACTOR)
- **Files modified:** 7

## Accomplishments
- MarkdownChunker splits Markdown at H1/H2/H3 boundaries, extracts fenced code blocks as separate code chunks
- H4+ headings remain inside parent H3 chunk, tables preserved intact via GFM extension
- DocumentChunkData record carries all 5 metadata fields (source_url, section_path, content_type, last_updated, language)
- LanguageDetector identifies 10 languages (java, python, javascript, typescript, yaml, xml, sql, bash, go, rust) via keyword scoring
- 29 comprehensive unit tests (14 LanguageDetector + 15 MarkdownChunker) covering all 11 plan cases plus edge cases

## Task Commits

Each task was committed atomically:

1. **Task 1: Add commonmark-java dependencies and DocumentChunkData record** - `d03cb70` (feat)
2. **Feature RED: Failing tests for MarkdownChunker and LanguageDetector** - `7d6eb11` (test)
3. **Feature GREEN: Implement MarkdownChunker and LanguageDetector** - `f055391` (feat)
4. **Feature REFACTOR: Simplify source span collection** - `c509ee0` (refactor)

## Files Created/Modified
- `gradle/libs.versions.toml` - Added commonmark 0.27.1 version and library entries
- `build.gradle.kts` - Added commonmark and GFM tables implementation dependencies
- `src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java` - Java record with 6 fields (text + 5 metadata)
- `src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java` - AST-based heading splitter and code extractor (211 lines)
- `src/main/java/dev/alexandria/ingestion/chunking/LanguageDetector.java` - Keyword-based language detection heuristic (68 lines)
- `src/test/java/dev/alexandria/ingestion/chunking/MarkdownChunkerTest.java` - 15 unit tests for chunking logic (356 lines)
- `src/test/java/dev/alexandria/ingestion/chunking/LanguageDetectorTest.java` - 14 unit tests for language detection (79 lines)

## Decisions Made
- **commonmark-java 0.27.1** chosen over flexmark-java (actively maintained Jan 2026 release, zero-dependency core, cleaner AST API)
- **IncludeSourceSpans.BLOCKS** enabled on parser to extract original Markdown text from source positions, preserving formatting (tables, lists, inline markup)
- **TablesExtension** registered on both Parser and TextContentRenderer for full GFM table support
- **Heading excluded from prose** when section has only code blocks to avoid empty/heading-only prose chunks
- **Null-safe source span handling** added for extension nodes (TableBody) that may contain null entries in source span lists

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Source spans not enabled on parser**
- **Found during:** TDD GREEN (MarkdownChunker implementation)
- **Issue:** commonmark-java does not include source spans by default; nodes had empty source span lists, causing table content to be lost
- **Fix:** Added `includeSourceSpans(IncludeSourceSpans.BLOCKS)` to parser configuration
- **Files modified:** MarkdownChunker.java
- **Verification:** Table preservation test passes
- **Committed in:** f055391

**2. [Rule 1 - Bug] Null source span entries in GFM tables extension**
- **Found during:** TDD GREEN (table test)
- **Issue:** TableBody node's source span list contained null entries, causing NPE
- **Fix:** Added null-safety check when iterating source spans
- **Files modified:** MarkdownChunker.java
- **Verification:** All 29 tests pass
- **Committed in:** f055391

**3. [Rule 1 - Bug] Empty prose chunk from heading-only sections**
- **Found during:** TDD GREEN (code-only section test)
- **Issue:** Sections with only code blocks emitted a prose chunk containing just the heading text
- **Fix:** Separated heading tracking from content nodes; prose chunk only emitted when non-heading content exists
- **Files modified:** MarkdownChunker.java
- **Verification:** sectionWithOnlyCodeBlocksProducesNoEmptyProseChunk test passes
- **Committed in:** f055391

---

**Total deviations:** 3 auto-fixed (3 bugs via Rule 1)
**Impact on plan:** All fixes were necessary for correct chunking behavior. No scope creep.

## Issues Encountered
None -- all issues were discovered and resolved during TDD GREEN phase as expected.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- MarkdownChunker and LanguageDetector are ready for integration into IngestionService (Plan 04-02)
- DocumentChunkData record provides the bridge between chunking and embedding storage
- All classes are pure unit-testable (no Spring context needed for tests)
- MarkdownChunker is annotated @Component for Spring DI in the orchestration layer

## Self-Check: PASSED

All 6 files found. All 4 commits verified.

---
*Phase: 04-ingestion-pipeline*
*Completed: 2026-02-18*
