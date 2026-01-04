# Spring AI MCP SDK: Complete @Tool annotation guide for SSE transport

Spring AI provides **two complementary annotation systems** for exposing MCP tools: the core `@Tool` annotation and the MCP-specific `@McpTool` annotation. For your Alexandria RAG Server using SSE transport with WebMVC, **`@McpTool`** is the recommended approach as it offers MCP-specific features like progress tracking and capability hints. Both annotations work with the `spring-ai-starter-mcp-server-webmvc` starter.

---

## Annotation signatures and attributes

### @McpTool (MCP-specific, recommended)

```java
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

@Target(METHOD)
@Retention(RUNTIME)
public @interface McpTool {
    String name() default "";              // Tool identifier (defaults to method name)
    String description() default "";        // Tool description for AI model
    String title() default "";              // Human-readable display name
    boolean generateOutputSchema() default false;  // Include output JSON schema
    McpAnnotations annotations() default @McpAnnotations();
    
    @interface McpAnnotations {
        String title() default "";
        boolean readOnlyHint() default false;      // Tool only reads data
        boolean destructiveHint() default false;   // Tool modifies/deletes data
        boolean idempotentHint() default true;     // Safe to retry
    }
}

@Target({PARAMETER, FIELD})
@Retention(RUNTIME)  
public @interface McpToolParam {
    String description() default "";       // Parameter description
    boolean required() default true;       // Required or optional
}
```

### @Tool (Core Spring AI, alternative)

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Target({METHOD, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface Tool {
    String name() default "";                  // Tool name
    String description() default "";           // Tool description
    boolean returnDirect() default false;      // Return to client vs model
    Class<? extends ToolCallResultConverter> resultConverter() 
        default DefaultToolCallResultConverter.class;
}

@Target({PARAMETER, FIELD, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ToolParam {
    String description() default "";
    boolean required() default true;
}
```

---

## Complete working examples

### Tool with simple parameters (String, int)

```java
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class RagSearchTools {

    @McpTool(
        name = "search-documents",
        description = "Search the document corpus using semantic similarity",
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true,
            idempotentHint = true
        ))
    public String searchDocuments(
            @McpToolParam(description = "Natural language search query", required = true) 
            String query,
            @McpToolParam(description = "Maximum results to return (1-50)", required = false) 
            int limit) {
        
        int actualLimit = (limit > 0 && limit <= 50) ? limit : 10;
        List<SearchResult> results = ragService.search(query, actualLimit);
        return objectMapper.writeValueAsString(results);
    }

    @McpTool(name = "get-document-count", description = "Get total indexed document count")
    public int getDocumentCount() {
        return ragService.getTotalDocuments();
    }
}
```

### Tool with complex object parameter

```java
@Component
public class AdvancedSearchTools {

    // Complex input object
    public record SearchRequest(
        String query,
        List<String> collections,
        Map<String, String> filters,
        double minScore,
        int limit
    ) {}

    // Complex output object  
    public record SearchResponse(
        List<DocumentMatch> matches,
        int totalFound,
        long queryTimeMs
    ) {}

    public record DocumentMatch(
        String id,
        String title,
        String snippet,
        double score,
        Map<String, Object> metadata
    ) {}

    @McpTool(
        name = "advanced-search",
        description = "Perform advanced RAG search with filters and collection scoping",
        generateOutputSchema = true)  // Include output schema in tool definition
    public SearchResponse advancedSearch(
            @McpToolParam(description = "Search configuration object", required = true)
            SearchRequest request) {
        
        long startTime = System.currentTimeMillis();
        
        List<DocumentMatch> matches = ragService.advancedSearch(
            request.query(),
            request.collections(),
            request.filters(),
            request.minScore(),
            request.limit()
        );
        
        return new SearchResponse(
            matches,
            matches.size(),
            System.currentTimeMillis() - startTime
        );
    }
}
```

### Tool returning a list of objects

```java
@Component
public class CollectionTools {

    public record Collection(
        String id,
        String name,
        String description,
        int documentCount,
        Instant lastUpdated
    ) {}

    @McpTool(
        name = "list-collections",
        description = "List all available document collections in the RAG system")
    public List<Collection> listCollections() {
        return ragService.getAllCollections().stream()
            .map(c -> new Collection(
                c.getId(),
                c.getName(), 
                c.getDescription(),
                c.getDocumentCount(),
                c.getLastUpdated()
            ))
            .toList();
    }

    @McpTool(name = "get-collection-documents", description = "Get documents in a collection")
    public List<DocumentSummary> getCollectionDocuments(
            @McpToolParam(description = "Collection ID", required = true) String collectionId,
            @McpToolParam(description = "Page number (0-indexed)", required = false) int page,
            @McpToolParam(description = "Page size", required = false) int size) {
        
        int pageSize = (size > 0 && size <= 100) ? size : 20;
        int pageNum = Math.max(0, page);
        
        return ragService.getDocumentsInCollection(collectionId, pageNum, pageSize);
    }
}
```

### Tool with progress tracking and context

```java
import org.springframework.ai.mcp.server.McpSyncRequestContext;

@Component  
public class IndexingTools {

    @McpTool(
        name = "reindex-collection",
        description = "Reindex all documents in a collection (long-running operation)",
        annotations = @McpTool.McpAnnotations(
            destructiveHint = true,
            idempotentHint = false
        ))
    public String reindexCollection(
            McpSyncRequestContext context,  // Auto-injected, not exposed as parameter
            @McpToolParam(description = "Collection ID to reindex", required = true)
            String collectionId) {
        
        List<String> docIds = ragService.getDocumentIds(collectionId);
        int total = docIds.size();
        
        context.info("Starting reindex of " + total + " documents");
        
        for (int i = 0; i < total; i++) {
            ragService.reindexDocument(docIds.get(i));
            
            // Send progress updates
            double progress = (i + 1.0) / total;
            context.progress(p -> p
                .progress(progress)
                .total(1.0)
                .message("Indexed " + (i + 1) + "/" + total));
        }
        
        context.info("Reindex complete");
        return "Successfully reindexed " + total + " documents in collection " + collectionId;
    }
}
```

---

## Response serialization behavior

The SDK automatically handles serialization based on return type:

| Return Type | Serialization Behavior |
|-------------|----------------------|
| `String` | Returned as-is (text content) |
| `int`, `double`, `boolean` | Converted to string representation |
| `void` | Returns "Done" as text |
| `null` | Returns "null" as text |
| POJOs, Records | **Serialized to JSON automatically** |
| `List<T>`, `Map<K,V>` | Serialized to JSON array/object |
| `CallToolResult` | Returned directly (native MCP format) |
| `Mono<T>`, `Flux<T>` | Async handling (requires ASYNC server type) |

For custom response formatting, return `CallToolResult` directly:

```java
import io.modelcontextprotocol.server.McpServerFeatures.CallToolResult;

@McpTool(name = "get-image", description = "Retrieve document image")
public CallToolResult getDocumentImage(
        @McpToolParam(description = "Document ID") String docId) {
    
    byte[] imageData = documentService.getImage(docId);
    String base64 = Base64.getEncoder().encodeToString(imageData);
    
    return CallToolResult.builder()
        .addImageContent(base64, "image/png")
        .build();
}
```

---

## Maven dependencies and configuration

### pom.xml

```xml
<properties>
    <java.version>17</java.version>
    <spring-ai.version>1.1.2</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- MCP Server with SSE/WebMVC transport -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>
    
    <!-- Spring Boot Web (included by starter, but explicit for clarity) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

### application.yaml for Alexandria RAG Server

```yaml
spring:
  application:
    name: alexandria-rag-server
    
  ai:
    mcp:
      server:
        name: alexandria-rag-server
        version: 1.0.0
        type: SYNC                    # Use ASYNC for reactive (Mono/Flux returns)
        protocol: SSE                 # SSE transport for Claude Code
        instructions: |
          Alexandria RAG Server provides semantic search over document collections.
          Use search-documents for natural language queries.
          Use list-collections to discover available document collections.
        
        # SSE endpoint configuration
        sse-endpoint: /sse            # SSE connection endpoint
        sse-message-endpoint: /mcp/messages
        keep-alive-interval: 30s
        request-timeout: 60s
        
        # Capabilities
        capabilities:
          tool: true
          resource: false
          prompt: false
          completion: false
        
        # Auto-discover @McpTool annotations
        annotation-scanner:
          enabled: true
        
        # Change notifications
        tool-change-notification: true

server:
  port: 8080

logging:
  level:
    io.modelcontextprotocol: DEBUG
    org.springframework.ai.mcp: DEBUG
```

---

## Tool registration methods

### Method 1: Automatic annotation scanning (recommended)

Simply annotate your Spring beans with `@McpTool`. The starter auto-discovers them when `annotation-scanner.enabled: true`:

```java
@Component  // or @Service
public class RagSearchTools {
    @McpTool(name = "search", description = "Search documents")
    public String search(@McpToolParam(description = "Query") String query) {
        return ragService.search(query);
    }
}
```

### Method 2: Explicit ToolCallbackProvider bean (for @Tool annotation)

```java
@Configuration
public class McpToolConfig {
    
    @Bean
    public ToolCallbackProvider ragTools(RagService ragService) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(ragService)  // Scans @Tool annotations
            .build();
    }
}
```

### Method 3: Dynamic runtime registration

```java
@Component
public class DynamicToolRegistrar implements CommandLineRunner {
    
    private final McpSyncServer mcpServer;
    
    @Override
    public void run(String... args) {
        // Register tools at runtime
        List<SyncToolSpecification> tools = McpToolUtils
            .toSyncToolSpecifications(ToolCallbacks.from(new CustomTools()));
        
        for (SyncToolSpecification tool : tools) {
            mcpServer.addTool(tool);
        }
    }
}
```

---

## Claude Code MCP configuration

Create `.mcp.json` in your project root or configure in Claude Code settings:

```json
{
  "mcpServers": {
    "alexandria": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

Or for Claude Desktop (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "alexandria-rag": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

---

## Critical gotchas and best practices

**Server type must match return types.** A `SYNC` server ignores methods returning `Mono<T>` or `Flux<T>`. An `ASYNC` server ignores non-reactive methods. Mismatches cause silent failures with only a warning log.

**Parameter names require compiler flag.** For parameter names to be preserved (instead of `arg0`, `arg1`), compile with `-parameters` flag:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

**Use records for complex types.** Java records serialize cleanly and generate proper JSON schemas. The SDK automatically generates input schemas from record components.

**Context parameters are hidden.** Parameters like `McpSyncRequestContext` and `McpMeta` are auto-injected and not exposed in the tool's parameter schema to the AI model.

**SSE requires keep-alive.** Long-idle SSE connections may timeout. The `keep-alive-interval: 30s` setting sends periodic pings to maintain the connection.

**Avoid logging to stdout.** For STDIO transport (not your case with SSE), any stdout output corrupts the protocol. Even with SSE, prefer structured logging over `System.out.println`.

---

## Version compatibility matrix

| Component | Minimum Version | Recommended |
|-----------|-----------------|-------------|
| Spring AI | 1.1.0 | **1.1.2** |
| Spring Boot | 3.3.x | **3.4.x** |
| Java | 17 | 21 |
| MCP Protocol | 2024-11-05 | Latest |

The `@McpTool` annotation was introduced in Spring AI **1.1.0-M2**. For production use, version **1.1.2** is the current stable release with full annotation support.

---

## Complete Alexandria RAG Server example

```java
@SpringBootApplication
public class AlexandriaRagServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlexandriaRagServerApplication.class, args);
    }
}

@Component
@RequiredArgsConstructor
public class AlexandriaRagTools {
    
    private final RagSearchService searchService;
    private final CollectionService collectionService;

    public record SearchResult(String id, String title, String content, double score) {}
    public record Collection(String id, String name, int documentCount) {}

    @McpTool(
        name = "rag-search",
        description = "Search Alexandria document corpus using semantic similarity. Returns ranked results with relevance scores.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public List<SearchResult> search(
            @McpToolParam(description = "Natural language search query") String query,
            @McpToolParam(description = "Collection ID to search (optional, searches all if omitted)", required = false) String collection,
            @McpToolParam(description = "Maximum results (1-100, default 10)", required = false) Integer limit) {
        
        int maxResults = (limit != null && limit > 0 && limit <= 100) ? limit : 10;
        return searchService.search(query, collection, maxResults);
    }

    @McpTool(name = "list-collections", description = "List all available document collections")
    public List<Collection> listCollections() {
        return collectionService.getAllCollections();
    }

    @McpTool(name = "get-document", description = "Retrieve full document content by ID")
    public String getDocument(
            @McpToolParam(description = "Document ID") String documentId) {
        return searchService.getDocumentContent(documentId);
    }
}
```

This configuration exposes three MCP tools (`rag-search`, `list-collections`, `get-document`) via SSE transport at `http://localhost:8080/sse`, ready for Claude Code integration.