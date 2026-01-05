# Logging et Health Checks pour serveur RAG Spring Boot 3.5.x

Un serveur RAG production-ready nécessite une observabilité complète couvrant **trois piliers** : logs structurés avec correlation ID, health checks proactifs, et métriques de latence par étape du pipeline. Spring Boot 3.5.9 apporte le structured logging natif et des améliorations Actuator significatives qui simplifient cette implémentation.

Les Virtual Threads de Java 25 introduisent une complexité pour MDC (basé sur ThreadLocal), mais le pattern **TaskDecorator** résout efficacement ce problème. Pour un serveur mono-utilisateur self-hosted avec Langchain4j et pgvector, l'architecture recommandée combine Logback ECS pour les logs JSON, des HealthIndicators custom pour Infinity et pgvector, et un InfoContributor exposant les statistiques RAG.

---

## Événements critiques à logger dans un pipeline RAG

Le logging d'un serveur RAG doit capturer **chaque étape du pipeline** avec ses métriques de latence. Pour l'ingestion : début/fin avec batch_id, nombre de chunks générés, et durée totale. Pour la recherche sémantique : query reçue, latence embedding (**typiquement 20-50ms** pour BGE-M3), latence recherche pgvector (**5-100ms** selon l'index), latence reranking (**100-300ms** pour bge-reranker-v2-m3), et nombre de résultats retournés.

Les erreurs méritent une attention particulière : timeout Infinity (configurer **5s pour embedding, 15s pour reranking**), erreurs connexion pgvector, et failures de parsing documents. Chaque erreur doit inclure le correlation ID pour traçabilité cross-requête.

| Phase | Événement | Données à capturer | Niveau |
|-------|-----------|-------------------|--------|
| Ingestion | Début | source, batch_id, timestamp | INFO |
| Ingestion | Chunking | nb_chunks, taille_moyenne, stratégie | INFO |
| Ingestion | Embedding docs | latence_ms, nb_vecteurs, modèle | INFO |
| Recherche | Query reçue | query, user_session, correlation_id | INFO |
| Recherche | Embedding query | latence_ms, tokens | DEBUG |
| Recherche | Vector search | latence_ms, top_k, filtres | INFO |
| Recherche | Reranking | latence_ms, scores_avant_après | INFO |
| Erreur | Timeout externe | service, timeout_ms, retry_count | ERROR |
| Erreur | Connexion DB | error_type, stack_trace | ERROR |

---

## Configuration logback-spring.xml complète

Cette configuration utilise le **StructuredLogEncoder natif** de Spring Boot 3.4+ au format ECS (Elastic Common Schema), optimisé pour Loki/Grafana. Le profil dev affiche des logs colorés lisibles, tandis que prod génère du JSON structuré avec async appender.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">
    
    <!-- Propriétés Spring injectées -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="rag-server"/>
    <springProperty scope="context" name="LOG_PATH" source="logging.file.path" defaultValue="./logs"/>
    
    <!-- ============================================ -->
    <!-- PROFIL DÉVELOPPEMENT - Console lisible       -->
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
        
        <!-- Debug application et SQL -->
        <logger name="com.mycompany.rag" level="DEBUG"/>
        <logger name="dev.langchain4j" level="DEBUG"/>
        <logger name="org.hibernate.SQL" level="DEBUG"/>
        <logger name="org.hibernate.orm.jdbc.bind" level="TRACE"/>
    </springProfile>
    
    <!-- ============================================ -->
    <!-- PROFIL PRODUCTION - JSON Structuré ECS      -->
    <!-- ============================================ -->
    <springProfile name="prod,production">
        
        <!-- Console JSON pour stdout (Docker/K8s) -->
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
            <neverBlock>false</neverBlock>
            <appender-ref ref="JSON_CONSOLE"/>
        </appender>
        
        <root level="INFO">
            <appender-ref ref="ASYNC_JSON"/>
            <appender-ref ref="JSON_FILE"/>
        </root>
        
        <!-- Niveaux production optimisés -->
        <logger name="com.mycompany.rag" level="INFO"/>
        <logger name="dev.langchain4j" level="WARN"/>
        <logger name="org.springframework" level="WARN"/>
        <logger name="org.springframework.ai" level="WARN"/>
        <logger name="org.hibernate" level="WARN"/>
        <logger name="org.hibernate.SQL" level="ERROR"/>
        <logger name="com.zaxxer.hikari" level="WARN"/>
        <logger name="com.zaxxer.hikari.pool.HikariPool" level="INFO"/>
        <logger name="io.netty" level="WARN"/>
    </springProfile>
    
</configuration>
```

---

## Configuration application.yml pour Actuator et logging

La configuration ci-dessous active les Virtual Threads, expose les endpoints Actuator essentiels avec sécurité, et définit les niveaux de log recommandés. Le port de management **8081** isole les endpoints de monitoring du trafic applicatif.

```yaml
spring:
  application:
    name: rag-mcp-server
  
  # Virtual Threads Java 21+ activés
  threads:
    virtual:
      enabled: true
  
  # Configuration DataSource pgvector
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdb
    username: ${DB_USERNAME:rag}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: RAG-HikariPool

# Configuration Logging
logging:
  level:
    root: INFO
    com.mycompany.rag: INFO
    dev.langchain4j: WARN
    org.springframework: WARN
    org.springframework.ai.mcp: WARN
    org.hibernate: WARN
    org.hibernate.SQL: ERROR
    org.hibernate.stat: OFF
    com.zaxxer.hikari: WARN
    com.zaxxer.hikari.pool.HikariPool: INFO
    org.apache.catalina: WARN
    io.netty: WARN
  
  # Structured logging natif Spring Boot 3.4+
  structured:
    format:
      console: ecs
    ecs:
      service:
        name: ${spring.application.name}
        version: "@project.version@"
        environment: ${ENVIRONMENT:production}
  
  file:
    path: ./logs

# Configuration Actuator
management:
  server:
    port: 8081
    address: 127.0.0.1
  
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include:
          - health
          - info
          - metrics
          - prometheus
  
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
      probes:
        enabled: true
      access: unrestricted
    info:
      access: unrestricted
    heapdump:
      access: none
  
  health:
    defaults:
      enabled: true
    diskspace:
      enabled: true
      threshold: 104857600  # 100MB minimum
    db:
      enabled: true
    # Custom health groups
    group:
      readiness:
        include: db, diskSpace, infinity, pgvector
      liveness:
        include: ping
  
  info:
    env:
      enabled: true
    java:
      enabled: true
    os:
      enabled: true
    build:
      enabled: true

# Informations /actuator/info
info:
  app:
    name: RAG MCP Server
    description: Serveur RAG avec Langchain4j et pgvector
    version: "@project.version@"
  rag:
    embedding-model: BGE-M3
    reranking-model: bge-reranker-v2-m3
    vector-dimensions: 1024

# Configuration RAG custom
rag:
  infinity:
    embedding-url: ${INFINITY_EMBEDDING_URL:http://localhost:7997}
    reranking-url: ${INFINITY_RERANKING_URL:http://localhost:7998}
    timeout-embedding-ms: 5000
    timeout-reranking-ms: 15000
  pgvector:
    table-name: document_embeddings
    index-type: hnsw
```

---

## Filter pour correlation ID avec propagation MDC

Ce filter génère automatiquement un correlation ID pour chaque requête MCP et le propage dans MDC. Il gère correctement la récupération d'un ID existant dans les headers pour le chaînage inter-services.

```java
package com.mycompany.rag.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(1)
public class CorrelationIdFilter implements Filter {
    
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                         FilterChain chain) throws IOException, ServletException {
        
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;
        long startTime = System.currentTimeMillis();
        
        try {
            // Récupérer ou générer le correlation ID
            String correlationId = Optional
                .ofNullable(httpRequest.getHeader(CORRELATION_ID_HEADER))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> generateCorrelationId());
            
            // Request ID unique pour cette requête spécifique
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            
            // Peupler MDC
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            MDC.put(REQUEST_ID_MDC_KEY, requestId);
            MDC.put("method", httpRequest.getMethod());
            MDC.put("path", httpRequest.getRequestURI());
            
            // Header de réponse pour traçabilité client
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            log.debug("Request started: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
            
            chain.doFilter(request, response);
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("Request completed in {}ms - status: {}", duration, httpResponse.getStatus());
            
            // TOUJOURS nettoyer MDC pour éviter les fuites
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(REQUEST_ID_MDC_KEY);
            MDC.remove("method");
            MDC.remove("path");
        }
    }
    
    private String generateCorrelationId() {
        return "CID-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
```

---

## Configuration Virtual Threads avec propagation MDC

Pour Java 25 avec Virtual Threads, le TaskDecorator assure la propagation du contexte MDC lors des opérations asynchrones (@Async, CompletableFuture).

```java
package com.mycompany.rag.config;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class VirtualThreadConfig {
    
    /**
     * Configure Tomcat pour utiliser Virtual Threads
     */
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreads() {
        return protocolHandler -> 
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Configure @Async pour Virtual Threads avec propagation MDC
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        var executor = new TaskExecutorAdapter(
            Executors.newVirtualThreadPerTaskExecutor()
        );
        executor.setTaskDecorator(mdcPropagatingDecorator());
        return executor;
    }
    
    /**
     * TaskDecorator qui propage le contexte MDC vers les Virtual Threads
     */
    @Bean
    public TaskDecorator mdcPropagatingDecorator() {
        return runnable -> {
            // Capturer le contexte MDC du thread parent
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            
            return () -> {
                try {
                    // Restaurer le contexte dans le Virtual Thread
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    // Nettoyer pour éviter les fuites mémoire
                    MDC.clear();
                }
            };
        };
    }
}
```

---

## Custom HealthIndicator pour Infinity embeddings

Ce HealthIndicator vérifie la disponibilité du service Infinity pour les embeddings BGE-M3. Il teste le endpoint `/health` et rapporte la latence.

```java
package com.mycompany.rag.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

@Component("infinity")
public class InfinityEmbeddingHealthIndicator implements HealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(InfinityEmbeddingHealthIndicator.class);
    
    private final RestClient restClient;
    private final String embeddingUrl;
    private final Duration timeout;
    
    public InfinityEmbeddingHealthIndicator(
            RestClient.Builder restClientBuilder,
            @Value("${rag.infinity.embedding-url}") String embeddingUrl,
            @Value("${rag.infinity.timeout-embedding-ms:5000}") int timeoutMs) {
        
        this.embeddingUrl = embeddingUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.restClient = restClientBuilder
            .baseUrl(embeddingUrl)
            .build();
    }
    
    @Override
    public Health health() {
        var startTime = Instant.now();
        
        try {
            // Vérifier /health endpoint Infinity
            var response = restClient.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity();
            
            var latency = Duration.between(startTime, Instant.now());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // Vérifier aussi les modèles chargés
                String modelsInfo = fetchModelsInfo();
                
                return Health.up()
                    .withDetail("service", "Infinity Embedding")
                    .withDetail("url", embeddingUrl)
                    .withDetail("model", "BGE-M3")
                    .withDetail("latencyMs", latency.toMillis())
                    .withDetail("models", modelsInfo)
                    .build();
            }
            
            return Health.down()
                .withDetail("service", "Infinity Embedding")
                .withDetail("url", embeddingUrl)
                .withDetail("status", response.getStatusCode().value())
                .build();
                
        } catch (Exception e) {
            log.warn("Infinity embedding health check failed: {}", e.getMessage());
            
            return Health.down()
                .withDetail("service", "Infinity Embedding")
                .withDetail("url", embeddingUrl)
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
    
    private String fetchModelsInfo() {
        try {
            return restClient.get()
                .uri("/models")
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            return "Unable to fetch models";
        }
    }
}
```

---

## Custom HealthIndicator pour service de reranking

Le reranking nécessite un test plus complet avec une requête minimale pour valider que le modèle répond correctement.

```java
package com.mycompany.rag.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

@Component("reranking")
public class RerankingServiceHealthIndicator implements HealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(RerankingServiceHealthIndicator.class);
    
    private final RestClient restClient;
    private final String rerankingUrl;
    
    // Payload minimal pour test de santé
    private static final String TEST_PAYLOAD = """
        {
            "query": "health check test",
            "documents": ["test document for health verification"],
            "return_documents": false,
            "top_n": 1
        }
        """;
    
    public RerankingServiceHealthIndicator(
            RestClient.Builder restClientBuilder,
            @Value("${rag.infinity.reranking-url}") String rerankingUrl) {
        
        this.rerankingUrl = rerankingUrl;
        this.restClient = restClientBuilder
            .baseUrl(rerankingUrl)
            .build();
    }
    
    @Override
    public Health health() {
        var startTime = Instant.now();
        
        try {
            // Test avec requête reranking minimale
            var response = restClient.post()
                .uri("/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TEST_PAYLOAD)
                .retrieve()
                .toEntity(String.class);
            
            var latency = Duration.between(startTime, Instant.now());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // Vérifier que la latence est acceptable (< 5s pour test minimal)
                if (latency.toMillis() > 5000) {
                    return Health.status("DEGRADED")
                        .withDetail("service", "Reranking (bge-reranker-v2-m3)")
                        .withDetail("url", rerankingUrl)
                        .withDetail("latencyMs", latency.toMillis())
                        .withDetail("warning", "High latency detected")
                        .build();
                }
                
                return Health.up()
                    .withDetail("service", "Reranking (bge-reranker-v2-m3)")
                    .withDetail("url", rerankingUrl)
                    .withDetail("latencyMs", latency.toMillis())
                    .build();
            }
            
            return Health.down()
                .withDetail("service", "Reranking")
                .withDetail("url", rerankingUrl)
                .withDetail("status", response.getStatusCode().value())
                .withDetail("body", response.getBody())
                .build();
                
        } catch (Exception e) {
            log.warn("Reranking health check failed: {}", e.getMessage());
            
            return Health.down()
                .withDetail("service", "Reranking")
                .withDetail("url", rerankingUrl)
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
```

---

## Custom HealthIndicator pour pgvector

Ce HealthIndicator vérifie non seulement la connectivité PostgreSQL mais aussi la présence et version de l'extension pgvector.

```java
package com.mycompany.rag.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

@Component("pgvector")
public class PgVectorHealthIndicator implements HealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(PgVectorHealthIndicator.class);
    
    private final DataSource dataSource;
    private final String tableName;
    
    public PgVectorHealthIndicator(
            DataSource dataSource,
            @Value("${rag.pgvector.table-name:document_embeddings}") String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
    }
    
    @Override
    public Health health() {
        var startTime = Instant.now();
        
        try (Connection conn = dataSource.getConnection()) {
            var builder = Health.up()
                .withDetail("database", "PostgreSQL + pgvector");
            
            // 1. Vérifier l'extension pgvector
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'"
                );
                
                if (rs.next()) {
                    builder.withDetail("pgvectorVersion", rs.getString(1));
                } else {
                    return Health.down()
                        .withDetail("error", "pgvector extension not installed")
                        .withDetail("action", "Run: CREATE EXTENSION vector;")
                        .build();
                }
            }
            
            // 2. Vérifier la table d'embeddings
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(String.format(
                    "SELECT COUNT(*) as count FROM %s", tableName
                ));
                
                if (rs.next()) {
                    builder.withDetail("vectorCount", rs.getLong("count"));
                }
            }
            
            // 3. Vérifier l'index HNSW/IVFFlat
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(String.format("""
                    SELECT indexname, indexdef 
                    FROM pg_indexes 
                    WHERE tablename = '%s' 
                    AND indexdef LIKE '%%vector%%'
                    """, tableName));
                
                if (rs.next()) {
                    builder.withDetail("vectorIndexName", rs.getString("indexname"));
                    // Extraire le type d'index (hnsw ou ivfflat)
                    String indexDef = rs.getString("indexdef");
                    if (indexDef.contains("hnsw")) {
                        builder.withDetail("indexType", "HNSW");
                    } else if (indexDef.contains("ivfflat")) {
                        builder.withDetail("indexType", "IVFFlat");
                    }
                } else {
                    builder.withDetail("warning", "No vector index found - queries may be slow");
                }
            }
            
            // 4. Calculer la latence
            var latency = Duration.between(startTime, Instant.now());
            builder.withDetail("latencyMs", latency.toMillis());
            
            // Alerte si latence élevée
            if (latency.toMillis() > 1000) {
                return builder
                    .status("DEGRADED")
                    .withDetail("warning", "High database latency")
                    .build();
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("pgvector health check failed", e);
            
            return Health.down()
                .withDetail("database", "PostgreSQL + pgvector")
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
```

---

## InfoContributor pour statistiques RAG

L'InfoContributor expose les métriques business du serveur RAG sur `/actuator/info` : nombre de documents indexés, date du dernier ingest, et statistiques du pipeline.

```java
package com.mycompany.rag.actuator;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RagStatsInfoContributor implements InfoContributor {
    
    private final JdbcTemplate jdbcTemplate;
    private final Instant startTime = Instant.now();
    
    // Statistiques en mémoire (mises à jour par le service RAG)
    private final LongAdder totalQueries = new LongAdder();
    private final LongAdder totalEmbeddingTimeMs = new LongAdder();
    private final LongAdder totalSearchTimeMs = new LongAdder();
    private final LongAdder totalRerankTimeMs = new LongAdder();
    private final AtomicLong lastQueryTimestamp = new AtomicLong(0);
    
    public RagStatsInfoContributor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void contribute(Info.Builder builder) {
        // Runtime info
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("uptime", formatDuration(Duration.between(startTime, Instant.now())));
        runtime.put("processors", Runtime.getRuntime().availableProcessors());
        runtime.put("maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        runtime.put("usedHeapMb", (Runtime.getRuntime().totalMemory() - 
                                   Runtime.getRuntime().freeMemory()) / (1024 * 1024));
        
        // Virtual Threads info (Java 21+)
        var runtimeMx = ManagementFactory.getRuntimeMXBean();
        runtime.put("jvmVersion", runtimeMx.getVmVersion());
        runtime.put("virtualThreadsEnabled", true);
        
        builder.withDetail("runtime", runtime);
        
        // RAG Statistics from database
        Map<String, Object> ragStats = new LinkedHashMap<>();
        try {
            // Nombre total de documents
            Long documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT document_id) FROM document_embeddings", Long.class);
            ragStats.put("totalDocuments", documentCount);
            
            // Nombre total de chunks/vecteurs
            Long vectorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_embeddings", Long.class);
            ragStats.put("totalVectors", vectorCount);
            
            // Date du dernier ingest
            String lastIngest = jdbcTemplate.queryForObject(
                "SELECT MAX(created_at)::text FROM document_embeddings", String.class);
            ragStats.put("lastIngestAt", lastIngest != null ? lastIngest : "Never");
            
            // Taille de la table
            String tableSize = jdbcTemplate.queryForObject(
                "SELECT pg_size_pretty(pg_total_relation_size('document_embeddings'))", 
                String.class);
            ragStats.put("storageSize", tableSize);
            
        } catch (Exception e) {
            ragStats.put("error", "Unable to fetch DB stats: " + e.getMessage());
        }
        
        builder.withDetail("vectorStore", ragStats);
        
        // Pipeline performance stats
        Map<String, Object> pipelineStats = new LinkedHashMap<>();
        long queries = totalQueries.sum();
        pipelineStats.put("totalQueriesProcessed", queries);
        
        if (queries > 0) {
            pipelineStats.put("avgEmbeddingLatencyMs", totalEmbeddingTimeMs.sum() / queries);
            pipelineStats.put("avgSearchLatencyMs", totalSearchTimeMs.sum() / queries);
            pipelineStats.put("avgRerankLatencyMs", totalRerankTimeMs.sum() / queries);
        }
        
        long lastQuery = lastQueryTimestamp.get();
        if (lastQuery > 0) {
            pipelineStats.put("lastQueryAt", Instant.ofEpochMilli(lastQuery).toString());
        }
        
        builder.withDetail("pipelineMetrics", pipelineStats);
    }
    
    // Méthodes pour enregistrer les métriques (appelées par le service RAG)
    public void recordQuery(long embeddingMs, long searchMs, long rerankMs) {
        totalQueries.increment();
        totalEmbeddingTimeMs.add(embeddingMs);
        totalSearchTimeMs.add(searchMs);
        totalRerankTimeMs.add(rerankMs);
        lastQueryTimestamp.set(System.currentTimeMillis());
    }
    
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        return String.format("%dd %dh %dm", days, hours, minutes);
    }
}
```

---

## Service de logging RAG avec métriques de latence

Ce service wrapper illustre comment logger chaque étape du pipeline RAG avec les latences et le correlation ID dans MDC.

```java
package com.mycompany.rag.service;

import com.mycompany.rag.actuator.RagStatsInfoContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class InstrumentedRAGService {
    
    private static final Logger log = LoggerFactory.getLogger(InstrumentedRAGService.class);
    
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final RerankingService rerankingService;
    private final RagStatsInfoContributor statsContributor;
    
    public InstrumentedRAGService(
            EmbeddingService embeddingService,
            VectorSearchService vectorSearchService,
            RerankingService rerankingService,
            RagStatsInfoContributor statsContributor) {
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.rerankingService = rerankingService;
        this.statsContributor = statsContributor;
    }
    
    public RAGResponse query(String query, int topK) {
        String correlationId = MDC.get("correlationId");
        long totalStart = System.currentTimeMillis();
        
        log.atInfo()
            .addKeyValue("query", truncate(query, 100))
            .addKeyValue("topK", topK)
            .log("RAG query started");
        
        try {
            // 1. Embedding de la query
            long embeddingStart = System.currentTimeMillis();
            float[] queryVector = embeddingService.embed(query);
            long embeddingMs = System.currentTimeMillis() - embeddingStart;
            
            log.atDebug()
                .addKeyValue("step", "embedding")
                .addKeyValue("latencyMs", embeddingMs)
                .addKeyValue("dimensions", queryVector.length)
                .log("Query embedding completed");
            
            // 2. Recherche vectorielle pgvector
            long searchStart = System.currentTimeMillis();
            var searchResults = vectorSearchService.search(queryVector, topK * 2);
            long searchMs = System.currentTimeMillis() - searchStart;
            
            log.atDebug()
                .addKeyValue("step", "vectorSearch")
                .addKeyValue("latencyMs", searchMs)
                .addKeyValue("resultsCount", searchResults.size())
                .log("Vector search completed");
            
            // 3. Reranking
            long rerankStart = System.currentTimeMillis();
            var rerankedResults = rerankingService.rerank(query, searchResults, topK);
            long rerankMs = System.currentTimeMillis() - rerankStart;
            
            log.atDebug()
                .addKeyValue("step", "reranking")
                .addKeyValue("latencyMs", rerankMs)
                .addKeyValue("finalResultsCount", rerankedResults.size())
                .log("Reranking completed");
            
            // 4. Enregistrer les métriques
            statsContributor.recordQuery(embeddingMs, searchMs, rerankMs);
            
            long totalMs = System.currentTimeMillis() - totalStart;
            
            log.atInfo()
                .addKeyValue("totalLatencyMs", totalMs)
                .addKeyValue("embeddingMs", embeddingMs)
                .addKeyValue("searchMs", searchMs)
                .addKeyValue("rerankMs", rerankMs)
                .addKeyValue("resultsCount", rerankedResults.size())
                .log("RAG query completed successfully");
            
            return new RAGResponse(rerankedResults, totalMs);
            
        } catch (Exception e) {
            long totalMs = System.currentTimeMillis() - totalStart;
            
            log.atError()
                .addKeyValue("totalLatencyMs", totalMs)
                .addKeyValue("errorType", e.getClass().getSimpleName())
                .setCause(e)
                .log("RAG query failed");
            
            throw new RAGException("Query processing failed", e);
        }
    }
    
    private String truncate(String s, int maxLength) {
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...";
    }
}
```

---

## Configuration sécurité Actuator endpoints

Pour un déploiement self-hosted mono-utilisateur, cette configuration sécurise les endpoints sensibles tout en gardant `/health` accessible.

```java
package com.mycompany.rag.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class ActuatorSecurityConfig {
    
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                // Health basique et probes: publics
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                
                // Info: accessible sans auth pour mono-utilisateur
                .requestMatchers(EndpointRequest.to("info")).permitAll()
                
                // Metrics/Prometheus: restreint au localhost
                .requestMatchers(EndpointRequest.to("metrics", "prometheus"))
                    .access((authentication, context) -> {
                        String remoteAddr = context.getRequest().getRemoteAddr();
                        boolean isLocal = "127.0.0.1".equals(remoteAddr) || 
                                         "0:0:0:0:0:0:0:1".equals(remoteAddr);
                        return new org.springframework.security.authorization
                            .AuthorizationDecision(isLocal);
                    })
                
                // Tous les autres endpoints: bloqués
                .anyRequest().denyAll()
            )
            .csrf(csrf -> csrf.disable())
            .httpBasic(withDefaults());
        
        return http.build();
    }
}
```

---

## Niveaux de log recommandés par environnement

| Package | Dev | Prod | Notes |
|---------|-----|------|-------|
| `com.mycompany.rag` | DEBUG | INFO | Application principale |
| `dev.langchain4j` | DEBUG | WARN | Verbeux en dev, silencieux en prod |
| `org.springframework` | INFO | WARN | Framework Spring |
| `org.springframework.ai.mcp` | DEBUG | WARN | MCP server |
| `org.hibernate.SQL` | DEBUG | ERROR | SQL queries - jamais en prod |
| `org.hibernate.orm.jdbc.bind` | TRACE | OFF | Paramètres SQL - dev only |
| `com.zaxxer.hikari` | INFO | WARN | Connection pool |
| `com.zaxxer.hikari.pool.HikariPool` | DEBUG | INFO | Stats pool utiles |
| `io.netty` | WARN | WARN | Networking |
| `org.apache.catalina` | INFO | WARN | Tomcat |

---

## Conclusion

L'observabilité d'un serveur RAG Spring Boot 3.5.x repose sur trois éléments synergiques. Le **structured logging ECS** avec correlation ID permet de tracer chaque requête à travers embedding, recherche vectorielle et reranking. Les **HealthIndicators custom** pour Infinity et pgvector détectent proactivement les dégradations avant qu'elles n'impactent les utilisateurs. L'**InfoContributor** expose les métriques business essentielles : **nombre de vecteurs indexés, latences moyennes par étape, et horodatage du dernier ingest**.

Pour Java 25 avec Virtual Threads, le pattern TaskDecorator résout élégamment la propagation MDC. À terme, la migration vers **ScopedValue** (finalisé dans Java 25 via JEP 506) offrira une solution plus robuste et performante pour le context propagation dans les environnements massively-concurrent.

L'architecture recommandée expose le port **8081** pour Actuator, isolé du trafic applicatif, avec `/health` public pour les load balancers et `/metrics` restreint au localhost pour Prometheus/Grafana.