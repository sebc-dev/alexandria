# Stack Research: Docker Packaging

**Domain:** Docker packaging for Java Spring Boot MCP application
**Researched:** 2026-01-22
**Confidence:** HIGH

## Executive Summary

Docker packaging for Alexandria requires a multi-stage build using layered JARs for optimal caching, the Eclipse Temurin or BellSoft Liberica JRE 21 base image, and adding HTTP/SSE transport via Spring AI MCP SDK. The current STDIO-only MCP server needs to support HTTP transport for containerized deployments where STDIO is impractical. GitHub Actions with Docker buildx provides the CI/CD pipeline for publishing to ghcr.io.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Eclipse Temurin | 21-jre-alpine | JRE base image | TCK-tested, vendor-neutral, balanced size (~387 MB vs ~738 MB full JDK), good security posture. Official Adoptium builds with broad community support. |
| Spring Boot jarmode tools | 3.4+ | Layered JAR extraction | Supersedes deprecated layertools. Creates cache-efficient Docker layers: dependencies/spring-boot-loader/snapshot-dependencies/application. AOT/CDS ready out of the box. |
| spring-ai-starter-mcp-server-webmvc | 1.0.0 | HTTP/SSE MCP transport | Adds HTTP-based MCP transport alongside existing STDIO. Uses familiar Spring MVC. Streamable-HTTP is the forward-compatible transport replacing SSE. |
| Docker multi-stage build | - | Image optimization | Separates build (Maven) from runtime (JRE only). Produces ~200-400 MB images vs 600+ MB single-stage. Standard industry practice. |
| GitHub Actions | - | CI/CD pipeline | Native ghcr.io integration with GITHUB_TOKEN. docker/build-push-action provides caching, multi-platform builds. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| docker/login-action | v3 | Registry authentication | Login to ghcr.io before push |
| docker/metadata-action | v5 | Tag/label generation | Automatic semantic tagging from git tags/branches |
| docker/build-push-action | v5+ | Image build/push | Multi-platform builds, layer caching |
| docker/setup-buildx-action | v3 | Buildx configuration | Required for advanced caching, multi-platform |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Docker Compose | Local development | Extend existing compose.yml to add app service |
| Eclipse Temurin JDK 21 | Build stage | Full JDK for Maven compilation |
| Maven wrapper (mvnw) | Reproducible builds | Include in Docker build context |

## Dockerfile Strategy

### Recommended: Multi-Stage with Layered JAR

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Cache Maven dependencies
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /extractor
COPY --from=builder /build/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /application
# Copy layers in order of change frequency (least -> most)
COPY --from=extractor /extractor/extracted/dependencies/ ./
COPY --from=extractor /extractor/extracted/spring-boot-loader/ ./
COPY --from=extractor /extractor/extracted/snapshot-dependencies/ ./
COPY --from=extractor /extractor/extracted/application/ ./
# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
```

### Layer Caching Benefits

| Layer | Contents | Change Frequency |
|-------|----------|------------------|
| dependencies | Third-party JARs | Rarely (pom.xml changes) |
| spring-boot-loader | Spring loader classes | Almost never |
| snapshot-dependencies | SNAPSHOT JARs | Occasionally |
| application | Your code | Every build |

Docker only rebuilds layers that change. Application code changes only affect the top layer.

## MCP Transport Configuration

### Current: STDIO Only (application-mcp.yml)

```yaml
spring:
  main:
    web-application-type: none
  ai:
    mcp:
      server:
        name: alexandria
        version: 0.1.0
        type: SYNC
        stdio: true
```

### Target: HTTP + STDIO Dual Transport

**Option A: WebMVC (Recommended for this project)**

Add dependency to pom.xml:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

Configuration (application-mcp-http.yml):
```yaml
spring:
  ai:
    mcp:
      server:
        name: alexandria
        version: 0.1.0
        type: SYNC
        stdio: true  # Keep STDIO for Claude Code local
        protocol: STREAMABLE  # New recommended transport
        streamable-http:
          mcp-endpoint: /mcp
          keep-alive-interval: 30s
server:
  port: 8080
```

**Why WebMVC over WebFlux:**
- Project already uses blocking JDBC (HikariCP)
- SYNC server type aligns with blocking operations
- Simpler mental model, no reactive paradigm shift
- WebFlux would require reactive database access for full benefit

### Transport Protocol Choice

| Protocol | Status | Use Case |
|----------|--------|----------|
| STDIO | Current | Claude Code local integration |
| SSE | Deprecated | Legacy HTTP clients |
| **Streamable-HTTP** | **Recommended** | Container deployments, multi-client support |

Streamable-HTTP is the forward-compatible choice per MCP spec 2025-03-26. Spring AI supports it via `protocol: STREAMABLE`.

## GitHub Actions Workflow

### Recommended Workflow Structure

```yaml
name: Build and Publish Docker Image

on:
  push:
    branches: [main, master]
    tags: ['v*']
  pull_request:
    branches: [main, master]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      attestations: write
      id-token: write
    steps:
      - uses: actions/checkout@v5

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GitHub Container Registry
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=ref,event=branch
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=sha

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile.app
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Generate attestation
        if: github.event_name != 'pull_request'
        uses: actions/attest-build-provenance@v3
        with:
          subject-name: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          subject-digest: ${{ steps.push.outputs.digest }}
          push-to-registry: true
```

### Key Features

- **Cache optimization**: `cache-from/to: type=gha` uses GitHub Actions cache
- **Semantic tagging**: Automatic tags from git tags/branches
- **Attestation**: Supply chain security via build provenance
- **PR builds**: Build but don't push on PRs (validation only)

## Docker Compose Extension

Extend existing compose.yml for local development:

```yaml
services:
  postgres:
    # ... existing postgres configuration

  alexandria:
    build:
      context: .
      dockerfile: Dockerfile.app
    container_name: alexandria
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: mcp-http
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria
      SPRING_DATASOURCE_USERNAME: alexandria
      SPRING_DATASOURCE_PASSWORD: alexandria
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Multi-stage Dockerfile | Buildpacks (Paketo) | When you need zero Dockerfile maintenance and accept larger images (~2x size) or slower builds |
| Eclipse Temurin | BellSoft Liberica | When you need smallest possible image (<100 MB with Alpaquita), or are using Spring Boot buildpacks (default) |
| Streamable-HTTP | SSE transport | Never for new implementations - SSE is deprecated |
| WebMVC | WebFlux | When you have reactive database access and need non-blocking I/O throughout |
| ghcr.io | Docker Hub | When you need public anonymous pulls without rate limits, or organization already uses Docker Hub |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `eclipse-temurin:21-jdk-*` for runtime | ~350 MB wasted on unused compiler, includes attack surface | `eclipse-temurin:21-jre-alpine` |
| Single-stage Dockerfile | Image contains Maven, sources, build cache (~600+ MB) | Multi-stage with JRE-only final stage |
| `-Djarmode=layertools` | Deprecated in Spring Boot 3.3+ | `-Djarmode=tools -jar app.jar extract --layers` |
| `latest` tag for base images | Non-reproducible builds, security audits fail | Specific version tags: `21-jre-alpine` |
| Running as root | Security anti-pattern, many clusters reject | `USER spring:spring` or non-root user |
| `COPY . .` before dependency resolution | Cache invalidation on any file change | Copy pom.xml first, run dependency:go-offline, then copy src |
| SSE transport for new development | Deprecated protocol | Streamable-HTTP via `protocol: STREAMABLE` |
| spring-ai-starter-mcp-server (STDIO only) in container | No HTTP endpoint for container-to-container communication | spring-ai-starter-mcp-server-webmvc |

## Stack Patterns by Variant

**If deploying to Kubernetes/container orchestrator:**
- Use Streamable-HTTP transport (not STDIO)
- Add health check endpoint via Spring Boot Actuator
- Configure resource limits in deployment manifest
- Use `spring.lifecycle.timeout-per-shutdown-phase` for graceful shutdown

**If need both local Claude Code and remote container access:**
- Keep STDIO profile for local development (`--spring.profiles.active=mcp`)
- Add HTTP profile for container (`--spring.profiles.active=mcp-http`)
- Use profile-specific configuration files

**If startup time is critical:**
- Add CDS (Class Data Sharing) training run in Dockerfile
- Use `java -XX:ArchiveClassesAtExit=application.jsa` during build
- Use `java -XX:SharedArchiveFile=application.jsa` at runtime
- ~25-40% startup improvement

**If image size is critical (<150 MB):**
- Switch to `bellsoft/liberica-runtime-container:jre-21-slim-musl` (~220 MB)
- Or use distroless: `gcr.io/distroless/java21-debian12` (security-focused)

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Spring Boot 3.4.x | Spring AI 1.0.0 | BOM alignment verified |
| Spring AI MCP 1.0.0 | MCP spec 2025-03-26 | Supports STDIO, SSE, Streamable-HTTP |
| Eclipse Temurin 21 | Spring Boot 3.4 | Virtual threads supported |
| jarmode tools | Spring Boot 3.3+ | Replaces deprecated layertools |
| Docker buildx v0.12+ | cache-from: type=gha | GitHub Actions cache integration |

## Sources

- [Spring Boot Dockerfiles Documentation](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html) - Official jarmode tools examples, multi-stage Dockerfile patterns (HIGH confidence)
- [Spring AI MCP STDIO and SSE Server Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html) - Transport configuration, dependency selection (HIGH confidence)
- [Spring AI Streamable-HTTP MCP Servers](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html) - Streamable-HTTP as recommended transport (HIGH confidence)
- [GitHub Actions Publishing Docker Images](https://docs.github.com/actions/guides/publishing-docker-images) - Official ghcr.io workflow (HIGH confidence)
- [Docker Multi-Stage Builds Documentation](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/) - Build optimization patterns (HIGH confidence)
- [Best Docker Base Images for Java 2025](https://dev.to/devaaai/best-docker-base-images-and-performance-optimization-for-java-applications-in-2025-kdd) - Temurin vs Liberica comparison (MEDIUM confidence)
- [9 Tips for Containerizing Spring Boot](https://www.docker.com/blog/9-tips-for-containerizing-your-spring-boot-code/) - Docker best practices (MEDIUM confidence)
- [Spring AI MCP Blog](https://spring.io/blog/2025/09/16/spring-ai-mcp-intro-blog/) - MCP transport overview (HIGH confidence)

---
*Stack research for: Docker packaging of Java Spring Boot MCP application*
*Researched: 2026-01-22*
