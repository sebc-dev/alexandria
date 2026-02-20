# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-14)

**Core value:** Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.
**Current focus:** Phase 7 - Crawl Operations (complete)

## Current Position

Phase: 7 of 8 (Crawl Operations)
Plan: 5 of 5 in Phase 07 (COMPLETE)
Status: Phase 07 complete
Last activity: 2026-02-20 -- Completed 07-05 (MCP Tool Integration)

Progress: [█████████░] 87%

## Performance Metrics

**Velocity:**
- Total plans completed: 18
- Average duration: 4.8min
- Total execution time: 1.5 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 00-ci-quality-gate | 2 | 11min | 5.5min |
| 01-foundation-infrastructure | 2 | 7min | 3.5min |
| 03-web-crawling | 2 | 9min | 4.5min |
| 04.5-code-quality-consolidation | 5 | 31min | 6.2min |
| 05-mcp-server | 2 | 6min | 3.0min |
| 07-crawl-operations | 5 | 33min | 6.6min |

**Recent Trend:**
- Last 5 plans: 5min, 5min, 7min, 10min, 6min
- Trend: stabilizing (MCP integration faster than crawl orchestration)

*Updated after each plan completion*
| Phase 04.5 P03 | 5min | 2 tasks | 5 files |
| Phase 04.5 P04 | 3min | 2 tasks | 6 files |
| Phase 04.5 P05 | 11min | 2 tasks | 38 files |
| Phase 05 P01 | 3min | 2 tasks | 4 files |
| Phase 05 P02 | 3min | 2 tasks | 3 files |
| Phase 07 P01 | 5min | 2 tasks | 6 files |
| Phase 07 P02 | 5min | 1 task | 2 files |
| Phase 07 P03 | 7min | 2 tasks | 11 files |
| Phase 07 P04 | 10min | 2 tasks | 5 files |
| Phase 07 P05 | 6min | 2 tasks | 2 files |

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
- [04.5-01]: Records with List/Map fields use compact constructors with null-safe List.copyOf()/Map.copyOf() for defensive copies
- [04.5-01]: SpotBugs exclusion filter: Spring bean constructors (EI_EXPOSE_REP2), JPA entities (CT_CONSTRUCTOR_THROW, EI_EXPOSE_REP), MarkdownChunker (CT_CONSTRUCTOR_THROW)
- [04.5-01]: Test fixture builders (SourceBuilder, DocumentChunkBuilder) for JPA entities only; records constructed inline
- [Phase 04.5]: 6 surviving PIT mutations classified as equivalent (downstream guards make them unkillable)
- [04.5-04]: InOrder verification for PreChunkedImporter embed-before-delete safety ordering
- [04.5-04]: No test for failed CrawlResult filtering -- IngestionService does not check success flag (caller responsibility)
- [04.5-05]: BaseIntegrationTest.embeddingStore uses protected @Autowired(required=false) for safe cross-package inheritance
- [04.5-05]: camelCase test naming convention enforced across entire codebase (~56 renames)
- [Phase 04.5-03]: RestClient mock chain uses answer-based URI routing for multi-URL SitemapParser tests
- [Phase 04.5-03]: CrawlService.crawlSite() refactored from ~50 to ~22 lines (seedQueue, dequeueAndNormalize, processPage, enqueueDiscoveredLinks)
- [05-01]: Token estimation uses chars/4 industry standard, configurable via alexandria.mcp.token-budget property (default 5000)
- [05-01]: Source management tools are functional stubs (add_source/remove_source interact with DB, others query status) with future-update messages for crawl orchestration
- [05-01]: First search result always included even if exceeding token budget (truncated at char level) to guarantee non-empty responses
- [05-02]: Pre-existing SpotBugs VA_FORMAT_STRING_USES_NEWLINE in TokenBudgetTruncator is intentional -- MCP stdio output uses Unix \n, not platform-dependent %n
- [07-01]: PathMatcher glob on URL paths: java.nio.file.FileSystems.getDefault().getPathMatcher for pattern matching on URL path segments
- [07-01]: Block patterns take priority over allow patterns in UrlScopeFilter (per user decision from plan)
- [07-02]: LlmsTxtParser link index threshold 0.3 ratio: >= 30% markdown link lines classifies content as llms.txt link index
- [07-02]: LlmsTxtResult as nested record in LlmsTxtParser (self-contained API, not separate file)
- [07-03]: CrawlProgress.Status enum instead of SourceStatus to avoid crawl<->source package cycle (ArchUnit no_package_cycles)
- [07-03]: CrawlScope.fromSource() factory instead of Source.toCrawlScope() to maintain unidirectional crawl->source dependency
- [07-03]: Comma-separated TEXT columns for scope patterns (simplest JPA mapping, no Hibernate array type complexity)
- [07-03]: Answer-based URI routing in PageDiscoveryService tests for RestClient mock chain
- [07-04]: Incremental ingestion logic in CrawlService (not IngestionService) to avoid ArchUnit crawl<->ingestion package cycle
- [07-04]: Removed ingest(List<CrawlResult>) from IngestionService to break ingestion->crawl dependency
- [07-04]: deleteChunksForUrl() on IngestionService as URL-scoped chunk deletion abstraction
- [07-04]: LinkedHashMap<URL, depth> for BFS depth tracking instead of LinkedHashSet
- [07-05]: dispatchCrawl as package-private method for testability -- spy pattern suppresses virtual thread in unit tests
- [07-05]: Scope overrides on recrawl are one-time: CrawlScope built from overrides, Source entity unchanged
- [07-05]: results.size() used for chunkCount on Source after crawl (page count proxy; actual chunk counts tracked internally by CrawlService)

### Pending Todos

None yet.

### Roadmap Evolution

- Phase 0 added: CI & Quality Gate (local + GitHub CI, unit/integration/mutation tests, dead code detection, architecture tests)
- Phase 4.5 inserted: Code Quality & Test Consolidation (consolidate tests, increase coverage, refactor long methods, codebase cleanup) -- replaces Phase 9 placeholder

### Blockers/Concerns

- [Research]: Flexmark-java chunking API needs validation during Phase 4 planning (hands-on prototyping recommended)
- [Resolved]: LangChain4j 1.11.0-beta19 hybrid search API verified: SearchMode is nested enum, DatasourceBuilder has searchMode/textSearchConfig/rrfK methods, EmbeddingSearchRequest has query() builder method
- [Resolved]: Spring AI MCP @Tool annotation compiles and passes ArchUnit with existing webmvc starter -- verified in Phase 5 Plan 1

## Session Continuity

Last session: 2026-02-20
Stopped at: Completed 07-05-PLAN.md (Phase 07 complete)
Resume file: .planning/phases/07-crawl-operations/07-05-SUMMARY.md
Next: Phase 08 (Advanced Search & Quality)
