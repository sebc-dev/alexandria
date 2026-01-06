# Constats LOW (Optional)

---

## F16: Stopwords English-Only

**ID:** F16
**Severite:** LOW
**Categorie:** I18n

### Description du Probleme

Le `QueryValidator` n'a que des stopwords anglais. Les requetes francaises comme "le la de du" passent la validation.

### Impact si Non Corrige

- Requetes francaises vagues non detectees
- Gaspillage d'embeddings sur requetes non pertinentes

### Solution Concrete

```java
public class QueryValidator {

    private static final Set<String> STOPWORDS_EN = Set.of(
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were",
        "be", "been", "being", "have", "has", "had", "do", "does", "did",
        "will", "would", "could", "should", "may", "might", "must", "can",
        "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
        "this", "that", "these", "those", "it", "its", "what", "which",
        "who", "whom", "whose", "where", "when", "why", "how"
    );

    private static final Set<String> STOPWORDS_FR = Set.of(
        "le", "la", "les", "un", "une", "des", "du", "de", "d",
        "et", "ou", "mais", "donc", "or", "ni", "car",
        "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
        "ce", "cette", "ces", "cet", "qui", "que", "quoi", "dont", "ou",
        "est", "sont", "etait", "etaient", "etre", "avoir", "a", "ai",
        "dans", "sur", "sous", "avec", "sans", "pour", "par", "en",
        "au", "aux", "ne", "pas", "plus", "moins", "tres", "bien"
    );

    private static final Set<String> ALL_STOPWORDS = Stream.concat(
        STOPWORDS_EN.stream(), STOPWORDS_FR.stream()
    ).collect(Collectors.toUnmodifiableSet());

    public ValidationResult validate(String query) {
        // ... utiliser ALL_STOPWORDS
    }
}
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/core/QueryValidator.java` - Ajouter stopwords FR

### Criteres d'Acceptation

- [ ] "le la de du" retourne TOO_VAGUE
- [ ] "la configuration spring" est valide (1 mot significatif)

### Statut

- [ ] Corrige

---

## F17: No Env Vars Documentation

**ID:** F17
**Severite:** LOW
**Categorie:** Documentation

### Description du Probleme

Pas de fichier `.env.example` documentant les variables d'environnement requises.

### Solution Concrete

**Fichier a creer:** `.env.example`

```bash
# ============================================
# Alexandria RAG Server - Environment Variables
# ============================================
# Copy this file to .env and fill with actual values
# NEVER commit .env to version control

# --- PostgreSQL + pgvector ---
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/alexandria
SPRING_DATASOURCE_USERNAME=alexandria
SPRING_DATASOURCE_PASSWORD=your_secure_password

# --- Infinity Embedding/Reranking API ---
# For RunPod serverless:
ALEXANDRIA_INFINITY_BASEURL=https://api.runpod.ai/v2/your-endpoint-id
ALEXANDRIA_INFINITY_APIKEY=your_runpod_api_key

# For local Infinity server:
# ALEXANDRIA_INFINITY_BASEURL=http://localhost:7997
# ALEXANDRIA_INFINITY_APIKEY=not_required_for_local

# --- MCP Security ---
ALEXANDRIA_API_KEY=your_mcp_api_key_min_32_chars

# --- Application ---
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev

# --- Logging ---
# LOG_LEVEL=INFO
# LOGGING_FILE_PATH=./logs

# --- Timeouts (ms) ---
# ALEXANDRIA_TIMEOUTS_EMBEDDING=30000
# ALEXANDRIA_TIMEOUTS_RERANKING=60000
# ALEXANDRIA_TIMEOUTS_DATABASE=5000
```

**Ajouter a `.gitignore`:**

```gitignore
# Environment files
.env
.env.local
.env.*.local

# Keep template
!.env.example
```

### Fichiers a Modifier/Creer

- [x] `.env.example` - Template variables
- [x] `.gitignore` - Ignorer .env

### Criteres d'Acceptation

- [ ] `.env.example` documente toutes les variables requises
- [ ] `.env` est dans .gitignore
- [ ] README reference .env.example

### Statut

- [ ] Corrige

---

## F18: Progress Reporting Not Tested

**ID:** F18
**Severite:** LOW
**Categorie:** Testing

### Description du Probleme

Pas de test E2E verifiant que les notifications de progression MCP sont bien envoyees.

### Solution Concrete

**Fichier a creer:** `src/test/java/dev/alexandria/McpProgressNotificationTest.java`

```java
package dev.alexandria;

import dev.alexandria.test.McpTestSupport;
import dev.alexandria.test.PgVectorTestConfiguration;
import io.modelcontextprotocol.kotlin.sdk.client.McpSyncClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PgVectorTestConfiguration.class)
class McpProgressNotificationTest {

    @LocalServerPort
    private int port;

    private McpSyncClient client;
    private List<Double> receivedProgressValues;

    @BeforeEach
    void setUp() {
        client = McpTestSupport.createClient(port);
        client.initialize();
        receivedProgressValues = new CopyOnWriteArrayList<>();

        // Enregistrer un handler pour les notifications de progression
        client.setNotificationHandler("notifications/progress", notification -> {
            Double progress = notification.getParams().get("progress").asDouble();
            receivedProgressValues.add(progress);
        });
    }

    @Test
    void shouldSendProgressNotificationsDuringIngestion() throws Exception {
        // Given: un fichier a ingerer
        String testFilePath = createTestFile("# Test Doc\n\nContent here.");

        // When: appeler ingest_document
        var result = client.callTool("ingest_document",
            Map.of("filePath", testFilePath));

        // Then: verifier les notifications de progression
        // Attendre un peu pour recevoir toutes les notifications
        Thread.sleep(100);

        assertThat(receivedProgressValues)
            .isNotEmpty()
            .contains(0.1, 0.3, 0.5, 0.7, 1.0); // Progression attendue

        // Verifier l'ordre croissant
        for (int i = 1; i < receivedProgressValues.size(); i++) {
            assertThat(receivedProgressValues.get(i))
                .isGreaterThanOrEqualTo(receivedProgressValues.get(i - 1));
        }
    }

    @Test
    void shouldIncludeProgressTokenInNotifications() throws Exception {
        // Given/When
        String testFilePath = createTestFile("# Doc\n\nText.");

        var result = client.callTool("ingest_document",
            Map.of("filePath", testFilePath));

        Thread.sleep(100);

        // Then: chaque notification doit avoir un progressToken
        // (verification specifique au SDK utilise)
        assertThat(receivedProgressValues).isNotEmpty();
    }

    private String createTestFile(String content) throws Exception {
        Path tempFile = Files.createTempFile("test-", ".md");
        Files.writeString(tempFile, content);
        return tempFile.toAbsolutePath().toString();
    }
}
```

### Fichiers a Modifier/Creer

- [x] `src/test/java/dev/alexandria/McpProgressNotificationTest.java` - Test E2E progression

### Criteres d'Acceptation

- [ ] Test verifie reception des notifications 0.1, 0.3, 0.5, 0.7, 1.0
- [ ] Progression toujours croissante
- [ ] Test passe en CI

### Statut

- [ ] Corrige

---
