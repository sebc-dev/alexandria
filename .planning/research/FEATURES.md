# Feature Research: Dockerized Java MCP Application

**Domain:** Container deployment for Java MCP server
**Researched:** 2026-01-22
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Multi-stage Dockerfile | Standard for Java apps to avoid bloated images; separates build from runtime | LOW | Use `eclipse-temurin:21-jdk-jammy` for build, `eclipse-temurin:21-jre-jammy` for runtime |
| Docker Compose orchestration | Users expect `docker compose up` to just work | LOW | Define app + postgres services with proper depends_on |
| Health checks | Required for depends_on condition: service_healthy | LOW | Use Spring Actuator `/actuator/health` endpoint |
| Service dependency ordering | App must wait for database to be ready | LOW | `depends_on: postgres: condition: service_healthy` |
| Externalized configuration | Database URLs, paths, etc. must be configurable at runtime | LOW | Use environment variables, not baked-in config |
| Volume mounts for data | Documents to index must be accessible from host | LOW | Mount host directory to container path |
| Non-root user | Security requirement; default for production containers | LOW | Create dedicated user in Dockerfile |
| Graceful shutdown | SIGTERM must be handled properly for clean DB connection closure | MEDIUM | Configure `server.shutdown=graceful` + exec form ENTRYPOINT |
| Container-aware JVM | JVM must respect container memory limits | LOW | Java 21 is container-aware by default (UseContainerSupport=true) |
| Log to stdout | Docker captures stdout/stderr; file logging doesn't work well | LOW | Spring Boot logs to console by default |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Spring Boot layered JARs | Faster rebuilds by caching dependency layers separately from application code | LOW | Use `-Djarmode=tools` extraction in Dockerfile |
| HTTP/SSE transport option | Enables Docker MCP Toolkit integration and remote access | MEDIUM | Spring AI MCP supports both STDIO and HTTP; add profile-based config |
| CLI wrapper script | Simplifies running CLI commands via docker exec | LOW | Shell script that wraps `docker exec -it alexandria-app java -jar ... --spring.profiles.active=cli` |
| GitHub Container Registry publishing | Easy installation via `ghcr.io/user/alexandria:latest` | MEDIUM | GitHub Actions workflow for build + push |
| .env file support | Clean configuration without editing docker-compose.yml | LOW | Docker Compose natively supports .env files |
| Structured JSON logging | Machine-parseable logs for aggregation tools | LOW | Configure Logback with JSON encoder |
| Log rotation | Prevent disk exhaustion from container logs | LOW | Configure Docker json-file driver with max-size/max-file |
| JVM memory tuning | Optimize for container constraints | LOW | Use `-XX:MaxRAMPercentage=75` instead of fixed -Xmx |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Kubernetes manifests | "Should support K8s deployment" | Overkill for personal tool; adds complexity without value; Docker Compose is sufficient | Keep Docker Compose only; K8s users can write their own manifests |
| Auto-scaling | "What if I have lots of documents" | Personal use case has predictable load; complexity of orchestration not justified | Single container with adequate resources |
| Secrets management via Vault | "Secrets should be secure" | Adds external dependency; personal tool doesn't need enterprise security | Use Docker secrets or .env file (not committed to git) |
| Embedded database option | "Don't want to run PostgreSQL" | pgvector + AGE require PostgreSQL; H2/SQLite cannot provide vector+graph capabilities | Keep PostgreSQL; it's already containerized |
| Native image (GraalVM) | "Faster startup" | ONNX embedding model may not work with GraalVM; complexity of native-image config; not worth the effort for long-running service | JIT is fine; container starts in seconds anyway |
| Multiple embedding models | "Support different models" | all-MiniLM-L6-v2 is sufficient; adding models increases image size and complexity | Keep single model; works well |
| REST API | "Alternative to MCP" | MCP is the integration point for Claude Code; REST adds maintenance burden without clear user | MCP only; CLI for direct access |
| Watch mode for indexing | "Auto-index on file change" | Adds inotify/polling complexity; manual reindex via CLI is sufficient for personal use | Manual `alexandria index` command |
| Web UI | "Nice to have" | Personal tool accessed via Claude Code; web UI adds frontend complexity | CLI for debugging; MCP for normal use |
| Multi-arch images (ARM) | "Support M1/M2 Macs" | AGE compilation for ARM is complex; most dev machines are x86_64; adds CI complexity | x86_64 only initially; add ARM if requested |

## Feature Dependencies

```
[Docker Compose service definition]
    |--requires--> [Dockerfile]
    |--requires--> [Health check endpoint]
                       |--requires--> [Spring Actuator dependency]

[depends_on: service_healthy]
    |--requires--> [PostgreSQL health check]
    |--requires--> [App health check]

[HTTP/SSE transport]
    |--requires--> [Spring AI MCP HTTP config]
    |--enhances--> [Docker MCP Toolkit compatibility]

[CLI wrapper script]
    |--requires--> [Docker Compose running]
    |--requires--> [CLI profile configuration]

[GitHub Container Registry]
    |--requires--> [Dockerfile working]
    |--requires--> [GitHub Actions workflow]

[Layered JARs]
    |--enhances--> [Dockerfile build speed]
    |--independent--> [Application functionality]
```

### Dependency Notes

- **Docker Compose requires Dockerfile:** Can't define app service without knowing how to build it
- **depends_on: service_healthy requires health checks:** Both PostgreSQL and app need health check definitions
- **HTTP/SSE transport is optional:** STDIO still works for local use; HTTP enables Docker MCP Toolkit
- **GitHub Container Registry requires working Dockerfile:** CI/CD builds on top of local build working
- **Layered JARs are independent:** Optimization that doesn't affect functionality

## MVP Definition

### Launch With (v1.1)

Minimum viable product for Docker deployment.

- [x] Multi-stage Dockerfile for Java app (JDK build, JRE runtime)
- [x] Docker Compose with app + postgres services
- [x] Health check integration (Spring Actuator)
- [x] Service dependency ordering (depends_on with condition)
- [x] Volume mount for documents directory
- [x] Environment variable configuration (database URL, paths)
- [x] Non-root user in container
- [x] Graceful shutdown handling
- [x] Spring Boot layered JARs for cache efficiency
- [x] CLI wrapper script

### Add After Validation (v1.1.x)

Features to add once core is working.

- [ ] HTTP/SSE transport option — trigger: user wants Docker MCP Toolkit integration
- [ ] GitHub Container Registry publishing — trigger: want easy installation without git clone
- [ ] Structured JSON logging — trigger: need log aggregation
- [ ] .env.example file — trigger: documentation completeness

### Future Consideration (v1.2+)

Features to defer until product-market fit is established.

- [ ] Multi-arch images (ARM) — defer: complexity; wait for user request
- [ ] Kubernetes manifests — defer: Docker Compose sufficient for personal use
- [ ] Helm chart — defer: no K8s support planned

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Multi-stage Dockerfile | HIGH | LOW | P1 |
| Docker Compose orchestration | HIGH | LOW | P1 |
| Health checks | HIGH | LOW | P1 |
| Service dependency ordering | HIGH | LOW | P1 |
| Volume mounts | HIGH | LOW | P1 |
| Externalized config | HIGH | LOW | P1 |
| Non-root user | MEDIUM | LOW | P1 |
| Graceful shutdown | MEDIUM | LOW | P1 |
| Layered JARs | MEDIUM | LOW | P1 |
| CLI wrapper script | HIGH | LOW | P1 |
| HTTP/SSE transport | MEDIUM | MEDIUM | P2 |
| GitHub Actions CI/CD | MEDIUM | MEDIUM | P2 |
| .env.example | LOW | LOW | P2 |
| Structured JSON logging | LOW | LOW | P3 |
| Multi-arch images | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for v1.1 launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Technical Implementation Notes

### Dockerfile Recommendations

Based on 2025 best practices:

1. **Base image:** `eclipse-temurin:21-jre-jammy` (industry standard, excellent security, optimized for containers)
2. **Multi-stage:** Build stage with JDK, runtime stage with JRE
3. **Layered JARs:** Use Spring Boot's `-Djarmode=tools` for layer extraction
4. **Non-root:** Create user with fixed UID (e.g., 10001) for consistent permissions
5. **ENTRYPOINT:** Use exec form `["java", "org.springframework.boot.loader.launch.JarLauncher"]`

### Spring Boot Configuration

1. **Graceful shutdown:** Set `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`
2. **Health endpoint:** Add `spring-boot-starter-actuator` dependency
3. **Container-aware JVM:** Java 21 has `UseContainerSupport` enabled by default
4. **Memory:** Use `-XX:MaxRAMPercentage=75` to leave headroom for non-heap memory

### Docker Compose Patterns

1. **Health check for PostgreSQL:**
   ```yaml
   healthcheck:
     test: ["CMD-SHELL", "pg_isready -U alexandria -d alexandria"]
     interval: 10s
     timeout: 5s
     retries: 5
     start_period: 30s
   ```

2. **Depends_on with condition:**
   ```yaml
   depends_on:
     postgres:
       condition: service_healthy
   ```

3. **Volume mounts:**
   - Documents: `./docs:/app/docs:ro`
   - Data persistence: `./data:/var/lib/postgresql/data`

### MCP Transport Considerations

Current: STDIO transport (works with Claude Code directly)
Optional: HTTP/SSE transport (enables Docker MCP Toolkit)

For STDIO in containers:
- Container must be started with `-i` flag for stdin
- Works with `docker run -i` or docker exec
- Claude Code configures MCP with docker command

For HTTP/SSE:
- Requires exposed port (e.g., 8080)
- Spring AI MCP supports `spring.ai.mcp.server.transport=http`
- Enables multiple clients and remote access

## Sources

- [Docker Official: Multi-stage Builds](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/)
- [Spring Boot: Dockerfiles Documentation](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html)
- [Docker: Control Startup Order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Baeldung: Reusing Docker Layers with Spring Boot](https://www.baeldung.com/docker-layers-spring-boot)
- [Docker Blog: Top 5 MCP Server Best Practices](https://www.docker.com/blog/mcp-server-best-practices/)
- [Docker Blog: Add MCP Servers to Claude Code with MCP Toolkit](https://www.docker.com/blog/add-mcp-servers-to-claude-code-with-mcp-toolkit/)
- [DEV Community: Best Docker Base Images for Java 2025](https://dev.to/devaaai/best-docker-base-images-and-performance-optimization-for-java-applications-in-2025-kdd)
- [Medium: Graceful Shutdowns for Containerized Spring Boot](https://medium.com/viascom/graceful-shutdowns-for-containerized-spring-boot-applications-d9c465ce4fd9)
- [Red Hat: Java 17 Container Awareness](https://developers.redhat.com/articles/2022/04/19/java-17-whats-new-openjdks-container-awareness)
- [Datadog: Java on Containers Guide](https://www.datadoghq.com/blog/java-on-containers/)

---
*Feature research for: Dockerized Java MCP Application*
*Researched: 2026-01-22*
