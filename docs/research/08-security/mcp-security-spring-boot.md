# MCP Server Security compatibility with Spring Boot 3.5.9 and Spring AI

The `org.springaicommunity:mcp-server-security` library version **0.0.5** is compatible with Spring Boot 3.5.9 and the Spring AI 1.1.x branch. However, **Spring AI 1.1.2 does not yet exist** — the latest stable release is Spring AI 1.1.1 (December 5, 2025). If your project specifies Spring AI 1.1.2 in dependencies, it will fail to resolve.

## Spring AI version clarification is critical

The user's stated version of Spring AI 1.1.2 is not yet available on Maven Central. Current stable versions are:
- **Spring AI 1.1.1** — released December 5, 2025
- **Spring AI 1.1.0** — released November 12, 2025

Spring AI 1.1.2 is a planned future release targeted to fix a Kotlin compatibility issue (GitHub Issue #5045). For the Alexandria project, update the dependency to **Spring AI 1.1.1** or **1.1.0**.

## Official version compatibility matrix

| Component | Requirement | Alexandria Project | Compatible? |
|-----------|-------------|-------------------|-------------|
| **Spring AI** | 1.1.x branch only | 1.1.1 (corrected) | ✅ Yes |
| **Spring Boot** | 3.5.x (via Spring AI 1.1.x) | 3.5.9 | ✅ Yes |
| **Java** | 17+ | (assumed 17+) | ✅ Yes |
| **Server Type** | WebMVC only | Requires check | ⚠️ Verify |

The library's README explicitly states: *"This project only works with Spring AI's 1.1.x branch."* Since Spring AI 1.1.x internally uses Spring Boot 3.5.8 as its dependency base, **Spring Boot 3.5.9 is fully compatible**.

## GitHub repository findings

The library resides at `https://github.com/spring-ai-community/mcp-security` (note: `mcp-security`, not `mcp-server-security`) and contains three modules:

- **mcp-server-security** — OAuth 2.0 resource server for MCP endpoints
- **mcp-client-security** — OAuth 2.0 client flows
- **mcp-authorization-server** — Spring Authorization Server extensions

The latest release is **v0.0.5** (November 27, 2025), with 174 commits, 4 contributors, and 57 GitHub stars. The repository has 14 open issues, though none specifically mention Spring Boot 3.5.x incompatibility.

## Dependencies declared in the library

The library requires these companion dependencies:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>mcp-server-security</artifactId>
    <version>0.0.5</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- Optional: for OAuth2 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

The library does not lock specific Spring Boot versions, instead relying on the parent BOM. This means it inherits whatever Spring Boot version your project declares — **no transitive conflicts expected** with Spring Boot 3.5.9.

## Known limitations affecting Alexandria

Three constraints may impact your RAG server architecture:

1. **WebMVC only** — WebFlux-based servers are explicitly not supported. If Alexandria uses reactive programming with WebFlux, mcp-server-security will not work.

2. **JWT tokens required** — Opaque tokens are not supported. Your authentication must use JWT.

3. **No SSE transport** — The deprecated SSE transport is unsupported. Use Streamable HTTP or stateless transport instead.

## Langchain4j 1.10.0 coexistence

No documented conflicts exist between mcp-server-security and Langchain4j. The libraries operate in different domains — mcp-server-security handles OAuth2/security for MCP endpoints while Langchain4j provides LLM orchestration. Since both work with standard Spring Boot 3.x, they should coexist without issues.

## One potential issue for Kotlin users

Spring AI 1.1.1 has a documented Kotlin compatibility bug (GitHub Issue #5045) when used with Spring Boot 3.5.x's default Kotlin 1.9.x. The error manifests as: *"Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.2.0, expected version is 1.9.0."* This is assigned to the Spring AI 1.1.2 milestone for fix. **Java-only projects like most RAG servers are unaffected.**

## Recommended dependency configuration for Alexandria

```xml
<properties>
    <spring-boot.version>3.5.9</spring-boot.version>
    <spring-ai.version>1.1.1</spring-ai.version>
    <mcp-security.version>0.0.5</mcp-security.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springaicommunity</groupId>
        <artifactId>mcp-server-security</artifactId>
        <version>${mcp-security.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-bom</artifactId>
        <version>${spring-ai.version}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencies>
```

## Conclusion

**mcp-server-security 0.0.5 is compatible with Spring Boot 3.5.9 and Spring AI 1.1.1** for securing MCP endpoints in the Alexandria project. The primary action required is correcting the Spring AI version from the non-existent 1.1.2 to the actual latest **1.1.1**. Ensure Alexandria uses WebMVC (not WebFlux), JWT authentication, and Streamable HTTP transport. This is a community-maintained library with active development but is not officially endorsed by the Spring AI project — appropriate for production use with that understanding.