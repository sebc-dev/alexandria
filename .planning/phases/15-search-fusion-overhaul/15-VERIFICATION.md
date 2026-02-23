---
phase: 15-search-fusion-overhaul
verified: 2026-02-22T18:30:00Z
status: passed
score: 12/12 must-haves verified
re_verification: false
gaps: []
human_verification:
  - test: "Run integration tests and start the application"
    expected: "Application starts, alpha=0.7 and rerank-candidates=30 are logged; SearchProperties validates on startup; all unit tests green"
    why_human: "Unit tests verify the wiring but actual DB FTS behaviour and startup validation require a running Spring context"
---

# Phase 15: Search Fusion Overhaul Verification Report

**Phase Goal:** Hybrid search uses Convex Combination with configurable parameters, replacing RRF for better score utilisation
**Verified:** 2026-02-22T18:30:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

#### Plan 01 Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Convex Combination fuses two scored lists into one combined list using alpha-weighted normalised scores | VERIFIED | `ConvexCombinationFusion.fuse()` at line 47; combined = alpha * normVector + (1-alpha) * normFTS at lines 68, 74, 84 |
| 2 | Min-max normalisation scales both vector and FTS scores to [0, 1] per query | VERIFIED | `normalise()` at lines 109-114; `scoreStats()` at 117-119; applied separately to each source |
| 3 | Edge case: when max == min for a source, normalised score is 1.0 | VERIFIED | `ConvexCombinationFusion.java` line 110-112: `if (stats.getMax() == stats.getMin()) return 1.0` |
| 4 | Alpha controls vector vs FTS weight: combined = alpha * normVector + (1-alpha) * normFTS | VERIFIED | Lines 68 (vector side: `alpha * normScore`), 73 (`(1.0 - alpha) * normScore`); 13 tests confirm this |
| 5 | Results appearing in only one source get 0.0 for the missing source | VERIFIED | Vector-only: FTS contribution zero (not added to map); FTS-only: `new FusedEntry(..., ftsContribution)` with no prior vector contribution |

#### Plan 02 Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 6 | SearchService executes vector and FTS queries in parallel using virtual threads (CompletableFuture) | VERIFIED | `SearchService.java` lines 91-96: `CompletableFuture.supplyAsync()` for both; `allOf().join()` waits on both |
| 7 | Scores are fused via ConvexCombinationFusion with configurable alpha (default 0.7) | VERIFIED | Line 110: `ConvexCombinationFusion.fuse(vectorResults, ftsResults, alpha, candidates)`; alpha sourced from `searchProperties.getAlpha()` |
| 8 | Alpha is configurable via application.yml and validated at startup to [0.0, 1.0] | VERIFIED | `SearchProperties.java` lines 32-36: @PostConstruct validates alpha; `application.yml` line 34: `alpha: 0.7` |
| 9 | Rerank candidate count is configurable via application.yml (default 30) | VERIFIED | `SearchProperties.java` lines 37-40: rerank-candidates [10, 100]; `application.yml` line 35: `rerank-candidates: 30` |
| 10 | RRF is completely removed: no rrfK in SearchRequest, EmbeddingConfig, McpToolService, application.yml | VERIFIED | `grep -rn "rrfK\|rrf-k\|RRF\|HYBRID" src/main/` returned zero matches |
| 11 | Pipeline order: parallel fetch -> CC fusion -> parent-child dedup -> rerank -> parent text substitution | VERIFIED | `SearchService.search()` lines 91-124 follow exactly this order |
| 12 | Application fails to start if alpha is outside [0.0, 1.0] | VERIFIED | `SearchProperties.validate()` throws `IllegalStateException` on invalid alpha; invoked by @PostConstruct |

**Score:** 12/12 truths verified

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact | Min Lines | Actual Lines | Status | Details |
|----------|-----------|--------------|--------|---------|
| `src/main/java/dev/alexandria/search/ConvexCombinationFusion.java` | 40 | 129 | VERIFIED | Pure static utility: private constructor, no Spring annotations, correct algorithm |
| `src/test/java/dev/alexandria/search/ConvexCombinationFusionTest.java` | 80 | 213 | VERIFIED | 13 test methods covering all required cases (empty, single-source, overlap, normalisation, alpha extremes, max==min, maxResults, sort order, embedding placeholders) |
| `src/main/java/dev/alexandria/search/ScoredCandidate.java` | — | 17 | VERIFIED | Package-private record with @Nullable embedding as specified |

### Plan 02 Artifacts

| Artifact | Requirement | Status | Details |
|----------|-------------|--------|---------|
| `src/main/java/dev/alexandria/search/SearchProperties.java` | @ConfigurationProperties for alpha and rerank-candidates | VERIFIED | 58 lines; `@Configuration @ConfigurationProperties(prefix = "alexandria.search")`; @PostConstruct validation; default alpha=0.7, rerankCandidates=30 |
| `src/main/java/dev/alexandria/search/SearchService.java` | Dual-query parallel pipeline with CC fusion | VERIFIED | 361 lines; CompletableFuture parallel execution; CC fusion call at line 110; full pipeline preserved |
| `src/main/java/dev/alexandria/config/EmbeddingConfig.java` | EmbeddingStore in VECTOR mode | VERIFIED | Line 73: `.searchMode(SearchMode.VECTOR)`; no rrfK parameter; no SearchMode.HYBRID |
| `src/main/resources/application.yml` | alpha and rerank-candidates config | VERIFIED | Lines 34-35: `alpha: 0.7` and `rerank-candidates: 30` under `alexandria.search:` |
| `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` | fullTextSearch() native query method | VERIFIED | Lines 160-180: native SQL with ts_rank, individual metadata field extraction (9 metadata fields + score) |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ConvexCombinationFusionTest.java` | `ConvexCombinationFusion.java` | direct method call | VERIFIED | Line 31: `ConvexCombinationFusion.fuse(List.of(), List.of(), ALPHA, 10)` — 13 test methods exercise this |
| `SearchService.java` | `ConvexCombinationFusion.java` | static method call | VERIFIED | Line 110: `ConvexCombinationFusion.fuse(vectorResults, ftsResults, alpha, candidates)` |
| `SearchService.java` | `SearchProperties.java` | constructor injection | VERIFIED | Lines 57, 64: field declared and injected; alpha and rerankCandidates read at lines 87-88 |
| `SearchProperties.java` | `application.yml` | @ConfigurationProperties prefix | VERIFIED | `prefix = "alexandria.search"` maps to yml block `alexandria.search:` which contains alpha and rerank-candidates |
| `EmbeddingConfig.java` | `SearchMode.VECTOR` | builder configuration | VERIFIED | Line 73: `.searchMode(SearchMode.VECTOR)` — no HYBRID, no rrfK |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| FUSE-01 | 15-01, 15-02 | Fusion hybride utilise Convex Combination au lieu de RRF | SATISFIED | `ConvexCombinationFusion.fuse()` replaces RRF; zero RRF references in production code; SearchMode.VECTOR replaces HYBRID |
| FUSE-02 | 15-01, 15-02 | Le parametre alpha est configurable via application properties | SATISFIED | `SearchProperties.alpha` bound from `alexandria.search.alpha`; default 0.7 in application.yml; @PostConstruct validates [0.0, 1.0] |
| FUSE-03 | 15-02 | Le nombre de candidats reranking est configurable (defaut: 30) | SATISFIED | `SearchProperties.rerankCandidates` bound from `alexandria.search.rerank-candidates`; default 30; validated [10, 100]; passed to both vector and FTS queries |

All three requirements map to Phase 15 in REQUIREMENTS.md traceability table and are marked `[x]` (complete).

**No orphaned requirements:** REQUIREMENTS.md Traceability table maps only FUSE-01, FUSE-02, FUSE-03 to Phase 15. All three are accounted for.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `EmbeddingStoreIT.java` | 39, 47 | Stale HYBRID-mode comments in integration test | INFO | Comment says "Required for HYBRID search mode" and mentions RRF formula — production code is VECTOR mode; test behaviour unaffected since `.query()` is ignored in VECTOR mode |

No blocker or warning anti-patterns found. The stale comment in the integration test is informational only — it does not affect correctness.

---

## Human Verification Required

### 1. Application Startup Validation

**Test:** Start the application (`./gradlew bootRun` or `docker compose up -d`) with `application.yml` defaults
**Expected:** Application starts successfully; SearchProperties.validate() passes with alpha=0.7 and rerank-candidates=30
**Why human:** @PostConstruct validation only executes in a live Spring context — unit tests do not start the full application

### 2. Startup Failure on Invalid Alpha

**Test:** Temporarily set `alexandria.search.alpha: 1.5` in application.yml, then start the application
**Expected:** Application fails to start with a clear `IllegalStateException: alexandria.search.alpha must be in [0.0, 1.0], got: 1.5`
**Why human:** Requires live Spring Boot startup to observe the failure message

### 3. Stale Integration Test Comment Cleanup (Optional)

**Test:** Review `EmbeddingStoreIT.java` lines 39 and 47
**Expected:** Comments updated to reflect VECTOR mode (no functional issue)
**Why human:** Code smell, not a correctness issue — warrants a cleanup commit but not a blocker

---

## Gaps Summary

No gaps found. All 12 observable truths are verified, all artifacts pass all three levels (exists, substantive, wired), all key links are confirmed, and all three requirement IDs (FUSE-01, FUSE-02, FUSE-03) are satisfied.

The phase delivers exactly what was specified:
- `ConvexCombinationFusion` is a pure, stateless static utility with full TDD test coverage (13 tests)
- `SearchProperties` externalises alpha and rerank-candidates with startup validation
- `SearchService` uses parallel CompletableFuture execution for both search sources, fuses via CC, then deduplicates, reranks, and substitutes parent text
- RRF has been completely removed from all production code paths (`SearchRequest`, `EmbeddingConfig`, `McpToolService`, `application.yml`)
- REQUIREMENTS.md marks FUSE-01, FUSE-02, FUSE-03 as complete with Phase 15 as the implementing phase

---

_Verified: 2026-02-22T18:30:00Z_
_Verifier: Claude (gsd-verifier)_
