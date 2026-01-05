# Suivi d'implémentation - Alexandria RAG Server

**Créé:** 2026-01-05
**Mis à jour:** 2026-01-05 (Phase: Tech-Spec 73% - Document Update Strategy ajouté)
**Basé sur:** Recherches consolidées dans `docs/research/resultats/consolidation/`
**Tech-spec:** `docs/implementation-artifacts/tech-spec-wip.md`
**Recherches complémentaires:** 12/12 résolues (voir `recherches-complementaires.md`)

---

## Légende des statuts

| Statut | Description |
|--------|-------------|
| `[ ]` | Non commencé |
| `[TS]` | **Spécifié dans tech-spec** (design terminé, prêt à implémenter) |
| `[~]` | En cours d'implémentation |
| `[x]` | Terminé (implémenté et testé) |
| `[N/A]` | Non applicable / Décision de ne pas implémenter |

---

## 1. Configuration & Infrastructure

### 1.1 Configuration complète pour Alexandria RAG Server.md
**Fichier:** Configuration principale application.yml
**Analysé:** 2026-01-05 | **Validé partiellement** (SSE/IVFFlat/Spring Retry refusés)

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Configuration application.yml complète | | TS: Virtual Threads activés, structure définie |
| `[TS]` Configuration server (port, timeouts) | | TS: Tomcat connection-timeout documenté |
| `[TS]` Configuration datasource PostgreSQL | | TS: HikariCP + PostgreSQL 18 config documentée |
| `[TS]` Configuration Spring AI MCP | | TS: HTTP Streamable `/mcp` endpoint |
| `[TS]` Configuration Langchain4j embeddings | | TS: baseUrl custom vers Infinity documenté |
| `[TS]` Bean programmatique PgVectorEmbeddingStore | | TS: Pattern dans LangchainConfig.java |
| `[TS]` Profils Spring (dev/prod) | | TS: Structure définie |
| `[N/A]` Property `index-list-size` (IVFFlat) | | ❌ Refusé - utiliser HNSW manuel |
| `[N/A]` Property YAML `metadata-storage` | | ❌ Refusé - syntaxe invalide, config Java |
| `[N/A]` Spring Retry (`@Retryable`) | | ❌ Refusé - utiliser Resilience4j 2.3.0 |

### 1.2 Configuration par variables d'environnement dans Spring Boot 3.5.x.md
**Fichier:** Externalisation de la configuration
**Recherche:** R1.1, R2.1, R2.2 résolues

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Variables d'environnement pour secrets | | TS: INFINITY_API_KEY, DB_PASSWORD définis |
| `[TS]` Fichier .env pour dev local | | TS: spring-dotenv 5.1.0 choisi |
| `[TS]` Dépendance spring-dotenv 5.1.0 | | TS: À ajouter au pom.xml |
| `[ ]` ConfigurationProperties typées | | Non spécifié dans tech-spec |
| `[N/A]` Properties `spring.jpa.*` | | ❌ Langchain4j = JDBC pur, pas JPA |
| `[N/A]` Dépendance spring-boot-starter-data-jpa | | ❌ Non requise - utiliser starter-jdbc |

### 1.3 Packaging Docker pour Java 25 et Spring Boot 3.5.9 avec PostgreSQL 18.md
**Fichier:** Containerisation
**Recherche:** R3.1, R3.2 résolues

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Dockerfile multi-stage | | TS: Eclipse Temurin 25 spécifié |
| `[TS]` docker-compose.yml | | TS: pgvector/pgvector:0.8.1-pg18, PGDATA corrigé |
| `[TS]` Health checks Docker | | TS: `depends_on: condition: service_healthy` |
| `[TS]` Configuration JVM pour containers | | TS: -XX:MaxRAMPercentage documenté |
| `[N/A]` Volume mount `/var/lib/postgresql/data` | | ❌ PostgreSQL 18 utilise `/var/lib/postgresql/18/docker` |

---

## 2. Transport MCP & Outils

### 2.1 Support complet dans Spring AI MCP SDK 1.1.2.md
**Fichier:** Migration SSE → HTTP Streamable
**Recherche:** R4.1, R4.2 résolues

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[x]` Tech-spec mise à jour pour HTTP Streamable | | Transport recommandé MCP 2025-03-26 |
| `[TS]` Configuration protocol: STREAMABLE | | TS: Endpoint unique `/mcp` documenté |
| `[TS]` Configuration Claude Code type: http | | TS: Pattern .mcp.json à adapter |
| `[TS]` streamable-http.mcp-endpoint: /mcp | | TS: Documenté dans tech-spec |
| `[TS]` streamable-http.keep-alive-interval: 30s | | TS: Documenté |
| `[TS]` Progress notifications via `McpSyncRequestContext` | | TS: API `context.progress()` documentée |
| `[N/A]` Variable MCP_TOOL_TIMEOUT | | ❌ N'existe pas - ignorée par Claude Code |

### 2.2 MCP Tool Patterns for RAG Servers with Spring AI 1.1.2.md
**Fichier:** Implémentation des outils MCP
**Recherche:** R5.1, R5.2 résolues

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Tool `search_documents` | | TS: Mentionné dans scope |
| `[TS]` Tool `ingest_document` | | TS: Mentionné dans scope |
| `[TS]` Tool `list_documents` | | TS: Mentionné dans scope |
| `[TS]` Tool `delete_document` | | TS: Mentionné dans scope |
| `[ ]` Tool `get_server_status` | | Optionnel, non spécifié |
| `[TS]` Naming convention snake_case | | TS: Implicite dans @McpTool |
| `[TS]` Utiliser `@McpTool` | | TS: Imports documentés |
| `[TS]` Utiliser `McpSyncRequestContext` | | TS: Imports documentés |
| `[TS]` McpTools.java classe complète | | TS: Structure projet définie |

### 2.3 Optimal MCP tool response formats for Claude Code RAG integration.md
**Fichier:** Format des réponses MCP
**Analysé:** 2026-01-05 | **Validé** - Dual-format response pattern

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Dual-format response (JSON + Markdown) | | TS: structuredContent + addTextContent |
| `[TS]` Limite 8000 tokens par réponse | | TS: Sous warning 10K Claude Code |
| `[TS]` Metadata inline (score, source) | | TS: Dans text + structured séparé |
| `[TS]` Truncation indicator explicite | | TS: "[Content truncated...]" pattern |
| `[TS]` CallToolResult pour erreurs | | TS: Imports documentés |
| `[v2]` Relevance-gap filtering | | Reporté v2 - over-engineering MVP |
| `[v2]` Pagination si > 10 résultats | | Reporté v2 - complexité |

### 2.4 Gestion des erreurs MCP pour un serveur RAG Spring AI.md
**Fichier:** Error handling MCP
**Analysé:** 2026-01-05 | **Validé** - Hiérarchie exceptions + isError pattern

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` isError: true pour erreurs métier | | TS: Visible par LLM, pas JSON-RPC |
| `[TS]` AlexandriaException hierarchy | | TS: ErrorCategory enum |
| `[TS]` Messages actionnables (Contexte/Problème/Solution) | | TS: Format structuré |
| `[TS]` Mapping exception → réponse MCP | | TS: Tableau dans tech-spec |
| `[TS]` CallToolResult.error() helper | | TS: Pattern errorResult() |
| `[N/A]` Spring Retry dans exemples | | ❌ Ignorer - utiliser Resilience4j |

---

## 3. Pipeline RAG

### 3.1 Paramètres optimaux pour un pipeline RAG avec BGE-M3 et reranking.md
**Fichier:** Configuration du pipeline
**Analysé:** 2026-01-05 | **Validé** - RagProperties.java ajouté

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Top-K initial: 50 candidats | | TS: rag.retrieval.top-k-initial=50 |
| `[TS]` Top-N final: 3-5 résultats | | TS: rag.retrieval.top-n-final=5 |
| `[TS]` Score reranker normalisé (sigmoïde) | | TS: rag.reranking.normalize-scores=true |
| `[TS]` Seuil de pertinence: 0.3 | | TS: rag.retrieval.min-score=0.3 |
| `[TS]` Seuil relatif 50% du meilleur | | TS: relative-threshold-ratio=0.5 |
| `[TS]` Garantie minimum 2 résultats | | TS: min-results-guarantee=2 |
| `[TS]` RagProperties @ConfigurationProperties | | TS: Structure Java complète documentée |
| `[N/A]` Budget contexte: 5-20% fenêtre | | Implicite avec top-n-final=5 (2500 tokens max) |

### 3.2 Handling no results in RAG systems - A practical implementation guide.md
**Fichier:** Gestion des résultats vides
**Analysé:** 2026-01-05 | **Validé** - Patterns core implémentés, v2 items reportés

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` 4 scénarios d'échec distincts | | TS: Tableau scénarios documenté |
| `[TS]` QueryValidator (validation avant recherche) | | TS: Classe complète documentée |
| `[TS]` McpSearchResponse avec SearchMetadata | | TS: Record avec factory methods |
| `[TS]` SearchStatus: SUCCESS/PARTIAL/NO_RESULTS/ERROR | | TS: Enum documenté |
| `[TS]` RelevanceLevel: HIGH/MEDIUM/LOW | | TS: Tiered confidence |
| `[TS]` Tiered response logic dans RetrievalService | | TS: Seuils 0.7/0.4/0.2 |
| `[TS]` Fallback vector si reranker filtre tout | | TS: score > 0.6 → fallback |
| `[TS]` Messages explicites par scénario | | TS: Messages user-facing |
| `[v2]` Hybrid search BM25 + vector | | Reporté v2 - over-engineering MVP |
| `[v2]` Query suggestions basées sur corpus | | Reporté v2 - complexité |

### 3.3 Métadonnées RAG optimales pour le projet Alexandria.md
**Fichier:** Schéma de métadonnées
**Analysé:** 2026-01-05 | **Validé** - ChunkMetadata record pattern

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` ChunkMetadata record (7 champs) | | TS: source_uri, chunk_index, breadcrumbs... |
| `[TS]` Breadcrumbs format "H1 > H2 > H3" | | TS: String délimitée (contrainte Langchain4j) |
| `[TS]` toLogicalUri() sécurité | | TS: Ne pas exposer filesystem paths |
| `[TS]` Labels qualitatifs vs scores numériques | | TS: HIGH/MEDIUM/LOW pour LLM |
| `[TS]` Budget metadata: 50-80 tokens/chunk | | TS: 10-15% du budget chunk |
| `[ ]` modified_at en Long (epoch millis) | | Optionnel MVP |
| `[N/A]` Exemple indexListSize(100) | | ❌ HNSW pas IVFFlat |
| `[N/A]` Dimension 1536 dans exemple | | ❌ BGE-M3 = 1024 |

---

## 4. Ingestion & Chunking

### 4.1 Markdown splitter edge cases for RAG systems.md
**Fichier:** AlexandriaMarkdownSplitter
**Analysé:** 2026-01-05 | **Validé** - Custom splitter requis

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Code blocks atomiques (jamais split) | | TS: Unités indivisibles |
| `[TS]` Tables atomiques | | TS: Préserver structure complète |
| `[TS]` Breadcrumb depth = 3 (H1-H3) | | TS: Éviter H4+ (bruit) |
| `[TS]` YAML front matter → metadata | | TS: Extraction pré-chunking |
| `[TS]` maxOversizedChunk: 1500 tokens | | TS: Pour code blocks volumineux |
| `[TS]` Chunks 450-500 tokens | | TS: Chunking Strategy documentée |
| `[TS]` Overlap 50-75 tokens (10-15%) | | TS: Chunking Strategy documentée |
| `[TS]` Priorité split: code > tables > headers > lists | | TS: Ordre de préservation |
| `[N/A]` Langchain4j MarkdownSplitter | | ❌ PR #2418 toujours draft - custom requis |

### 4.2 Stratégies d'ingestion pour serveur RAG MCP Alexandria.md
**Fichier:** Workflow d'ingestion
**Analysé:** 2026-01-05 | **Validé** - Architecture hybride CLI + MCP

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` CLI Picocli pour ingestion bulk | | TS: picocli-spring-boot-starter 4.7.7 |
| `[TS]` MCP tool limité à 5 docs (timeout 60s) | | TS: ingest_document pour usage léger |
| `[TS]` Pas de watcher répertoire | | TS: Complexité évitée |
| `[TS]` Batch size 25 docs | | TS: Pour hardware limité (4 cores) |
| `[ ]` Détection de doublons (content_hash) | | Non spécifié - v2 |
| `[ ]` Re-ingestion intelligente | | Non spécifié - v2 |
| `[v2]` Rate limiter Resilience4j | | Reporté v2 - simplifier MVP |
| `[N/A]` SSE transport dans exemples | | ❌ Remplacer par HTTP Streamable |

### 4.3 Stratégies de mise à jour des documents dans un RAG PostgreSQL pgvector avec Langchain4j.md
**Fichier:** Update strategy
**Analysé:** 2026-01-05 | **Validé** - Pattern DELETE+INSERT, identification hybride

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Identification hybride (chemin + SHA-256) | | TS: document_hash ajouté à ChunkMetadata |
| `[TS]` Pattern DELETE + INSERT transactionnel | | TS: removeAll(Filter) + addAll() |
| `[TS]` Détection changement 2 phases (mtime/size → hash) | | TS: Fast path optimisation |
| `[TS]` Index B-tree sur document_id et document_hash | | TS: Ajouté au schéma SQL |
| `[TS]` Hard delete explicite (pas d'historique) | | TS: Implicite MVP |
| `[v2]` Soft delete avec is_active | | Reporté v2 - over-engineering MVP |
| `[v2]` Garbage collection chunks orphelins | | Reporté v2 - logique applicative requise |
| `[N/A]` `.indexListSize(100)` dans exemple | | ❌ Refusé - IVFFlat, utiliser HNSW |

### 4.4 Spécification complète llms.txt pour implémentation Java.md
**Fichier:** LlmsTxtParser.java
**Analysé:** 2026-01-05 | **Validé** - Parser complet prêt

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` LlmsTxtParser.java avec regex | | TS: Code Java complet |
| `[TS]` LlmsTxtDocument record | | TS: title, description, sections |
| `[TS]` LlmsTxtLink record | | TS: url, title, description |
| `[TS]` Section "## Optional" sémantique | | TS: Distinguer requis/optionnel |
| `[TS]` LlmsTxtContentFetcher async | | TS: Séparé du parsing |
| `[TS]` Support llms.txt ET llms-full.txt | | TS: Spec officielle llmstxt.org |
| `[TS]` Limite taille llms-full.txt | | TS: Éviter 3.7M tokens (Cloudflare) |

### 4.5 Formats de documents pour Alexandria.md
**Fichier:** Types de documents supportés
**Analysé:** 2026-01-05 | **Validé** - MVP formats définis

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Support Markdown (.md) | | TS: CommonMark-java 0.27.0 + GFM |
| `[TS]` Support texte brut (.txt) | | TS: TextDocumentParser Langchain4j |
| `[TS]` Support llms.txt / llms-full.txt | | TS: Parser custom |
| `[TS]` Support HTML basique | | TS: JsoupDocumentParser |
| `[TS]` Détection format par extension | | TS: Simple switch (pas SPI) |
| `[TS]` Extensions GFM (tables, YAML front matter) | | TS: commonmark-ext-gfm-tables |
| `[v2]` Interface SPI custom | | Reporté v2 - utiliser Langchain4j natif |
| `[v2]` PDF / AsciiDoc | | Reporté v2 - Phase 2 |

---

## 5. Base de données

### 5.1 Langchain4j et pgvector - guide complet d'initialisation du schéma PostgreSQL.md
**Fichier:** Schéma et index

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Table document_embeddings | | TS: Schéma SQL complet documenté |
| `[TS]` Index HNSW manuel | | TS: CREATE INDEX documenté |
| `[TS]` m=16, ef_construction=128 | | TS: Paramètres documentés |
| `[TS]` SET hnsw.ef_search=100 | | TS: Runtime settings documentés |
| `[TS]` SET hnsw.iterative_scan=on | | TS: Documenté |

### 5.2 Java 25 RAG server architecture validated for Spring Boot 3.5.9.md
**Fichier:** Architecture validée

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[x]` Stack validée dans tech-spec | | Validation exhaustive effectuée |
| `[x]` Versions mises à jour | | WireMock 3.13.2 |

---

## 6. Résilience & Timeouts

### 6.1 Stratégies de résilience pour Alexandria RAG Server.md
**Fichier:** Resilience4j 2.3.0 configuration (Spring Retry REFUSÉ - voir recommandations-refusees.md)
**Recherche:** R7.1 résolu - Resilience4j 2.3.0 choisi

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Dépendance resilience4j-spring-boot3:2.3.0 | | TS: Dans pom.xml via BOM |
| `[TS]` @Retry sur clients HTTP | | TS: Pattern @Retry documenté |
| `[ ]` @CircuitBreaker pour cold starts | | Non dans tech-spec (simplifié) |
| `[ ]` @Bulkhead(SEMAPHORE) | | Non dans tech-spec (simplifié) |
| `[TS]` Config YAML retry instances | | TS: Config infinityApi corrigée |
| `[ ]` Config YAML circuitbreaker instances | | Non dans tech-spec |
| `[TS]` Fallback methods | | TS: Pattern rerankFallback documenté |
| `[TS]` Health indicators | | TS: Actuator /actuator/retries configuré |

### 6.2 Resilience4j 2.3.0 pour Spring Boot 3.5.x.md (NOUVEAU)
**Fichier:** Validation technique complète Resilience4j
**Analysé:** 2026-01-05 | **Validé** - Toutes recommandations intégrées

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Artifact via BOM resilience4j-bom:2.3.0 | | TS: dependencyManagement corrigé |
| `[TS]` spring-boot-starter-aop OBLIGATOIRE | | TS: Dépendance ajoutée |
| `[TS]` spring-boot-starter-actuator recommandé | | TS: Dépendance ajoutée |
| `[TS]` enableExponentialBackoff: true | | TS: Config YAML corrigée |
| `[TS]` enableRandomizedWait: true pour jitter | | TS: Config YAML corrigée |
| `[TS]` SemaphoreBulkhead préféré VT | | Non requis (simplifié) |
| `[N/A]` RetryConfigCustomizer pour jitter | | YAML suffit avec enableRandomizedWait |

### 6.3 Guide technique Resilience4j 2.3.0 pour Alexandria RAG Server.md (NOUVEAU)
**Fichier:** Guide d'implémentation détaillé
**Analysé:** 2026-01-05 | **Validé** - Corrections appliquées

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` ResourceAccessException dans retryExceptions | | TS: Ajouté (timeouts/connexion) |
| `[TS]` ignoreExceptions: HttpClientErrorException | | TS: Ajouté (ne pas retry 4xx) |
| `[TS]` Fallback method pattern documenté | | TS: rerankFallback() exemple |
| `[TS]` Endpoints Actuator /actuator/retries | | TS: Config management ajoutée |
| `[TS]` Tests WireMock retry scenarios | | TS: Mentionné dans test patterns |
| `[TS]` Métriques Micrometer resilience4j_retry_calls | | TS: Via Actuator + BOM |

### 6.4 Optimal timeout configuration for Alexandria RAG pipeline.md
**Fichier:** Timeouts
**Analysé:** 2026-01-05 | **Validé** - Budget timeouts complet

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Global timeout 90s (cold) / 30s (warm) | | TS: Dual-tier strategy |
| `[TS]` Timeout embeddings: 30s | | TS: Cold start buffer |
| `[TS]` Timeout reranking: 30s | | TS: Cold start buffer |
| `[TS]` Timeout pgvector: 5s | | TS: HNSW rapide (5-50ms p95) |
| `[TS]` Timeout assembly: 1s | | TS: Local, rapide |
| `[TS]` JDK HttpClient (factory: jdk) | | TS: Virtual Threads compatible |
| `[TS]` Graceful degradation: skip rerank | | TS: Si temps insuffisant |
| `[TS]` RestClient beans séparés par service | | TS: Timeouts par client |
| `[v2]` Worker warming every 4 min | | Reporté v2 - coût $0.50-2/day |
| `[N/A]` spring.jpa.properties | | ❌ Alexandria = JDBC pur |
| `[N/A]` MCP_TOOL_TIMEOUT variable | | ❌ N'existe pas (R4.1) |

---

## 7. Sécurité

### 7.1 Sécuriser un serveur MCP Spring AI - guide complet pour mono-utilisateur.md
**Fichier:** Sécurité basique

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[ ]` Bearer token simple | | Non dans tech-spec |
| `[ ]` Validation token dans filtre | | Non dans tech-spec |
| `[TS]` Pas d'auth complexe (mono-user) | | TS: "Usage mono-utilisateur" dans scope |
| `[ ]` HTTPS en production | | Non spécifié |

---

## 8. Logging & Monitoring

### 8.1 Logging et Health Checks pour serveur RAG Spring Boot 3.5.x.md
**Fichier:** Observabilité
**Analysé:** 2026-01-05 | **Validé** - 6 items implémentés, 2 v2, 1 refusé

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Logging structuré ECS (logback-spring.xml) | | TS: StructuredLogEncoder natif Spring Boot 3.4+ |
| `[TS]` CorrelationIdFilter avec MDC | | TS: X-Correlation-Id header + MDC propagation |
| `[TS]` VirtualThreadConfig + TaskDecorator | | TS: MDC propagation pour Virtual Threads |
| `[TS]` InfinityEmbeddingHealthIndicator | | TS: /health endpoint + latence |
| `[TS]` RerankingHealthIndicator (simplifié) | | TS: /health simple (pas /rerank - coûts RunPod) |
| `[TS]` PgVectorHealthIndicator | | TS: Extension + table + index HNSW vérifié |
| `[TS]` Métriques embeddings/reranking | | TS: Resilience4j metrics Micrometer |
| `[TS]` Niveaux de log par environnement | | TS: dev=DEBUG, prod=WARN (sans refs Hibernate) |
| `[v2]` RagStatsInfoContributor | | Reporté v2 - nice-to-have stats /actuator/info |
| `[v2]` Port Actuator séparé 8081 | | Reporté v2 - évaluer besoin mono-user |
| `[N/A]` ActuatorSecurityConfig Spring Security | | ❌ Refusé - over-engineering MVP |

---

## 9. Tests

### 9.1 Testing Best Practices for RAG Spring Boot Servers.md
**Fichier:** Stratégie de tests
**Analysé:** 2026-01-05 | **Validé** - Pyramide 70/20/10

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` Pyramide 70% unit / 20% integ / 10% E2E | | TS: Répartition recommandée |
| `[TS]` PgVectorTestConfiguration reusable | | TS: @ServiceConnection pgvector:0.8.1-pg18 |
| `[TS]` WireMock stubs Infinity embeddings | | TS: /v1/embeddings + /v1/rerank |
| `[TS]` WireMock cold start simulation | | TS: withLogNormalRandomDelay(2000, 0.5) |
| `[TS]` MarkdownDocumentSplitter tests | | TS: Code blocks, tables, headers |
| `[TS]` EmbeddingFixtures generator 1024D | | TS: Vecteurs normalisés seeds |
| `[TS]` Tests retry Resilience4j | | TS: Scénarios WireMock + verify |
| `[v2]` Retrieval quality metrics CI/CD | | Reporté v2 - P@5, R@10, MRR |
| `[N/A]` @EnableRetry dans exemples | | ❌ Adapter pour Resilience4j |

### 9.2 Testing E2E des serveurs MCP avec Spring AI SDK 1.1.2.md
**Fichier:** Tests E2E MCP
**Analysé:** 2026-01-05 | **Validé** - McpSyncClient pattern

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[TS]` McpSyncClient pour tests intégration | | TS: WebFluxSseClientTransport |
| `[TS]` Test flow initialize → listTools → callTool | | TS: Pattern complet documenté |
| `[TS]` Validation JSON-RPC 2.0 structure | | TS: jsonrpc, id, result XOR error |
| `[TS]` Test concurrent connections | | TS: ExecutorService + CountDownLatch |
| `[TS]` McpTestSupport classe utilitaire | | TS: Helper methods réutilisables |
| `[TS]` WebTestClient + StepVerifier SSE | | TS: Pour tests flux SSE brut |
| `[v2]` Claude Code headless (-p) E2E | | Reporté v2 - CI/CD avancé |
| `[v2]` MCP Inspector validation | | Reporté v2 - npx @modelcontextprotocol/inspector |
| `[N/A]` SSE transport dans exemples | | ❌ Adapter pour HTTP Streamable |

### 9.3 Validation exhaustive de la stack Alexandria RAG Server.md
**Fichier:** Validation versions

| Recommandation | Statut | Notes |
|----------------|--------|-------|
| `[x]` Versions validées et mises à jour | | Tech-spec synchronisée |
| `[x]` Matrice compatibilité vérifiée | | Boot 3.5.9 + Langchain4j 1.10 |
| `[TS]` Planifier Java 25.0.2 | | TS: "prévu 20 jan 2026" documenté |

---

## Résumé de progression

| Catégorie | Total | Terminé | Tech-Spec | N/A/v2 | Restant |
|-----------|-------|---------|-----------|--------|---------|
| Configuration & Infrastructure | 21 | 0 | 14 | 6 | 1 |
| Transport MCP & Outils | 24 | 1 | 18 | 4 | 1 |
| Pipeline RAG | 24 | 0 | 20 | 4 | 0 |
| Ingestion & Chunking | 41 | 0 | 33 | 6 | 2 |
| Base de données | 7 | 2 | 5 | 0 | 0 |
| Résilience & Timeouts | 32 | 0 | 22 | 4 | 6 |
| Sécurité | 4 | 0 | 1 | 0 | 3 |
| Logging & Monitoring | 11 | 0 | 8 | 3 | 0 |
| Tests | 20 | 2 | 13 | 5 | 0 |
| **TOTAL** | **184** | **5** | **134** | **35** | **13** |

### État actuel

- **Phase 0 - Recherche & Planification:** ✅ TERMINÉE
  - 12/12 recherches complémentaires résolues
  - 7/7 conflits inter-fichiers résolus
  - Tech-spec WIP validé (étapes 1-2)
  - **10/10 rapports analysés** (2026-01-05) - Document Update Strategy ajouté

- **Phase 0.5 - Spécification (Tech-Spec):** ✅ 134/184 items (73%)
  - Structure projet définie
  - Dépendances Maven documentées (BOM Resilience4j + CommonMark + Picocli)
  - Schéma SQL pgvector complet
  - Patterns Resilience4j corrigés (enableExponentialBackoff, fallback)
  - Configuration Infinity API documentée
  - Actuator endpoints configurés (/actuator/retries)
  - Pipeline RAG configuré (Top-K/Top-N, seuils, RagProperties.java)
  - No-results handling (QueryValidator, McpSearchResponse, tiered confidence)
  - **MCP responses** (dual-format, truncation, isError pattern)
  - **Chunking complet** (code blocks atomiques, breadcrumbs, YAML extraction)
  - **Timeouts budgétés** (90s cold / 30s warm, graceful degradation)
  - **Tests patterns** (WireMock stubs, McpSyncClient, Testcontainers)
  - **Logging & Monitoring** (ECS structuré, CorrelationId, HealthIndicators, MDC Virtual Threads)
  - **Document Update Strategy** (identification hybride, DELETE+INSERT, index B-tree)

- **Phase 1-4 - Implémentation:** ⏳ NON DÉMARRÉE
  - Aucun fichier source Java créé
  - Aucun pom.xml créé
  - Aucun application.yml créé
  - **13 items restent à spécifier ou sont optionnels**
  - **14 items reportés à v2** (voir liste ci-dessous)

---

## Notes d'implémentation

### Prochaine étape recommandée

**Option A - Compléter le tech-spec (45 items restants):**
- Pipeline RAG: paramètres top-K, seuils, gestion résultats vides
- Résilience: CircuitBreaker, Bulkhead, fallbacks, timeouts détaillés
- Sécurité: Bearer token, filtre auth
- Logging: Health checks, structured logging

**Option B - Démarrer l'implémentation (58 items prêts):**
- Les items `[TS]` sont suffisamment spécifiés pour coder
- Commencer par Phase 1 (Fondations)

### Phases d'implémentation

1. **Phase 1 - Fondations** (prochaine)
   - [ ] Créer structure projet Maven (pom.xml)
   - [ ] Configuration application.yml
   - [ ] Schéma PostgreSQL + index HNSW
   - [ ] Bean PgVectorEmbeddingStore
   - [ ] Configuration Resilience4j 2.3.0

2. **Phase 2 - Pipeline RAG**
   - [ ] AlexandriaMarkdownSplitter
   - [ ] InfinityEmbeddingModel
   - [ ] InfinityRerankClient
   - [ ] RetrievalService

3. **Phase 3 - Outils MCP**
   - [ ] McpTools.java (search, ingest, list, delete)
   - [ ] Format réponses
   - [ ] Gestion erreurs

4. **Phase 4 - Tests & Polish**
   - [ ] Tests unitaires
   - [ ] Tests intégration Testcontainers
   - [ ] Tests E2E MCP
   - [ ] Docker packaging

### Dépendances critiques

- `Bean PgVectorEmbeddingStore` → Langchain4j n'a pas de starter, config manuelle requise
- `Index HNSW` → Créer manuellement, Langchain4j crée IVFFlat par défaut
- `Resilience4j 2.3.0` → Support natif Virtual Threads, remplace Spring Retry
- `Reranking client` → Format Cohere, pas OpenAI - client HTTP custom requis

### Décisions architecturales clés (issues des recherches)

| Décision | Choix | Justification |
|----------|-------|---------------|
| **Persistence** | JDBC pur | Langchain4j PgVectorEmbeddingStore = JDBC, pas JPA |
| **Résilience** | Resilience4j 2.3.0 | Virtual Threads natif, Circuit Breaker intégré |
| **Annotations MCP** | `@McpTool` | `org.springaicommunity.mcp.annotation` pour progress |
| **Context MCP** | `McpSyncRequestContext` | Remplace deprecated `McpSyncServerExchange` |
| **Dotenv** | spring-dotenv 5.1.0 | Relaxed binding pour `UPPERCASE_UNDERSCORE` |
| **PGDATA PG18** | `/var/lib/postgresql` | Breaking change PostgreSQL 18 Docker |
| **prepareThreshold** | Default (5) | `=0` non requis pour pgvector |
| **Cold starts RunPod** | 120-180s + retry | 10-30s réel, Resilience4j gère |

### Imports à utiliser

```java
// Annotations MCP
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

// Context et résultats
import org.springframework.ai.mcp.server.McpSyncRequestContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
```
