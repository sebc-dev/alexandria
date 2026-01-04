# Testing E2E des serveurs MCP avec Spring AI SDK 1.1.2

**Spring AI 1.1.2 offre un écosystème complet pour tester les serveurs MCP**, incluant des clients synchrones/asynchrones, des transports SSE natifs, et une intégration transparente avec les outils de test Spring Boot. Cette stratégie détaille les approches recommandées, du test unitaire au test E2E avec le vrai Claude Code CLI.

---

## Client MCP Spring AI pour les tests

Spring AI ne fournit pas de "TestMcpClient" dédié, mais les classes **`McpSyncClient`** et **`McpAsyncClient`** servent à la fois en production et en test. Le module `mcp-test` du SDK expérimental apporte des utilitaires supplémentaires.

### Dépendances Maven essentielles

```xml
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

<dependencies>
    <!-- Client MCP WebFlux (recommandé) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-client-webflux</artifactId>
    </dependency>
    
    <!-- Transport SSE bas niveau -->
    <dependency>
        <groupId>org.springframework.experimental</groupId>
        <artifactId>mcp-webflux-sse-transport</artifactId>
    </dependency>
    
    <!-- Test utilities -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Classes principales pour les tests

| Classe | Package | Usage |
|--------|---------|-------|
| `McpSyncClient` | `io.modelcontextprotocol.client` | Client synchrone bloquant |
| `McpAsyncClient` | `io.modelcontextprotocol.client` | Client réactif asynchrone |
| `WebFluxSseClientTransport` | `mcp-webflux-sse-transport` | Transport SSE WebFlux |
| `HttpClientSseClientTransport` | `mcp` | Transport SSE JDK HttpClient |
| `SyncMcpToolCallbackProvider` | `spring-ai-mcp` | Fournit les callbacks d'outils |

---

## Format du protocole MCP sur SSE

Le protocole MCP utilise **JSON-RPC 2.0** sur deux types de transport SSE. Le transport moderne "Streamable HTTP" (version 2025-06-18) tend à remplacer l'ancien transport HTTP+SSE.

### Transport HTTP+SSE (endpoints /sse et /mcp/message)

```
┌─────────────┐                      ┌─────────────┐
│   Client    │──── GET /sse ───────>│   Server    │
│             │<──── event:endpoint ─│             │
│             │      data:/mcp/msg?s=│             │
│             │                      │             │
│             │── POST /mcp/message ─│             │
│             │   {JSON-RPC request} │             │
│             │<──── event:message ──│             │
│             │   {JSON-RPC response}│             │
└─────────────┘                      └─────────────┘
```

### Handshake d'initialisation MCP

```java
// 1. Client envoie initialize
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": { "roots": { "listChanged": true } },
    "clientInfo": { "name": "test-client", "version": "1.0.0" }
  }
}

// 2. Server répond avec ses capabilities
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": { "tools": { "listChanged": true } },
    "serverInfo": { "name": "my-mcp-server", "version": "1.0.0" }
  }
}

// 3. Client confirme avec notification (pas d'id)
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

### Messages tools/list et tools/call

```java
// Découverte des outils
{ "jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {} }

// Réponse
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [{
      "name": "calculate",
      "description": "Effectue des calculs",
      "inputSchema": {
        "type": "object",
        "properties": {
          "operation": { "type": "string", "enum": ["add", "subtract"] },
          "a": { "type": "number" },
          "b": { "type": "number" }
        },
        "required": ["operation", "a", "b"]
      }
    }]
  }
}

// Invocation d'outil
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": { "name": "calculate", "arguments": { "operation": "add", "a": 5, "b": 3 } }
}

// Réponse
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [{ "type": "text", "text": "Résultat: 8" }],
    "isError": false
  }
}
```

---

## Test d'intégration avec McpSyncClient

L'approche la plus directe consiste à utiliser le client MCP de Spring AI pour tester votre serveur SSE.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
class McpServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Devrait découvrir et exécuter les outils MCP")
    void shouldDiscoverAndExecuteMcpTools() {
        // Configuration du transport SSE
        WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        
        WebFluxSseClientTransport transport = WebFluxSseClientTransport
            .builder(webClient)
            .build();
        
        // Création du client MCP synchrone
        McpSyncClient mcpClient = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .build();
        
        try {
            // Initialisation du protocole MCP
            mcpClient.initialize();
            mcpClient.ping();
            
            // Test tools/list
            ListToolsResult toolsResult = mcpClient.listTools();
            assertThat(toolsResult.tools())
                .isNotEmpty()
                .extracting(McpSchema.Tool::name)
                .contains("calculate", "getWeather");
            
            // Test tools/call
            CallToolResult result = mcpClient.callTool(
                new CallToolRequest("calculate", 
                    Map.of("operation", "add", "a", 10, "b", 5))
            );
            
            assertThat(result.content())
                .isNotEmpty()
                .first()
                .extracting(content -> ((TextContent) content).text())
                .asString()
                .contains("15");
            
            assertThat(result.isError()).isFalse();
            
        } finally {
            mcpClient.closeGracefully();
        }
    }
    
    @Test
    @DisplayName("Devrait gérer les erreurs d'outil gracieusement")
    void shouldHandleToolErrors() {
        withMcpClient(client -> {
            CallToolResult result = client.callTool(
                new CallToolRequest("calculate", 
                    Map.of("operation", "divide", "a", 10, "b", 0))
            );
            
            assertThat(result.isError()).isTrue();
            assertThat(result.content().get(0))
                .extracting(c -> ((TextContent) c).text())
                .asString()
                .containsIgnoringCase("division");
        });
    }
    
    private void withMcpClient(Consumer<McpSyncClient> testFunc) {
        WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        WebFluxSseClientTransport transport = WebFluxSseClientTransport
            .builder(webClient)
            .build();
        McpSyncClient client = McpClient.sync(transport).build();
        
        try {
            client.initialize();
            testFunc.accept(client);
        } finally {
            client.closeGracefully();
        }
    }
}
```

---

## Test des endpoints SSE avec WebTestClient

Pour tester le flux SSE brut sans passer par le client MCP, utilisez **WebTestClient avec StepVerifier**. MockMvc n'est pas recommandé pour les streams SSE infinis.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SseEndpointTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    @DisplayName("Devrait recevoir l'événement endpoint sur /sse")
    void shouldReceiveEndpointEvent() {
        FluxExchangeResult<String> result = webClient.get()
            .uri("/sse")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String.class);

        StepVerifier.create(result.getResponseBody())
            .expectNextMatches(event -> event.contains("/mcp/message"))
            .thenCancel()  // Crucial pour les streams infinis
            .verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Devrait traiter les messages JSON-RPC sur /mcp/message")
    void shouldProcessJsonRpcMessages() {
        // D'abord obtenir le sessionId via /sse
        String sessionId = extractSessionIdFromSse();
        
        String initializeRequest = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": { "name": "test", "version": "1.0" }
                }
            }
            """;
        
        webClient.post()
            .uri("/mcp/message?sessionId=" + sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(initializeRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.jsonrpc").isEqualTo("2.0")
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.result.protocolVersion").exists()
            .jsonPath("$.result.serverInfo.name").exists();
    }
}
```

---

## Validation des réponses MCP

### Schémas JSON officiels

Les schémas MCP sont disponibles sur GitHub :
- **URL**: `https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/main/schema/2025-06-18/schema.json`

### Validation programmatique avec Jackson et JSON Schema

```java
@Component
public class McpResponseValidator {
    
    private final ObjectMapper objectMapper;
    private final JsonSchema mcpSchema;
    
    public McpResponseValidator() throws Exception {
        this.objectMapper = new ObjectMapper();
        
        // Charger le schéma MCP
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        InputStream schemaStream = getClass().getResourceAsStream("/mcp-schema.json");
        this.mcpSchema = factory.getSchema(schemaStream);
    }
    
    public ValidationResult validateJsonRpcResponse(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // Validation JSON-RPC 2.0 de base
            if (!jsonNode.has("jsonrpc") || !"2.0".equals(jsonNode.get("jsonrpc").asText())) {
                return ValidationResult.error("jsonrpc doit être '2.0'");
            }
            
            if (!jsonNode.has("id") || jsonNode.get("id").isNull()) {
                return ValidationResult.error("id requis et non null");
            }
            
            boolean hasResult = jsonNode.has("result");
            boolean hasError = jsonNode.has("error");
            
            if (hasResult == hasError) {
                return ValidationResult.error("Doit avoir result XOR error");
            }
            
            // Validation contre le schéma MCP complet
            Set<ValidationMessage> errors = mcpSchema.validate(jsonNode);
            if (!errors.isEmpty()) {
                return ValidationResult.error(errors.toString());
            }
            
            return ValidationResult.success();
            
        } catch (JsonProcessingException e) {
            return ValidationResult.error("JSON invalide: " + e.getMessage());
        }
    }
    
    public ValidationResult validateToolsListResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            JsonNode result = node.get("result");
            
            if (!result.has("tools") || !result.get("tools").isArray()) {
                return ValidationResult.error("tools doit être un array");
            }
            
            for (JsonNode tool : result.get("tools")) {
                if (!tool.has("name") || tool.get("name").asText().isEmpty()) {
                    return ValidationResult.error("Chaque tool doit avoir un name");
                }
                if (!tool.has("inputSchema")) {
                    return ValidationResult.error("inputSchema requis pour " + tool.get("name"));
                }
            }
            
            return ValidationResult.success();
        } catch (Exception e) {
            return ValidationResult.error(e.getMessage());
        }
    }
}
```

### Utilisation du MCP Inspector pour validation

```bash
# Installation et validation automatisée
npx @modelcontextprotocol/inspector --cli \
    http://localhost:8080/mcp \
    --method tools/list

# Tester un outil spécifique
npx @modelcontextprotocol/inspector --cli \
    http://localhost:8080/mcp \
    --method tools/call \
    --tool-name calculate \
    --tool-arg operation=add \
    --tool-arg a=5 \
    --tool-arg b=3
```

---

## Tests avec le vrai Claude Code CLI

Claude Code CLI supporte un **mode headless** (`-p`) idéal pour l'automatisation et les tests CI/CD.

### Configuration MCP pour Claude Code

Créez `.mcp.json` à la racine du projet :

```json
{
  "mcpServers": {
    "my-spring-mcp-server": {
      "type": "http",
      "url": "http://localhost:8080",
      "headers": {
        "Authorization": "Bearer ${TEST_API_KEY}"
      }
    }
  }
}
```

### Script de test automatisé

```bash
#!/bin/bash
# test-with-claude-code.sh

set -euo pipefail

# Démarrer le serveur MCP Spring en arrière-plan
./mvnw spring-boot:run &
SERVER_PID=$!
sleep 10  # Attendre le démarrage

cleanup() {
    kill $SERVER_PID 2>/dev/null || true
}
trap cleanup EXIT

# Exécuter Claude Code en mode headless
result=$(claude -p \
    "Utilise l'outil calculate pour additionner 15 et 27, puis retourne le résultat exact" \
    --mcp-config ./test-mcp.json \
    --allowedTools "mcp__my-spring-mcp-server__calculate" \
    --output-format json \
    --max-turns 3 \
    --dangerously-skip-permissions)

# Valider le résultat
if echo "$result" | jq -e '.result | contains("42")' > /dev/null; then
    echo "✅ Test passé: résultat correct"
    exit 0
else
    echo "❌ Test échoué: résultat inattendu"
    echo "$result" | jq .
    exit 1
fi
```

### Intégration GitHub Actions

```yaml
name: MCP Server E2E Tests
on: [push, pull_request]

jobs:
  e2e-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Java 25
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '25'
      
      - name: Start MCP Server
        run: |
          ./mvnw spring-boot:run &
          sleep 15
      
      - name: Test avec Claude Code
        uses: anthropics/claude-code-action@v1
        with:
          anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}
          prompt: "Liste tous les outils MCP disponibles et teste l'outil calculate"
          mcp_config: |
            {
              "mcpServers": {
                "test-server": {
                  "type": "http",
                  "url": "http://localhost:8080"
                }
              }
            }
          allowed_tools: "mcp__test-server"
          max_turns: "5"
```

---

## Configuration Testcontainers pour environnement complet

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class McpServerE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("mcp_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("E2E: workflow complet avec persistance")
    void shouldExecuteCompleteWorkflow() {
        WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        
        WebFluxSseClientTransport transport = WebFluxSseClientTransport
            .builder(webClient)
            .build();
        
        McpSyncClient client = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(60))
            .build();
        
        try {
            client.initialize();
            
            // 1. Créer une entité via outil MCP
            CallToolResult createResult = client.callTool(
                new CallToolRequest("createUser", 
                    Map.of("name", "Test User", "email", "test@example.com"))
            );
            assertThat(createResult.isError()).isFalse();
            
            String userId = extractUserId(createResult);
            
            // 2. Récupérer l'entité
            CallToolResult getResult = client.callTool(
                new CallToolRequest("getUser", Map.of("id", userId))
            );
            assertThat(getResult.content())
                .extracting(c -> ((TextContent) c).text())
                .asString()
                .contains("Test User");
            
            // 3. Vérifier en base
            // ... assertions sur la base de données
            
        } finally {
            client.closeGracefully();
        }
    }
}
```

---

## Patterns de mocking vs tests réels

### Quand utiliser le mocking

```java
@WebFluxTest(controllers = McpController.class)
class McpControllerMockTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private ToolExecutor toolExecutor;

    @Test
    void shouldReturnMockedToolResult() {
        when(toolExecutor.execute(eq("calculate"), any()))
            .thenReturn(new ToolResult("42", false));

        // Test du controller avec service mocké
        webClient.post()
            .uri("/mcp/message?sessionId=test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(toolCallRequest("calculate", Map.of("a", 1, "b", 2)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.result.content[0].text").isEqualTo("42");
    }
}
```

### Stratégie recommandée par niveau

| Niveau | Approche | Outils | Couverture |
|--------|----------|--------|------------|
| **Unitaire** | Mock des services | Mockito, @MockBean | Logique des outils |
| **Intégration** | McpSyncClient réel | WebFluxSseClientTransport | Protocol MCP complet |
| **E2E léger** | MCP Inspector CLI | npx @modelcontextprotocol/inspector | Conformité protocol |
| **E2E complet** | Claude Code headless | claude -p --mcp-config | Workflow utilisateur |

---

## Classe utilitaire de test complète

```java
public class McpTestSupport {

    public static McpSyncClient createClient(int port) {
        WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        
        return McpClient.sync(
            WebFluxSseClientTransport.builder(webClient).build()
        )
        .requestTimeout(Duration.ofSeconds(30))
        .build();
    }
    
    public static void withInitializedClient(int port, Consumer<McpSyncClient> test) {
        McpSyncClient client = createClient(port);
        try {
            client.initialize();
            test.accept(client);
        } finally {
            client.closeGracefully();
        }
    }
    
    public static String toolCallRequest(String toolName, Map<String, Object> args) {
        return String.format("""
            {
                "jsonrpc": "2.0",
                "id": %d,
                "method": "tools/call",
                "params": {
                    "name": "%s",
                    "arguments": %s
                }
            }
            """, 
            System.currentTimeMillis(), 
            toolName,
            new ObjectMapper().writeValueAsString(args));
    }
    
    public static void assertToolSuccess(CallToolResult result) {
        assertThat(result.isError())
            .as("L'outil ne devrait pas retourner d'erreur")
            .isFalse();
        assertThat(result.content())
            .as("Le résultat devrait contenir du contenu")
            .isNotEmpty();
    }
    
    public static void assertValidJsonRpc(String response) {
        JsonNode node = new ObjectMapper().readTree(response);
        assertThat(node.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(node.has("id")).isTrue();
        assertThat(node.has("result") ^ node.has("error"))
            .as("Doit avoir result XOR error")
            .isTrue();
    }
}
```

---

## Conclusion

La stratégie de test E2E pour les serveurs MCP Spring AI 1.1.2 s'appuie sur quatre piliers complémentaires. **Les tests d'intégration avec `McpSyncClient`** constituent le socle, validant le protocole complet sans dépendance externe. **WebTestClient avec StepVerifier** permet de tester les flux SSE de manière fine et réactive. **MCP Inspector** offre une validation rapide de conformité en CLI. Enfin, **Claude Code en mode headless** permet des tests E2E réalistes simulant l'usage production.

L'absence de "TestMcpClient" dédié n'est pas un obstacle : les clients standard `McpSyncClient`/`McpAsyncClient` sont parfaitement adaptés aux tests. Le schéma JSON officiel MCP et les validateurs communautaires (Janix-ai, RHEcosystemAppEng) complètent l'arsenal pour garantir la conformité protocolaire.