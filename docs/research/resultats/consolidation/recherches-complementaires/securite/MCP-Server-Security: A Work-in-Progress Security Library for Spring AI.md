# MCP-Server-Security: A Work-in-Progress Security Library for Spring AI

**Version 0.0.5 is the current latest release**, published on November 27, 2025. This community-driven library provides OAuth 2.0 and API key authentication for Spring AI's Model Context Protocol servers—but it remains in early development with explicit warnings that APIs may change. The library is **not production-ready** and lacks official endorsement from Spring AI or the MCP project.

## Maven Central confirms version 0.0.5 as current

The library is available on Maven Central with these exact coordinates:

| Attribute | Value |
|-----------|-------|
| **GroupId** | `org.springaicommunity` |
| **ArtifactId** | `mcp-server-security` |
| **Latest Version** | 0.0.5 |
| **Release Date** | November 27, 2025 |
| **License** | Apache 2.0 |

The complete version history shows five releases, with version **0.0.3 marking the first public release** announced via the Spring blog on September 30, 2025:

- **v0.0.5** — November 27, 2025 (current)
- **v0.0.4** — October/November 2025
- **v0.0.3** — September 30, 2025 (first public announcement)
- **v0.0.2** — Pre-public release
- **v0.0.1** — Initial internal release

Maven dependency declaration:
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>mcp-server-security</artifactId>
    <version>0.0.5</version>
</dependency>
```

## GitHub repository reveals work-in-progress status

The library lives at **github.com/spring-ai-community/mcp-security** (note: the repository name is `mcp-security`, with `mcp-server-security` being one of three modules). The project has **57 stars**, 10 forks, 174 commits, and 4 contributors, led by Daniel Garnier-Moiroux (@Kehrlann).

The official Spring AI documentation explicitly states: *"This is still work in progress. The documentation and APIs may change in future releases."* The README adds a critical disclaimer: *"This is a community-driven project and is not officially endorsed by Spring AI or the MCP project."*

**Stability status: Pre-GA / Alpha.** The 0.0.x versioning scheme confirms early development. No GA badges or stable release designations exist.

The repository includes three modules under the same groupId:
- **mcp-server-security** — Resource server OAuth 2.0 and API key authentication
- **mcp-client-security** — OAuth 2.0 client flows for MCP clients
- **mcp-authorization-server** — Enhanced Spring Authorization Server with Dynamic Client Registration (RFC 7591)

## Spring AI 1.1.x compatibility is required

The project explicitly states: **"This project only works with Spring AI's 1.1.x branch."** Version 0.0.5 is compatible with Spring AI MCP SDK 1.1.2, which falls within the supported 1.1.x range.

| Requirement | Version |
|-------------|---------|
| Java | 17+ |
| Spring AI | 1.1.x only |
| Spring Boot | 3.x |
| Spring Security | 6.x |
| MCP Java SDK | 0.13.1+ (uses 0.15.0 with Spring AI 1.1.0 GA) |
| Server Framework | **Spring WebMVC only** |
| Token Format | **JWT only** |

**Key dependencies** include `spring-boot-starter-security`, `spring-security-oauth2-resource-server`, and the MCP Java SDK. Spring AI 1.1.0 GA released on November 12, 2025, and compatibility with the upcoming Spring AI 2.0 (shipping with Spring Boot 4.0) is not guaranteed.

## Known limitations and breaking changes affect adoption

Several significant limitations constrain production use:

- **No WebFlux support** — Only Spring WebMVC-based servers work; reactive applications cannot use this library
- **JWT tokens only** — Opaque tokens are not supported
- **SSE transport deprecated** — Must use Streamable HTTP or stateless transport
- **Performance concerns** — The `InMemoryApiKeyEntityRepository` uses bcrypt, making it computationally expensive for high-traffic scenarios
- **No fine-grained resource control** — Every authenticated client can access ALL resource identifiers

A notable bug exists in Spring AI core: **Issue #2506** reports "MCP server: Authentication lost in tool execution," where `SecurityContextHolder.getContext().getAuthentication()` returns null inside tool execution code, preventing access to authenticated user information.

Breaking changes have occurred between versions as the MCP specification evolved rapidly through 2025 (versions 2025-03-26 and 2025-06-18). Class names evolved from manual configuration approaches to simplified configurers like `McpServerOAuth2Configurer` and `mcpServerApiKey()`. The MCP SDK itself introduced breaking changes, including `Tool.inputSchema` type changing from `Map<String, Object>` to `JsonSchema`.

## Production readiness assessment

**This library is NOT production-ready.** It should be used for development, proofs of concept, and experimentation only. The combination of 0.0.x versioning, explicit WIP documentation warnings, lack of official endorsement, and known authentication bugs makes it unsuitable for production workloads requiring stable APIs.

However, the library shows promising momentum: coverage in official Spring AI documentation, multiple Spring blog posts, Baeldung tutorials, and integration with popular tools like Claude Code, Cursor, and MCP Inspector indicate active community engagement.

## Conclusion

The **org.springaicommunity:mcp-server-security** library at version **0.0.5** (released November 27, 2025) represents the current state of MCP security tooling for Spring AI 1.1.x applications. While it fills a genuine need for OAuth 2.0 and API key authentication in MCP servers, the explicit work-in-progress status, WebMVC-only limitation, and lack of official Spring endorsement mean organizations should treat it as experimental. Teams adopting it should pin versions carefully, implement custom API key repositories for production scenarios, monitor GitHub issues for breaking changes, and plan for API migrations as the library matures toward a 1.0 release.