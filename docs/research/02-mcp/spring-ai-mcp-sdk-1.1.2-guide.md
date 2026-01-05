# Transport HTTP Streamable pour MCP avec Spring AI SDK

Le transport HTTP Streamable est la nouvelle norme MCP recommandée depuis mars 2025, remplaçant le transport SSE. **Spring AI MCP SDK 1.1.2 le supporte pleinement** avec une simple modification de configuration : passer de `protocol: SSE` à `protocol: STREAMABLE`. Pour le projet Alexandria, la migration depuis SSE est directe et apporte des avantages significatifs : architecture simplifiée (un seul endpoint `/mcp`), meilleure compatibilité infrastructure, et support natif de la reprise de connexion.

---

## L'évolution majeure de la spécification MCP

Le transport HTTP Streamable a été introduit dans la **spécification MCP version 2025-03-26** via le PR #206 du repository officiel. Cette évolution représente un changement architectural fondamental dans la façon dont les clients et serveurs MCP communiquent.

Contrairement au transport SSE qui nécessitait **deux endpoints distincts** (`/sse` pour la connexion SSE et `/mcp/message` pour les requêtes POST), HTTP Streamable utilise un **endpoint unique** (`/mcp`) qui gère toutes les communications. Le serveur peut répondre soit en JSON simple (`application/json`), soit en flux SSE (`text/event-stream`) selon le contexte de chaque requête.

| Caractéristique | SSE (2024-11-05) | HTTP Streamable (2025-03-26) |
|-----------------|------------------|------------------------------|
| Endpoints | `/sse` + `/mcp/message` | `/mcp` unique |
| Connexion | Persistante obligatoire | À la demande |
| Gestion session | Implicite | Explicite via `Mcp-Session-Id` |
| Reprise connexion | Non supportée | Native via `Last-Event-ID` |
| Serverless | Incompatible | Optimisé |
| **Statut** | **Déprécié** | **Recommandé** |

L'architecture HTTP Streamable apporte une **meilleure compatibilité infrastructure** : proxies, load balancers, et firewalls d'entreprise gèrent mieux les requêtes HTTP standard que les connexions SSE longue durée qui peuvent être interrompues après timeout.

---

## Support complet dans Spring AI MCP SDK 1.1.2

Spring AI MCP SDK 1.1.2 GA **supporte pleinement HTTP Streamable** depuis la version **1.1.0-M1** publiée le 9 septembre 2025. L'implémentation s'appuie sur le MCP Java SDK v0.15.0 et offre trois modes de transport : **SSE** (legacy), **STREAMABLE** (recommandé), et **STATELESS** (cloud-native).

**Aucun changement de dépendances Maven n'est nécessaire** pour migrer. Les mêmes starters supportent les deux protocoles :

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

La seule modification requise est la propriété de configuration qui passe de SSE à STREAMABLE. Le starter WebMVC convient parfaitement à votre stack Spring Boot 3.5.9, tandis que le starter WebFlux est disponible pour les architectures réactives.

---

## Configuration concrète pour le projet Alexandria

### Configuration serveur application.yml

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE      # Passage de SSE à Streamable
        name: alexandria-mcp-server
        version: 1.0.0
        type: SYNC                # Compatible Virtual Threads
        
        annotation-scanner:
          enabled: true
        
        capabilities:
          tool: true
          resource: true
          prompt: true
          completion: true
        
        tool-change-notification: true
        resource-change-notification: true
        request-timeout: 60s      # Timeout MCP standard
        
        streamable-http:
          mcp-endpoint: /mcp      # Remplace /sse + /mcp/message
          keep-alive-interval: 30s

server:
  port: 8080
```

### Comparaison des endpoints exposés

Avec cette configuration, votre serveur exposera un **unique endpoint** au lieu des deux actuels :

| Transport actuel (SSE) | Nouveau (HTTP Streamable) |
|------------------------|---------------------------|
| `GET /sse` | - |
| `POST /mcp/message` | - |
| - | `POST /mcp` (requêtes) |
| - | `GET /mcp` (stream optionnel) |
| - | `DELETE /mcp` (fermeture session) |

Les annotations Java `@McpTool`, `@McpResource`, `@McpPrompt` restent **strictement identiques** :

```java
@Service
public class AlexandriaTools {

    @McpTool(name = "searchDocuments", 
             description = "Recherche dans les documents Alexandria")
    public SearchResult search(
            @McpToolParam(description = "Query de recherche", required = true) 
            String query,
            @McpToolParam(description = "Nombre max de résultats") 
            Integer limit) {
        // Implémentation inchangée
    }
}
```

---

## Configuration client Claude Code

Claude Code supporte HTTP Streamable via le flag `--transport http`. Voici les configurations selon le contexte d'utilisation :

### Via CLI (recommandé)

```bash
# Ajouter le serveur Alexandria
claude mcp add --transport http alexandria http://localhost:8080/mcp

# Avec authentification Bearer
claude mcp add --transport http alexandria http://localhost:8080/mcp \
  --header "Authorization: Bearer ${ALEXANDRIA_TOKEN}"
```

### Via fichier .mcp.json (scope projet)

```json
{
  "mcpServers": {
    "alexandria": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer ${ALEXANDRIA_TOKEN}"
      }
    }
  }
}
```

### Différences avec la configuration SSE actuelle

| Aspect | Configuration SSE | Configuration HTTP Streamable |
|--------|-------------------|-------------------------------|
| Type | `"type": "sse"` | `"type": "http"` |
| URL | `http://localhost:8080/sse` | `http://localhost:8080/mcp` |
| CLI flag | `--transport sse` | `--transport http` |

Le transport SSE est **officiellement déprécié** par Anthropic et sera supprimé dans les prochains mois.

---

## Compatibilité avec l'écosystème du projet

### Langchain4j 1.10.0

**Langchain4j 1.10.0 supporte pleinement HTTP Streamable** via `StreamableHttpMcpTransport`, introduit dans la version 1.4.0. Configuration d'un client Langchain4j connecté à votre serveur Spring AI :

```java
McpTransport transport = StreamableHttpMcpTransport.builder()
    .url("http://localhost:8080/mcp")
    .logRequests(true)
    .build();

McpClient mcpClient = DefaultMcpClient.builder()
    .key("AlexandriaClient")
    .transport(transport)
    .build();

McpToolProvider toolProvider = McpToolProvider.builder()
    .mcpClients(mcpClient)
    .build();
```

**Limitation connue** : l'implémentation Langchain4j ne crée pas de stream SSE global, donc les notifications serveur-vers-client peuvent ne pas fonctionner selon l'implémentation serveur.

### Virtual Threads Java 21+

HTTP Streamable est **compatible avec les Virtual Threads** Java 21+. Points d'attention :

- **Apache HttpComponents 5.4+** : entièrement compatible, remplace les blocs `synchronized` par des primitives de verrouillage
- **JDK HttpClient** : limite de 100 flux HTTP/2 concurrents par connexion
- **Spring AI type: SYNC** : supporte les thread-locals utilisés par les Virtual Threads

Pour monitorer le thread pinning potentiel :
```bash
java -Djdk.tracePinnedThreads=full -jar alexandria-server.jar
```

### Timeout MCP 60 secondes

Le timeout par défaut MCP est **60 secondes**. Configuration côté Claude Code :

```json
{
  "env": {
    "MCP_TIMEOUT": "60000",
    "MCP_TOOL_TIMEOUT": "120000"
  }
}
```

Pour les opérations longues côté serveur, envoyez des **notifications de progression** pour maintenir la connexion active :

```java
@McpTool(name = "longOperation")
public Result longOperation(
        McpSyncServerExchange exchange,
        @McpProgressToken String progressToken) {
    
    exchange.progressNotification(new ProgressNotification(
        progressToken, 0.5, 1.0, "50% terminé"));
    
    // Suite du traitement...
}
```

---

## Migration recommandée pour Alexandria

La migration de SSE vers HTTP Streamable est **simple et à faible risque** :

1. **Aucune modification des dépendances Maven** - mêmes starters
2. **Une seule propriété à modifier** : `spring.ai.mcp.server.protocol=STREAMABLE`
3. **Annotations Java inchangées** - `@McpTool`, `@McpResource`, `@McpPrompt`
4. **Mise à jour configuration Claude Code** : changer `type: sse` en `type: http` et l'URL de `/sse` vers `/mcp`

Cette migration apporte une **architecture plus simple** (un endpoint au lieu de deux), une **meilleure compatibilité** avec les infrastructures d'entreprise, et l'alignement avec la direction officielle du protocole MCP. Le transport SSE étant déprécié, la migration est conseillée dès que possible.