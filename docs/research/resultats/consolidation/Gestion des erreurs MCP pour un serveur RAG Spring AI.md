# Gestion des erreurs MCP pour un serveur RAG Spring AI

Les erreurs d'un serveur MCP doivent être formatées différemment selon leur nature : **erreurs protocole** (JSON-RPC) pour les problèmes de communication, **erreurs tool avec `isError: true`** pour les échecs métier. Cette distinction critique permet à Claude de voir les erreurs d'exécution et d'adapter sa stratégie, tandis que les erreurs protocole sont discardées. Pour votre projet Alexandria utilisant Spring AI MCP SDK 1.1.2 avec transport SSE, la clé réside dans une hiérarchie d'exceptions claire mappant chaque type d'erreur vers le format MCP approprié.

## Format d'erreur MCP selon la spécification officielle

La spécification MCP hérite de JSON-RPC 2.0 avec une structure d'erreur précise. Une réponse d'erreur protocole contient obligatoirement `jsonrpc`, `id`, et un objet `error` avec trois champs : `code` (entier obligatoire), `message` (chaîne courte, idéalement une phrase), et `data` (optionnel, format libre pour détails supplémentaires).

| Code | Nom | Usage |
|------|-----|-------|
| **-32700** | Parse Error | JSON invalide reçu |
| **-32600** | Invalid Request | Structure de requête incorrecte |
| **-32601** | Method Not Found | Tool ou méthode inexistant |
| **-32602** | Invalid Params | Paramètres de méthode invalides |
| **-32603** | Internal Error | Erreur interne serveur |
| **-32000 à -32099** | Server Error | Réservés pour erreurs custom |

Le champ `data` peut contenir des détails structurés comme l'URI de ressource concernée, les informations de validation (`path`, `expected`, `received`), ou des métadonnées de retry (`retryAfter`). Exemple de réponse avec validation détaillée :

```json
{
  "jsonrpc": "2.0",
  "id": 42,
  "error": {
    "code": -32602,
    "message": "Invalid params: query must be a non-empty string",
    "data": {"path": "query", "expected": "string (1-4000 chars)", "received": "empty"}
  }
}
```

## Erreurs tool : le pattern isError pour la récupération par le LLM

**Point critique de la spécification** : les erreurs d'exécution de tools ne doivent PAS utiliser le format d'erreur protocole. Elles doivent être retournées dans un `result` avec `isError: true`, permettant au LLM de voir l'erreur et potentiellement réessayer ou adapter sa stratégie.

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "content": [{"type": "text", "text": "La recherche sémantique n'a retourné aucun résultat pour 'quantum computing basics'. Suggestions : reformuler avec des termes plus spécifiques ou vérifier l'orthographe."}],
    "isError": true
  }
}
```

Cette architecture permet à Claude de distinguer les erreurs récupérables (service temporairement indisponible → retry) des erreurs nécessitant une action utilisateur (query invalide → reformulation). Les erreurs protocole JSON-RPC, elles, ne sont **pas injectées dans le contexte du LLM** et causent simplement l'échec de l'appel.

## Spring AI MCP SDK : mécanismes de traduction des exceptions

Spring AI MCP SDK 1.1.2 traduit automatiquement les exceptions des méthodes `@McpTool` en `CallToolResult` avec `isError: true`. Depuis la version 1.1.0-M1, les `RuntimeException` sont correctement wrappées en `ToolExecutionException`, permettant la récupération par le modèle.

```java
@McpTool(name = "rag-search", description = "Recherche sémantique dans Alexandria")
public CallToolResult searchDocuments(
        @McpToolParam(description = "Requête de recherche", required = true) String query,
        @McpToolParam(description = "Nombre max de résultats") Integer maxResults) {
    
    try {
        List<Document> results = ragService.search(query, maxResults != null ? maxResults : 10);
        return CallToolResult.builder()
            .addTextContent(formatResults(results))
            .build();
    } catch (EmbeddingServiceException e) {
        return CallToolResult.builder()
            .isError(true)
            .addTextContent("Service d'embedding temporairement indisponible. Réessayez dans quelques secondes.")
            .build();
    }
}
```

**Important** : les annotations `@ExceptionHandler` et `@ControllerAdvice` de Spring MVC **ne fonctionnent pas** avec `@McpTool` car MCP utilise son propre cycle de vie via `McpSyncServer`. Spring AI fournit `ToolExecutionExceptionProcessor` pour personnaliser le traitement des exceptions, configurable via `spring.ai.tools.throw-exception-on-error=false`.

## Hiérarchie d'exceptions recommandée pour Alexandria

Une hiérarchie claire distinguant erreurs récupérables et fatales, utilisateur et système :

```java
public abstract class AlexandriaException extends RuntimeException {
    private final ErrorCategory category;
    private final boolean recoverable;
    private final String userActionHint;
    
    public enum ErrorCategory {
        EMBEDDING_SERVICE,   // Infinity Server sur RunPod
        VECTOR_STORE,        // PostgreSQL + pgvector
        DOCUMENT_INGESTION,  // Parsing llms.txt, markdown
        QUERY_VALIDATION,    // Input utilisateur invalide
        RETRIEVAL            // Recherche sémantique
    }
}

// Erreurs transitoires (retry possible)
public class EmbeddingTimeoutException extends AlexandriaException { /* recoverable=true */ }
public class VectorStoreConnectionException extends AlexandriaException { /* recoverable=true */ }

// Erreurs fatales (action utilisateur requise)  
public class QueryTooLongException extends AlexandriaException { /* recoverable=false */ }
public class VectorDimensionMismatchException extends AlexandriaException { /* recoverable=false */ }
public class DocumentParsingException extends AlexandriaException { /* recoverable=false */ }
```

## Mapping exception Java vers erreur MCP

| Exception Java | Code MCP | Type | Message template |
|---------------|----------|------|------------------|
| `SocketTimeoutException` | Result + isError | Transitoire | "Service d'embedding temporairement indisponible. Réessayez." |
| `ConnectException` | Result + isError | Transitoire | "Connexion au service d'embedding impossible. Retry automatique en cours." |
| `PSQLException` (08xxx) | Result + isError | Transitoire | "Base de données temporairement inaccessible." |
| `QueryTooLongException` | Result + isError | Utilisateur | "La requête dépasse {max} caractères. Reformulez de manière plus concise." |
| `VectorDimensionMismatchException` | -32603 | Configuration | "Erreur de configuration serveur (dimension mismatch)." |
| `EmptyResultsException` | Result + isError | Métier | "Aucun document trouvé pour '{query}'. Suggestions : {hints}" |
| `IllegalArgumentException` (param) | -32602 | Protocole | Validation automatique par le SDK |

## Configuration Resilience4j pour les appels externes

Les appels HTTP vers Infinity Server (embeddings/reranking sur RunPod) nécessitent une configuration de résilience robuste :

```yaml
resilience4j:
  retry:
    instances:
      infinity-embeddings:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.net.SocketTimeoutException
          - java.net.ConnectException
          - java.io.IOException
        ignoreExceptions:
          - org.alexandria.exception.QueryValidationException
  
  circuitbreaker:
    instances:
      infinity-embeddings:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
  
  timelimiter:
    instances:
      embeddings:
        timeoutDuration: 30s
      reranking:
        timeoutDuration: 60s
```

L'ordre d'application recommandé : **Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead**.

## Erreurs pgvector spécifiques à surveiller

| Erreur pgvector | Cause | Récupérable | Action |
|-----------------|-------|-------------|--------|
| `expected X dimensions, not Y` | Dimension embedding incorrecte | Non | Vérifier config Infinity Server |
| `cannot have more than 2000 dimensions for hnsw index` | Limite HNSW dépassée | Non | Utiliser halfvec ou réduction dimensionnelle |
| `column does not have dimensions` | Colonne vector mal définie | Non | Migration schema requise |

Pour PostgreSQL, les codes SQLState **08xxx** (connection errors) et **57P01** (admin shutdown) sont récupérables avec retry, tandis que **42601** (syntax error) et **23505** (unique violation) sont fatals.

## Comment Claude Code interprète les erreurs

Claude Code **ne fait pas de retry automatique** sur les erreurs MCP. Les erreurs protocole (JSON-RPC) sont affichées à l'utilisateur mais **non injectées dans le contexte du LLM**. Seules les erreurs avec `isError: true` sont visibles par le modèle, lui permettant d'adapter sa stratégie.

Format d'affichage observé dans Claude Code :
```
● alexandria:rag-search (MCP)(query: "quantum computing")…
⎿ Error: Error calling tool rag-search: Service d'embedding temporairement indisponible.
```

**Messages d'erreur actionnables** — la clé pour une bonne expérience utilisateur :

```json
{
  "content": [{
    "type": "text",
    "text": "[Contexte] La recherche dans Alexandria a échoué.\n[Problème] Le service d'embedding Infinity sur RunPod n'a pas répondu dans le délai imparti (30s).\n[Solution] Réessayez immédiatement. Si le problème persiste après 3 tentatives, le service est probablement surchargé."
  }],
  "isError": true
}
```

## Pattern d'implémentation complet pour un tool RAG

```java
@Component
public class AlexandriaRagTools {

    private final RagService ragService;
    private final MeterRegistry meterRegistry;

    @McpTool(name = "alexandria-search", 
             description = "Recherche sémantique dans la base documentaire Alexandria")
    public CallToolResult search(
            McpSyncRequestContext context,
            @McpToolParam(description = "Requête en langage naturel", required = true) String query,
            @McpToolParam(description = "Nombre max de résultats (1-50)") Integer maxResults) {
        
        // Validation explicite
        if (query == null || query.isBlank()) {
            return errorResult("La requête ne peut pas être vide.");
        }
        if (query.length() > 4000) {
            return errorResult("La requête dépasse 4000 caractères. Reformulez de manière plus concise.");
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            context.info("Recherche Alexandria: " + query.substring(0, Math.min(50, query.length())));
            
            int limit = (maxResults != null && maxResults >= 1 && maxResults <= 50) ? maxResults : 10;
            List<Document> results = ragService.search(query, limit);
            
            sample.stop(meterRegistry.timer("alexandria.search.success"));
            
            if (results.isEmpty()) {
                return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("Aucun document pertinent trouvé pour '" + query + 
                        "'. Suggestions : utilisez des termes plus généraux ou vérifiez l'orthographe.")
                    .build();
            }
            
            return CallToolResult.builder()
                .addTextContent(formatResults(results))
                .build();
                
        } catch (EmbeddingServiceException e) {
            sample.stop(meterRegistry.timer("alexandria.search.error.embedding"));
            context.error("Embedding service failed: " + e.getMessage());
            return errorResult("Service d'embedding temporairement indisponible. Réessayez dans quelques secondes.");
            
        } catch (VectorStoreException e) {
            sample.stop(meterRegistry.timer("alexandria.search.error.vectorstore"));
            context.error("Vector store query failed: " + e.getMessage());
            return errorResult("Base de données indisponible. L'équipe technique a été notifiée.");
        }
    }
    
    private CallToolResult errorResult(String message) {
        return CallToolResult.builder()
            .isError(true)
            .addTextContent(message)
            .build();
    }
}
```

## Conclusion : stratégie unifiée de gestion des erreurs

Pour votre serveur MCP Alexandria, adoptez une approche à trois niveaux. **Niveau 1 : validation input** avec des messages clairs indiquant à l'utilisateur comment corriger sa requête. **Niveau 2 : résilience externe** via Resilience4j pour les appels Infinity Server et PostgreSQL, avec circuit breakers protégeant contre les cascades de pannes. **Niveau 3 : messages actionnables** retournés avec `isError: true` permettant à Claude de comprendre l'erreur et suggérer des alternatives.

Les points critiques à retenir : ne jamais utiliser les erreurs JSON-RPC protocole pour les échecs métier (le LLM ne les voit pas), toujours inclure une suggestion d'action dans les messages d'erreur, et implémenter les retries côté serveur plutôt que de compter sur le client. Cette architecture garantit une expérience utilisateur fluide même en cas de défaillance partielle des composants externes.