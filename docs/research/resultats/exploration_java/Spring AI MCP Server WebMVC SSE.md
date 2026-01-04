# Spring AI MCP Server WebMVC SSE : Guide Technique Complet

L'endpoint SSE par défaut de `spring-ai-starter-mcp-server-webmvc` est **`/sse`**, avec les messages clients envoyés vers **`/mcp/message`**. Spring AI **1.1.2** (GA, janvier 2026) introduit un système dual d'annotations : `@Tool` pour le function calling générique et `@McpTool` pour les serveurs MCP avec découverte automatique via `annotation-scanner.enabled=true`.

## Endpoints SSE et leur configuration

Le starter `spring-ai-starter-mcp-server-webmvc` expose automatiquement deux endpoints via `WebMvcSseServerTransportProvider`. L'endpoint **`/sse`** établit la connexion SSE bidirectionnelle persistante, tandis que **`/mcp/message`** reçoit les requêtes JSON-RPC du client.

Ces paths sont entièrement configurables via les propriétés `spring.ai.mcp.server.sse-endpoint` et `spring.ai.mcp.server.sse-message-endpoint`. Un préfixe optionnel peut être ajouté avec `base-url` pour obtenir par exemple `/api/v1/sse`.

```yaml
spring:
  ai:
    mcp:
      server:
        sse-endpoint: /sse              # Défaut: /sse
        sse-message-endpoint: /mcp/message  # Défaut: /mcp/message
        base-url: /api/v1               # Optionnel, préfixe les deux endpoints
```

## Configuration application.yaml complète

Voici la configuration exhaustive avec toutes les propriétés disponibles pour le projet Alexandria :

```yaml
spring:
  application:
    name: alexandria-mcp-server
    
  ai:
    mcp:
      server:
        # Identification du serveur
        name: alexandria-rag-server
        version: 1.0.0
        instructions: "Serveur RAG Alexandria exposant des outils de recherche documentaire"
        
        # Type de serveur
        enabled: true
        type: SYNC                    # SYNC ou ASYNC
        stdio: false                  # false pour SSE/HTTP, true pour STDIO
        
        # Configuration SSE (WebMVC)
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        base-url:                     # Préfixe URL optionnel
        keep-alive-interval: 30s     # Intervalle heartbeat SSE
        request-timeout: 60s         # Timeout des requêtes MCP
        
        # Capacités exposées
        capabilities:
          tool: true                 # Activer les outils
          resource: true             # Activer les ressources
          prompt: true               # Activer les prompts
          completion: true           # Activer l'auto-complétion
        
        # Notifications de changement
        tool-change-notification: true
        resource-change-notification: true
        prompt-change-notification: true
        
        # Découverte automatique des annotations
        annotation-scanner:
          enabled: true              # Scanner automatique @McpTool, @McpResource, @McpPrompt
        
        # Conversion automatique Spring AI -> MCP
        tool-callback-converter: true
        
        # Types MIME personnalisés par outil
        tool-response-mime-type:
          search-documents: application/json
          get-document: text/markdown

server:
  port: 8080
```

## Mécanisme de découverte des Tools MCP

Spring AI MCP utilise **deux systèmes d'annotations distincts** avec des mécanismes de découverte différents.

### Annotation @McpTool pour serveurs MCP

L'annotation `@McpTool` est spécifique aux serveurs MCP et bénéficie d'un **scanning automatique** lorsque `annotation-scanner.enabled=true`. Les beans Spring annotés sont détectés au démarrage et automatiquement enregistrés.

```java
@Component
public class AlexandriaTools {
    
    private final DocumentService documentService;
    
    @McpTool(
        name = "search-documents",
        description = "Recherche sémantique dans la base documentaire Alexandria",
        annotations = @McpTool.McpAnnotations(
            title = "Recherche de documents",
            readOnlyHint = true,
            idempotentHint = true
        )
    )
    public SearchResult searchDocuments(
            McpSyncServerExchange exchange,  // Contexte optionnel pour logging/progress
            @McpToolParam(description = "Requête de recherche en langage naturel", required = true) 
            String query,
            @McpToolParam(description = "Nombre maximum de résultats (défaut: 10)", required = false) 
            Integer limit) {
        
        exchange.loggingNotification(LoggingLevel.INFO, "Recherche: " + query);
        return documentService.semanticSearch(query, limit != null ? limit : 10);
    }
    
    @McpTool(
        name = "get-document",
        description = "Récupère le contenu complet d'un document par son identifiant"
    )
    public Document getDocument(
            @McpToolParam(description = "Identifiant unique du document", required = true) 
            String documentId) {
        return documentService.findById(documentId);
    }
}
```

### Annotation @Tool pour integration Spring AI

L'annotation `@Tool` est générique à Spring AI et nécessite une **registration explicite** via `ToolCallbackProvider` ou `MethodToolCallbackProvider` :

```java
@Service
public class RagService {
    
    @Tool(
        name = "rag-query",
        description = "Exécute une requête RAG sur la base de connaissances"
    )
    public String ragQuery(
            @ToolParam(description = "Question en langage naturel") String question) {
        // Implémentation RAG
        return retrieveAndGenerate(question);
    }
}

@Configuration
public class ToolConfig {
    
    @Bean
    public ToolCallbackProvider ragTools(RagService ragService) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(ragService)
            .build();
    }
}
```

La propriété `tool-callback-converter: true` convertit automatiquement les `ToolCallback` Spring AI en spécifications MCP, permettant d'utiliser les deux systèmes simultanément.

## Configuration CORS pour Claude Code local

Pour que Claude Code puisse se connecter au serveur SSE local, une configuration CORS permissive est nécessaire. Les endpoints SSE requièrent des headers spécifiques pour maintenir la connexion.

```java
@Configuration
public class McpCorsConfiguration implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/sse/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("Accept", "Cache-Control", "Connection", "Content-Type")
            .exposedHeaders("Content-Type", "Cache-Control", "Transfer-Encoding")
            .maxAge(3600);
            
        registry.addMapping("/mcp/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Content-Type")
            .maxAge(3600);
    }
}
```

Pour une configuration plus fine avec `CorsFilter` :

```java
@Bean
public FilterRegistrationBean<CorsFilter> corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(false);
    config.addAllowedOriginPattern("*");
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.addExposedHeader("Content-Type");
    config.addExposedHeader("Transfer-Encoding");
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/sse/**", config);
    source.registerCorsConfiguration("/mcp/**", config);
    
    FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
}
```

## Logging et debugging des messages MCP

Le debugging des messages JSON-RPC MCP nécessite l'activation de plusieurs loggers spécifiques :

```yaml
logging:
  level:
    # Transport SSE - messages entrants/sortants
    io.modelcontextprotocol.server.transport: DEBUG
    io.modelcontextprotocol.server.transport.WebMvcSseServerTransport: TRACE
    
    # Spécifications MCP - parsing JSON-RPC
    io.modelcontextprotocol.spec: DEBUG
    
    # Auto-configuration Spring AI MCP
    org.springframework.ai.mcp: DEBUG
    org.springframework.ai.mcp.server.autoconfigure: DEBUG
    
    # Vos outils Alexandria
    com.alexandria.mcp.tools: DEBUG
    
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    
  file:
    name: logs/mcp-server.log
```

Pour capturer les messages JSON-RPC complets, créez un intercepteur :

```java
@Component
@Slf4j
public class McpMessageLogger {
    
    @EventListener
    public void onMcpRequest(McpRequestEvent event) {
        log.debug("MCP Request: method={}, id={}, params={}", 
            event.getMethod(), event.getId(), event.getParams());
    }
    
    @EventListener  
    public void onMcpResponse(McpResponseEvent event) {
        log.debug("MCP Response: id={}, result={}", 
            event.getId(), event.getResult());
    }
}
```

## Configuration Claude Code pour MCP SSE

Pour connecter Claude Code au serveur Alexandria SSE, ajoutez dans `~/.claude/claude_desktop_config.json` :

```json
{
  "mcpServers": {
    "alexandria": {
      "url": "http://localhost:8080/sse",
      "transport": "sse"
    }
  }
}
```

**Note importante** : Claude Desktop requiert HTTPS pour les connexions distantes. En développement local sur `localhost`, HTTP fonctionne. Pour un accès distant, utilisez un tunnel HTTPS (ngrok, Cloudflare Tunnel) ou configurez TLS.

## Dépendances Maven requises

```xml
<dependencies>
    <!-- Spring AI MCP Server WebMVC (SSE) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>
    
    <!-- Annotations MCP (optionnel, pour @McpTool avancé) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-mcp-server-annotations</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Conclusion

Le projet Alexandria peut exploiter Spring AI MCP **1.1.2** avec le transport SSE WebMVC via trois éléments clés : les endpoints `/sse` et `/mcp/message` configurables, le scanning automatique des `@McpTool` pour exposer les outils RAG, et une configuration CORS permissive pour Claude Code local. L'activation du logging sur `io.modelcontextprotocol.server.transport` niveau DEBUG permet de tracer tous les échanges JSON-RPC pour le debugging. Pour Java 25, vérifiez la compatibilité avec la preview **2.0.0-M1** qui cible Spring Boot 4.0/Spring Framework 7.0.