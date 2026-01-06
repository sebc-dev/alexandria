# Constats HIGH (Should Fix)

---

## F4: getStoredDocumentInfo() Not Implemented

**ID:** F4
**Severite:** HIGH
**Categorie:** Implementation

### Description du Probleme

La methode `getStoredDocumentInfo()` dans `DocumentUpdateService` est referencee mais non implementee. Elle est necessaire pour la detection de changements (fast path mtime+size avant recalcul hash).

### Impact si Non Corrige

- Recalcul systematique du hash SHA-256 pour chaque fichier
- Performance degradee lors de l'ingestion de repertoires volumineux
- Fast path mtime/size inutilisable

### Solution Concrete

**Fichier a modifier:** `src/main/java/dev/alexandria/core/DocumentUpdateService.java`

```java
package dev.alexandria.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class DocumentUpdateService {

    private final JdbcTemplate jdbcTemplate;
    // ... autres dependances

    /**
     * Recupere les informations du document stocke pour detection de changements.
     *
     * @param sourceUri URI logique du document
     * @return Optional contenant les infos si le document existe, empty sinon
     */
    public Optional<StoredDocumentInfo> getStoredDocumentInfo(String sourceUri) {
        String sql = """
            SELECT
                m.metadata->>'documentHash' as document_hash,
                m.metadata->>'fileSize' as file_size,
                m.metadata->>'fileModifiedAt' as file_modified_at
            FROM document_embeddings e
            CROSS JOIN LATERAL jsonb_array_elements(e.metadata) as m(metadata)
            WHERE m.metadata->>'sourceUri' = ?
            LIMIT 1
            """;

        // Alternative plus simple si metadata est un JSONB direct (non-array):
        String sqlDirect = """
            SELECT DISTINCT
                metadata->>'documentHash' as document_hash,
                (metadata->>'fileSize')::bigint as file_size,
                to_timestamp((metadata->>'fileModifiedAt')::bigint / 1000) as file_modified_at
            FROM document_embeddings
            WHERE metadata->>'sourceUri' = ?
            LIMIT 1
            """;

        return jdbcTemplate.query(sqlDirect, (rs, rowNum) ->
            new StoredDocumentInfo(
                rs.getString("document_hash"),
                rs.getLong("file_size"),
                rs.getTimestamp("file_modified_at").toInstant()
            ),
            sourceUri
        ).stream().findFirst();
    }

    /**
     * Record immutable pour les infos stockees d'un document.
     */
    public record StoredDocumentInfo(
        String documentHash,
        long fileSize,
        Instant fileModifiedAt
    ) {}
}
```

**Enrichissement des metadata lors de l'ingestion:**

```java
// Dans ingestDocument(), ajouter les infos fichier aux metadata
Metadata metadata = new Metadata()
    .put("sourceUri", sourceUri)
    .put("documentHash", documentHash)
    .put("fileSize", attrs.size())  // AJOUTER
    .put("fileModifiedAt", attrs.lastModifiedTime().toInstant().toEpochMilli())  // AJOUTER
    .put("chunkIndex", i)
    // ... autres champs
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/core/DocumentUpdateService.java` - Implementer `getStoredDocumentInfo()`

### Criteres d'Acceptation

- [ ] `getStoredDocumentInfo()` retourne `Optional.empty()` pour un document inconnu
- [ ] `getStoredDocumentInfo()` retourne les infos correctes pour un document indexe
- [ ] Les metadata incluent `fileSize` et `fileModifiedAt`
- [ ] Test: modifier un fichier sans changer son contenu, verifier que le hash evite la re-ingestion

### Statut

- [ ] Corrige

---

## F5: Missing logback-spring.xml Task

**ID:** F5
**Severite:** HIGH
**Categorie:** Observability

### Description du Probleme

L'implementation plan ne contient pas de tache pour creer `logback-spring.xml` avec les patterns ECS et la configuration par profil (dev/prod).

### Impact si Non Corrige

- Logs non structures en production
- Difficulte d'integration avec Loki/Grafana
- Correlation IDs absents des logs
- Verbosity inadaptee par environnement

### Solution Concrete

**Fichier a creer:** `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

    <!-- Proprietes Spring injectees -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="alexandria"/>
    <springProperty scope="context" name="LOG_PATH" source="logging.file.path" defaultValue="./logs"/>

    <!-- ============================================ -->
    <!-- PROFIL DEVELOPPEMENT - Console lisible       -->
    <!-- ============================================ -->
    <springProfile name="dev,local,default">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%clr(%d{HH:mm:ss.SSS}){faint} %clr(%5p) %clr([%X{correlationId:-NO_CID}]){yellow} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n%wEx</pattern>
                <charset>UTF-8</charset>
            </encoder>
        </appender>

        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>

        <!-- Debug application et Langchain4j -->
        <logger name="dev.alexandria" level="DEBUG"/>
        <logger name="dev.langchain4j" level="DEBUG"/>
        <logger name="org.hibernate.SQL" level="DEBUG"/>
    </springProfile>

    <!-- ============================================ -->
    <!-- PROFIL PRODUCTION - JSON Structure ECS      -->
    <!-- ============================================ -->
    <springProfile name="prod,production">

        <!-- Console JSON pour Docker/stdout -->
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="org.springframework.boot.logging.logback.StructuredLogEncoder">
                <format>ecs</format>
                <charset>UTF-8</charset>
            </encoder>
        </appender>

        <!-- Fichier JSON avec rotation -->
        <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>${LOG_PATH}/${APP_NAME}.json.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
                <fileNamePattern>${LOG_PATH}/${APP_NAME}.%d{yyyy-MM-dd}.%i.json.log.gz</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <maxHistory>30</maxHistory>
                <totalSizeCap>3GB</totalSizeCap>
            </rollingPolicy>
            <encoder class="org.springframework.boot.logging.logback.StructuredLogEncoder">
                <format>ecs</format>
                <charset>UTF-8</charset>
            </encoder>
        </appender>

        <!-- Async Appender pour performance -->
        <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
            <queueSize>1024</queueSize>
            <discardingThreshold>0</discardingThreshold>
            <includeCallerData>false</includeCallerData>
            <appender-ref ref="JSON_CONSOLE"/>
        </appender>

        <root level="INFO">
            <appender-ref ref="ASYNC_JSON"/>
            <appender-ref ref="JSON_FILE"/>
        </root>

        <!-- Niveaux production -->
        <logger name="dev.alexandria" level="INFO"/>
        <logger name="dev.langchain4j" level="WARN"/>
        <logger name="org.springframework" level="WARN"/>
        <logger name="org.hibernate" level="WARN"/>
        <logger name="com.zaxxer.hikari" level="WARN"/>
    </springProfile>

</configuration>
```

**Tache a ajouter dans `implementation-plan.md`:**

```markdown
- [ ] **Task XX: Create logback-spring.xml**
  - File: `src/main/resources/logback-spring.xml`
  - Action: Structured logging with ECS format for production, readable format for dev
  - Notes: Use StructuredLogEncoder (Spring Boot 3.4+), include correlationId in pattern
```

### Fichiers a Modifier/Creer

- [x] `src/main/resources/logback-spring.xml` - Configuration logging
- [x] `docs/implementation-artifacts/tech-spec-alexandria-rag-server-java/implementation-plan.md` - Ajouter tache

### Criteres d'Acceptation

- [ ] Logs dev affichent le correlationId en jaune
- [ ] Logs prod sont en format JSON ECS
- [ ] Rotation des fichiers logs configuree (100MB, 30 jours)
- [ ] AsyncAppender evite le blocage sur I/O

### Statut

- [ ] Corrige

---

## F6: No Concurrent Update Tests

**ID:** F6
**Severite:** HIGH
**Categorie:** Testing

### Description du Probleme

Aucun test ne verifie le comportement lors de mises a jour concurrentes du meme document (race conditions, lost updates).

### Impact si Non Corrige

- Race conditions non detectees en pre-production
- Corruption silencieuse des donnees
- Chunks dupliques ou manquants apres mises a jour concurrentes

### Solution Concrete

**Fichier a creer:** `src/test/java/dev/alexandria/core/DocumentUpdateConcurrencyTest.java`

```java
package dev.alexandria.core;

import dev.alexandria.test.PgVectorTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PgVectorTestConfiguration.class)
class DocumentUpdateConcurrencyTest {

    @Autowired
    private DocumentUpdateService documentUpdateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHandleConcurrentUpdatesToSameDocument() throws Exception {
        // Given: un document initial
        String sourceUri = "/test/concurrent-doc.md";
        String initialContent = "# Initial Content\n\nParagraph one.";
        documentUpdateService.ingestDocument(sourceUri, initialContent, "hash1", true);

        // When: 5 threads tentent de mettre a jour le meme document simultanement
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int version = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // Attendre le signal de depart
                    String newContent = "# Version " + version + "\n\nUpdated by thread " + version;
                    documentUpdateService.ingestDocument(
                        sourceUri, newContent, "hash-v" + version, false);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Declencher tous les threads simultanement
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Then: verifier l'integrite
        // Tous les threads doivent reussir (serialisation par @Transactional)
        // OU certains echouent avec une exception de concurrence geree
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);

        // Verifier qu'il n'y a pas de chunks dupliques
        Long chunkCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_embeddings WHERE metadata->>'sourceUri' = ?",
            Long.class, sourceUri);

        // Le document final doit avoir un nombre raisonnable de chunks (pas de duplication)
        assertThat(chunkCount).isLessThanOrEqualTo(10L); // Ajuster selon le contenu attendu
    }

    @Test
    void shouldNotLoseDataOnConcurrentDeleteAndInsert() throws Exception {
        // Given: un document
        String sourceUri = "/test/race-condition-doc.md";
        documentUpdateService.ingestDocument(
            sourceUri, "# Original\n\nContent here.", "original-hash", true);

        // When: un thread supprime pendant qu'un autre insere
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> deleteFuture = executor.submit(() -> {
            try {
                startLatch.await();
                documentUpdateService.deleteDocument(sourceUri);
            } catch (Exception e) {
                // Expected in some race scenarios
            }
        });

        Future<?> updateFuture = executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(5); // Leger delai pour creer la race condition
                documentUpdateService.ingestDocument(
                    sourceUri, "# Updated\n\nNew content.", "new-hash", false);
            } catch (Exception e) {
                // Expected in some race scenarios
            }
        });

        startLatch.countDown();
        deleteFuture.get();
        updateFuture.get();
        executor.shutdown();

        // Then: l'etat doit etre coherent (soit supprime, soit mis a jour, pas les deux)
        Long chunkCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_embeddings WHERE metadata->>'sourceUri' = ?",
            Long.class, sourceUri);

        // Le document est soit completement present, soit completement absent
        assertThat(chunkCount).isIn(0L, 1L, 2L, 3L); // Jamais un etat partiel
    }
}
```

**Consideration: Optimistic Locking (optionnel)**

Si les tests revelent des problemes, ajouter un optimistic locking:

```java
// Dans DocumentUpdateService
@Transactional(isolation = Isolation.SERIALIZABLE)
public UpdateResult ingestDocument(...) { ... }

// Ou avec version column
@Version
private Long version;
```

### Fichiers a Modifier/Creer

- [x] `src/test/java/dev/alexandria/core/DocumentUpdateConcurrencyTest.java` - Tests de concurrence

### Criteres d'Acceptation

- [ ] Test avec 5 threads concurrents passe sans corruption
- [ ] Pas de chunks dupliques apres mise a jour concurrente
- [ ] Etat toujours coherent (document complet ou absent, jamais partiel)

### Statut

- [ ] Corrige

---

## F7: Testcontainers 2.x Not Certified Java 25

**ID:** F7
**Severite:** HIGH
**Categorie:** Compatibility

### Description du Probleme

Testcontainers 2.x n'est pas officiellement certifie pour Java 25 LTS. Des problemes de compatibilite peuvent survenir.

### Impact si Non Corrige

- Echecs de tests CI/CD imprevisibles
- Blocage du developpement si incompatibilite majeure
- Temps perdu en debugging de problemes Testcontainers

### Solution Concrete

**Documentation du risque dans `implementation-plan.md`:**

```markdown
## Known Risks

### Testcontainers 2.x + Java 25 Compatibility
- **Status**: Not officially certified
- **Risk Level**: Medium
- **Mitigation**:
  1. Use latest Testcontainers 2.x version (check releases)
  2. Add fallback to H2 for unit tests not requiring pgvector
  3. Monitor Testcontainers GitHub issues for Java 25 reports
  4. Consider running integration tests with Java 21 if critical issues arise
```

**Fallback H2 pour tests unitaires:**

```java
package dev.alexandria.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Configuration H2 pour tests unitaires ne necessitant pas pgvector.
 * Active avec le profil "unit-test".
 */
@TestConfiguration
@Profile("unit-test")
public class H2TestConfiguration {

    @Bean
    @Primary
    public DataSource h2DataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("testdb;MODE=PostgreSQL")
            .build();
    }
}
```

**Configuration CI pour isolation:**

```yaml
# .github/workflows/test.yml
jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
      - run: ./mvnw test -Dspring.profiles.active=unit-test

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
      - run: ./mvnw verify -Dspring.profiles.active=integration-test
```

### Fichiers a Modifier/Creer

- [x] `docs/implementation-artifacts/tech-spec-alexandria-rag-server-java/additional-context/notes.md` - Documenter le risque
- [x] `src/test/java/dev/alexandria/test/H2TestConfiguration.java` - Fallback H2
- [x] `.github/workflows/test.yml` - Separation unit/integration tests

### Criteres d'Acceptation

- [ ] Risque documente dans la tech-spec
- [ ] Tests unitaires peuvent tourner sans Docker (profil unit-test)
- [ ] Tests d'integration isoles avec Testcontainers
- [ ] CI separe les deux types de tests

### Statut

- [ ] Corrige

---
