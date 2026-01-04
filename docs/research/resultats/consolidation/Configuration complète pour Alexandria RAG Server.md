# Configuration complète pour Alexandria RAG Server

**Spring Boot 3.5.9 + Spring AI MCP 1.1.2 + Langchain4j 1.10.0** : cette configuration exhaustive couvre tous les aspects d'un serveur RAG Java moderne avec embeddings BGE-M3 sur RunPod et stockage pgvector. L'architecture utilise les Virtual Threads de Java 25, le transport SSE pour MCP, et une stratégie de profils adaptée au développement local comme à la production.

## Points critiques découverts

La recherche révèle **trois contraintes majeures** à prendre en compte. Premièrement, **Langchain4j pgvector n'a pas de Spring Boot starter** — la configuration requiert un `@Bean` programmatique. Deuxièmement, **HikariCP + Virtual Threads cause du pinning** — Java 24+ résout ce problème via JEP 491. Troisièmement, **le reranking Infinity utilise le format Cohere**, non compatible OpenAI, nécessitant un client HTTP séparé.

---

## Fichier application.yml complet et commenté

```yaml
# ============================================================================
# ALEXANDRIA RAG SERVER - Configuration principale
# Runtime: Java 25 LTS | Spring Boot 3.5.9 | Spring AI MCP 1.1.2
# ============================================================================

# ----------------------------------------------------------------------------
# 1. SERVER CONFIGURATION
# ----------------------------------------------------------------------------
server:
  # Port d'écoute - externalisé pour flexibilité Docker/K8s
  port: ${SERVER_PORT:8080}
  
  # Context path pour l'API REST (optionnel, utile pour reverse proxy)
  servlet:
    context-path: ${SERVER_CONTEXT_PATH:/}
  
  # Timeouts serveur
  tomcat:
    connection-timeout: 30s
    keep-alive-timeout: 60s

# ----------------------------------------------------------------------------
# 2. SPRING CORE CONFIGURATION
# ----------------------------------------------------------------------------
spring:
  application:
    name: alexandria-rag-server
  
  # Profil actif - défaut dev pour développement local
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
  # Chargement du fichier .env (optionnel, format properties)
  config:
    import: optional:file:.env[.properties]
  
  # VIRTUAL THREADS - Activation pour Java 21+
  # Améliore drastiquement la scalabilité I/O-bound (embeddings, DB)
  threads:
    virtual:
      enabled: true

  # ----------------------------------------------------------------------------
  # 3. DATASOURCE POSTGRESQL + PGVECTOR
  # ----------------------------------------------------------------------------
  datasource:
    # URL JDBC avec options pgvector optimisées
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:alexandria}?reWriteBatchedInserts=true&prepareThreshold=0
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    
    # HikariCP - Optimisé pour 24GB RAM + Virtual Threads
    hikari:
      # Pool sizing: avec Virtual Threads, augmenter significativement
      # Formula classique: (core_count * 2) + spindle_count = ~10
      # Avec Virtual Threads: 50-100 pour mono-utilisateur intensif
      maximum-pool-size: ${HIKARI_MAX_POOL:50}
      minimum-idle: ${HIKARI_MIN_IDLE:10}
      
      # Timeouts de connexion
      connection-timeout: 30000        # 30s max pour obtenir une connexion
      idle-timeout: 600000             # 10min avant fermeture connexion idle
      max-lifetime: 1800000            # 30min durée de vie max
      validation-timeout: 5000         # 5s pour validation
      
      # Validation query PostgreSQL
      connection-test-query: SELECT 1
      
      # Détection de fuites (debug)
      leak-detection-threshold: ${HIKARI_LEAK_DETECTION:60000}
      
      # Nom du pool pour monitoring
      pool-name: AlexandriaHikariPool
      
      # JMX pour métriques
      register-mbeans: true
      
      # Optimisations PostgreSQL
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true

  # ----------------------------------------------------------------------------
  # 4. SPRING AI MCP SERVER CONFIGURATION
  # Transport: SSE (Server-Sent Events) pour communication bidirectionnelle
  # ----------------------------------------------------------------------------
  ai:
    mcp:
      server:
        # Activation du serveur MCP
        enabled: true
        
        # Identification du serveur
        name: alexandria-rag-server
        version: ${project.version:1.0.0}
        
        # Type de serveur: SYNC (recommandé avec Virtual Threads)
        # ASYNC pour WebFlux/reactive
        type: SYNC
        
        # Instructions pour les clients MCP (description des capacités)
        instructions: |
          Alexandria RAG Server - Serveur de recherche sémantique documentaire.
          Capacités: recherche vectorielle, reranking intelligent, gestion de collections.
          Utilise BGE-M3 (1024D) pour embeddings et BGE-reranker-v2-m3 pour reranking.
        
        # Timeout des requêtes MCP
        request-timeout: ${MCP_REQUEST_TIMEOUT:60s}
        
        # Capacités exposées
        capabilities:
          tool: true                   # Expose les @McpTool
          resource: true               # Expose les @McpResource
          prompt: true                 # Expose les @McpPrompt
          completion: true             # Auto-complétion
        
        # Notifications de changement (temps réel)
        resource-change-notification: true
        prompt-change-notification: true
        tool-change-notification: true
        
        # ENDPOINTS SSE
        # Client se connecte sur /sse, envoie messages sur /mcp/message
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        
        # Préfixe optionnel pour tous les endpoints MCP
        # base-url: /api/v1  # Décommentez pour /api/v1/sse, /api/v1/mcp/message
        
        # Keep-alive pour maintenir connexions SSE actives
        keep-alive-interval: ${MCP_KEEPALIVE:30s}
        
        # MIME types personnalisés par outil (optionnel)
        # tool-response-mime-type:
        #   searchDocuments: application/json
        #   getDocument: text/markdown
        
        # Scanner d'annotations @McpTool/@McpResource/@McpPrompt
        annotation-scanner:
          enabled: true
        
        # Conversion automatique des ToolCallbacks Spring AI en MCP Tools
        tool-callback-converter: true

# ----------------------------------------------------------------------------
# 5. LANGCHAIN4J CONFIGURATION
# Embedding via OpenAI-compatible API (Infinity sur RunPod)
# ----------------------------------------------------------------------------
langchain4j:
  open-ai:
    embedding-model:
      # API Key - peut être "not-required" si Infinity n'exige pas d'auth
      api-key: ${INFINITY_API_KEY:not-required}
      
      # Base URL - Infinity endpoint (OpenAI-compatible)
      # Local: http://localhost:7997/v1
      # RunPod: https://api.runpod.ai/v2/{ENDPOINT_ID}/openai/v1
      base-url: ${INFINITY_EMBEDDING_URL:http://localhost:7997/v1}
      
      # Modèle BGE-M3 - utiliser le chemin HuggingFace complet
      model-name: ${EMBEDDING_MODEL:BAAI/bge-m3}
      
      # Dimensions du vecteur (BGE-M3 = 1024, fixe)
      dimensions: ${EMBEDDING_DIMENSIONS:1024}
      
      # Timeout - augmenté pour cold start RunPod
      timeout: ${INFINITY_TIMEOUT:PT120S}
      
      # Retry intégré Langchain4j
      max-retries: ${EMBEDDING_MAX_RETRIES:3}
      
      # Logging pour debug
      log-requests: ${LANGCHAIN4J_LOG_REQUESTS:false}
      log-responses: ${LANGCHAIN4J_LOG_RESPONSES:false}

# ----------------------------------------------------------------------------
# 6. CONFIGURATION CUSTOM ALEXANDRIA
# Paramètres RAG Pipeline + Infinity/RunPod
# ----------------------------------------------------------------------------
alexandria:
  # INFINITY SERVER - Endpoints séparés embeddings/reranking
  infinity:
    # Embeddings (OpenAI-compatible /v1/embeddings)
    embedding:
      base-url: ${INFINITY_EMBEDDING_URL:http://localhost:7997}
      model: ${EMBEDDING_MODEL:BAAI/bge-m3}
      dimensions: 1024
      # Batch size pour traitement en lot
      batch-size: ${EMBEDDING_BATCH_SIZE:32}
    
    # Reranking (Cohere-compatible /rerank - NON OpenAI!)
    rerank:
      base-url: ${INFINITY_RERANK_URL:http://localhost:7997}
      # Endpoint: /rerank (format Cohere)
      endpoint: /rerank
      model: ${RERANK_MODEL:BAAI/bge-reranker-v2-m3}
      # Max tokens (query + doc combinés, recommandé 1024)
      max-length: ${RERANK_MAX_LENGTH:1024}
    
    # Timeouts réseau (critiques pour RunPod serverless)
    timeouts:
      # Connect: 60s pour cold start (chargement modèle)
      connect: ${INFINITY_CONNECT_TIMEOUT:60s}
      # Read: 300s pour inference GPU + queue
      read: ${INFINITY_READ_TIMEOUT:300s}
  
  # RUNPOD SERVERLESS (si utilisé au lieu de local Infinity)
  runpod:
    enabled: ${RUNPOD_ENABLED:false}
    api-key: ${RUNPOD_API_KEY:}
    endpoint-id: ${RUNPOD_ENDPOINT_ID:}
    # Base URL template: https://api.runpod.ai/v2/{endpoint-id}
    # Pour OpenAI-compat: ajouter /openai/v1
  
  # RAG PIPELINE PARAMETERS
  rag:
    retrieval:
      # Top-K: nombre de candidats récupérés (avant reranking)
      top-k: ${RAG_TOP_K:100}
      
      # Score minimum de similarité cosinus (0.0 à 1.0)
      min-score: ${RAG_MIN_SCORE:0.3}
      
      # HNSW ef_search: trade-off recall/vitesse (50-500)
      # Plus élevé = meilleur recall, plus lent
      hnsw-ef-search: ${RAG_HNSW_EF_SEARCH:100}
    
    reranking:
      # Activer/désactiver reranking
      enabled: ${RAG_RERANK_ENABLED:true}
      
      # Top-N: résultats finaux après reranking
      top-n: ${RAG_TOP_N:10}
      
      # Score minimum après reranking (0.0 à 1.0 après sigmoid)
      min-score: ${RAG_RERANK_MIN_SCORE:0.5}
  
  # PGVECTOR EMBEDDING STORE
  # NOTE: Pas de Spring Boot starter officiel - configuration via @Bean
  pgvector:
    table: ${PGVECTOR_TABLE:document_embeddings}
    dimension: 1024                    # BGE-M3 fixe
    use-index: true
    # IVFFlat index configuration (Langchain4j default)
    index-list-size: ${PGVECTOR_INDEX_LISTS:100}
    create-table: ${PGVECTOR_CREATE_TABLE:true}
    drop-table-first: false            # DANGER: ne jamais activer en prod
    # Metadata storage: COMBINED_JSONB recommandé pour flexibilité
    metadata-storage: COMBINED_JSONB

# ----------------------------------------------------------------------------
# 7. SPRING RETRY CONFIGURATION
# NOTE: Pas de properties auto-config - utiliser @EnableRetry + @Retryable
# Ces properties custom sont référencées via SpEL dans @Retryable
# ----------------------------------------------------------------------------
retry:
  # Configuration pour appels Infinity/RunPod
  infinity:
    max-attempts: ${RETRY_MAX_ATTEMPTS:4}
    initial-delay: ${RETRY_INITIAL_DELAY:1000}
    max-delay: ${RETRY_MAX_DELAY:30000}
    multiplier: ${RETRY_MULTIPLIER:2.0}
  
  # Configuration pour appels base de données
  database:
    max-attempts: 3
    initial-delay: 500
    max-delay: 5000
    multiplier: 1.5

# ----------------------------------------------------------------------------
# 8. LOGGING CONFIGURATION
# ----------------------------------------------------------------------------
logging:
  level:
    # Root level
    root: ${LOG_LEVEL_ROOT:INFO}
    
    # Application
    com.alexandria: ${LOG_LEVEL_APP:DEBUG}
    
    # Spring AI MCP
    org.springframework.ai.mcp: ${LOG_LEVEL_MCP:INFO}
    
    # Langchain4j
    dev.langchain4j: ${LOG_LEVEL_LANGCHAIN4J:INFO}
    
    # Database
    org.postgresql: WARN
    com.zaxxer.hikari: ${LOG_LEVEL_HIKARI:WARN}
    com.zaxxer.hikari.HikariConfig: INFO
    
    # Spring Framework
    org.springframework.web: INFO
    org.springframework.retry: ${LOG_LEVEL_RETRY:DEBUG}
    
    # HTTP Client (pour debug appels Infinity)
    org.apache.http: ${LOG_LEVEL_HTTP:WARN}
    
  # Pattern console avec Virtual Thread ID
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [VT:%X{virtualThreadId}] %-5level %logger{36} - %msg%n"

# ----------------------------------------------------------------------------
# 9. ACTUATOR / MONITORING
# ----------------------------------------------------------------------------
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
    env:
      # Sanitisation des secrets dans /actuator/env
      keys-to-sanitize: 
        - password
        - secret
        - key
        - token
        - .*credentials.*
        - .*api[-_]?key.*
        - .*runpod.*
  metrics:
    tags:
      application: ${spring.application.name}
```

---

## Profil développement (application-dev.yml)

```yaml
# ============================================================================
# ALEXANDRIA - PROFIL DÉVELOPPEMENT LOCAL
# ============================================================================

spring:
  # Désactiver Virtual Threads si problèmes de debug
  threads:
    virtual:
      enabled: ${DEV_VIRTUAL_THREADS:true}
  
  datasource:
    # PostgreSQL local avec pgvector
    url: jdbc:postgresql://localhost:5432/alexandria_dev
    username: postgres
    password: postgres
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      leak-detection-threshold: 30000

# Langchain4j - Infinity local
langchain4j:
  open-ai:
    embedding-model:
      base-url: http://localhost:7997/v1
      api-key: not-required
      log-requests: true
      log-responses: true
      timeout: PT60S

# Configuration Alexandria dev
alexandria:
  infinity:
    embedding:
      base-url: http://localhost:7997
    rerank:
      base-url: http://localhost:7997
    timeouts:
      connect: 30s
      read: 60s
  
  runpod:
    enabled: false
  
  rag:
    retrieval:
      top-k: 50
      hnsw-ef-search: 50
    reranking:
      enabled: true
      top-n: 5
  
  pgvector:
    create-table: true
    drop-table-first: false

# Logging verbose pour développement
logging:
  level:
    root: INFO
    com.alexandria: DEBUG
    org.springframework.ai.mcp: DEBUG
    dev.langchain4j: DEBUG
    org.springframework.retry: DEBUG
    # SQL queries
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

# DevTools
spring:
  devtools:
    restart:
      enabled: true
    livereload:
      enabled: true
```

---

## Profil production (application-prod.yml)

```yaml
# ============================================================================
# ALEXANDRIA - PROFIL PRODUCTION
# ============================================================================

server:
  # Compression GZIP
  compression:
    enabled: true
    mime-types: application/json,text/plain,application/xml

spring:
  threads:
    virtual:
      enabled: true
  
  datasource:
    # Toutes les valeurs depuis variables d'environnement
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?reWriteBatchedInserts=true&ssl=true&sslmode=require
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL:100}
      minimum-idle: ${HIKARI_MIN_IDLE:20}
      leak-detection-threshold: 0  # Désactivé en prod

# Langchain4j - RunPod production
langchain4j:
  open-ai:
    embedding-model:
      base-url: https://api.runpod.ai/v2/${RUNPOD_ENDPOINT_ID}/openai/v1
      api-key: ${RUNPOD_API_KEY}
      log-requests: false
      log-responses: false
      timeout: PT180S
      max-retries: 5

# Configuration Alexandria prod
alexandria:
  infinity:
    embedding:
      base-url: https://api.runpod.ai/v2/${RUNPOD_ENDPOINT_ID}
      batch-size: 16
    rerank:
      base-url: https://api.runpod.ai/v2/${RUNPOD_ENDPOINT_ID}
    timeouts:
      connect: 60s    # Cold start RunPod
      read: 300s      # Inference GPU
  
  runpod:
    enabled: true
    api-key: ${RUNPOD_API_KEY}
    endpoint-id: ${RUNPOD_ENDPOINT_ID}
  
  rag:
    retrieval:
      top-k: 100
      min-score: 0.35
      hnsw-ef-search: 150
    reranking:
      enabled: true
      top-n: 10
      min-score: 0.5
  
  pgvector:
    create-table: false  # Tables pré-créées en prod
    drop-table-first: false

# Retry plus agressif pour RunPod
retry:
  infinity:
    max-attempts: 5
    initial-delay: 2000
    max-delay: 60000
    multiplier: 2.5

# Logging minimal
logging:
  level:
    root: WARN
    com.alexandria: INFO
    org.springframework.ai.mcp: WARN
    dev.langchain4j: WARN
    com.zaxxer.hikari: ERROR

# Actuator sécurisé
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: never
```

---

## Fichier .env.example

```bash
# ============================================================================
# ALEXANDRIA RAG SERVER - Variables d'environnement
# Copier vers .env et remplir les valeurs
# NE JAMAIS COMMITTER .env DANS GIT
# ============================================================================

# ---- PROFIL SPRING ----
SPRING_PROFILES_ACTIVE=dev
# Options: dev, test, prod

# ---- SERVEUR ----
SERVER_PORT=8080
SERVER_CONTEXT_PATH=/

# ---- BASE DE DONNÉES POSTGRESQL ----
DB_HOST=localhost
DB_PORT=5432
DB_NAME=alexandria
DB_USER=postgres
DB_PASSWORD=your_secure_password_here

# HikariCP (optionnel - défauts sensés inclus)
HIKARI_MAX_POOL=50
HIKARI_MIN_IDLE=10
HIKARI_LEAK_DETECTION=60000

# ---- PGVECTOR ----
PGVECTOR_TABLE=document_embeddings
PGVECTOR_INDEX_LISTS=100
PGVECTOR_CREATE_TABLE=true

# ---- INFINITY LOCAL (développement) ----
# Lancer avec: docker run -it --gpus all -p 7997:7997 michaelf34/infinity:latest v2 --model-id BAAI/bge-m3 --model-id BAAI/bge-reranker-v2-m3
INFINITY_EMBEDDING_URL=http://localhost:7997/v1
INFINITY_RERANK_URL=http://localhost:7997
INFINITY_API_KEY=not-required
INFINITY_TIMEOUT=PT120S
INFINITY_CONNECT_TIMEOUT=60s
INFINITY_READ_TIMEOUT=300s

# ---- RUNPOD SERVERLESS (production) ----
RUNPOD_ENABLED=false
RUNPOD_API_KEY=your_runpod_api_key_here
RUNPOD_ENDPOINT_ID=your_endpoint_id_here
# URL finale: https://api.runpod.ai/v2/${RUNPOD_ENDPOINT_ID}/openai/v1

# ---- MODÈLES ----
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIMENSIONS=1024
EMBEDDING_BATCH_SIZE=32
RERANK_MODEL=BAAI/bge-reranker-v2-m3
RERANK_MAX_LENGTH=1024

# ---- RAG PIPELINE ----
RAG_TOP_K=100
RAG_MIN_SCORE=0.3
RAG_HNSW_EF_SEARCH=100
RAG_RERANK_ENABLED=true
RAG_TOP_N=10
RAG_RERANK_MIN_SCORE=0.5

# ---- SPRING AI MCP ----
MCP_REQUEST_TIMEOUT=60s
MCP_KEEPALIVE=30s

# ---- RETRY ----
RETRY_MAX_ATTEMPTS=4
RETRY_INITIAL_DELAY=1000
RETRY_MAX_DELAY=30000
RETRY_MULTIPLIER=2.0

# ---- LOGGING ----
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_APP=DEBUG
LOG_LEVEL_MCP=INFO
LOG_LEVEL_LANGCHAIN4J=INFO
LOG_LEVEL_HIKARI=WARN
LOG_LEVEL_RETRY=DEBUG
LOG_LEVEL_HTTP=WARN
LANGCHAIN4J_LOG_REQUESTS=false
LANGCHAIN4J_LOG_RESPONSES=false

# ---- VIRTUAL THREADS (dev only) ----
DEV_VIRTUAL_THREADS=true
```

---

## Configuration Java complémentaire requise

### RetryConfig.java (Spring Retry)

```java
package com.alexandria.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
    // Spring Retry n'a PAS de properties auto-config
    // Utiliser @Retryable avec expressions SpEL référençant les properties custom
}
```

### Exemple @Retryable pour appels Infinity

```java
@Service
@Slf4j
public class EmbeddingService {

    @Retryable(
        retryFor = {ConnectException.class, SocketTimeoutException.class, HttpServerErrorException.class},
        noRetryFor = {HttpClientErrorException.BadRequest.class},
        maxAttemptsExpression = "${retry.infinity.max-attempts}",
        backoff = @Backoff(
            delayExpression = "${retry.infinity.initial-delay}",
            maxDelayExpression = "${retry.infinity.max-delay}",
            multiplierExpression = "${retry.infinity.multiplier}"
        )
    )
    public List<float[]> generateEmbeddings(List<String> texts) {
        log.debug("Generating embeddings for {} texts (attempt {})", 
            texts.size(), RetrySynchronizationManager.getContext().getRetryCount() + 1);
        // Appel Infinity...
    }

    @Recover
    public List<float[]> recoverEmbeddings(Exception e, List<String> texts) {
        log.error("All retry attempts exhausted for embeddings generation", e);
        throw new EmbeddingGenerationException("Failed after retries", e);
    }
}
```

### PgVectorConfig.java (Configuration manuelle requise)

```java
package com.alexandria.config;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class PgVectorConfig {

    @Value("${alexandria.pgvector.table}")
    private String table;

    @Value("${alexandria.pgvector.dimension}")
    private int dimension;

    @Value("${alexandria.pgvector.use-index}")
    private boolean useIndex;

    @Value("${alexandria.pgvector.index-list-size}")
    private int indexListSize;

    @Value("${alexandria.pgvector.create-table}")
    private boolean createTable;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
            .datasource(dataSource)
            .table(table)
            .dimension(dimension)
            .useIndex(useIndex)
            .indexListSize(indexListSize)
            .createTable(createTable)
            .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
            .build();
    }
}
```

---

## Stratégie de sécurisation des secrets

### Variables qui DOIVENT être externalisées

| Variable | Risque | Solution production |
|----------|--------|---------------------|
| `DB_PASSWORD` | **CRITIQUE** | Kubernetes Secret, Vault |
| `RUNPOD_API_KEY` | **CRITIQUE** | Kubernetes Secret, Vault |
| `INFINITY_API_KEY` | Modéré | Variable d'environnement |

### Alternatives au fichier .env

**Kubernetes Secrets (recommandé)**:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: alexandria-secrets
type: Opaque
stringData:
  DB_PASSWORD: "secure_password"
  RUNPOD_API_KEY: "rpa_xxxxx"
```

**HashiCorp Vault** avec Spring Cloud Vault:
```yaml
spring:
  cloud:
    vault:
      uri: https://vault.company.com
      authentication: kubernetes
      kv:
        backend: secret
        default-context: alexandria
```

### Fichier .gitignore requis

```gitignore
# Secrets - NE JAMAIS COMMITTER
.env
.env.local
.env.*.local
*.secrets.yml
application-secrets.yml

# Keystores
*.jks
*.p12
*.pem
*.key
```

---

## Lancement Infinity local pour développement

```bash
# Docker avec GPU (NVIDIA)
docker run -it --gpus all \
  -v $PWD/infinity-cache:/app/.cache \
  -p 7997:7997 \
  michaelf34/infinity:latest \
  v2 \
  --model-id BAAI/bge-m3 \
  --model-id BAAI/bge-reranker-v2-m3 \
  --port 7997 \
  --batch-size 32 \
  --batch-size 16

# Sans GPU (CPU only - lent mais fonctionnel)
docker run -it \
  -v $PWD/infinity-cache:/app/.cache \
  -p 7997:7997 \
  michaelf34/infinity:latest \
  v2 \
  --model-id BAAI/bge-m3 \
  --model-id BAAI/bge-reranker-v2-m3 \
  --engine torch \
  --device cpu
```

---

## Points d'attention Virtual Threads + HikariCP

Avec Java 25, le problème de **pinning** des Virtual Threads sur les blocs `synchronized` d'HikariCP est résolu grâce au JEP 491 (intégré depuis Java 24). Pour les versions antérieures à Java 24:

- Utiliser des **pool sizes plus importants** (100-200 connexions)
- Activer le diagnostic: `-Djdk.tracePinnedThreads=full`
- Alternative: remplacer HikariCP par **Agroal** (natif Virtual Threads)

La configuration fournie utilise `maximum-pool-size: 50` comme compromis équilibré pour un serveur mono-utilisateur avec 24GB RAM, ce qui est suffisant pour des workloads RAG typiques où la concurrence provient principalement des appels parallèles aux embeddings.