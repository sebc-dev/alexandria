---
phase: 08-advanced-search-quality
plan: 02
subsystem: search
tags: [cross-encoder, reranking, onnx, langchain4j, ms-marco, scoring-model]

# Dependency graph
requires:
  - phase: 01-foundation-infrastructure
    provides: EmbeddingConfig, PgVectorEmbeddingStore, SearchResult
  - phase: 02-core-search (plan 01)
    provides: SearchService, SearchResult record, hybrid search
provides:
  - RerankerService for cross-encoder reranking of search candidates
  - ScoringModel bean (OnnxScoringModel) in EmbeddingConfig
  - SearchResult.rerankScore field for reranking confidence
  - Configurable RRF k via Spring property
  - Docker model download for ms-marco-MiniLM-L-6-v2
affects: [08-advanced-search-quality (plans 03-04), mcp search_docs integration]

# Tech tracking
tech-stack:
  added: [langchain4j-onnx-scoring 1.11.0-beta19, ms-marco-MiniLM-L-6-v2 ONNX model]
  patterns: [cross-encoder reranking pipeline, TDD for scoring logic]

key-files:
  created:
    - src/main/java/dev/alexandria/search/RerankerService.java
    - src/test/java/dev/alexandria/search/RerankerServiceTest.java
  modified:
    - src/main/java/dev/alexandria/search/SearchResult.java
    - src/main/java/dev/alexandria/config/EmbeddingConfig.java
    - gradle/libs.versions.toml
    - build.gradle.kts
    - src/main/resources/application.yml
    - Dockerfile
    - docker-compose.yml

key-decisions:
  - "SearchResult backward compatibility via convenience 4-arg constructor (rerankScore defaults to 0.0)"
  - "RRF k is store-level config via Spring property, not per-request (LangChain4j limitation)"
  - "Model files downloaded during Docker build, not at runtime"

patterns-established:
  - "Cross-encoder reranking: ScoringModel.scoreAll() for batch scoring, zip with candidates, sort by score"
  - "Backward-compatible record extension: add field with convenience constructor for old callers"

requirements-completed: [SRCH-07]

# Metrics
duration: 5min
completed: 2026-02-20
---

# Phase 8 Plan 02: Cross-Encoder Reranking Service Summary

**RerankerService scores RRF candidates via OnnxScoringModel (ms-marco-MiniLM-L-6-v2), with configurable RRF k and Docker model setup**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-20T12:38:49Z
- **Completed:** 2026-02-20T12:44:00Z
- **Tasks:** 2 (TDD: 1 RED + 1 GREEN + 1 config)
- **Files modified:** 9

## Accomplishments
- RerankerService scores query-passage pairs via ScoringModel, sorts by reranking score, respects maxResults and minScore threshold
- SearchResult extended with rerankScore field (backward-compatible convenience constructor)
- ScoringModel bean configured in EmbeddingConfig with file path injection
- Docker build downloads ms-marco-MiniLM-L-6-v2 model from HuggingFace
- RRF k configurable via `alexandria.search.rrf-k` Spring property (default 60)
- 7 unit tests covering reranking behavior, limits, thresholds, exceptions, and metadata preservation

## Task Commits

Each task was committed atomically:

1. **Task 1 (TDD RED): RerankerServiceTest** - `6781a92` (test)
2. **Task 1 (TDD GREEN): RerankerService + setup** - `ddbf4c9` (feat)
3. **Task 2: Docker model setup + RRF k** - `756509d` (chore)

_TDD task split into RED (failing tests) and GREEN (implementation) commits._

## Files Created/Modified
- `src/main/java/dev/alexandria/search/RerankerService.java` - Cross-encoder reranking via ScoringModel
- `src/test/java/dev/alexandria/search/RerankerServiceTest.java` - 7 unit tests for reranking behavior
- `src/main/java/dev/alexandria/search/SearchResult.java` - Added rerankScore field with backward-compatible constructor
- `src/main/java/dev/alexandria/config/EmbeddingConfig.java` - ScoringModel bean + configurable rrfK
- `gradle/libs.versions.toml` - langchain4j-onnx-scoring dependency
- `build.gradle.kts` - langchain4j-onnx-scoring implementation dependency
- `src/main/resources/application.yml` - reranker paths + rrf-k config
- `Dockerfile` - Model download during build, COPY to runtime stage
- `docker-compose.yml` - RERANKER_MODEL_PATH and RERANKER_TOKENIZER_PATH env vars

## Decisions Made
- **SearchResult backward compatibility:** Added `rerankScore` as 5th record field with a convenience 4-arg constructor defaulting rerankScore to 0.0. All existing callers (SearchService, TokenBudgetTruncator, McpToolServiceTest) continue working without changes.
- **RRF k is store-level only:** Per LangChain4j API, rrfK is configured at PgVectorEmbeddingStore construction time, not per-request. Made configurable via `alexandria.search.rrf-k` Spring property.
- **Docker model download in build stage:** Model files downloaded during `docker build` (not runtime) for reproducible builds. Runtime stage receives files via COPY --from=builder.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None for Docker deployments (model files are auto-downloaded during build).

For local development without Docker, the ms-marco-MiniLM-L-6-v2 model files must be downloaded manually to `models/ms-marco-MiniLM-L-6-v2/`:
- `model.onnx` from https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model.onnx
- `tokenizer.json` from https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json

## Next Phase Readiness
- RerankerService ready to be wired into SearchService pipeline (plan 03)
- SearchResult.rerankScore ready for MCP output formatting
- ScoringModel bean available for injection into any service

---
*Phase: 08-advanced-search-quality*
*Completed: 2026-02-20*
