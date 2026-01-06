# Phase 1: Project Foundation

- [ ] **Task 1: Initialize Maven project with BOM dependencies**
  - File: `pom.xml`
  - Action: Create Maven project with Spring Boot 3.5.9 parent, Java 25, BOMs (Spring AI 1.1.2, Langchain4j 1.10.0, Resilience4j 2.3.0), and all dependencies from tech-spec
  - Notes: Use exact versions from dependencies.md. Testcontainers 2.x requires `testcontainers-postgresql` artifact (not `postgresql`)

- [ ] **Task 2: Create main application class**
  - File: `src/main/java/dev/alexandria/AlexandriaApplication.java`
  - Action: Standard Spring Boot main class with `@SpringBootApplication`
  - Notes: No special configuration needed - Virtual Threads enabled via properties

- [ ] **Task 3: Create base application.yml**
  - File: `src/main/resources/application.yml`
  - Action: Configure Spring Boot, Resilience4j retry instances, RAG properties, timeout budgets, MCP server
  - Notes: Include all properties from tech-spec: `rag.*`, `alexandria.timeouts.*`, `resilience4j.retry.*`, `spring.threads.virtual.enabled=true`
  - **F1/F11/F12 Remediations:**
    - Add `spring.datasource.hikari.auto-commit: false` (transaction safety)
    - Add `spring.datasource.hikari.connection-init-sql` with HNSW config:
      ```yaml
      connection-init-sql: |
        SET hnsw.ef_search = 100;
        SET hnsw.iterative_scan = relaxed_order;
      ```
    - Add graceful shutdown:
      ```yaml
      server:
        shutdown: graceful
      spring:
        lifecycle:
          timeout-per-shutdown-phase: 30s
      ```

- [ ] **Task 3b: Create logback-spring.xml** *(NEW - F5 Remediation)*
  - File: `src/main/resources/logback-spring.xml`
  - Action: Create minimal logback config with correlationId in log pattern
  - Pattern: `%d{HH:mm:ss.SSS} %5p [%X{correlationId:-NO_CID}] %-40.40logger{39} : %m%n`
  - Notes: Required for AC 12 (correlationId in logs). No ECS format needed for MVP
