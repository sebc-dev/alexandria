---
phase: 01-infrastructure
plan: 02
subsystem: java-build
tags: [maven, langchain4j, spring-boot, pgvector, apache-age, java-21]

# Dependency graph
requires:
  - phase: 01-infrastructure-01
    provides: Docker PostgreSQL with pgvector and AGE extensions
provides:
  - Maven project compiling with all LangChain4j dependencies
  - Spring Boot 3.4.1 application structure
  - Integration tests validating pgvector and AGE connectivity
  - Three-layer package architecture (api, core, infra)
  - HikariCP configuration with AGE session initialization
affects: [02-ingestion, 03-retrieval, all-java-components]

# Tech tracking
tech-stack:
  added: [spring-boot-3.4.1, langchain4j-1.0.0-beta3, pgvector-java-0.1.6, hikaricp]
  patterns: [layered-architecture, dependency-inversion, virtual-threads]

key-files:
  created:
    - pom.xml
    - lib/age-jdbc-1.6.0.jar
    - src/main/java/fr/kalifazzia/alexandria/Application.java
    - src/main/java/fr/kalifazzia/alexandria/core/package-info.java
    - src/main/java/fr/kalifazzia/alexandria/infra/package-info.java
    - src/main/java/fr/kalifazzia/alexandria/api/package-info.java
    - src/main/resources/application.yml
    - src/test/java/fr/kalifazzia/alexandria/infra/DatabaseConnectionIT.java
  modified: []

key-decisions:
  - "LangChain4j 1.0.0-beta3 for stable API with embeddings support"
  - "AGE JDBC placeholder jar - use raw JDBC for AGE queries in Phase 1"
  - "Removed --enable-preview as Java 21 features are stable"
  - "HikariCP connection-init-sql loads AGE and sets search_path per connection"
  - "Virtual threads enabled via spring.threads.virtual.enabled"

patterns-established:
  - "Three-layer architecture: api (entry) -> core (business) -> infra (external)"
  - "Package-info.java documents each layer's responsibilities"
  - "Integration tests in *IT.java files separated by Failsafe plugin"
  - "Session init pattern for AGE via HikariCP connection-init-sql"

# Metrics
duration: 3min
completed: 2026-01-19
---

# Phase 01 Plan 02: Maven Project with LangChain4j Summary

**Maven project with Spring Boot 3.4.1, LangChain4j 1.0.0-beta3, and integration tests validating pgvector/AGE connectivity**

## Performance

- **Duration:** 3 min 21 sec
- **Started:** 2026-01-19T13:52:39Z
- **Completed:** 2026-01-19T13:56:00Z
- **Tasks:** 3
- **Files created:** 8

## Accomplishments

- Maven pom.xml with all dependencies resolving and compiling
- Spring Boot application with three-layer package structure (api, core, infra)
- HikariCP configuration with AGE session initialization per connection
- 8 integration tests validating PostgreSQL, pgvector, and AGE functionality
- Java 21 virtual threads enabled for I/O-bound operations
- Java 21 records demonstrated in integration tests

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Maven pom.xml with dependencies** - `6e3b375` (feat)
2. **Task 2: Create project structure and configuration** - `a7fd7e2` (feat)
3. **Task 3: Create integration test validating pgvector and AGE** - `3d23662` (test)

## Files Created/Modified

- `pom.xml` - Maven build with Spring Boot 3.4.1, LangChain4j BOM, pgvector, Liquibase
- `lib/age-jdbc-1.6.0.jar` - Placeholder JAR (raw JDBC used for AGE queries)
- `src/main/java/fr/kalifazzia/alexandria/Application.java` - Spring Boot entry point
- `src/main/java/fr/kalifazzia/alexandria/core/package-info.java` - Core layer docs
- `src/main/java/fr/kalifazzia/alexandria/infra/package-info.java` - Infra layer docs
- `src/main/java/fr/kalifazzia/alexandria/api/package-info.java` - API layer docs
- `src/main/resources/application.yml` - Database config with virtual threads
- `src/test/java/fr/kalifazzia/alexandria/infra/DatabaseConnectionIT.java` - 8 integration tests

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| LangChain4j 1.0.0-beta3 | Latest stable beta with embeddings support, managed via BOM |
| AGE placeholder JAR | Real driver not on Maven Central; raw JDBC works fine for Phase 1 |
| Removed --enable-preview | Java 21 features (records, virtual threads, pattern matching) are stable |
| HikariCP connection-init-sql | Ensures every connection loads AGE and sets search_path |
| Liquibase enabled=false | Schema handled by Docker init scripts, not Spring Boot startup |
| Pool size = 5 | Virtual threads reduce need for large connection pools |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Removed --enable-preview compiler argument**
- **Found during:** Task 2 (Compilation after structure creation)
- **Issue:** Java 25 JDK compiling with release 21 cannot use --enable-preview (preview is release-specific)
- **Fix:** Removed --enable-preview from compiler, surefire, and failsafe plugins
- **Files modified:** pom.xml
- **Verification:** `mvn compile` succeeds without errors
- **Committed in:** a7fd7e2 (Task 2 commit - pom.xml update bundled)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Auto-fix necessary for compilation. Java 21 features (records, virtual threads) are stable and don't need preview flag.

## Issues Encountered

**Docker not available in WSL environment**
- Integration tests compile but cannot execute
- Docker Desktop WSL integration not enabled
- Tests are syntactically correct and validated via `mvn test-compile`
- Full verification pending Docker availability (same blocker as 01-01)

## User Setup Required

**Docker Desktop WSL Integration required.** Before running integration tests:
1. Enable Docker Desktop WSL integration for this distro
2. Run `docker compose up -d` to start PostgreSQL container
3. Wait for healthy status: `docker compose ps`
4. Run `mvn verify` to execute integration tests

## Next Phase Readiness

- Maven project compiles with all required dependencies
- Spring Boot application structure ready for component development
- Integration tests ready to validate database connectivity when Docker available
- Three-layer architecture established for clean separation of concerns
- **Blocker:** Docker verification pending - requires Docker Desktop WSL integration
- **Blocker:** Integration tests (8 tests) cannot be executed without Docker

---
*Phase: 01-infrastructure*
*Completed: 2026-01-19*
