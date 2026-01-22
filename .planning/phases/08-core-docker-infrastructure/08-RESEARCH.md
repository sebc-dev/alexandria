# Phase 8: Core Docker Infrastructure - Research

**Researched:** 2026-01-22
**Domain:** Docker containerization, Spring AI MCP HTTP/SSE transport
**Confidence:** HIGH

## Summary

Phase 8 requires packaging Alexandria as a Docker container with HTTP/SSE transport for MCP communication. The research covers three main areas:

1. **MCP Transport**: Spring AI 1.0.0 provides `spring-ai-starter-mcp-server-webmvc` for HTTP/SSE transport. This replaces the current STDIO-only `spring-ai-starter-mcp-server`. The WebMVC starter auto-configures SSE endpoints at `/sse` and `/mcp/message` on port 8080 by default.

2. **Docker Packaging**: Spring Boot 3.4 supports layered JARs via `java -Djarmode=tools -jar app.jar extract --layers`. A multi-stage Dockerfile should use JDK for build and JRE for runtime, extracting layers (dependencies, spring-boot-loader, snapshot-dependencies, application) for optimal caching.

3. **Container Configuration**: Non-root user execution, exec-form ENTRYPOINT for SIGTERM handling, and Spring Boot's `server.shutdown=graceful` enable safe container lifecycle. Health checks should use Spring Boot Actuator's `/actuator/health` endpoint with extended `start_period` for ONNX model loading.

**Primary recommendation:** Use `spring-ai-starter-mcp-server-webmvc` with SSE protocol, multi-stage Dockerfile with layered JAR extraction, and Spring profiles to toggle between STDIO (local dev) and HTTP/SSE (container) transports.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-ai-starter-mcp-server-webmvc | 1.0.0 | HTTP/SSE MCP transport | Official Spring AI starter, includes spring-boot-starter-web |
| spring-boot-starter-actuator | 3.4.x | Health endpoints | Standard for container health checks |
| eclipse-temurin | 21-jre-jammy | JRE runtime image | Official OpenJDK distribution, well-maintained |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-ai-starter-mcp-server | 1.0.0 | STDIO MCP transport | Local development, Claude Desktop STDIO mode |
| tini | 0.19+ | Init system | Optional - handles zombie reaping if needed |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| eclipse-temurin | bellsoft/liberica | Liberica has CDS support, Temurin more common |
| spring-boot-starter-web (via webmvc) | spring-boot-starter-webflux | WebFlux adds complexity, not needed for sync tools |
| Manual SSE config | Streamable HTTP protocol | Streamable is newer (2025-03-26 spec), SSE more stable |

**Installation:**
```xml
<!-- Replace existing spring-ai-starter-mcp-server -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

## Architecture Patterns

### Recommended Project Structure

```
/
├── Dockerfile              # Existing: PostgreSQL with pgvector + AGE
├── Dockerfile.app          # NEW: Java application container
├── docker-compose.yml      # Updated: postgres + app services
├── src/main/resources/
│   ├── application.yml           # Base config
│   ├── application-mcp.yml       # STDIO transport (existing)
│   ├── application-http.yml      # NEW: HTTP/SSE transport
│   └── application-docker.yml    # NEW: Docker-specific overrides
```

### Pattern 1: Spring Profile-Based Transport Selection

**What:** Use Spring profiles to switch between STDIO and HTTP/SSE transport
**When to use:** Always - enables same codebase for local dev and container
**Example:**

```yaml
# application-http.yml (NEW - for HTTP/SSE transport)
spring:
  main:
    web-application-type: servlet  # Enable web server
    banner-mode: console           # Can show banner in HTTP mode
  ai:
    mcp:
      server:
        name: alexandria
        version: ${project.version:0.1.0}
        type: SYNC
        protocol: SSE              # SSE transport
        stdio: false               # Disable STDIO
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message

server:
  port: 8080
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

### Pattern 2: Multi-Stage Docker Build with Layered JAR

**What:** Build stage extracts JAR layers, runtime stage copies only necessary layers
**When to use:** All Spring Boot Docker builds for optimal layer caching
**Example:**

```dockerfile
# Dockerfile.app
# Build stage
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /builder
COPY target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /application

# Create non-root user
RUN groupadd -r -g 10000 alexandria && \
    useradd -r -g alexandria -u 10000 -s /bin/false alexandria

# Copy layers in order of change frequency (least to most)
COPY --from=builder --chown=alexandria:alexandria /builder/extracted/dependencies/ ./
COPY --from=builder --chown=alexandria:alexandria /builder/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=alexandria:alexandria /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=alexandria:alexandria /builder/extracted/application/ ./

USER alexandria
EXPOSE 8080

# Exec form for proper signal handling
ENTRYPOINT ["java", "-jar", "application.jar"]
```

### Pattern 3: Docker Compose Service Dependencies

**What:** Use healthcheck + depends_on with condition: service_healthy
**When to use:** When app needs database to be fully ready before starting
**Example:**

```yaml
services:
  postgres:
    # ... existing postgres config ...
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria -d alexandria"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  app:
    build:
      context: .
      dockerfile: Dockerfile.app
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s  # ONNX model loading takes time
```

### Anti-Patterns to Avoid

- **Shell form ENTRYPOINT:** `ENTRYPOINT java -jar app.jar` - signals not forwarded to Java process
- **Running as root:** Default Docker user is root, security risk
- **Single-layer COPY:** `COPY target/*.jar app.jar` - rebuilds entire layer on any change
- **Hardcoded database URLs:** Use environment variables for all connection strings
- **`latest` tag:** Always use specific version tags for reproducibility

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MCP SSE transport | Custom SSE endpoints | spring-ai-starter-mcp-server-webmvc | Auto-configures endpoints, handles MCP protocol |
| Health checks | Custom /health endpoint | Spring Boot Actuator | Standard, configurable, production-ready |
| Layered JAR extraction | Manual JAR unpacking | `java -Djarmode=tools -jar app.jar extract` | Built-in Spring Boot feature |
| Signal handling | Custom shutdown hooks | `server.shutdown=graceful` | Handles SIGTERM properly out of box |
| Init system | Custom PID 1 handling | exec form ENTRYPOINT or tini | Java handles signals fine with exec form |

**Key insight:** Spring Boot 3.4 and Spring AI 1.0.0 provide all infrastructure needed for containerized MCP servers. The only custom code needed is Spring profile configuration.

## Common Pitfalls

### Pitfall 1: STDIO Pollution in HTTP Mode

**What goes wrong:** Logging to stdout/stderr breaks STDIO MCP protocol
**Why it happens:** Logback defaults to console appender
**How to avoid:** Use separate logback profiles - existing `application-mcp.yml` disables console, `application-http.yml` can enable it
**Warning signs:** MCP client can't parse responses, "invalid JSON" errors

### Pitfall 2: Health Check Fails During ONNX Loading

**What goes wrong:** Container marked unhealthy before model loads, restart loop
**Why it happens:** Default health check start_period too short (0-30s), ONNX model takes 60-120s
**How to avoid:** Set `start_period: 120s` in docker-compose healthcheck
**Warning signs:** Container restarts repeatedly, never reaches "healthy" state

### Pitfall 3: Signals Not Reaching Java Process

**What goes wrong:** Container doesn't shut down gracefully, data corruption possible
**Why it happens:** Shell form ENTRYPOINT runs shell as PID 1, shell doesn't forward SIGTERM
**How to avoid:** Use exec form `ENTRYPOINT ["java", "-jar", "app.jar"]`
**Warning signs:** Container takes 10+ seconds to stop (Docker's SIGKILL timeout)

### Pitfall 4: Database Connection Before Postgres Ready

**What goes wrong:** App fails to start, connection refused errors
**Why it happens:** Docker starts services in parallel, app tries to connect before postgres is ready
**How to avoid:** Use `depends_on` with `condition: service_healthy` and postgres healthcheck
**Warning signs:** "Connection refused" errors in app logs on first startup

### Pitfall 5: Wrong Profile Activated in Container

**What goes wrong:** Container starts in STDIO mode, no HTTP endpoint available
**Why it happens:** SPRING_PROFILES_ACTIVE not set or set incorrectly
**How to avoid:** Set `SPRING_PROFILES_ACTIVE=http,docker` in docker-compose environment
**Warning signs:** No web server started, `/sse` endpoint returns 404

### Pitfall 6: ONNX Model Download Requires Network

**What goes wrong:** First startup fails in air-gapped environment
**Why it happens:** LangChain4j downloads ONNX model on first use if not cached
**How to avoid:** Document network requirement on first run, or pre-download model during build
**Warning signs:** Timeout errors during model initialization, "unable to download" messages

## Code Examples

### Spring AI MCP WebMVC Configuration

```yaml
# Source: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html
spring:
  ai:
    mcp:
      server:
        name: alexandria
        version: 0.1.0
        type: SYNC
        protocol: SSE
        stdio: false
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        capabilities:
          tool: true
          resource: false
          prompt: false
```

### Spring Boot Graceful Shutdown

```yaml
# Source: https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

### Docker Compose Environment Variables

```yaml
# Source: https://docs.spring.io/spring-boot/reference/features/external-config.html
services:
  app:
    environment:
      SPRING_PROFILES_ACTIVE: http,docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria?sslmode=disable
      SPRING_DATASOURCE_USERNAME: alexandria
      SPRING_DATASOURCE_PASSWORD: alexandria
      LOG_LEVEL: ${LOG_LEVEL:-INFO}
```

### Non-Root User Creation

```dockerfile
# Source: https://www.docker.com/blog/9-tips-for-containerizing-your-spring-boot-code/
# Use UID/GID > 10000 for security best practice
RUN groupadd -r -g 10000 alexandria && \
    useradd -r -g alexandria -u 10000 -s /bin/false alexandria
USER alexandria
```

### Claude Code MCP Configuration (SSE)

```json
// Source: https://github.com/anthropics/claude-code/issues/9522
// .mcp.json for Claude Code CLI
{
  "mcpServers": {
    "alexandria": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Fat JAR in single layer | Layered JAR extraction | Spring Boot 2.3+ (2020) | Faster rebuilds |
| `spring-ai-starter-mcp-server` STDIO only | `spring-ai-starter-mcp-server-webmvc` SSE | Spring AI 1.0.0 (2025) | Remote MCP clients supported |
| SSE protocol | Streamable HTTP protocol | MCP spec 2025-03-26 | SSE still supported, Streamable newer |
| Manual signal handling | `server.shutdown=graceful` | Spring Boot 2.3+ (2020) | Built-in graceful shutdown |

**Deprecated/outdated:**
- `jarmode=layertools`: Replaced by `jarmode=tools` in Spring Boot 3.2+
- Direct SSE endpoint configuration: Use `spring.ai.mcp.server.protocol=SSE` instead

## Open Questions

1. **ONNX Model Caching Location**
   - What we know: Model is downloaded on first use by LangChain4j
   - What's unclear: Exact cache directory, how to pre-populate in Docker image
   - Recommendation: Test first startup, document network requirement, consider volume mount for cache

2. **Memory Requirements for ONNX**
   - What we know: PROJECT.md states "~100MB RAM" for embedding model
   - What's unclear: Peak memory during model loading, total container memory needed
   - Recommendation: Start with 2GB memory limit, monitor and adjust

3. **Claude Desktop SSE Configuration**
   - What we know: Claude Desktop requires Settings > Connectors for remote servers, not config file
   - What's unclear: Exact UI steps for Claude Desktop 2025 version
   - Recommendation: Document both Claude Code (.mcp.json) and Claude Desktop (Settings > Connectors) approaches

## Sources

### Primary (HIGH confidence)
- [Spring AI MCP Server Boot Starter Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) - Transport configuration, protocol options
- [Spring AI STDIO and SSE MCP Servers](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html) - SSE endpoint configuration
- [Spring Boot Dockerfiles](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html) - Layered JAR extraction commands
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) - Environment variable binding
- [Docker Compose depends_on](https://docs.docker.com/compose/how-tos/startup-order/) - Service health dependencies

### Secondary (MEDIUM confidence)
- [Docker 9 Tips for Spring Boot](https://www.docker.com/blog/9-tips-for-containerizing-your-spring-boot-code/) - Non-root user, multi-stage builds
- [Baeldung Docker Layers Spring Boot](https://www.baeldung.com/docker-layers-spring-boot) - Layered JAR patterns
- [Graceful Shutdown Spring Boot Containers](https://dev.to/niksta/graceful-shutdowns-for-containerized-spring-boot-applications-5a8n) - SIGTERM handling

### Tertiary (LOW confidence)
- Claude Desktop SSE configuration behavior (based on GitHub discussions, may change)
- ONNX model memory benchmarks (not officially documented)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official Spring AI documentation, verified Maven coordinates
- Architecture: HIGH - Official Spring Boot and Docker documentation
- Pitfalls: MEDIUM - Combination of official docs and community experience

**Research date:** 2026-01-22
**Valid until:** 2026-03-22 (60 days - Spring AI/Boot stable, Docker patterns stable)
