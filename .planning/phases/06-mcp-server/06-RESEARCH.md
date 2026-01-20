# Phase 6: MCP Server - Research

**Researched:** 2026-01-20
**Domain:** Model Context Protocol (MCP) server implementation with Spring Boot
**Confidence:** HIGH

## Summary

MCP (Model Context Protocol) is Anthropic's open standard for AI-tool integration, now industry-standard with 97M+ monthly SDK downloads. The official MCP Java SDK (maintained by Spring AI team and Anthropic) provides native Spring Boot integration through dedicated starters.

For this project (Spring Boot 3.4, Java 21), the **Spring AI MCP Server Boot Starter** is the recommended approach. It provides annotation-based tool definition (`@McpTool`, `@McpToolParam`) with automatic JSON schema generation, eliminating boilerplate. For Claude Code integration, **STDIO transport** is required - the server runs as a subprocess launched by Claude Code.

**Primary recommendation:** Use `spring-ai-starter-mcp-server` (STDIO transport) with `@Tool` annotation for simple tools. Spring AI 1.0.0 GA is stable; use annotation scanner for automatic tool registration.

## Standard Stack

The established libraries/tools for MCP server in Spring Boot:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-ai-starter-mcp-server | 1.0.0+ | MCP server STDIO transport | Official Spring AI starter, auto-configuration |
| io.modelcontextprotocol.sdk:mcp | 0.17.0 | MCP Java SDK (transitive) | Official Anthropic SDK |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-ai-starter-mcp-server-webmvc | 1.0.0+ | HTTP/SSE transport | Multi-client scenarios, web deployment |
| spring-ai-starter-mcp-server-webflux | 1.0.0+ | Reactive SSE transport | Reactive applications |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring AI annotations | Raw MCP Java SDK | More control but manual JSON schema, more boilerplate |
| STDIO transport | HTTP/SSE transport | HTTP allows multiple clients but STDIO is what Claude Code uses |

**Installation:**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
    <version>1.0.0</version>
</dependency>
```

Note: Spring AI 1.0.0+ uses renamed artifacts (`spring-ai-starter-*` not `spring-ai-*-spring-boot-starter`).

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/fr/kalifazzia/alexandria/
├── api/
│   └── mcp/                    # MCP layer (entry point)
│       └── AlexandriaTools.java  # @McpTool annotated methods
├── core/                       # Business logic (unchanged)
│   ├── search/SearchService.java
│   └── ingestion/IngestionService.java
└── infra/                      # Infrastructure (unchanged)
```

### Pattern 1: Tool Facade with Service Delegation
**What:** MCP tools are thin facades that delegate to existing core services
**When to use:** Always - keeps MCP layer thin, business logic in core
**Example:**
```java
// Source: Spring AI MCP documentation
@Component
public class AlexandriaTools {

    private final SearchService searchService;
    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;

    // Constructor injection...

    @McpTool(name = "search_docs", description = "Search documentation by semantic similarity")
    public List<SearchResultDto> searchDocs(
            @McpToolParam(description = "Search query text", required = true) String query,
            @McpToolParam(description = "Maximum results (1-100)", required = false) Integer maxResults,
            @McpToolParam(description = "Category filter", required = false) String category) {

        SearchFilters filters = new SearchFilters(
            maxResults != null ? maxResults : 10,
            null,  // minSimilarity
            category,
            null   // tags
        );
        return searchService.search(query, filters).stream()
            .map(this::toDto)
            .toList();
    }
}
```

### Pattern 2: STDIO Transport Configuration
**What:** Disable web server, banner, and console logging for STDIO
**When to use:** Always for Claude Code integration
**Example:**
```properties
# Source: Spring AI MCP Server docs
spring.main.web-application-type=none
spring.main.banner-mode=off
logging.pattern.console=
spring.ai.mcp.server.name=alexandria
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.stdio=true
```

### Pattern 3: DTO Response Types
**What:** Return DTOs instead of domain objects for clean JSON serialization
**When to use:** Always - MCP serializes responses to JSON
**Example:**
```java
// Simplified DTO for MCP response
public record SearchResultDto(
    String documentTitle,
    String documentPath,
    String category,
    String matchedContent,
    String parentContext,
    double similarity
) {}
```

### Anti-Patterns to Avoid
- **Exposing domain objects directly:** MCP serializes to JSON - use DTOs to control output
- **Complex return types:** Avoid nested objects, prefer flat structures for LLM consumption
- **Blocking operations in async server:** Use SYNC server type for simplicity with existing sync services
- **Logging to stdout:** STDIO transport uses stdout - use file logging or disable console

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON Schema generation | Manual schema strings | `@McpToolParam` annotations | Automatic schema from Java types |
| Tool registration | Manual McpServer.addTool() | Spring auto-configuration | Annotation scanner handles it |
| Transport handling | Custom stdin/stdout parsing | spring-ai-starter-mcp-server | Battle-tested STDIO implementation |
| Parameter validation | Custom validation logic | Record validation + annotations | Spring validates from schema |
| Error handling | Try-catch everywhere | Framework exception handling | MCP SDK handles error responses |

**Key insight:** Spring AI MCP starter eliminates all MCP protocol boilerplate. Focus on business logic in tool methods.

## Common Pitfalls

### Pitfall 1: Console Output Corrupts STDIO
**What goes wrong:** Any System.out.println or console logging breaks MCP JSON-RPC
**Why it happens:** STDIO transport uses stdout for MCP messages
**How to avoid:** Set `logging.pattern.console=` (empty) and `spring.main.banner-mode=off`
**Warning signs:** Claude Code shows "Invalid JSON" or connection errors

### Pitfall 2: Web Server Still Running
**What goes wrong:** Application starts web server consuming ports, STDIO not working
**Why it happens:** Default Spring Boot starts embedded Tomcat
**How to avoid:** Set `spring.main.web-application-type=none`
**Warning signs:** Port binding errors, STDIO not responding

### Pitfall 3: Wrong Artifact Name
**What goes wrong:** Maven fails to resolve dependency
**Why it happens:** Spring AI 1.0.0-M7+ changed naming convention
**How to avoid:** Use `spring-ai-starter-mcp-server` not `spring-ai-mcp-server-spring-boot-starter`
**Warning signs:** "Could not find artifact" Maven error

### Pitfall 4: Returning Complex Domain Objects
**What goes wrong:** Circular references, lazy loading failures, huge JSON
**Why it happens:** JPA entities or complex domain objects serialized
**How to avoid:** Use flat DTOs with only needed fields
**Warning signs:** Stack overflow, huge responses, serialization errors

### Pitfall 5: Missing Tool Annotation Scanner
**What goes wrong:** Tools not registered, Claude Code shows no available tools
**Why it happens:** Annotation scanning disabled or bean not Spring-managed
**How to avoid:** Use `@Component` on tool classes, ensure `@SpringBootApplication` scans package
**Warning signs:** Empty tool list in Claude Code `/mcp` output

### Pitfall 6: Synchronous Operations in Async Server
**What goes wrong:** Thread blocking, timeouts, poor performance
**Why it happens:** Using ASYNC server type with blocking database calls
**How to avoid:** Use SYNC server type (`spring.ai.mcp.server.type=SYNC`) since existing services are synchronous
**Warning signs:** Timeouts, thread pool exhaustion

## Code Examples

Verified patterns from official sources:

### Tool with Optional Parameters
```java
// Source: Spring AI MCP Server Boot Starter docs
@Component
public class AlexandriaTools {

    @McpTool(name = "search_docs",
             description = "Search Alexandria documentation by semantic similarity. Returns matching chunks with parent context.")
    public List<SearchResultDto> searchDocs(
            @McpToolParam(description = "Natural language search query", required = true)
            String query,
            @McpToolParam(description = "Maximum number of results (1-100, default 10)", required = false)
            Integer maxResults,
            @McpToolParam(description = "Filter by category name", required = false)
            String category,
            @McpToolParam(description = "Filter by tags (comma-separated)", required = false)
            String tags) {

        List<String> tagList = tags != null ? Arrays.asList(tags.split(",")) : null;
        SearchFilters filters = new SearchFilters(
            maxResults != null ? Math.min(Math.max(maxResults, 1), 100) : 10,
            null,
            category,
            tagList
        );

        return searchService.search(query, filters).stream()
            .map(this::toDto)
            .toList();
    }
}
```

### Tool Returning Simple Types
```java
// Source: Spring AI MCP Annotations Examples
@McpTool(name = "list_categories",
         description = "List all available documentation categories")
public List<String> listCategories() {
    return documentRepository.findDistinctCategories();
}
```

### Tool with Path Parameter
```java
// Source: modelcontextprotocol.io Java SDK docs
@McpTool(name = "index_docs",
         description = "Index markdown documentation from a directory path")
public IndexResultDto indexDocs(
        @McpToolParam(description = "Absolute path to directory containing .md files", required = true)
        String directoryPath) {

    Path path = Path.of(directoryPath);
    if (!Files.isDirectory(path)) {
        throw new IllegalArgumentException("Path is not a valid directory: " + directoryPath);
    }

    ingestionService.ingestDirectory(path);
    return new IndexResultDto(directoryPath, "Indexing started");
}
```

### Tool with UUID Parameter
```java
// Source: Spring AI MCP documentation
@McpTool(name = "get_doc",
         description = "Get full document content by its unique ID")
public DocumentDto getDoc(
        @McpToolParam(description = "Document UUID", required = true)
        String documentId) {

    UUID id = UUID.fromString(documentId);
    return documentRepository.findById(id)
        .map(this::toDocumentDto)
        .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
}
```

### Application Configuration (application.properties)
```properties
# Source: Spring AI MCP Server Boot Starter docs + Dan Vega tutorial
# MCP Server Configuration for STDIO transport
spring.main.web-application-type=none
spring.main.banner-mode=off
logging.pattern.console=

# MCP Server identity
spring.ai.mcp.server.name=alexandria
spring.ai.mcp.server.version=0.1.0
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.stdio=true

# Logging to file instead of console
logging.file.name=logs/alexandria.log
logging.level.fr.kalifazzia.alexandria=INFO
```

### Claude Code Configuration (.mcp.json)
```json
{
  "mcpServers": {
    "alexandria": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "/path/to/alexandria-0.1.0-SNAPSHOT.jar"
      ],
      "env": {
        "SPRING_DATASOURCE_URL": "jdbc:postgresql://localhost:5432/alexandria",
        "SPRING_DATASOURCE_USERNAME": "alexandria",
        "SPRING_DATASOURCE_PASSWORD": "alexandria"
      }
    }
  }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual JSON Schema | `@McpToolParam` annotations | Spring AI 1.0.0-M7 | No manual schema writing |
| `spring-ai-mcp-server-spring-boot-starter` | `spring-ai-starter-mcp-server` | Spring AI 1.0.0-M7 | Artifact naming standardized |
| SSE transport only | STDIO + SSE + Streamable-HTTP | MCP SDK 0.10.0+ | STDIO for Claude Code |
| McpServer.addTool() manual | Annotation scanner | Spring AI 1.0.0 | Auto-registration |
| MCP spec 2024-11-05 | MCP spec 2025-03-26 | March 2025 | Streamable-HTTP added |

**Deprecated/outdated:**
- `spring-ai-mcp-server-spring-boot-starter`: Replaced by `spring-ai-starter-mcp-server`
- Manual tool registration: Use annotation scanning instead
- SSE-only approach: STDIO is standard for CLI tools like Claude Code

## Open Questions

Things that could not be fully resolved:

1. **Spring AI BOM with Spring Boot 3.4**
   - What we know: Spring AI 1.0.0 GA released, requires Spring Boot 3.2+
   - What's unclear: Exact BOM import syntax for Spring Boot 3.4.1 (the project version)
   - Recommendation: Test with explicit version first, add BOM if dependency conflicts arise

2. **Document findById Method**
   - What we know: DocumentRepository has `findByIds(Collection<UUID>)` but no `findById(UUID)`
   - What's unclear: Whether to add a new method or use existing
   - Recommendation: Add `Optional<Document> findById(UUID id)` to DocumentRepository port

3. **Categories List Implementation**
   - What we know: Documents have `category` field but no query for distinct categories
   - What's unclear: Best query approach (distinct vs cached list)
   - Recommendation: Add `List<String> findDistinctCategories()` to DocumentRepository

## Sources

### Primary (HIGH confidence)
- [Spring AI MCP Server Boot Starter Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) - Configuration properties, starters
- [MCP Java SDK GitHub](https://github.com/modelcontextprotocol/java-sdk) - v0.17.0 release, API reference
- [MCP Server Annotations Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) - @McpTool usage
- [MCP Annotations Examples](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-examples.html) - Working code examples
- [Claude Code MCP Docs](https://code.claude.com/docs/en/mcp) - STDIO configuration, .mcp.json format

### Secondary (MEDIUM confidence)
- [Dan Vega MCP Tutorial](https://www.danvega.dev/blog/creating-your-first-mcp-server-java) - STDIO configuration details
- [Spring AI MCP Example GitHub](https://github.com/iseif/spring-ai-mcp-example) - Working example project
- [modelcontextprotocol.io Java Server Guide](https://modelcontextprotocol.io/sdk/java/mcp-server) - SDK API details

### Tertiary (LOW confidence)
- WebSearch results for community patterns - Cross-verified with official docs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official Spring AI documentation, stable 1.0.0 release
- Architecture: HIGH - Patterns from official examples, Spring best practices
- Pitfalls: HIGH - Documented in official guides and tutorials
- Claude Code integration: HIGH - Official Anthropic documentation

**Research date:** 2026-01-20
**Valid until:** 2026-02-20 (Spring AI evolving rapidly, check for 1.1.0 GA)
