# Project Research Summary

**Project:** Alexandria Docker Packaging
**Domain:** Docker containerization for Java Spring Boot MCP server with PostgreSQL/pgvector/Apache AGE
**Researched:** 2026-01-22
**Confidence:** HIGH

## Executive Summary

Docker packaging for Alexandria requires a multi-stage Dockerfile using Spring Boot's layered JAR extraction for optimal caching, Eclipse Temurin 21 JRE Alpine as the runtime image, and a critical transport protocol change from STDIO to HTTP/SSE (or Streamable-HTTP) to enable MCP communication within containerized environments. The existing STDIO transport works for local Claude Code integration but cannot function when the application runs inside Docker since MCP clients cannot spawn containers as subprocesses with stdin/stdout communication.

The recommended approach is a two-service Docker Compose architecture: the existing PostgreSQL container (with pgvector + Apache AGE) and a new Java application container. Both services communicate over a dedicated bridge network, with the application using service names (not localhost) for database connectivity. The application image should be approximately 200-400 MB using multi-stage builds, run as a non-root user, and include proper health checks with extended startup periods (120s) to accommodate ONNX model loading.

Key risks center on three areas: (1) transport incompatibility requiring code changes beyond just Dockerfile creation, (2) JVM signal propagation for graceful shutdown, and (3) native memory exhaustion from ONNX model loading outside the JVM heap. All three require attention during the first phase of implementation. The project builds on well-established patterns with high-quality official documentation from Spring, Docker, and Eclipse Temurin.

## Key Findings

### Recommended Stack

The stack combines industry-standard Docker patterns with Spring Boot optimizations for containers. Eclipse Temurin 21 JRE Alpine provides a small (~387 MB), security-focused runtime that is TCK-tested and vendor-neutral. Spring Boot's jarmode tools extract layers (dependencies/spring-boot-loader/snapshot-dependencies/application) for cache-efficient rebuilds where code changes only affect the top layer.

**Core technologies:**
- **Eclipse Temurin 21-jre-alpine**: Runtime base image - balanced size, LTS support, container-aware JVM
- **Spring Boot jarmode tools**: Layered JAR extraction - supersedes deprecated layertools, cache-efficient builds
- **spring-ai-starter-mcp-server-webmvc**: HTTP/SSE transport - replaces STDIO for Docker deployments
- **Docker multi-stage build**: Image optimization - separates build (Maven/JDK) from runtime (JRE only)
- **GitHub Actions + buildx**: CI/CD pipeline - native ghcr.io integration, layer caching

### Expected Features

**Must have (table stakes):**
- Multi-stage Dockerfile for Java app (JDK build, JRE runtime)
- Docker Compose with app + postgres services
- Health check integration (Spring Actuator with readiness/liveness probes)
- Service dependency ordering (depends_on with service_healthy condition)
- Volume mount for documents directory
- Environment variable configuration (database URL, paths)
- Non-root user in container
- Graceful shutdown handling (exec form ENTRYPOINT + Spring lifecycle)
- Spring Boot layered JARs for cache efficiency
- CLI wrapper script for docker exec usage

**Should have (competitive):**
- HTTP/SSE transport option for Docker MCP Toolkit integration
- GitHub Container Registry publishing for easy installation
- Structured JSON logging for aggregation tools
- .env.example file for documentation

**Defer (v2+):**
- Multi-arch images (ARM) - complexity outweighs benefit for personal use
- Kubernetes manifests - Docker Compose sufficient
- Native image (GraalVM) - ONNX compatibility issues, not worth complexity

### Architecture Approach

Two-service Docker Compose architecture with a dedicated bridge network (alexandria-net). The MCP server and CLI share the same Spring Boot application, activated via Spring profiles (`mcp` vs `cli`). PostgreSQL container continues using the existing custom Dockerfile with pgvector + Apache AGE extensions. The Java application container exposes port 8080 for HTTP/SSE MCP transport, connects to PostgreSQL via service name over the internal network, and mounts host documents as a read-only volume.

**Major components:**
1. **alexandria (Java App)** - MCP server + CLI entry point, Spring Boot 3.4 fat JAR, HTTP/SSE transport
2. **postgres** - pgvector + Apache AGE, persistent named volume, health check for dependency ordering
3. **alexandria-net** - Docker bridge network for service-to-service communication

### Critical Pitfalls

1. **STDIO transport incompatible with Docker** - Switch to HTTP/SSE or Streamable-HTTP transport; requires adding spring-ai-starter-mcp-server-webmvc dependency and changing configuration
2. **JVM not receiving SIGTERM** - Use exec form ENTRYPOINT (`["java", "-jar", "app.jar"]`) not shell form; combine with Spring graceful shutdown
3. **Native memory exhaustion from ONNX** - Set container memory 1.5-2x heap limit; use `-XX:MaxRAMPercentage=50.0` instead of fixed -Xmx
4. **Hardcoded localhost database URL** - Use environment variable substitution (`${DB_HOST:localhost}`) and service names in compose
5. **Slow startup causing health failures** - Configure start_period: 120s in health check to accommodate ONNX model loading

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Core Docker Infrastructure

**Rationale:** Must establish Dockerfile and Docker Compose foundation before any other Docker features can be built. Transport change is required for Docker deployment to function at all.

**Delivers:**
- Multi-stage Dockerfile.app with layered JAR extraction
- Updated docker-compose.yml with app service definition
- HTTP/SSE transport configuration (replacing STDIO)
- Non-root user, graceful shutdown, proper health checks
- Environment-based configuration for database connectivity

**Addresses features:**
- Multi-stage Dockerfile, Docker Compose orchestration
- Health checks, service dependency ordering
- Volume mounts, externalized config
- Non-root user, graceful shutdown
- Spring Boot layered JARs

**Avoids pitfalls:**
- STDIO transport incompatibility (transport change is core deliverable)
- JVM signal propagation failure (exec form ENTRYPOINT)
- Native memory exhaustion (memory configuration)
- Hardcoded localhost (environment variables)
- Slow startup health failures (120s start_period)

### Phase 2: Developer Experience

**Rationale:** With core Docker infrastructure working, enhance the developer experience with wrapper scripts and documentation.

**Delivers:**
- CLI wrapper script for docker exec commands
- .env.example with documented configuration options
- .dockerignore for build optimization
- Updated README with Docker usage instructions

**Addresses features:**
- CLI wrapper script
- .env.example file
- Build optimization

**Implements:** Volume mount patterns for documents, configuration externalization patterns

### Phase 3: CI/CD Pipeline

**Rationale:** Once local Docker builds work reliably, automate publishing to GitHub Container Registry for easy installation without git clone.

**Delivers:**
- GitHub Actions workflow for Docker image build
- Automatic tagging from git tags (semantic versioning)
- ghcr.io publishing with attestation
- Layer caching for faster CI builds

**Addresses features:**
- GitHub Container Registry publishing

**Uses:** docker/build-push-action, docker/metadata-action, GitHub Actions cache

### Phase Ordering Rationale

- **Phase 1 first:** Without working Docker infrastructure and transport change, nothing else matters. The transport change from STDIO to HTTP/SSE is a blocking requirement.
- **Phase 2 second:** Developer experience improvements only make sense once the core system works. CLI wrapper depends on working Docker Compose.
- **Phase 3 third:** CI/CD automation should only be built after local development workflow is proven. Publishing broken images wastes effort.
- **All MVP features in Phase 1:** Research indicates clear separation between essential (Phase 1) and nice-to-have (Phases 2-3) features.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** HTTP/SSE transport configuration - may need investigation into Spring AI MCP WebMVC starter specifics and Claude Desktop connector setup

Phases with standard patterns (skip research-phase):
- **Phase 1 (Dockerfile):** Well-documented multi-stage build patterns from Spring Boot official docs
- **Phase 2:** Standard shell scripting and documentation
- **Phase 3:** GitHub Actions Docker workflows have extensive examples and official actions

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Official Spring Boot Docker docs, Eclipse Temurin docs, Spring AI MCP docs all verified |
| Features | HIGH | Docker best practices well-established, feature prioritization based on multiple authoritative sources |
| Architecture | HIGH | Two-service architecture follows Docker Compose conventions, MCP transport options documented |
| Pitfalls | HIGH | Multiple sources confirm each pitfall; official docs + community experience aligned |

**Overall confidence:** HIGH

### Gaps to Address

- **MCP client configuration:** How Claude Desktop connects to HTTP/SSE endpoint needs validation during implementation (Settings > Connectors for Pro+ users, mcp-remote proxy for others)
- **Apache AGE session validation:** Health check should verify AGE extension is loaded; exact health check query needs implementation-time testing
- **ONNX memory footprint:** Actual memory usage under load should be profiled; 1.5-2x heap estimate may need adjustment

## Sources

### Primary (HIGH confidence)
- [Spring Boot Dockerfiles Documentation](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html) - jarmode tools, multi-stage patterns
- [Spring AI MCP STDIO and SSE Server Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html) - transport configuration
- [Spring AI Streamable-HTTP MCP Servers](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html) - forward-compatible transport
- [GitHub Actions Publishing Docker Images](https://docs.github.com/actions/guides/publishing-docker-images) - ghcr.io workflow
- [Docker Multi-Stage Builds](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/) - build optimization
- [Docker Compose Startup Order](https://docs.docker.com/compose/how-tos/startup-order/) - depends_on with health checks

### Secondary (MEDIUM confidence)
- [Best Docker Base Images for Java 2025](https://dev.to/devaaai/best-docker-base-images-and-performance-optimization-for-java-applications-in-2025-kdd) - Temurin vs alternatives
- [9 Tips for Containerizing Spring Boot](https://www.docker.com/blog/9-tips-for-containerizing-your-spring-boot-code/) - Docker best practices
- [Graceful Shutdowns for Containerized Spring Boot](https://medium.com/viascom/graceful-shutdowns-for-containerized-spring-boot-applications-d9c465ce4fd9) - signal handling
- [Docker Blog: MCP Server Best Practices](https://www.docker.com/blog/mcp-server-best-practices/) - MCP in containers

### Tertiary (LOW confidence)
- Memory profiling for ONNX - needs validation during implementation

---
*Research completed: 2026-01-22*
*Ready for roadmap: yes*
