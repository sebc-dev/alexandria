# Phase 5: MCP Server - Research

**Researched:** 2026-02-19
**Domain:** Spring AI MCP Server with stdio transport, Claude Code integration
**Confidence:** HIGH

## Summary

Phase 5 exposes Alexandria's search and management capabilities to Claude Code via the Model Context Protocol (MCP) stdio transport. The existing codebase already has `spring-ai-starter-mcp-server-webmvc` (v1.0.3) as a dependency and dual-profile configuration (`web`/`stdio`) in place. The core work is: (1) create an `mcp` adapter package with 6 tool methods using Spring AI's `@Tool` annotation, (2) register them via `MethodToolCallbackProvider`, (3) implement token budget truncation for search results, (4) add structured error handling, and (5) provide a `.mcp.json` configuration file.

The Spring AI 1.0.3 `@Tool`/`@ToolParam` annotation approach with `MethodToolCallbackProvider` bean registration is the standard, well-tested pattern. The `spring-ai-starter-mcp-server-webmvc` dependency already supports stdio transport when `spring.ai.mcp.server.stdio=true` is set with `spring.main.web-application-type=none` -- no additional dependency is needed.

**Primary recommendation:** Use the existing `spring-ai-starter-mcp-server-webmvc` dependency with `@Tool`-annotated service methods, registered via a `MethodToolCallbackProvider` bean. The `application-stdio.yml` profile already configures stdio transport correctly.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| MCP-01 | Server communicates via stdio transport | `application-stdio.yml` already configures `spring.ai.mcp.server.stdio: true` + `spring.main.web-application-type: none`. Spring AI auto-configuration handles the rest. |
| MCP-02 | Tools have clear, front-loaded descriptions | Use `@Tool(description = "...")` with verb-first, 1-2 sentence descriptions. Front-load the most important info in the first sentence. |
| MCP-03 | Tool errors return structured, actionable messages | MCP protocol uses `isError: true` in result content. Spring AI auto-converts return values. Tools should catch exceptions and return descriptive error strings, never propagate raw exceptions. |
| MCP-04 | Search results respect configurable token budget (default 5000) | Implement token estimation (chars / 4) and truncate results list until budget met. Configurable via `alexandria.mcp.token-budget` property. |
| MCP-05 | Server exposes max 6 tools: `search_docs`, `list_sources`, `add_source`, `remove_source`, `crawl_status`, `recrawl_source` | Each tool is a `@Tool`-annotated method. Register via single `MethodToolCallbackProvider` bean. |
| INFRA-03 | Claude Code integration guide (.mcp.json) | Create `.mcp.json` at project root with stdio config pointing to the fat jar. Document in integration guide. |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-ai-starter-mcp-server-webmvc` | 1.0.3 (via BOM) | MCP server auto-configuration + both SSE and stdio transports | Already in build.gradle.kts. Bundles `spring-ai-autoconfigure-mcp-server` which handles both WebMVC SSE and stdio modes. |
| `org.springframework.ai.tool.annotation.Tool` | 1.0.3 | Annotate methods as MCP tools | Standard Spring AI pattern for tool registration in 1.0.x. Auto-generates JSON schema from method signatures. |
| `org.springframework.ai.tool.annotation.ToolParam` | 1.0.3 | Annotate tool parameters with descriptions | Provides parameter-level documentation for LLM tool selection. |
| `org.springframework.ai.tool.method.MethodToolCallbackProvider` | 1.0.3 | Convert `@Tool` methods into MCP tool callbacks | Builder pattern: `.toolObjects(service1, service2).build()`. Standard registration mechanism. |

### Supporting (already in project)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot profiles | 3.5.7 | Dual transport via `web`/`stdio` profiles | Profile-based config already in place. No changes needed. |
| `io.modelcontextprotocol.sdk:mcp` | 0.10.0 (transitive) | MCP protocol implementation | Brought in transitively by Spring AI. Not used directly. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `spring-ai-starter-mcp-server-webmvc` | `spring-ai-starter-mcp-server` (stdio-only) | Would need a SEPARATE dependency for SSE support. Since webmvc is already in the build and supports both, no reason to switch. |
| `@Tool` + `MethodToolCallbackProvider` | `@McpTool` annotation scanning | `@McpTool` is a 1.1.x feature with reported bugs (issue #4392). Spring AI 1.0.x uses `@Tool`. Stick with 1.0.x pattern. |
| `MethodToolCallbackProvider` | `ToolCallbacks.from()` utility | Both work. `MethodToolCallbackProvider.builder().toolObjects()` is the documented pattern from official examples. |

## Architecture Patterns

### Recommended Package Structure

```
src/main/java/dev/alexandria/
  mcp/                          # NEW adapter package
    McpToolService.java         # @Tool-annotated methods (6 tools)
    McpToolConfig.java          # ToolCallbackProvider bean registration
    McpErrorHandler.java        # Structured error wrapping (optional, could be inline)
    TokenBudgetTruncator.java   # Truncate search results to token budget
  search/                       # Existing - SearchService
  source/                       # Existing - Source, SourceRepository
  crawl/                        # Existing - CrawlService
  ingestion/                    # Existing - IngestionService
  config/                       # Existing - EmbeddingConfig
```

### Pattern 1: Tool Registration via @Tool + MethodToolCallbackProvider

**What:** Annotate service methods with `@Tool`, register via bean.
**When to use:** Always for Spring AI 1.0.x MCP servers.
**Example:**

```java
// Source: Official Spring AI examples + MCP protocol docs
@Service
public class McpToolService {

    private final SearchService searchService;
    private final SourceRepository sourceRepository;
    // ... constructor injection

    @Tool(name = "search_docs",
          description = "Search indexed documentation. Returns relevant excerpts with source URLs and section paths for citation.")
    public String searchDocs(
            @ToolParam(description = "Search query text") String query,
            @ToolParam(description = "Max results to return (default 10)") Integer maxResults) {
        // call SearchService, format results, respect token budget
    }
}
```

Registration in config class:

```java
// Source: https://modelcontextprotocol.io/docs/develop/build-server (Java tab)
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider mcpTools(McpToolService toolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolService)
                .build();
    }
}
```

### Pattern 2: Structured Error Handling for MCP Tools

**What:** Catch exceptions in tool methods and return descriptive error strings.
**When to use:** Every tool method.
**Example:**

```java
// Source: MCP protocol spec - tool errors use isError in result, not protocol errors
@Tool(name = "search_docs", description = "...")
public String searchDocs(@ToolParam(description = "...") String query, ...) {
    try {
        if (query == null || query.isBlank()) {
            return "Error: Query must not be empty. Provide a search query string.";
        }
        // ... do search
        return formattedResults;
    } catch (Exception e) {
        return "Error searching documentation: " + e.getMessage()
             + ". Verify the server is connected to the database.";
    }
}
```

Key insight from MCP spec: Tool errors should be returned in the result `content` with `isError: true` -- they should NOT be thrown as exceptions. Spring AI's `@Tool` framework serializes the return value as the tool result content. Returning a string starting with "Error:" signals failure while giving the LLM actionable information.

### Pattern 3: Token Budget Truncation

**What:** Estimate token count of search results and truncate to stay within budget.
**When to use:** In `search_docs` tool output.
**Example:**

```java
// Token estimation: ~4 characters per token for English text
public class TokenBudgetTruncator {
    private static final double CHARS_PER_TOKEN = 4.0;

    public String truncateToTokenBudget(List<SearchResult> results, int tokenBudget) {
        StringBuilder output = new StringBuilder();
        int estimatedTokens = 0;

        for (SearchResult result : results) {
            String formatted = formatResult(result);
            int resultTokens = estimateTokens(formatted);

            if (estimatedTokens + resultTokens > tokenBudget) {
                break; // Stop adding results
            }
            output.append(formatted).append("\n\n");
            estimatedTokens += resultTokens;
        }

        return output.toString();
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
```

### Pattern 4: Stdio Transport Logging

**What:** All console output corrupts JSON-RPC in stdio mode. Logging MUST go to file.
**When to use:** Always for stdio profile.
**Example (already configured):**

```yaml
# application-stdio.yml (already exists in project)
spring:
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        name: alexandria
        version: 1.0.0
        type: SYNC
        stdio: true
logging:
  pattern:
    console: ""
  file:
    name: ./logs/alexandria-mcp.log
```

### Anti-Patterns to Avoid

- **Throwing exceptions from `@Tool` methods:** Spring AI may propagate these as MCP protocol errors (JSON-RPC error codes), which are less useful to the LLM than structured error strings in the result content. Catch all exceptions inside tool methods.
- **Writing to System.out in stdio mode:** Any non-JSON-RPC output to stdout corrupts the transport. The existing `application-stdio.yml` already handles this by blanking the console pattern and redirecting to file.
- **Creating interfaces for tool service:** The project convention is no ServiceImpl anti-pattern. `McpToolService` is a concrete `@Service` class. It is an adapter that delegates to feature services.
- **Putting business logic in tool methods:** Tool methods should be thin adapters that validate input, delegate to feature services (SearchService, SourceRepository, CrawlService, IngestionService), format output, and handle errors. No business logic.
- **Using `@McpTool` annotation:** This is Spring AI 1.1.x+ with known issues. The project uses Spring AI 1.0.3 BOM. Use `@Tool` from `org.springframework.ai.tool.annotation.Tool`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MCP protocol transport | Custom JSON-RPC over stdin/stdout | `spring-ai-starter-mcp-server-webmvc` auto-config | MCP protocol has versioning, capability negotiation, schema generation -- extremely complex to implement correctly |
| Tool JSON schema generation | Manual JSON schema for tool parameters | `@Tool` + `@ToolParam` annotations | Spring AI auto-generates JSON schema from method signatures and annotations |
| Tool registration with MCP server | Manual MCP server tool registration | `MethodToolCallbackProvider` bean | Handles serialization, deserialization, error wrapping, schema registration |
| Token counting | Custom tokenizer | Character-based estimation (chars/4) | BPE tokenization is complex, char/4 is industry-standard approximation for English text. Exact count is unnecessary. |

**Key insight:** The entire MCP server implementation in Spring AI is auto-configured. The developer's job is to write `@Tool`-annotated methods and register them via a `ToolCallbackProvider` bean. Everything else -- JSON-RPC transport, tool listing, tool execution dispatch, schema generation -- is handled by the framework.

## Common Pitfalls

### Pitfall 1: Console Output Corrupts STDIO Transport
**What goes wrong:** Any output to stdout (System.out, banner, log appender) corrupts the JSON-RPC message stream, causing "Connection closed" errors.
**Why it happens:** MCP stdio uses stdin/stdout for JSON-RPC. Interleaved non-protocol output breaks parsing.
**How to avoid:** Already handled. `application-stdio.yml` sets `banner-mode: off`, `console: ""`, and `web-application-type: none`. Verify no `System.out.println()` in any code path.
**Warning signs:** "Connection closed" errors when Claude Code connects. MCP server starts but tools don't appear.

### Pitfall 2: Using Wrong Annotation (@McpTool instead of @Tool)
**What goes wrong:** `@McpTool` annotation scanning was introduced in Spring AI 1.1.x. In 1.0.x, it doesn't exist or requires enabling annotation scanner property.
**Why it happens:** Blog posts and docs mix 1.0.x and 1.1.x examples. The current Spring AI docs show `@McpTool` which is for newer versions.
**How to avoid:** Use `@Tool` from `org.springframework.ai.tool.annotation.Tool` with `MethodToolCallbackProvider`. This is the 1.0.x pattern.
**Warning signs:** Compilation errors, tools not registering at server startup.

### Pitfall 3: Exception Propagation Breaks Tool Responses
**What goes wrong:** Uncaught exceptions from `@Tool` methods produce MCP protocol-level errors (JSON-RPC error codes) instead of tool result errors. The LLM sees a cryptic error instead of actionable information.
**Why it happens:** Spring AI wraps unhandled exceptions as protocol errors. The MCP spec distinguishes between tool execution failures (result with `isError: true`) and protocol errors.
**How to avoid:** Wrap all tool method bodies in try-catch. Return descriptive error strings. Never throw from a `@Tool` method.
**Warning signs:** Claude Code shows "tool execution failed" without useful details.

### Pitfall 4: Tool Descriptions Too Long or Vague
**What goes wrong:** LLM fails to select the correct tool, or wastes context window on verbose descriptions.
**Why it happens:** Descriptions not optimized for LLM consumption. Important info buried in middle of description.
**How to avoid:** Front-load the verb and purpose in the first sentence. Keep to 1-2 sentences. Example: "Search indexed documentation by semantic query. Returns excerpts with source URLs for citation."
**Warning signs:** Claude Code uses wrong tool or asks user which tool to use.

### Pitfall 5: Duplicate Dependency for STDIO
**What goes wrong:** Adding `spring-ai-starter-mcp-server` alongside `spring-ai-starter-mcp-server-webmvc` causes duplicate auto-configuration or classpath conflicts.
**Why it happens:** Assumption that a separate stdio-specific dependency is needed.
**How to avoid:** The webmvc starter already includes stdio support. When `spring.ai.mcp.server.stdio=true` and `spring.main.web-application-type=none` are set, it uses stdio transport. No additional dependency needed.
**Warning signs:** Multiple `McpServerAutoConfiguration` beans, startup errors.

### Pitfall 6: Fat Jar Path in .mcp.json
**What goes wrong:** `.mcp.json` uses a relative path to the jar, which breaks when Claude Code runs from a different working directory.
**Why it happens:** Forgetting that Claude Code's CWD varies.
**How to avoid:** Use absolute paths or environment variable expansion (`${PROJECT_ROOT}`) in `.mcp.json`. Document that users must update the jar path.
**Warning signs:** "Failed to start MCP server" in Claude Code.

## Code Examples

### Complete Tool Service (verified pattern from Spring AI 1.0.3)

```java
// Source: Verified against Spring AI 1.0.3 jar (org.springframework.ai.tool.annotation.Tool)
// Pattern from: https://modelcontextprotocol.io/docs/develop/build-server (Java tab)

package dev.alexandria.mcp;

import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class McpToolService {

    private final SearchService searchService;
    private final SourceRepository sourceRepository;
    private final TokenBudgetTruncator truncator;

    public McpToolService(SearchService searchService,
                          SourceRepository sourceRepository,
                          TokenBudgetTruncator truncator) {
        this.searchService = searchService;
        this.sourceRepository = sourceRepository;
        this.truncator = truncator;
    }

    @Tool(name = "search_docs",
          description = "Search indexed documentation by semantic query. "
                      + "Returns relevant excerpts with source URLs and section paths for citation.")
    public String searchDocs(
            @ToolParam(description = "Search query text") String query,
            @ToolParam(description = "Maximum number of results (1-50, default 10)") Integer maxResults) {
        try {
            int max = (maxResults != null && maxResults >= 1) ? Math.min(maxResults, 50) : 10;
            List<SearchResult> results = searchService.search(new SearchRequest(query, max));

            if (results.isEmpty()) {
                return "No results found for query: " + query;
            }

            return truncator.truncateToTokenBudget(results);
        } catch (Exception e) {
            return "Error searching documentation: " + e.getMessage();
        }
    }

    @Tool(name = "list_sources",
          description = "List all indexed documentation sources with status, last crawl time, and chunk count.")
    public String listSources() {
        try {
            List<Source> sources = sourceRepository.findAll();
            if (sources.isEmpty()) {
                return "No documentation sources configured. Use add_source to add one.";
            }
            // Format as readable text
            StringBuilder sb = new StringBuilder();
            for (Source s : sources) {
                sb.append(String.format("- %s (%s): %s | %d chunks | last crawled: %s%n",
                        s.getName(), s.getUrl(), s.getStatus(),
                        s.getChunkCount(),
                        s.getLastCrawledAt() != null ? s.getLastCrawledAt().toString() : "never"));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error listing sources: " + e.getMessage();
        }
    }

    // ... add_source, remove_source, crawl_status, recrawl_source follow same pattern
}
```

### ToolCallbackProvider Bean Registration

```java
// Source: https://modelcontextprotocol.io/docs/develop/build-server (Java tab)
package dev.alexandria.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider alexandriaTools(McpToolService toolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolService)
                .build();
    }
}
```

### .mcp.json Configuration for Claude Code

```json
{
  "mcpServers": {
    "alexandria": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-Dspring.profiles.active=stdio",
        "-jar",
        "build/libs/alexandria-0.0.1-SNAPSHOT.jar"
      ],
      "env": {
        "DB_HOST": "localhost",
        "DB_PORT": "5432",
        "DB_NAME": "alexandria",
        "DB_USER": "alexandria",
        "DB_PASSWORD": "alexandria_dev"
      }
    }
  }
}
```

### Claude Code CLI Registration (alternative)

```bash
claude mcp add --transport stdio --scope project \
  --env DB_HOST=localhost --env DB_PORT=5432 \
  --env DB_NAME=alexandria --env DB_USER=alexandria \
  --env DB_PASSWORD=alexandria_dev \
  alexandria -- java -Dspring.profiles.active=stdio -jar build/libs/alexandria-0.0.1-SNAPSHOT.jar
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@McpTool` + annotation scanner | `@Tool` + `MethodToolCallbackProvider` (1.0.x) / `@McpTool` (1.1.x+) | Spring AI 1.1.0 | `@McpTool` has reported issues (#4392). For 1.0.3, use `@Tool` + manual registration. |
| `spring-ai-mcp-server-spring-boot-starter` (old name) | `spring-ai-starter-mcp-server` / `spring-ai-starter-mcp-server-webmvc` | Spring AI 1.0.0 GA | Old artifact name was a documentation error. Use new names. |
| SSE transport only | SSE + Streamable HTTP + STDIO | Spring AI 1.0.0+ | webmvc starter now supports all three transports via properties. |
| MCP SSE deprecated | MCP Streamable HTTP / stdio preferred | MCP spec 2025-06 | Claude Code docs mark SSE as deprecated. HTTP or stdio are recommended. |

**Deprecated/outdated:**
- `spring-ai-mcp-server-spring-boot-starter`: Old artifact name. Use `spring-ai-starter-mcp-server-webmvc`.
- SSE transport for Claude Code: Deprecated in favor of HTTP or stdio.
- `@McpTool` for Spring AI 1.0.x: Does not exist. Only available in 1.1.x+.

## Open Questions

1. **Token budget accuracy**
   - What we know: Character-based estimation (chars/4) is the industry standard approximation for English text.
   - What's unclear: Whether Claude Code counts tokens differently. The default 5000-token budget may need tuning after end-to-end testing.
   - Recommendation: Implement chars/4 estimation. Make budget configurable via `alexandria.mcp.token-budget` property. Tune during integration testing.

2. **Tool name format: snake_case vs camelCase**
   - What we know: The requirements specify snake_case names (`search_docs`, `list_sources`). Spring AI's `@Tool(name = "search_docs")` supports arbitrary names.
   - What's unclear: Whether Spring AI preserves the exact name or transforms it.
   - Recommendation: Use `@Tool(name = "search_docs")` explicitly. Verify in integration test that the tool name appears correctly to the MCP client.

3. **add_source / remove_source / recrawl_source -- Phase 5 vs Phase 6 boundary**
   - What we know: Phase 5 requires exposing all 6 tools. Phase 6 adds the full source management logic. The `add_source` tool in Phase 5 needs at least basic create-and-save behavior.
   - What's unclear: How much source management logic belongs in Phase 5 vs Phase 6.
   - Recommendation: Phase 5 implements stub tools for `add_source`, `remove_source`, `crawl_status`, `recrawl_source` that do minimal work (e.g., `add_source` creates a Source entity in PENDING status, `remove_source` deletes by ID, `crawl_status` returns "not implemented yet", `recrawl_source` returns "not implemented yet"). Phase 6 fills in full orchestration (trigger crawl after add, cascade delete chunks, etc.).

4. **Integration testing with MCP client**
   - What we know: Spring AI provides `McpClient` for testing. `StdioClientTransport` can connect to a subprocess.
   - What's unclear: Whether Testcontainers + Spring Boot test context can support an MCP client test. This may require starting the app as a subprocess.
   - Recommendation: Unit-test tool methods directly (call `searchDocs()` with mocked dependencies). Integration-test the `ToolCallbackProvider` bean wiring. Manual end-to-end test with Claude Code for the stdio transport.

## Sources

### Primary (HIGH confidence)
- Spring AI 1.0.3 autoconfigure jar (`spring-ai-autoconfigure-mcp-server-1.0.3.jar`) -- extracted complete `spring-configuration-metadata.json` with all properties
- Spring AI 1.0.3 model jar (`spring-ai-model-1.0.3.jar`) -- verified exact annotation classes: `@Tool`, `@ToolParam`, `MethodToolCallbackProvider`
- [MCP protocol official docs - Build a server (Java tab)](https://modelcontextprotocol.io/docs/develop/build-server) -- complete Spring AI stdio server example
- [Claude Code MCP docs](https://code.claude.com/docs/en/mcp) -- `.mcp.json` format, stdio config, scope levels
- Existing codebase: `application-stdio.yml`, `application-web.yml`, `build.gradle.kts`, `libs.versions.toml`

### Secondary (MEDIUM confidence)
- [Spring AI MCP Server Boot Starter docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) -- WebFetch extracted dependency info, configuration properties
- [GitHub issue #3563](https://github.com/spring-projects/spring-ai/issues/3563) -- Clarified correct artifact name (`spring-ai-starter-mcp-server` not `spring-ai-mcp-server-spring-boot-starter`)
- [Dan Vega MCP server tutorial](https://www.danvega.dev/blog/creating-your-first-mcp-server-java) -- `@Tool` annotation usage, stdio config
- [Soham Kamani MCP server with Spring Boot](https://www.sohamkamani.com/java/creating-an-mcp-server/) -- Complete `@Tool` + `ToolCallbacks.from()` + properties example
- [MCP tool description best practices](https://www.merge.dev/blog/mcp-tool-description) -- Front-loaded descriptions, verb-first format

### Tertiary (LOW confidence)
- [GitHub issue #4392](https://github.com/spring-projects/spring-ai/issues/4392) -- `@McpTool` registration issues in 1.1.0-M1 (confirms avoiding `@McpTool` on 1.0.x)
- [MCP WebMVC server example](https://github.com/alexandredavi/mcp-webmvc-server) -- Dual transport (SSE + stdio) from single webmvc starter

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- Verified exact classes/annotations in the 1.0.3 jar files on disk. Dependency already in build.
- Architecture: HIGH -- Pattern directly from official MCP docs Java tab + Spring AI examples. Consistent with project conventions (adapter packages, constructor injection, no interfaces).
- Pitfalls: HIGH -- Stdio corruption issue is well-documented across all sources. Annotation version mismatch verified against jar contents. Already mitigated by existing profile config.
- Token budget: MEDIUM -- chars/4 estimation is standard but exact behavior with Claude Code's token counting unverified. Configurable property mitigates risk.

**Research date:** 2026-02-19
**Valid until:** 2026-03-19 (Spring AI 1.0.x is stable; MCP protocol is stable)
