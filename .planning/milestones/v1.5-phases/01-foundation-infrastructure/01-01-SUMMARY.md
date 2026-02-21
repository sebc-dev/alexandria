---
phase: 01-foundation-infrastructure
plan: 01
subsystem: infra
tags: [spring-boot, langchain4j, spring-ai-mcp, flyway, pgvector, docker, gradle]

# Dependency graph
requires:
  - phase: 00-ci-quality-gate
    provides: "Gradle build with quality gates (JaCoCo, PIT, SpotBugs, SonarQube)"
provides:
  - "All Phase 1 compile dependencies (LangChain4j 1.11.0, Spring AI MCP 1.0.3, Flyway, pgvector JDBC)"
  - "Dual-profile Spring Boot config (web: REST+MCP SSE, stdio: MCP Claude Code)"
  - "Docker Compose 3-service stack (PostgreSQL pgvector:pg16, Crawl4AI, app)"
  - "Multi-stage Dockerfile with JRE runtime and container-aware JVM flags"
affects: [01-02, 02-search, 03-crawling, 04-ingestion, 05-mcp]

# Tech tracking
tech-stack:
  added: [langchain4j-1.11.0, spring-ai-mcp-1.0.3, flyway-11.7.2, pgvector-jdbc, postgresql-42.7.8, spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-actuator]
  patterns: [dual-profile-spring-boot, multi-stage-docker-build, env-var-datasource-config, virtual-threads]

key-files:
  created: [src/main/resources/application.yml, src/main/resources/application-web.yml, src/main/resources/application-stdio.yml, Dockerfile, docker-compose.yml, .dockerignore]
  modified: [build.gradle.kts, src/integrationTest/java/dev/alexandria/SmokeIntegrationTest.java]

key-decisions:
  - "Kept AlexandriaApplicationTest enabled -- it has no @SpringBootTest and passes without DB"
  - "Spring AI BOM 1.0.3 manages spring-ai-starter-mcp-server-webmvc version"
  - "App service has no host port exposure -- internal Docker network only per decision"
  - "JVM uses MaxRAMPercentage=75 instead of fixed Xmx for container-aware sizing"

patterns-established:
  - "Dual-profile pattern: application.yml (shared) + application-web.yml + application-stdio.yml"
  - "Environment variable datasource config: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD"
  - "Docker Compose service_healthy dependency ordering for startup sequencing"

# Metrics
duration: 3min
completed: 2026-02-14
---

# Phase 1 Plan 01: Project Skeleton Summary

**Gradle build with LangChain4j 1.11.0, Spring AI MCP 1.0.3, Flyway, pgvector JDBC; dual-profile Spring Boot config (web + stdio); Docker Compose 3-service stack with pgvector:pg16 and Crawl4AI**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-14T17:20:13Z
- **Completed:** 2026-02-14T17:23:24Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- All Phase 1 dependencies resolve and compile (LangChain4j, Spring AI MCP, Flyway, pgvector JDBC, PostgreSQL driver)
- Dual-profile Spring Boot configuration: web profile (REST + MCP SSE on port 8080) and stdio profile (no web, no banner, file-only logging)
- Docker Compose stack with PostgreSQL pgvector:pg16, Crawl4AI 0.8.0, and app -- all with health checks and service_healthy dependency ordering
- Multi-stage Dockerfile with eclipse-temurin:21-jdk build and eclipse-temurin:21-jre runtime

## Task Commits

Each task was committed atomically:

1. **Task 1: Gradle dependencies and Spring Boot dual-profile YAML configuration** - `e3524e9` (feat)
2. **Task 2: Dockerfile and Docker Compose stack with health checks** - `24281d0` (feat)

## Files Created/Modified
- `build.gradle.kts` - Added Phase 1 deps (LangChain4j, Spring AI MCP, Flyway, pgvector), Spring AI BOM, integrationTest deps
- `src/main/resources/application.yml` - Shared config: datasource, JPA validate, Flyway, virtual threads, actuator health
- `src/main/resources/application-web.yml` - Web profile: port 8080, MCP SSE server config
- `src/main/resources/application-stdio.yml` - Stdio profile: no web, no banner, file logging, MCP stdio
- `src/integrationTest/java/dev/alexandria/SmokeIntegrationTest.java` - Added @Disabled (needs Testcontainers in Plan 02)
- `src/main/resources/application.properties` - Deleted (replaced by application.yml)
- `Dockerfile` - Multi-stage build with JRE runtime and container-aware JVM flags
- `docker-compose.yml` - 3-service stack: postgres (pgvector:pg16), crawl4ai, app with health checks
- `.dockerignore` - Exclude .git, .gradle, build, .planning, docs, .claude

## Decisions Made
- Kept `AlexandriaApplicationTest` enabled since it has no `@SpringBootTest` annotation and passes without a database (plan said to disable it but reasoning was incorrect -- the test is a simple class existence check)
- Spring AI BOM 1.0.3 manages the `spring-ai-starter-mcp-server-webmvc` version (no explicit version on the dependency)
- App service has no host port exposure in Docker Compose (internal Docker network only, per locked decision)
- JVM uses `MaxRAMPercentage=75.0` for container-aware heap sizing instead of a fixed `-Xmx`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Did not disable AlexandriaApplicationTest**
- **Found during:** Task 1
- **Issue:** Plan said to add `@Disabled` to `AlexandriaApplicationTest.java` with reasoning "same issue: @SpringBootTest will fail without DB" -- but this test has no `@SpringBootTest` annotation, just a simple `assertThat(AlexandriaApplication.class).isNotNull()` check
- **Fix:** Left the test enabled since it passes without a database. Disabling it would unnecessarily reduce test coverage.
- **Files modified:** None (kept existing test as-is)
- **Verification:** `./gradlew build` passes with the test enabled

---

**Total deviations:** 1 auto-fixed (1 bug in plan reasoning)
**Impact on plan:** Minimal -- one test kept enabled that the plan incorrectly wanted disabled. Better test coverage as a result.

## Issues Encountered
- `docker compose config` could not run because Docker is not available in this WSL 2 environment. The docker-compose.yml was verified manually against the specification. Docker validation will occur when the stack is first started.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All Phase 1 compile dependencies are on the classpath and resolved
- Dual-profile config is ready for web and stdio Spring Boot profiles
- Docker Compose stack is defined and ready to start (postgres, crawl4ai, app)
- Plan 02 (Flyway migrations, embedding beans, integration tests) can proceed immediately
- SmokeIntegrationTest is @Disabled pending Testcontainers setup in Plan 02

## Self-Check: PASSED

- All 7 created files verified present on disk
- `application.properties` confirmed deleted
- Both task commits verified in git log (e3524e9, 24281d0)
- `./gradlew build` passes (all tasks up-to-date)

---
*Phase: 01-foundation-infrastructure*
*Completed: 2026-02-14*
