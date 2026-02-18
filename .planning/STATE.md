# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-14)

**Core value:** Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.
**Current focus:** Phase 4 - Ingestion Pipeline (in progress)

## Current Position

Phase: 4 of 9 (Ingestion Pipeline)
Plan: 1 of 2 complete in Phase 04 (04-01 complete)
Status: Phase 04 in progress
Last activity: 2026-02-18 -- Completed 04-01 Markdown chunking engine

Progress: [████░░░░░░] 40%

## Performance Metrics

**Velocity:**
- Total plans completed: 9
- Average duration: 4.6min
- Total execution time: 0.68 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 00-ci-quality-gate | 2 | 11min | 5.5min |
| 01-foundation-infrastructure | 2 | 7min | 3.5min |
| 03-web-crawling | 2 | 9min | 4.5min |
| 04-ingestion-pipeline | 1 | 6min | 6min |

**Recent Trend:**
- Last 5 plans: 4min, 4min, 5min, 5min, 6min
- Trend: stable

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Phases 2 (Core Search) and 3 (Web Crawling) can execute in parallel since both depend only on Phase 1
- [Roadmap]: Search verified with test data before crawling exists (Phase 2 before Phase 4) to validate retrieval path early
- [Roadmap]: CHUNK-04 (ONNX embeddings) placed in Phase 1 foundation since it is a dependency for search and ingestion
- [00-01]: Used allowEmptyShould(true) on ArchUnit rules for skeleton project compatibility
- [00-01]: Added failWhenNoMutations=false to PIT config since skeleton has no mutable code
- [00-01]: Non-blocking quality gates: only test failures block the build
- [00-02]: quality.sh uses || true after Gradle for non-blocking gates, propagates exit codes for blocking ones
- [00-02]: PIT runs separately in quality.sh all (heavyweight, not parallelizable with tests)
- [00-02]: CI test job is the only blocking job; coverage/spotbugs/mutation/sonarcloud are non-blocking
- [00-02]: GitHub Actions pinned to commit SHAs for supply chain security
- [00-02]: SonarCloud config via environment variables (SONAR_PROJECT_KEY, SONAR_ORGANIZATION)
- [00-02]: Shared build job in CI avoids redundant compilation across parallel jobs
- [00-02]: Stack upgraded: Gradle 9.3.1, Spring Boot 4.0.2, PIT 1.21.0, Testcontainers 2.0.3
- [00-02]: Restored io.spring.dependency-management plugin for IDE compatibility
- [01-01]: Kept AlexandriaApplicationTest enabled -- no @SpringBootTest, passes without DB
- [01-01]: Spring AI BOM 1.0.3 manages spring-ai-starter-mcp-server-webmvc version
- [01-01]: App service internal-only in Docker Compose (no host port exposure)
- [01-01]: JVM MaxRAMPercentage=75 for container-aware sizing instead of fixed Xmx
- [01-02]: PgVectorEmbeddingStore.datasourceBuilder() shares HikariCP pool with JPA (no duplicate connections)
- [01-02]: ddl-auto=none because Hibernate cannot validate vector(384) column type -- Flyway is source of truth
- [01-02]: Deleted AlexandriaApplicationTest (SmokeIntegrationTest covers context loading with real DB)
- [01-02]: HNSW index with vector_cosine_ops and m=16, ef_construction=64 for cosine similarity search
- [02-01]: SearchMode is nested enum PgVectorEmbeddingStore.SearchMode, not top-level class (research doc was incorrect)
- [02-01]: Metadata keys use snake_case convention: source_url, section_path -- must match ingestion-time keys
- [02-01]: RRF k=60 (standard default from original RRF paper) for v1, tunable in Phase 8
- [03-01]: Catch RestClientException in Crawl4AiClient for graceful failure handling (Crawl4AI returns HTTP 500 for unreachable URLs)
- [03-01]: Made BaseIntegrationTest public for cross-package extension from crawl test package
- [03-01]: PruningContentFilter threshold 0.48 with min_word_threshold 20 for documentation content density
- [03-02]: UrlNormalizer as static utility (no Spring bean) since it has no dependencies
- [03-02]: CrawlService follows links only in LINK_CRAWL mode; trusts sitemap URL list when available
- [03-02]: Sequential page crawling (no concurrency) to keep Crawl4AI sidecar stable
- [04-01]: commonmark-java 0.27.1 over flexmark-java (actively maintained, cleaner API)
- [04-01]: IncludeSourceSpans.BLOCKS for preserving original Markdown formatting in prose chunks
- [04-01]: Heading text excluded from prose chunk when section has only code blocks
- [04-01]: TablesExtension added to both Parser and TextContentRenderer for full GFM table support

### Pending Todos

None yet.

### Roadmap Evolution

- Phase 0 added: CI & Quality Gate (local + GitHub CI, unit/integration/mutation tests, dead code detection, architecture tests)

### Blockers/Concerns

- [Resolved]: Flexmark-java replaced by commonmark-java 0.27.1 (actively maintained, cleaner AST API, validated in Phase 4)
- [Resolved]: LangChain4j 1.11.0-beta19 hybrid search API verified: SearchMode is nested enum, DatasourceBuilder has searchMode/textSearchConfig/rrfK methods, EmbeddingSearchRequest has query() builder method
- [Research]: Spring AI MCP @Tool annotation with stdio transport may differ from webmvc -- test early in Phase 5

## Session Continuity

Last session: 2026-02-18
Stopped at: Completed 04-01-PLAN.md -- Markdown chunking engine
Resume file: None
Next: 04-02-PLAN.md (ingestion pipeline orchestration and storage)
