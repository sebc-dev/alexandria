# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-14)

**Core value:** Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.
**Current focus:** Phase 1 - Foundation & Infrastructure

## Current Position

Phase: 1 of 9 (Foundation & Infrastructure) -- COMPLETE
Plan: 2 of 2 in current phase (01-01, 01-02 complete)
Status: Phase 01 complete, ready for Phase 02
Last activity: 2026-02-14 -- Completed 01-02-PLAN.md (Flyway migrations, embedding beans, integration tests)

Progress: [██░░░░░░░░] 20%

## Performance Metrics

**Velocity:**
- Total plans completed: 4
- Average duration: 4.5min
- Total execution time: 0.30 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 00-ci-quality-gate | 2 | 11min | 5.5min |
| 01-foundation-infrastructure | 2 | 7min | 3.5min |

**Recent Trend:**
- Last 5 plans: 7min, 4min, 3min, 4min
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

### Pending Todos

None yet.

### Roadmap Evolution

- Phase 0 added: CI & Quality Gate (local + GitHub CI, unit/integration/mutation tests, dead code detection, architecture tests)

### Blockers/Concerns

- [Research]: Flexmark-java chunking API needs validation during Phase 4 planning (hands-on prototyping recommended)
- [Research]: LangChain4j 1.11.0 hybrid search exact builder methods need code verification during Phase 2
- [Research]: Spring AI MCP @Tool annotation with stdio transport may differ from webmvc -- test early in Phase 5

## Session Continuity

Last session: 2026-02-14
Stopped at: Completed 01-02-PLAN.md (Flyway migrations, embedding beans, integration tests)
Resume file: None
Next: Phase 02 planning (Core Search or Web Crawling -- parallel eligible)
