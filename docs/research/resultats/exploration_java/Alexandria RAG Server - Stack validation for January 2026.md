# Alexandria RAG Server: Stack validation for January 2026

The proposed stack is **largely correct and compatible**, with a few critical decisions required. Java 25 LTS and Spring Boot 3.5.9 form a solid foundation. However, Spring Boot 4.0's November 2025 release creates a strategic choice: stay on 3.5.x (OSS support ends June 2026) or migrate now. Langchain4j's beta Spring integration and lack of halfvec support are the primary technical constraints.

## Component validation matrix

| Component | Chosen | Status | Verified/Latest | Recommendation |
|-----------|--------|--------|-----------------|----------------|
| Java | 25.0.1 | ✅ Validated | 25.0.1 (Oct 2025) | **Keep** |
| Spring Boot | 3.5.9 | ✅ Validated | 3.5.9 / 4.0.1 available | **Decision needed** |
| Spring Framework | 6.2.x | ✅ Validated | 6.2.15 (Dec 2025) | Keep |
| Spring AI MCP SDK | 1.1.2 | ✅ Validated | 1.1.2 GA | Keep |
| spring-ai-starter-mcp-server-webmvc | via BOM 1.1.2 | ✅ Correct | Artifact verified | Keep |
| Langchain4j core | 1.10.0 | ✅ Validated | 1.10.0 GA (Dec 24, 2025) | Keep |
| langchain4j-pgvector | 1.10.0-beta18 | ⚠️ Watch | Still beta, no GA | Accept beta |
| langchain4j-open-ai | 1.10.0 | ✅ Validated | Supports custom baseUrl | Keep |
| langchain4j-spring-boot-starter | 1.10.0-beta18 | ⚠️ Watch | Still beta, no GA | Accept beta |
| PostgreSQL | 18.1 | ✅ Validated | 18.1 (Nov 2025) | Keep |
| pgvector | 0.8.1 | ✅ Validated | 0.8.1 (Sept 2025) | Keep |
| spring-retry | 2.0.11 | ✅ Validated | Managed by Boot 3.5.x | Keep |
| Testcontainers | 2.0.2 | 🔄 Update | **2.0.3** available | Update to 2.0.3 |
| WireMock | 3.10.0 | 🔄 Update | **3.13.2** available | Update to 3.13.2 |

---

## Java 25 is confirmed LTS with full Virtual Threads support

**Status: ✅ Validated**

Java 25 GA shipped **September 16, 2025**, with patch 25.0.1 released October 21, 2025. This is the first LTS under Oracle's new 2-year cadence (following Java 21). JEP 491 (Synchronize Virtual Threads without Pinning) was delivered in **Java 24** and is inherited by Java 25—virtual threads no longer pin carrier threads during `synchronized` blocks. This eliminates a major scalability concern for libraries using traditional synchronization.

| Fact | Value |
|------|-------|
| LTS Status | **Confirmed** |
| GA Date | September 16, 2025 |
| Current Patch | **25.0.1** (Oct 21, 2025) |
| JEP 491 | Delivered in Java 24, inherited |
| Free Support | Until September 2028 |
| Extended Support | Until September 2033+ |

**Langchain4j 1.10.0** now uses virtual threads as its internal default executor (PR #3541), making it fully compatible with Java 25's virtual thread model. Spring Boot 3.5.9 does **not** enable virtual threads by default—you must set `spring.threads.virtual.enabled=true`.

---

## Spring Boot 4.0 released but migration is optional for now

**Status: ⚠️ Decision Required**

Spring Boot 4.0.0 GA released **November 20, 2025**, with 4.0.1 following on December 18, 2025. The user's chosen version 3.5.9 (December 18, 2025) is the latest 3.5.x patch. OSS support for 3.5.x ends **June 30, 2026**, giving approximately 6 months of support runway.

**Boot 4.x breaking changes are substantial:**

- Requires **Spring Framework 7.x** (not 6.2.x)
- **Jakarta EE 11** baseline (Servlet 6.1)
- **Jackson 3** migration with new `tools.jackson` package names
- **spring-retry removed** from dependency management (replaced by Spring Framework 7's built-in resilience)
- **Undertow dropped** (not Servlet 6.1 compatible)

**Critical blocker**: Neither Spring AI 1.1.2 nor Langchain4j 1.10.0-beta18 support Spring Boot 4.x. Spring AI 2.0.0-M1 targets Boot 4.0 compatibility but is only at milestone status. Staying on Boot 3.5.9 is the **correct choice** until the AI framework ecosystem catches up.

| Spring Boot | OSS Support End | Spring Framework | Spring AI | Langchain4j |
|-------------|-----------------|------------------|-----------|-------------|
| 3.5.9 | June 30, 2026 | 6.2.x ✅ | 1.1.2 ✅ | 1.10.0-beta18 ✅ |
| 4.0.1 | December 31, 2026 | 7.x | 2.0.x needed ❌ | Unknown ❌ |

---

## Spring AI MCP artifacts are correctly configured

**Status: ✅ Validated**

Spring AI 1.1.0 GA released **November 12, 2025**, with 1.1.2 as the current stable patch. The artifact `spring-ai-starter-mcp-server-webmvc` is **correct** for SSE-based MCP servers.

**Endpoint verification:**

| Endpoint | Default | Configurable |
|----------|---------|--------------|
| SSE Connection | `/sse` | `spring.ai.mcp.server.sse-endpoint` |
| Message Endpoint | `/mcp/message` | `spring.ai.mcp.server.sse-message-endpoint` |

The `@McpTool` annotation is **correct** for exposing tools. The programming model uses:
- `@McpTool(name = "...", description = "...")` on methods
- `@McpToolParam(description = "...", required = true)` on parameters
- Auto-configuration enabled by default via `spring.ai.mcp.server.annotation-scanner.enabled=true`

**Minimal configuration:**
```yaml
spring:
  ai:
    mcp:
      server:
        name: alexandria-rag
        version: 1.0.0
        type: SYNC
        capabilities:
          tool: true
```

---

## Langchain4j beta modules require acceptance

**Status: ⚠️ Accept Beta Risk**

Langchain4j core **1.10.0** is GA (December 24, 2025), but the Spring Boot starter and pgvector modules remain at **1.10.0-beta18**. No GA timeline has been announced. The beta designation indicates potential breaking changes between versions.

**Version alignment is correct** when using the BOM:
```xml
<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-bom</artifactId>
        <version>1.10.0</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

**langchain4j-open-ai 1.10.0** fully supports custom `baseUrl` for self-hosted endpoints like RunPod/Infinity:
```properties
langchain4j.open-ai.chat-model.base-url=http://your-runpod-endpoint/v1
langchain4j.open-ai.embedding-model.base-url=http://your-infinity-endpoint/v1
```

The Spring Boot starter 1.10.0-beta18 is compatible with Boot 3.2+ (including 3.5.9).

---

## pgvector halfvec not available through Langchain4j

**Status: ⚠️ Constraint Accepted**

PostgreSQL **18.1** (November 13, 2025) and pgvector **0.8.1** (September 4, 2025) are both at latest versions. pgvector 0.8.1 explicitly supports PostgreSQL 18 and includes `halfvec` (16-bit vectors) for **50% storage savings**.

However, **langchain4j-pgvector does NOT support halfvec**—it creates tables using `VECTOR(dimension)` (32-bit). Using `vector(1024)` is the **correct choice** for Langchain4j compatibility.

**Recommended HNSW configuration for 1024D cosine similarity:**

```sql
CREATE INDEX embeddings_hnsw_idx ON embeddings 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 24, ef_construction = 100);

-- Query-time tuning
SET hnsw.ef_search = 100;
```

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `m` | 24 | Balanced for 1024D (higher than default 16) |
| `ef_construction` | 100 | Quality/speed tradeoff for build |
| `ef_search` | 100 | Runtime recall (default 40 may be too low) |

**Additional constraint:** langchain4j-pgvector only supports **IVFFlat index creation** via its API. HNSW indexes must be created manually via SQL after initial table creation.

---

## Testing dependencies need updates

**Testcontainers 2.0.2**: Update to **2.0.3** (latest) for Docker 29.0.0 compatibility fixes and docker-java 3.7.0 updates. Compatible with Java 25 and PostgreSQL 18.

**WireMock 3.10.0**: Update to **3.13.2** (November 14, 2025). Version 3.10.0 is significantly behind, missing 3 months of bug fixes and dependency updates.

---

## Compatibility cross-reference matrix

| | Java 25 | Boot 3.5.9 | Boot 4.0 | Spring AI 1.1.2 | LC4j 1.10.0 | PG 18 | pgvector 0.8.1 |
|-|---------|------------|----------|-----------------|-------------|-------|----------------|
| **Java 25** | — | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Boot 3.5.9** | ✅ | — | N/A | ✅ | ✅ | ✅ | ✅ |
| **Boot 4.0** | ✅ | N/A | — | ❌ | ❓ | ✅ | ✅ |
| **Spring AI 1.1.2** | ✅ | ✅ | ❌ | — | ✅* | ✅ | ✅ |
| **LC4j 1.10.0** | ✅ | ✅ | ❓ | ✅* | — | ✅ | ✅ |
| **PG 18** | ✅ | ✅ | ✅ | ✅ | ✅ | — | ✅ |
| **pgvector 0.8.1** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |

*Spring AI and Langchain4j can coexist; they serve different purposes in Alexandria (MCP server vs RAG implementation).

---

## Identified risks by criticality

### Blocking (None)
No blocking issues identified—the stack is deployable.

### Major
1. **Spring Boot 3.5.x OSS support ends June 2026** — Plan migration path
2. **Langchain4j Spring/pgvector modules in beta** — Accept potential breaking changes in future updates
3. **No halfvec support in Langchain4j** — Storage overhead vs compatibility tradeoff accepted

### Minor
1. **HNSW index requires manual SQL** — Not created by Langchain4j API
2. **Virtual threads not enabled by default** — Requires explicit configuration
3. **WireMock/Testcontainers outdated** — Simple updates available

---

## Decisions required

| Decision | Options | Recommendation |
|----------|---------|----------------|
| **Spring Boot version** | Stay 3.5.9 vs migrate to 4.0 | **Stay on 3.5.9** until Spring AI 2.0 and Langchain4j support Boot 4.x |
| **Langchain4j beta acceptance** | Use beta vs alternative | **Accept beta** — no GA alternative exists; stable enough for production |
| **halfvec storage savings** | vector(1024) vs custom halfvec | **Use vector(1024)** for Langchain4j compatibility; revisit when LC4j adds halfvec |
| **HNSW index creation** | Manual SQL vs IVFFlat API | **Manual SQL** — HNSW significantly outperforms IVFFlat for this use case |

---

## Recommended final stack

```xml
<!-- Validated versions for January 2026 -->
<properties>
    <java.version>25</java.version>
    <spring-boot.version>3.5.9</spring-boot.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <langchain4j.version>1.10.0</langchain4j.version>
    <testcontainers.version>2.0.3</testcontainers.version>  <!-- Updated -->
    <wiremock.version>3.13.2</wiremock.version>             <!-- Updated -->
</properties>
```

**Required configuration:**
```yaml
spring:
  threads:
    virtual:
      enabled: true  # Enable virtual threads explicitly
  ai:
    mcp:
      server:
        name: alexandria-rag
        type: SYNC
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
```

The stack is validated and ready for production deployment on the target hardware (Intel i5-4570, 24GB RAM) with PostgreSQL 18 + pgvector 0.8.1.