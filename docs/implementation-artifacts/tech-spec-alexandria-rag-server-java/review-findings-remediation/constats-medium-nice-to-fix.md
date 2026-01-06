# Constats MEDIUM (Nice to Fix)

---

## F8: Missing CLI Error ACs

**ID:** F8
**Severite:** MEDIUM
**Categorie:** Acceptance

### Description du Probleme

Les criteres d'acceptation du CLI ne couvrent pas les cas d'erreur: encodage invalide, symlinks, disque plein.

### Impact si Non Corrige

- Comportement indefini sur erreurs d'I/O
- Messages d'erreur peu informatifs pour l'utilisateur
- Crashes non gracieux sur cas limites

### Solution Concrete

**Ajouter a `implementation-plan.md` dans la section Acceptance Criteria:**

```markdown
## AC CLI Error Handling

- [ ] Given a file with invalid UTF-8 encoding, when ingesting, then error message indicates "Invalid encoding in file: <path>. Expected UTF-8." and continues with other files
- [ ] Given a symlink to a file, when ingesting, then symlink is followed and file is processed normally
- [ ] Given a broken symlink, when ingesting, then warning "Broken symlink ignored: <path>" is logged and processing continues
- [ ] Given disk full during ingestion, when write fails, then error message indicates "Insufficient disk space" and transaction is rolled back
- [ ] Given a file larger than 10MB, when ingesting, then warning "Large file (<size>MB) may impact performance" is logged
- [ ] Given --dry-run flag, when ingesting, then no database writes occur and summary shows "Would ingest X files (Y chunks)"
```

**Implementation dans IngestCommand.java:**

```java
@Command(name = "ingest", description = "Ingest documents into the vector store")
public class IngestCommand implements Runnable {

    @Parameters(description = "Path to file or directory")
    private Path path;

    @Option(names = {"-r", "--recursive"}, description = "Recursively process directories")
    private boolean recursive;

    @Option(names = {"--dry-run"}, description = "Show what would be ingested without writing")
    private boolean dryRun;

    @Override
    public void run() {
        try {
            List<Path> files = collectFiles(path, recursive);

            for (Path file : files) {
                try {
                    processFile(file);
                } catch (MalformedInputException e) {
                    System.err.println("Invalid encoding in file: " + file + ". Expected UTF-8.");
                    // Continue with other files
                } catch (NoSuchFileException e) {
                    System.err.println("Broken symlink ignored: " + file);
                    // Continue with other files
                } catch (IOException e) {
                    if (e.getMessage().contains("No space left")) {
                        System.err.println("Insufficient disk space. Aborting.");
                        System.exit(1);
                    }
                    throw e;
                }
            }

            if (dryRun) {
                System.out.println("Would ingest " + files.size() + " files");
            }

        } catch (Exception e) {
            System.err.println("Ingestion failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private void processFile(Path file) throws IOException {
        // Verifier la taille
        long sizeMB = Files.size(file) / (1024 * 1024);
        if (sizeMB > 10) {
            System.err.println("Warning: Large file (" + sizeMB + "MB) may impact performance: " + file);
        }

        // Lire avec detection d'encodage
        String content = Files.readString(file, StandardCharsets.UTF_8);
        // ... rest of processing
    }
}
```

### Fichiers a Modifier/Creer

- [x] `docs/implementation-artifacts/tech-spec-alexandria-rag-server-java/implementation-plan.md` - Ajouter ACs
- [x] `src/main/java/dev/alexandria/cli/IngestCommand.java` - Gestion des erreurs

### Criteres d'Acceptation

- [ ] Fichier avec mauvais encodage: message clair et continuer
- [ ] Symlink casse: warning et continuer
- [ ] Disque plein: message et exit 1
- [ ] --dry-run ne modifie pas la base

### Statut

- [ ] Corrige

---

## F9: LlmsTxtParser Chunking Undefined

**ID:** F9
**Severite:** MEDIUM
**Categorie:** Design

### Description du Probleme

La strategie de chunking pour les fichiers `llms.txt` n'est pas definie. Doit-on creer un chunk par section? Par lien?

### Impact si Non Corrige

- Comportement imprevisible lors de l'ingestion llms.txt
- Granularite de recherche inadaptee
- Perte de contexte ou chunks trop gros

### Solution Concrete

**Strategie recommandee: Un chunk par section H2**

Chaque section H2 devient un chunk independant. Les liens sont stockes en metadata pour la navigation.

```java
package dev.alexandria.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Convertit un LlmsTxtDocument en TextSegments pour indexation.
 * Strategie: un segment par section H2.
 */
public class LlmsTxtChunker {

    /**
     * Cree des chunks a partir d'un document llms.txt parse.
     *
     * @param doc Document parse
     * @param sourceUri URI source du fichier
     * @return Liste de TextSegments prets pour embedding
     */
    public List<TextSegment> chunk(LlmsTxtDocument doc, String sourceUri) {
        List<TextSegment> segments = new ArrayList<>();

        // Chunk 1: Header (titre + description + info)
        StringBuilder headerContent = new StringBuilder();
        headerContent.append("# ").append(doc.title()).append("\n\n");
        if (doc.summary() != null) {
            headerContent.append("> ").append(doc.summary()).append("\n\n");
        }
        if (doc.info() != null) {
            headerContent.append(doc.info()).append("\n");
        }

        segments.add(TextSegment.from(
            headerContent.toString(),
            Metadata.from("sourceUri", sourceUri)
                .add("documentTitle", doc.title())
                .add("chunkType", "llms-txt-header")
                .add("chunkIndex", 0)
        ));

        // Chunks 2+: Une section par chunk
        int chunkIndex = 1;
        for (var entry : doc.sections().entrySet()) {
            String sectionName = entry.getKey();
            List<LlmsTxtLink> links = entry.getValue();

            // Construire le contenu de la section
            StringBuilder sectionContent = new StringBuilder();
            sectionContent.append("## ").append(sectionName).append("\n\n");

            for (LlmsTxtLink link : links) {
                sectionContent.append("- [").append(link.title()).append("](")
                    .append(link.url()).append(")");
                if (link.description() != null) {
                    sectionContent.append(": ").append(link.description());
                }
                sectionContent.append("\n");
            }

            // Extraire les URLs pour metadata (navigation future)
            String linkUrls = links.stream()
                .map(LlmsTxtLink::url)
                .collect(Collectors.joining("|"));

            segments.add(TextSegment.from(
                sectionContent.toString(),
                Metadata.from("sourceUri", sourceUri)
                    .add("documentTitle", doc.title())
                    .add("sectionName", sectionName)
                    .add("chunkType", "llms-txt-section")
                    .add("chunkIndex", chunkIndex)
                    .add("linkUrls", linkUrls)  // Pour navigation
                    .add("isOptional", sectionName.equalsIgnoreCase("Optional"))
            ));
            chunkIndex++;
        }

        return segments;
    }
}
```

**Documentation dans la tech-spec:**

```markdown
## LlmsTxt Chunking Strategy

The `llms.txt` format is chunked as follows:
1. **Header chunk**: Title + blockquote summary + info text
2. **Section chunks**: One chunk per H2 section, containing all links

Links are stored in metadata (`linkUrls` field, pipe-separated) for potential future navigation/crawling.

The `## Optional` section is tagged with `isOptional: true` metadata for priority filtering during retrieval.
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/core/LlmsTxtChunker.java` - Implementation chunking
- [x] `docs/implementation-artifacts/tech-spec-alexandria-rag-server-java/context-for-development/ingestion-strategy-from-research-11.md` - Documenter strategie

### Criteres d'Acceptation

- [ ] Un fichier llms.txt avec 3 sections genere 4 chunks (1 header + 3 sections)
- [ ] Les URLs sont stockees en metadata `linkUrls`
- [ ] La section Optional est taggee `isOptional: true`

### Statut

- [ ] Corrige

---

## F10: Token Estimation Naive

**ID:** F10
**Severite:** MEDIUM
**Categorie:** Accuracy

### Description du Probleme

L'estimation des tokens utilise `length / 4` au lieu du vrai comptage via un Tokenizer. Cela peut causer des chunks trop gros ou trop petits.

### Impact si Non Corrige

- Chunks depassant la limite reelle du modele
- Perte de precision dans le budget de tokens
- Chunks sous-optimaux pour la recherche

### Solution Concrete

**Utiliser le Tokenizer de Langchain4j:**

```java
package dev.alexandria.core;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.springframework.stereotype.Component;

@Component
public class AlexandriaMarkdownSplitter implements DocumentSplitter {

    private static final int MAX_CHUNK_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 75;

    private final Tokenizer tokenizer;

    public AlexandriaMarkdownSplitter() {
        // Tokenizer compatible avec la plupart des modeles embeddings modernes
        this.tokenizer = new OpenAiTokenizer("gpt-4o-mini");
    }

    /**
     * Compte les tokens reels au lieu d'estimer.
     */
    private int countTokens(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    /**
     * Verifie si un texte depasse la limite de tokens.
     */
    private boolean exceedsTokenLimit(String text, int limit) {
        return countTokens(text) > limit;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String content = document.text();
        List<TextSegment> segments = new ArrayList<>();

        // ... logique de split existante ...

        // Validation finale: chaque segment doit respecter la limite
        for (TextSegment segment : segments) {
            int tokens = countTokens(segment.text());
            if (tokens > MAX_CHUNK_TOKENS * 1.5) { // Tolerance 50%
                log.warn("Chunk exceeds token limit: {} tokens (max: {})",
                    tokens, MAX_CHUNK_TOKENS);
            }
        }

        return segments;
    }
}
```

**Configuration du Tokenizer dans LangchainConfig:**

```java
@Configuration
public class LangchainConfig {

    @Bean
    public Tokenizer tokenizer() {
        // OpenAiTokenizer est compatible avec BPE-based models
        // Alternative: utiliser un tokenizer specifique au modele d'embedding
        return new OpenAiTokenizer("gpt-4o-mini");
    }

    @Bean
    public AlexandriaMarkdownSplitter markdownSplitter(Tokenizer tokenizer) {
        return new AlexandriaMarkdownSplitter(tokenizer);
    }
}
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/core/AlexandriaMarkdownSplitter.java` - Utiliser Tokenizer
- [x] `src/main/java/dev/alexandria/config/LangchainConfig.java` - Bean Tokenizer

### Criteres d'Acceptation

- [ ] Aucun chunk ne depasse 750 tokens (MAX_CHUNK_TOKENS * 1.5)
- [ ] Warning log si un chunk depasse la limite
- [ ] Tests verifiant le comptage reel vs estimation

### Statut

- [ ] Corrige

---

## F11: No Graceful Shutdown

**ID:** F11
**Severite:** MEDIUM
**Categorie:** Reliability

### Description du Probleme

L'application n'a pas de hooks `@PreDestroy` pour terminer proprement les embeddings en cours ou les connexions actives.

### Impact si Non Corrige

- Embeddings partiellement indexes lors du shutdown
- Connexions orphelines dans le pool
- Perte de travail en cours lors de SIGTERM

### Solution Concrete

**Fichier a creer:** `src/main/java/dev/alexandria/lifecycle/GracefulShutdownHandler.java`

```java
package dev.alexandria.lifecycle;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GracefulShutdownHandler {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownHandler.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final AtomicInteger activeIngestions = new AtomicInteger(0);
    private volatile boolean shuttingDown = false;

    /**
     * Enregistre le debut d'une ingestion.
     * @throws IllegalStateException si shutdown en cours
     */
    public void startIngestion() {
        if (shuttingDown) {
            throw new IllegalStateException("Server is shutting down, cannot start new ingestion");
        }
        activeIngestions.incrementAndGet();
    }

    /**
     * Enregistre la fin d'une ingestion.
     */
    public void endIngestion() {
        activeIngestions.decrementAndGet();
    }

    /**
     * Wrapper pour executer une tache avec tracking automatique.
     */
    public <T> T withIngestionTracking(java.util.function.Supplier<T> task) {
        startIngestion();
        try {
            return task.get();
        } finally {
            endIngestion();
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Initiating graceful shutdown...");
        shuttingDown = true;

        // Attendre la fin des ingestions en cours
        int waitedSeconds = 0;
        while (activeIngestions.get() > 0 && waitedSeconds < SHUTDOWN_TIMEOUT_SECONDS) {
            log.info("Waiting for {} active ingestions to complete...", activeIngestions.get());
            try {
                Thread.sleep(1000);
                waitedSeconds++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (activeIngestions.get() > 0) {
            log.warn("Forcing shutdown with {} ingestions still active", activeIngestions.get());
        } else {
            log.info("All ingestions completed, proceeding with shutdown");
        }
    }
}
```

**Configuration Spring Boot pour graceful shutdown:**

```yaml
# application.yml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**Integration dans IngestionService:**

```java
@Service
public class IngestionService {

    private final GracefulShutdownHandler shutdownHandler;

    public void ingestSingle(Path file) {
        shutdownHandler.withIngestionTracking(() -> {
            // ... logique d'ingestion
            return null;
        });
    }
}
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/lifecycle/GracefulShutdownHandler.java` - Handler shutdown
- [x] `src/main/java/dev/alexandria/core/IngestionService.java` - Integration
- [x] `src/main/resources/application.yml` - Configuration graceful shutdown

### Criteres d'Acceptation

- [ ] SIGTERM attend la fin des ingestions en cours (max 30s)
- [ ] Nouvelles ingestions refusees pendant shutdown
- [ ] Logs indiquent le nombre d'ingestions en attente

### Statut

- [ ] Corrige

---

## F12: schema.sql SET Not Persistent

**ID:** F12
**Severite:** MEDIUM
**Categorie:** Database

### Description du Probleme

Les commandes `SET hnsw.ef_search=100` dans schema.sql ne sont pas persistantes - elles s'appliquent seulement a la session qui execute le script.

### Impact si Non Corrige

- Performances de recherche vectorielle sous-optimales
- Parametres HNSW non appliques aux requetes applicatives
- Comportement different entre environnements

### Solution Concrete

**Option 1: Configuration HikariCP (recommandee)**

```yaml
# application.yml
spring:
  datasource:
    hikari:
      connection-init-sql: |
        SET hnsw.ef_search = 100;
        SET hnsw.iterative_scan = relaxed_order;
```

**Option 2: @PostConstruct dans une config**

```java
package dev.alexandria.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PgVectorSessionConfig {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorSessionConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void configureHnswParameters() {
        // Ces SET seront executes par la connexion utilisee par JdbcTemplate
        // Note: ne persiste pas pour les autres connexions du pool
        jdbcTemplate.execute("SET hnsw.ef_search = 100");
        jdbcTemplate.execute("SET hnsw.iterative_scan = relaxed_order");
    }
}
```

**Option 3: Configuration PostgreSQL (niveau serveur)**

```sql
-- Dans postgresql.conf ou via ALTER SYSTEM
ALTER SYSTEM SET hnsw.ef_search = 100;
SELECT pg_reload_conf();
```

**Recommandation finale: Option 1 (connection-init-sql)**

C'est la seule approche qui garantit que CHAQUE connexion du pool a les bons parametres.

### Fichiers a Modifier/Creer

- [x] `src/main/resources/application.yml` - Ajouter `connection-init-sql`

### Criteres d'Acceptation

- [ ] Toutes les connexions du pool ont `hnsw.ef_search = 100`
- [ ] Verification: `SHOW hnsw.ef_search` retourne 100 depuis l'application
- [ ] Test d'integration verifiant le parametre

### Statut

- [ ] Corrige

---

## F13: Cold Start Detection Missing

**ID:** F13
**Severite:** MEDIUM
**Categorie:** Resilience

### Description du Probleme

Pas de detection du cold start RunPod pour adapter les timeouts. Le premier appel apres inactivite peut echouer sur timeout.

### Impact si Non Corrige

- Echecs sporadiques apres periode d'inactivite
- Timeouts alors que le service se reveille
- Experience utilisateur degradee le matin

### Solution Concrete

**Fichier a creer:** `src/main/java/dev/alexandria/adapters/InfinityColdStartTracker.java`

```java
package dev.alexandria.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detecte si le service Infinity est en cold start.
 * Utilise le timestamp du dernier appel reussi pour determiner
 * si un timeout etendu est necessaire.
 */
@Component
public class InfinityColdStartTracker {

    private static final Logger log = LoggerFactory.getLogger(InfinityColdStartTracker.class);

    // Seuil d'inactivite apres lequel on considere un cold start probable
    private static final Duration COLD_START_THRESHOLD = Duration.ofMinutes(5);

    // Timeout normal vs cold start
    private static final Duration NORMAL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration COLD_START_TIMEOUT = Duration.ofSeconds(120);

    private final AtomicReference<Instant> lastSuccessfulCall = new AtomicReference<>(null);

    /**
     * Determine si le prochain appel est probablement un cold start.
     */
    public boolean isColdStartLikely() {
        Instant lastCall = lastSuccessfulCall.get();
        if (lastCall == null) {
            return true; // Premier appel depuis le demarrage
        }
        return Duration.between(lastCall, Instant.now()).compareTo(COLD_START_THRESHOLD) > 0;
    }

    /**
     * Retourne le timeout adapte a l'etat actuel.
     */
    public Duration getAdaptiveTimeout() {
        if (isColdStartLikely()) {
            log.debug("Cold start likely, using extended timeout: {}s", COLD_START_TIMEOUT.toSeconds());
            return COLD_START_TIMEOUT;
        }
        return NORMAL_TIMEOUT;
    }

    /**
     * Enregistre un appel reussi.
     */
    public void recordSuccess() {
        Instant previous = lastSuccessfulCall.getAndSet(Instant.now());
        if (previous == null || Duration.between(previous, Instant.now()).compareTo(COLD_START_THRESHOLD) > 0) {
            log.info("Service warmed up after cold start");
        }
    }

    /**
     * Retourne le temps depuis le dernier appel reussi.
     */
    public Duration getTimeSinceLastCall() {
        Instant lastCall = lastSuccessfulCall.get();
        if (lastCall == null) {
            return Duration.ofDays(365); // "Jamais appele"
        }
        return Duration.between(lastCall, Instant.now());
    }
}
```

**Integration dans le client HTTP:**

```java
@Service
public class InfinityRerankClient {

    private final InfinityColdStartTracker coldStartTracker;
    private final RestClient.Builder restClientBuilder;

    public RerankResponse rerank(String query, List<String> documents, int topN) {
        Duration timeout = coldStartTracker.getAdaptiveTimeout();

        RestClient client = restClientBuilder
            .requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
            ))
            .build();

        try {
            RerankResponse response = client.post()
                .uri("/rerank")
                .body(new RerankRequest(...))
                .retrieve()
                .body(RerankResponse.class);

            coldStartTracker.recordSuccess();
            return response;

        } catch (Exception e) {
            // Ne pas enregistrer le succes
            throw e;
        }
    }
}
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/adapters/InfinityColdStartTracker.java` - Tracker cold start
- [x] `src/main/java/dev/alexandria/adapters/InfinityRerankClient.java` - Integration

### Criteres d'Acceptation

- [ ] Premier appel utilise timeout 120s
- [ ] Appels suivants (< 5min) utilisent timeout 30s
- [ ] Apres 5min d'inactivite, retour au timeout 120s
- [ ] Log "Service warmed up" apres cold start reussi

### Statut

- [ ] Corrige

---

## F14: HTML Ingestion Not Tested

**ID:** F14
**Severite:** MEDIUM
**Categorie:** Testing

### Description du Probleme

Le parseur HTML (JsoupDocumentParser) n'a pas de tests unitaires dedies.

### Impact si Non Corrige

- Regressions non detectees sur parsing HTML
- Comportement inconnu sur HTML malformed
- Extraction de texte potentiellement incorrecte

### Solution Concrete

**Fichier a creer:** `src/test/java/dev/alexandria/core/JsoupDocumentParserTest.java`

```java
package dev.alexandria.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.jsoup.JsoupDocumentParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class JsoupDocumentParserTest {

    private final JsoupDocumentParser parser = new JsoupDocumentParser();

    @Test
    void shouldExtractTextFromSimpleHtml() {
        String html = """
            <html>
                <head><title>Test Page</title></head>
                <body>
                    <h1>Main Title</h1>
                    <p>This is a paragraph.</p>
                </body>
            </html>
            """;

        Document doc = parse(html);

        assertThat(doc.text())
            .contains("Main Title")
            .contains("This is a paragraph.");
    }

    @Test
    void shouldIgnoreScriptAndStyleTags() {
        String html = """
            <html>
                <head>
                    <style>.hidden { display: none; }</style>
                    <script>alert('evil');</script>
                </head>
                <body>
                    <p>Visible content</p>
                    <script>console.log('ignored');</script>
                </body>
            </html>
            """;

        Document doc = parse(html);

        assertThat(doc.text())
            .contains("Visible content")
            .doesNotContain("alert")
            .doesNotContain("console.log")
            .doesNotContain("display: none");
    }

    @Test
    void shouldHandleNestedStructures() {
        String html = """
            <div>
                <div>
                    <ul>
                        <li>Item 1</li>
                        <li>Item 2</li>
                    </ul>
                </div>
            </div>
            """;

        Document doc = parse(html);

        assertThat(doc.text())
            .contains("Item 1")
            .contains("Item 2");
    }

    @Test
    void shouldExtractLinks() {
        String html = """
            <p>Check out <a href="https://example.com">this link</a> for more info.</p>
            """;

        Document doc = parse(html);

        assertThat(doc.text()).contains("this link");
        // Note: Jsoup extrait le texte du lien mais pas l'URL par defaut
    }

    @Test
    void shouldHandleMalformedHtml() {
        String malformedHtml = """
            <html>
                <p>Unclosed paragraph
                <div>Unclosed div
                <b>Bold <i>and italic</b></i>
            """;

        assertThatNoException().isThrownBy(() -> parse(malformedHtml));

        Document doc = parse(malformedHtml);
        assertThat(doc.text()).contains("Unclosed paragraph");
    }

    @Test
    void shouldHandleEmptyHtml() {
        Document doc = parse("");
        assertThat(doc.text()).isEmpty();
    }

    @Test
    void shouldHandleHtmlEntities() {
        String html = "<p>&lt;code&gt; &amp; &quot;quotes&quot;</p>";

        Document doc = parse(html);

        assertThat(doc.text()).contains("<code> & \"quotes\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UTF-8",
        "ISO-8859-1",
        "UTF-16"
    })
    void shouldHandleDifferentEncodings(String encoding) throws Exception {
        String html = "<p>Hello World</p>";
        byte[] bytes = html.getBytes(encoding);

        // Jsoup devrait detecter l'encodage
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            Document doc = parser.parse(is);
            assertThat(doc.text()).contains("Hello World");
        }
    }

    private Document parse(String html) {
        try (InputStream is = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))) {
            return parser.parse(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Fichiers a Modifier/Creer

- [x] `src/test/java/dev/alexandria/core/JsoupDocumentParserTest.java` - Tests HTML parsing

### Criteres d'Acceptation

- [ ] Tests pour HTML simple, nested, malformed
- [ ] Verification que script/style sont ignores
- [ ] Test des entites HTML decodees
- [ ] Test des differents encodages

### Statut

- [ ] Corrige

---

## F15: No RAG Latency Metrics

**ID:** F15
**Severite:** MEDIUM
**Categorie:** Observability

### Description du Probleme

Pas de metriques Micrometer pour mesurer la latence de chaque etape du pipeline RAG.

### Impact si Non Corrige

- Impossible d'identifier les goulots d'etranglement
- Pas de dashboards Grafana
- Debugging performance difficile

### Solution Concrete

**Fichier a creer:** `src/main/java/dev/alexandria/metrics/RagMetrics.java`

```java
package dev.alexandria.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * Metriques Micrometer pour le pipeline RAG.
 */
@Component
public class RagMetrics {

    private final Timer embeddingTimer;
    private final Timer vectorSearchTimer;
    private final Timer rerankingTimer;
    private final Timer totalSearchTimer;
    private final Timer ingestionTimer;

    private final Counter searchCounter;
    private final Counter searchErrorCounter;
    private final Counter ingestionCounter;
    private final Counter chunksCreatedCounter;

    public RagMetrics(MeterRegistry registry) {
        // Timers pour chaque etape
        this.embeddingTimer = Timer.builder("rag.embedding.duration")
            .description("Time spent generating embeddings")
            .tag("operation", "query")
            .register(registry);

        this.vectorSearchTimer = Timer.builder("rag.vector_search.duration")
            .description("Time spent in pgvector similarity search")
            .register(registry);

        this.rerankingTimer = Timer.builder("rag.reranking.duration")
            .description("Time spent in reranking")
            .register(registry);

        this.totalSearchTimer = Timer.builder("rag.search.duration")
            .description("Total search pipeline duration")
            .register(registry);

        this.ingestionTimer = Timer.builder("rag.ingestion.duration")
            .description("Time spent ingesting a document")
            .register(registry);

        // Counters
        this.searchCounter = Counter.builder("rag.search.total")
            .description("Total number of search requests")
            .register(registry);

        this.searchErrorCounter = Counter.builder("rag.search.errors")
            .description("Number of failed search requests")
            .register(registry);

        this.ingestionCounter = Counter.builder("rag.ingestion.total")
            .description("Total number of documents ingested")
            .register(registry);

        this.chunksCreatedCounter = Counter.builder("rag.chunks.created")
            .description("Total number of chunks created")
            .register(registry);
    }

    public <T> T timeEmbedding(Callable<T> callable) throws Exception {
        return embeddingTimer.recordCallable(callable);
    }

    public <T> T timeVectorSearch(Callable<T> callable) throws Exception {
        return vectorSearchTimer.recordCallable(callable);
    }

    public <T> T timeReranking(Callable<T> callable) throws Exception {
        return rerankingTimer.recordCallable(callable);
    }

    public <T> T timeTotalSearch(Callable<T> callable) throws Exception {
        searchCounter.increment();
        try {
            return totalSearchTimer.recordCallable(callable);
        } catch (Exception e) {
            searchErrorCounter.increment();
            throw e;
        }
    }

    public void recordIngestion(Runnable runnable) {
        ingestionCounter.increment();
        ingestionTimer.record(runnable);
    }

    public void recordChunksCreated(int count) {
        chunksCreatedCounter.increment(count);
    }
}
```

**Integration dans RetrievalService:**

```java
@Service
public class RetrievalService {

    private final RagMetrics metrics;

    public McpSearchResponse search(String query) {
        return metrics.timeTotalSearch(() -> {
            // 1. Embedding
            float[] embedding = metrics.timeEmbedding(() ->
                embeddingModel.embed(query).content().vector()
            );

            // 2. Vector search
            var results = metrics.timeVectorSearch(() ->
                embeddingStore.findRelevant(embedding, topK)
            );

            // 3. Reranking
            var reranked = metrics.timeReranking(() ->
                rerankClient.rerank(query, extractTexts(results), topN)
            );

            return buildResponse(reranked);
        });
    }
}
```

**Configuration Actuator:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: alexandria
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/metrics/RagMetrics.java` - Composant metriques
- [x] `src/main/java/dev/alexandria/core/RetrievalService.java` - Integration
- [x] `src/main/resources/application.yml` - Exposition /actuator/prometheus

### Criteres d'Acceptation

- [ ] `/actuator/metrics/rag.search.duration` disponible
- [ ] Metriques separees pour embedding, search, reranking
- [ ] Counter des erreurs de recherche
- [ ] Compatible Prometheus scraping

### Statut

- [ ] Corrige

---
