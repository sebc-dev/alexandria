# Architecture Research: Docker Packaging for Alexandria

**Domain:** Docker multi-service architecture for Java MCP server with PostgreSQL
**Researched:** 2026-01-22
**Confidence:** HIGH

## System Overview

```
                         Host Machine
    ┌─────────────────────────────────────────────────────────────────┐
    │                                                                  │
    │   ┌──────────────────────────────────────────────────────────┐  │
    │   │                    Docker Network                         │  │
    │   │                   (alexandria-net)                        │  │
    │   │                                                           │  │
    │   │   ┌─────────────────┐      ┌─────────────────────────┐   │  │
    │   │   │   alexandria    │      │      postgres           │   │  │
    │   │   │   (Java App)    │      │   (pgvector + AGE)      │   │  │
    │   │   │                 │      │                         │   │  │
    │   │   │  ┌───────────┐  │      │  ┌─────────────────┐    │   │  │
    │   │   │  │ MCP Server│◄─┼──────┼─►│  PostgreSQL 17  │    │   │  │
    │   │   │  │ (HTTP/SSE)│  │ JDBC │  │  + pgvector     │    │   │  │
    │   │   │  └───────────┘  │      │  │  + Apache AGE   │    │   │  │
    │   │   │  ┌───────────┐  │      │  └─────────────────┘    │   │  │
    │   │   │  │    CLI    │  │      │                         │   │  │
    │   │   │  └───────────┘  │      └─────────┬───────────────┘   │  │
    │   │   └────────┬────────┘                │                   │  │
    │   │            │                         │                   │  │
    │   └────────────┼─────────────────────────┼───────────────────┘  │
    │                │                         │                      │
    │       ┌────────┴────────┐       ┌────────┴────────┐             │
    │       │   Port 8080     │       │   Port 5432     │             │
    │       │  (MCP HTTP)     │       │  (PostgreSQL)   │             │
    │       └────────┬────────┘       └────────┬────────┘             │
    │                │                         │                      │
    │   ┌────────────┴───────────┐  ┌──────────┴──────────┐          │
    │   │  ~/docs (documents)    │  │  ./data (pg data)   │          │
    │   │  (bind mount, RO)      │  │  (named volume)     │          │
    │   └────────────────────────┘  └─────────────────────┘          │
    └─────────────────────────────────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │  Claude Desktop │
                    │  (MCP Client)   │
                    └─────────────────┘
```

## Component Responsibilities

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| alexandria | Java MCP server + CLI entry point | Spring Boot 3.4 fat JAR |
| postgres | Vector storage, full-text search, knowledge graph | PostgreSQL 17 + pgvector + AGE |
| alexandria-net | Service-to-service communication | Docker bridge network |
| documents volume | Host documents for indexing | Bind mount (read-only recommended) |
| pgdata volume | PostgreSQL data persistence | Named volume |

## Recommended Docker Architecture

### Service Structure

Two-service architecture with the existing PostgreSQL container and a new Java application container:

**Why two services (not three):**
- MCP server and CLI share the same Spring Boot application
- Profile-based activation (`--spring.profiles.active=mcp` or `cli`)
- Single JAR, single image, multiple entry points

### Network Configuration

Use a dedicated bridge network for service isolation:

```yaml
networks:
  alexandria-net:
    driver: bridge
```

**Rationale:**
- Service discovery by container name (e.g., `postgres:5432`)
- Isolates Alexandria services from other Docker workloads
- Enables port exposure control per-service

### Volume Strategy

| Volume | Type | Purpose | Access |
|--------|------|---------|--------|
| pgdata | Named volume | PostgreSQL data persistence | read-write |
| documents | Bind mount | Host documentation to index | read-only |
| config | Bind mount (optional) | Custom application.yml | read-only |

**Why named volume for pgdata:**
- Docker manages permissions and lifecycle
- Portable across host systems
- Survives container recreation

**Why bind mount for documents:**
- Users need direct host filesystem access
- Documents change frequently outside container
- Read-only prevents accidental modification

## Docker Compose Configuration

### Complete docker-compose.yml Structure

```yaml
services:
  postgres:
    build:
      context: .
      dockerfile: Dockerfile.postgres
    container_name: alexandria-postgres
    networks:
      - alexandria-net
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-alexandria}
      POSTGRES_DB: alexandria
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./src/main/resources/db/changelog:/docker-entrypoint-initdb.d/changelog:ro
      - ./scripts/init-db.sh:/docker-entrypoint-initdb.d/01-init-db.sh:ro
    shm_size: 3g
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria -d alexandria"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped

  alexandria:
    build:
      context: .
      dockerfile: Dockerfile.app
    container_name: alexandria-app
    networks:
      - alexandria-net
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: mcp
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria?sslmode=disable
      SPRING_DATASOURCE_USERNAME: alexandria
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-alexandria}
      ALEXANDRIA_MCP_ALLOWED_PATHS: /docs
    volumes:
      - ${ALEXANDRIA_DOCS_PATH:-~/docs}:/docs:ro
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped

networks:
  alexandria-net:
    driver: bridge

volumes:
  pgdata:
```

### Environment Variables (.env file)

```bash
# PostgreSQL credentials
POSTGRES_PASSWORD=your-secure-password

# Document paths (host paths)
ALEXANDRIA_DOCS_PATH=/home/user/docs

# MCP server port (optional, defaults to 8080)
MCP_SERVER_PORT=8080
```

## Java Application Dockerfile

### Multi-Stage Build Pattern

```dockerfile
# ===========================================
# Stage 1: Build
# ===========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Cache dependencies (changes less often than code)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Build application
COPY src ./src
RUN mvn package -DskipTests

# ===========================================
# Stage 2: Runtime
# ===========================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Alexandria RAG Project"
LABEL description="Alexandria MCP Server and CLI"

# Create non-root user
RUN addgroup -S alexandria && adduser -S alexandria -G alexandria

# Create directories with proper ownership
RUN mkdir -p /app /docs && chown -R alexandria:alexandria /app /docs

WORKDIR /app

# Copy JAR from build stage
COPY --from=build --chown=alexandria:alexandria /build/target/*.jar app.jar

# Install curl for healthcheck
RUN apk add --no-cache curl

# Switch to non-root user
USER alexandria

# Expose MCP HTTP port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Default to MCP server mode
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Key decisions:**
- Eclipse Temurin 21 JRE Alpine: small footprint (~150MB), LTS support
- Non-root user: security best practice
- Container-aware JVM: respects Docker memory limits
- curl installed: enables HTTP healthcheck

## MCP Transport Configuration

### HTTP/SSE for Docker (Recommended)

```yaml
# application-mcp.yml (or environment variables)
spring:
  ai:
    mcp:
      server:
        name: alexandria
        version: 1.0.0
        type: SYNC
        sse-endpoint: /mcp/sse
        sse-message-endpoint: /mcp/messages
        keep-alive-interval: 30s

server:
  address: 0.0.0.0  # Listen on all interfaces for Docker
  port: 8080
```

**Dependency change required:**
```xml
<!-- Replace spring-ai-starter-mcp-server with WebMVC variant -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

### Transport Options Comparison

| Transport | Use Case | Docker Config | Claude Desktop |
|-----------|----------|---------------|----------------|
| STDIO | Local CLI, Claude Code | Not for Docker | stdio command |
| HTTP/SSE | Docker deployment | Port 8080 exposed | Settings > Connectors (Pro+) |
| Streamable-HTTP | Future-proof | Same as SSE | Replaces SSE |

**Recommendation:** Start with HTTP/SSE, plan migration to Streamable-HTTP as MCP ecosystem matures.

### Claude Desktop Connection

For Docker-deployed MCP server, users configure via Settings > Connectors:
- URL: `http://localhost:8080/mcp/sse`
- Available on: Pro, Max, Team, Enterprise plans

For stdio fallback (local development), use mcp-remote proxy:
```json
{
  "mcpServers": {
    "alexandria": {
      "command": "npx",
      "args": ["mcp-remote", "http://localhost:8080/mcp/sse"]
    }
  }
}
```

## Data Flow

### Indexing Flow (CLI or MCP Tool)

```
User Request (index /docs/project)
        │
        ▼
┌───────────────────┐
│  Alexandria App   │
│  (CLI or MCP)     │
└────────┬──────────┘
         │ Read files from /docs
         ▼
┌───────────────────┐
│  /docs volume     │
│  (bind mount)     │
└────────┬──────────┘
         │ Parse markdown, generate embeddings
         ▼
┌───────────────────┐
│  Alexandria App   │
│  (IngestionSvc)   │
└────────┬──────────┘
         │ JDBC over alexandria-net
         ▼
┌───────────────────┐
│    PostgreSQL     │
│  (pgvector/AGE)   │
└───────────────────┘
```

### Search Flow (MCP Tool)

```
Claude Code/Desktop Request
        │
        ▼
┌───────────────────┐
│  MCP Client       │
│  (HTTP/SSE)       │
└────────┬──────────┘
         │ HTTP POST to :8080
         ▼
┌───────────────────┐
│  Alexandria App   │
│  (SearchService)  │
└────────┬──────────┘
         │ Vector similarity + FTS query
         ▼
┌───────────────────┐
│    PostgreSQL     │
│  (pgvector/AGE)   │
└────────┬──────────┘
         │ Results
         ▼
┌───────────────────┐
│  Alexandria App   │
│  (MCP Response)   │
└────────┬──────────┘
         │ SSE stream
         ▼
┌───────────────────┐
│  MCP Client       │
│  (Tool Result)    │
└───────────────────┘
```

## Build and Startup Order

### Dependency Graph

```
Dockerfile.postgres ─────┐
                         │
pom.xml dependencies ────┼──► docker compose build
                         │
Dockerfile.app ──────────┘
                               │
                               ▼
                    docker compose up
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                │                │
         postgres              │                │
         (starts first)        │                │
              │                │                │
              │ healthcheck    │                │
              ▼                │                │
         postgres              │                │
         (healthy)             │                │
              │                │                │
              │                │                │
              └────────────────┼────────────────┘
                               │
                               ▼
                          alexandria
                      (depends_on: postgres
                       condition: service_healthy)
                               │
                               │ Liquibase migrations
                               │ (if enabled)
                               │
                               ▼
                          alexandria
                          (healthy)
                               │
                               ▼
                        Ready for requests
```

### Build Commands

```bash
# Build both images
docker compose build

# Build specific service
docker compose build alexandria

# Build with no cache (force rebuild)
docker compose build --no-cache

# Start services
docker compose up -d

# View logs
docker compose logs -f alexandria

# Stop services
docker compose down

# Full reset (delete data)
docker compose down -v
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Single user | Current architecture sufficient |
| Small team (<10) | Increase postgres connection pool, consider replica |
| Production/Multi-tenant | Add load balancer, multiple alexandria replicas, pgbouncer |

### First Bottleneck: Database Connections

- **Symptom:** Connection pool exhaustion under load
- **Solution:** Increase `maximum-pool-size`, add pgbouncer connection pooler

### Second Bottleneck: Embedding Generation

- **Symptom:** Slow ingestion for large document sets
- **Solution:** Move embedding generation to separate worker service

## Anti-Patterns

### Anti-Pattern 1: Running as Root

**What people do:** Skip USER instruction in Dockerfile
**Why it's wrong:** Container compromise = host compromise potential
**Do this instead:** Create dedicated non-root user in Dockerfile

### Anti-Pattern 2: Hardcoded Database URL

**What people do:** Put `jdbc:postgresql://localhost:5432` in application.yml
**Why it's wrong:** Breaks in Docker (localhost is the container, not the host)
**Do this instead:** Use service name (`postgres`) or environment variable injection

### Anti-Pattern 3: Using `depends_on` Without Healthcheck

**What people do:** `depends_on: [postgres]` without condition
**Why it's wrong:** Container starts before PostgreSQL accepts connections
**Do this instead:** Use `depends_on: postgres: condition: service_healthy`

### Anti-Pattern 4: Bind Mount for Database Data

**What people do:** `./data:/var/lib/postgresql/data`
**Why it's wrong:** Permission issues, portability problems
**Do this instead:** Use named volume for database data

### Anti-Pattern 5: Exposing Database Port Publicly

**What people do:** Expose 5432 to 0.0.0.0
**Why it's wrong:** Security risk, database accessible from network
**Do this instead:** Only expose to localhost (`127.0.0.1:5432:5432`) or internal network only

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Claude Desktop | HTTP/SSE to :8080 | Settings > Connectors for Pro+ |
| Claude Code | STDIO (local) or HTTP/SSE (Docker) | mcp-remote proxy for stdio-to-HTTP |
| Host filesystem | Bind mount to /docs | Read-only recommended |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| alexandria <-> postgres | JDBC over bridge network | Use service name as hostname |
| MCP client <-> alexandria | HTTP/SSE on port 8080 | Keep-alive enabled for long connections |

## File Structure for Docker Packaging

```
sqlite-rag/
├── docker-compose.yml          # Main compose file (updated)
├── Dockerfile                   # Renamed to Dockerfile.postgres
├── Dockerfile.app              # NEW: Java application
├── .dockerignore               # NEW: Exclude target/, .git, etc.
├── .env.example                # NEW: Environment template
├── postgresql.conf             # Existing: PG configuration
├── scripts/
│   ├── init-db.sh             # Existing: DB initialization
│   └── healthcheck.sh         # Existing: PG healthcheck
└── src/
    └── main/
        └── resources/
            ├── application.yml           # Updated: externalized config
            ├── application-mcp.yml       # NEW: MCP profile
            └── application-docker.yml    # NEW: Docker-specific overrides
```

## Sources

- [Spring AI MCP Server Boot Starter Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [STDIO and SSE MCP Servers - Spring AI](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html)
- [Docker Compose Startup Order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Docker Multi-Stage Builds](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/)
- [Eclipse Temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin)
- [Configure MCP Transport Protocols for Docker](https://mcpcat.io/guides/configuring-mcp-transport-protocols-docker-containers/)
- [Build and Deliver MCP Server for Production - Docker Blog](https://www.docker.com/blog/build-to-prod-mcp-servers-with-docker/)
- [Remote MCP Servers - Claude Documentation](https://platform.claude.com/docs/en/agents-and-tools/remote-mcp-servers)
- [Docker Shared Volumes Permissions - Baeldung](https://www.baeldung.com/ops/docker-shared-volumes-permissions)
- [Docker Compose Spring Boot PostgreSQL - Baeldung](https://www.baeldung.com/spring-boot-postgresql-docker)

---
*Architecture research for: Docker packaging of Alexandria MCP server*
*Researched: 2026-01-22*
