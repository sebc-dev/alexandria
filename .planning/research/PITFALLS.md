# Pitfalls Research

**Domain:** Dockerizing Java 21 Spring Boot + MCP Server with ONNX Embeddings
**Researched:** 2026-01-22
**Confidence:** HIGH (verified with official docs and multiple sources)

## Critical Pitfalls

### Pitfall 1: STDIO Transport Incompatible with Docker MCP Server

**What goes wrong:**
The current MCP configuration uses STDIO transport (`spring.ai.mcp.server.stdio: true`), which requires the MCP client to spawn the server as a subprocess with stdin/stdout communication. When the application runs inside Docker, Claude Code cannot spawn a Docker container as a subprocess in the same way.

**Why it happens:**
STDIO transport assumes in-process communication where the client directly manages stdin/stdout of the server process. Docker containers run as isolated processes with their own network namespace.

**How to avoid:**
Switch from STDIO to HTTP/SSE or StreamableHTTP transport for Docker deployment:
```yaml
spring:
  ai:
    mcp:
      server:
        stdio: false  # Disable STDIO
  main:
    web-application-type: servlet  # Enable web server
```

Configure HTTP/SSE endpoint that Claude Code can connect to via network. Note: SSE is deprecated as of MCP spec 2025-03-26 revision; StreamableHTTP is the recommended replacement.

**Warning signs:**
- MCP tools not appearing in Claude Code when using Docker
- "Connection refused" errors
- No response from container despite it being healthy

**Phase to address:**
Phase 1 - Initial Docker containerization must implement HTTP/SSE transport

---

### Pitfall 2: JVM Not Receiving SIGTERM in Docker (Signal Propagation Failure)

**What goes wrong:**
Docker sends SIGTERM to stop containers, but if the JVM doesn't receive it, graceful shutdown fails. The container gets forcefully killed after docker's grace period (default 10s), leading to:
- Incomplete database transactions
- HikariCP connections not properly closed
- ONNX model resources not released

**Why it happens:**
Using shell form in Dockerfile ENTRYPOINT (e.g., `ENTRYPOINT java -jar app.jar`) starts the JVM as a child of `/bin/sh`, which doesn't forward signals. Only PID 1 receives Docker signals.

**How to avoid:**
Use exec form in Dockerfile:
```dockerfile
# CORRECT: Java runs as PID 1, receives signals directly
ENTRYPOINT ["java", "-jar", "app.jar"]

# WRONG: Shell is PID 1, doesn't forward SIGTERM
ENTRYPOINT java -jar app.jar
```

For scripts, use `exec`:
```bash
#!/bin/sh
exec java -jar app.jar
```

Also configure Spring Boot graceful shutdown:
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**Warning signs:**
- Container takes exactly 10s to stop (Docker's default timeout)
- Database connections remain open after container stops
- "Connection reset by peer" errors in dependent services

**Phase to address:**
Phase 1 - Dockerfile must use exec form from the start

---

### Pitfall 3: Native Memory Exhaustion from ONNX Model Loading

**What goes wrong:**
The all-MiniLM-L6-v2 ONNX model (~100MB) loads into native memory outside the JVM heap. With default JVM settings, the container may exceed memory limits and get OOM-killed despite heap being within bounds.

**Why it happens:**
`-Xmx` only limits heap memory. ONNX Runtime allocates native memory for:
- Model weights (~100MB)
- Inference buffers
- Thread-local storage

JVM also uses native memory for metaspace, thread stacks, and JIT compilation.

**How to avoid:**
1. Set container memory 1.5-2x the heap limit:
```yaml
services:
  alexandria:
    deploy:
      resources:
        limits:
          memory: 1G  # For 512MB heap
```

2. Use `-XX:MaxRAMPercentage` instead of fixed `-Xmx`:
```dockerfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50.0", "-jar", "app.jar"]
```
This leaves 50% of container memory for native allocations.

3. Monitor native memory:
```bash
java -XX:NativeMemoryTracking=summary -jar app.jar
```

**Warning signs:**
- Container OOM-killed despite heap utilization being low
- `docker stats` shows memory near limit
- "Cannot allocate memory" in logs without heap OutOfMemoryError

**Phase to address:**
Phase 1 - Memory configuration must account for ONNX from initial containerization

---

### Pitfall 4: Apache AGE Session State Lost in Connection Pool

**What goes wrong:**
Apache AGE requires `LOAD 'age'` and `SET search_path = ag_catalog` on every PostgreSQL session. If connections are pooled and reused, or if a pooler like PgBouncer is used in transaction mode, these session commands may not execute, causing Cypher queries to fail with "function cypher does not exist".

**Why it happens:**
HikariCP's `connection-init-sql` runs when a connection is first established, not on every borrow. If the PostgreSQL server restarts, or if an external pooler (PgBouncer) is added in transaction mode, the session state is lost.

**How to avoid:**
1. Current setup is correct for session mode (HikariCP direct to PostgreSQL):
```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: "LOAD 'age'; SET search_path = ag_catalog, \"$user\", public"
```

2. If adding PgBouncer, use session mode only:
```ini
[databases]
alexandria = host=postgres port=5432 dbname=alexandria pool_mode=session
```

3. Consider defensive validation in code:
```java
// Before AGE queries, verify session state
jdbcTemplate.execute("SELECT ag_catalog._cypher_merge_clause(NULL, NULL, NULL)");
```

**Warning signs:**
- Intermittent "function cypher(unknown, unknown) does not exist" errors
- Graph queries fail after database restarts but work after app restart
- Errors correlate with connection pool recycling

**Phase to address:**
Phase 2 - Health checks should validate AGE session state

---

### Pitfall 5: Slow Startup Causing Health Check Failures

**What goes wrong:**
Spring Boot with ONNX model loading can take 30-60 seconds to start. Default Docker health check parameters (interval=30s, start_period=30s) may mark the container unhealthy before it's ready, triggering restart loops.

**Why it happens:**
ONNX model loading happens during Spring context initialization. The model must be fully loaded into memory and validated before the application is ready. Docker health checks don't distinguish between "still starting" and "failed".

**How to avoid:**
1. Configure appropriate health check timing:
```yaml
services:
  alexandria:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/readiness"]
      interval: 10s
      timeout: 5s
      start_period: 120s  # Allow 2 minutes for startup
      retries: 3
```

2. Separate liveness and readiness in Spring Boot:
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

3. For Kubernetes, use startup probes:
```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30
  periodSeconds: 10
```

**Warning signs:**
- Container keeps restarting during deployment
- `docker logs` shows "Started AlexandriaApplication" but container marked unhealthy
- Orchestrator events show "Liveness probe failed"

**Phase to address:**
Phase 1 - Health check configuration is part of initial Dockerfile/compose

---

### Pitfall 6: Hardcoded localhost Database URL in Container

**What goes wrong:**
The current `application.yml` uses `jdbc:postgresql://localhost:5432/alexandria`. Inside a Docker container, localhost refers to the container itself, not the host machine or another container.

**Why it happens:**
Development configuration works because the app runs directly on the host where PostgreSQL is accessible via localhost. Container networking isolates each container's localhost.

**How to avoid:**
Use environment variable substitution:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:alexandria}
```

In docker-compose:
```yaml
services:
  alexandria:
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: alexandria
    depends_on:
      postgres:
        condition: service_healthy
```

**Warning signs:**
- "Connection refused" immediately after container start
- Works in development but fails in Docker
- `psql` from inside container fails to connect to localhost

**Phase to address:**
Phase 1 - Environment-based configuration is fundamental to containerization

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Using `latest` tag for base image | Always up-to-date | Builds become non-reproducible, may break on updates | Never in production |
| Single-stage Dockerfile | Simpler to write | 2x larger image, includes build tools | Quick prototyping only |
| Running as root in container | No permission issues | Security vulnerability, pod security policies reject | Never |
| Skipping layered JAR extraction | Faster initial build | Every code change rebuilds dependencies layer (5+ min) | Initial development only |
| Copying entire target directory | Simple COPY command | Includes test artifacts, .class files, ~2x size | Never |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| PostgreSQL container | Using `depends_on` without health check | Use `depends_on: postgres: condition: service_healthy` |
| HikariCP in container | Same pool size as development | Reduce to match container CPU allocation (2-3 connections per vCPU) |
| Apache AGE with Docker | Not preloading in shared_preload_libraries | Add `shared_preload_libraries = 'age'` to postgresql.conf |
| ONNX model loading | Packaging model inside JAR | Mount as volume or use init container for faster startup |
| MCP over network | No CORS configuration | Configure CORS for MCP HTTP/SSE endpoints |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Default thread pool with virtual threads | High memory usage, slow response | Virtual threads enabled (`spring.threads.virtual.enabled=true`) but ensure blocking calls are virtual-thread-friendly | 100+ concurrent requests |
| ONNX model per-request loading | Slow first request, high memory | Load model once at startup, reuse for all requests | First request after cold start |
| Large Docker build context | 5+ minute builds | Add `.dockerignore` excluding `target/`, `data/`, `.git/` | Any size project with data volumes |
| Embedded model in JAR | Slow startup, high memory during unpack | Extract ONNX model to volume, load from path | Model > 50MB |
| Default HikariCP pool (10 connections) | Connection exhaustion | Size pool to container resources: `(CPU cores * 2) + 1` | High concurrency |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Hardcoded credentials in docker-compose | Credentials in version control | Use Docker secrets or environment files in `.gitignore` |
| Running JVM as root | Container escape, privilege escalation | `USER 1000:1000` in Dockerfile after copying files |
| Exposing database port (5432) to host | Direct database access bypassing app | Only expose on internal Docker network |
| MCP server without authentication | Unauthorized tool invocation | Implement authentication for HTTP/SSE transport |
| Using JRE with debug enabled | Remote code execution | Use production JRE, no `-agentlib:jdwp` in prod |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No startup progress indication | User thinks container is hung | Add startup banner with progress, use build-time info |
| Silent ONNX loading failure | MCP tools error with cryptic messages | Validate model on startup, fail fast with clear error |
| Container logs too verbose | Hard to find actual errors | Use structured logging, separate debug from info |
| No health endpoint for MCP | User can't verify server is running | Expose `/health` endpoint independent of MCP transport |

## "Looks Done But Isn't" Checklist

- [ ] **Dockerfile:** Has `HEALTHCHECK` instruction, not just relying on orchestrator
- [ ] **Graceful shutdown:** `server.shutdown=graceful` AND exec form ENTRYPOINT
- [ ] **Memory limits:** Container limit > heap + native memory (ONNX)
- [ ] **Network config:** Database URL uses service name, not localhost
- [ ] **Secrets:** No credentials in docker-compose.yml, using env vars or secrets
- [ ] **Build optimization:** Layered JAR extracted, .dockerignore present
- [ ] **Security:** Running as non-root user, no debug ports exposed
- [ ] **Transport:** HTTP/SSE configured for Docker, not STDIO
- [ ] **Health probes:** Separate liveness and readiness with appropriate timeouts
- [ ] **AGE validation:** Health check verifies `LOAD 'age'` session state

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong transport (STDIO in Docker) | LOW | Change config to HTTP/SSE, rebuild image, update client config |
| Signal not forwarded | LOW | Update Dockerfile ENTRYPOINT to exec form, rebuild |
| OOM from native memory | MEDIUM | Adjust container memory limits, may need to resize infrastructure |
| AGE session state lost | HIGH | If using transaction-mode pooler, must switch to session mode or application-level workaround |
| Hardcoded localhost | LOW | Add environment variable substitution, update compose |
| Running as root | MEDIUM | Add USER instruction, may need to fix file permissions |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| STDIO transport incompatibility | Phase 1: Initial containerization | MCP client can connect to Docker container |
| Signal propagation failure | Phase 1: Dockerfile creation | `docker stop` completes in < 5s, not default 10s |
| Native memory exhaustion | Phase 1: Memory configuration | `docker stats` shows headroom, no OOM events |
| AGE session state | Phase 2: Health checks | Graph queries work after pool recycling |
| Slow startup health failures | Phase 1: Health check config | Container becomes healthy on first deploy |
| Hardcoded localhost | Phase 1: Configuration externalization | Container connects to compose network DB |
| Large image size | Phase 1: Multi-stage build | Image < 400MB |
| Root user | Phase 1: Security hardening | `docker exec id` shows non-root |

## Sources

### Official Documentation
- [Spring AI MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [Docker Best Practices: ENTRYPOINT](https://www.docker.com/blog/docker-best-practices-choosing-between-run-cmd-and-entrypoint/)
- [HikariCP GitHub](https://github.com/brettwooldridge/HikariCP)

### Community Resources
- [Graceful Shutdowns for containerized Spring Boot](https://medium.com/viascom/graceful-shutdowns-for-containerized-spring-boot-applications-d9c465ce4fd9)
- [Why Your Dockerized Spring Boot App Will Eventually Crash](https://medium.com/@himanshu675/why-your-dockerized-spring-boot-app-will-eventually-crash-the-2025-memory-leak-epidemic-9d50605f2e5e)
- [Java inside Docker: What you must know](https://developers.redhat.com/blog/2017/03/14/java-inside-docker)
- [Best practices for Java containerization](https://bell-sw.com/announcements/2022/09/01/avoiding-side-effects-of-containerization/)
- [Configure MCP Transport Protocols for Docker](https://mcpcat.io/guides/configuring-mcp-transport-protocols-docker-containers/)
- [HikariCP Connection Pool Issues](https://medium.com/@raphy.26.007/navigating-hikaricp-connection-pool-issues-when-your-database-says-no-more-connections-3203217a14a0)
- [JVM Performance 2025: Virtual Threads and Container Optimization](https://www.atruedev.com/blog/performance/jvm-performance-2025-virtual-threads-graalvm-containers)
- [Spring Boot Docker Layered JARs](https://www.baeldung.com/docker-layers-spring-boot)

---
*Pitfalls research for: Dockerizing Java 21 Spring Boot + MCP Server*
*Researched: 2026-01-22*
