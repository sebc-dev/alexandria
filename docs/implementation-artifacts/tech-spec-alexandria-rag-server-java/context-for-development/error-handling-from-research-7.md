# Error Handling (from research #7)

**Principe:** Hiérarchie d'exceptions métier avec mapping vers réponses MCP.

```java
package dev.alexandria.core;

/**
 * Exception racine Alexandria avec catégorie et message actionnable.
 */
public class AlexandriaException extends RuntimeException {
    private final ErrorCategory category;

    public AlexandriaException(ErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    public AlexandriaException(ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public ErrorCategory getCategory() { return category; }
}

/**
 * Catégories d'erreurs avec messages user-facing.
 */
public enum ErrorCategory {
    VALIDATION("Validation Error", "Check your query and try again"),
    NOT_FOUND("Not Found", "The requested resource doesn't exist"),
    SERVICE_UNAVAILABLE("Service Unavailable", "External service is temporarily unavailable. Retry in a few seconds"),
    INGESTION_FAILED("Ingestion Failed", "Document could not be processed. Check format and try again"),
    DATABASE_ERROR("Database Error", "Storage operation failed. Contact administrator if persists"),
    TIMEOUT("Timeout", "Operation took too long. Try with a simpler query or smaller document");

    private final String title;
    private final String suggestedAction;

    ErrorCategory(String title, String suggestedAction) {
        this.title = title;
        this.suggestedAction = suggestedAction;
    }

    public String title() { return title; }
    public String suggestedAction() { return suggestedAction; }
}

// Exceptions spécialisées
public class DocumentNotFoundException extends AlexandriaException {
    public DocumentNotFoundException(String documentId) {
        super(ErrorCategory.NOT_FOUND, "Document not found: " + documentId);
    }
}

public class QueryValidationException extends AlexandriaException {
    public QueryValidationException(String message) {
        super(ErrorCategory.VALIDATION, message);
    }
}

public class EmbeddingServiceException extends AlexandriaException {
    public EmbeddingServiceException(String message, Throwable cause) {
        super(ErrorCategory.SERVICE_UNAVAILABLE, message, cause);
    }
}

public class IngestionException extends AlexandriaException {
    public IngestionException(String message, Throwable cause) {
        super(ErrorCategory.INGESTION_FAILED, message, cause);
    }
}
```

**Mapping Exception → Réponse MCP:**

| Exception | isError | Message Pattern |
|-----------|---------|-----------------|
| QueryValidationException | true | "Validation: {message}. Try again." |
| DocumentNotFoundException | true | "Not found: {id}. Check document list." |
| EmbeddingServiceException | true | "Embedding service unavailable. Retry." |
| TimeoutException | true | "Timeout. Simplify query or retry." |
| Other RuntimeException | true | "Unexpected error. Contact admin." |

```java
// Dans McpTools.java
import org.springframework.ai.mcp.server.annotation.McpTool;
import org.springframework.ai.mcp.server.annotation.McpToolParam;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

@McpTool(name = "search_documents", description = "Search documentation")
public CallToolResult searchDocuments(@McpToolParam(description = "Search query") String query) {
    try {
        var response = retrievalService.search(query);
        return McpResponseFormatter.formatSearchResults(response);
    } catch (AlexandriaException e) {
        log.warn("Search failed: {}", e.getMessage());
        return McpResponseFormatter.errorResult(e.getMessage(), e.getCategory());
    } catch (Exception e) {
        log.error("Unexpected error in search", e);
        return McpResponseFormatter.errorResult(
            "An unexpected error occurred", ErrorCategory.DATABASE_ERROR);
    }
}
```
