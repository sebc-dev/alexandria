# Recommandations Refusées / Points Problématiques

**Créé:** 2026-01-05
**Mis à jour:** 2026-01-05
**Objectif:** Centraliser les recommandations à NE PAS appliquer car erronées, obsolètes ou incohérentes
**Statut:** 9 conflits résolus, 22 points à corriger dans tech-spec (ajout Document Update Strategy)

---

## Légende

| Statut | Description |
|--------|-------------|
| **REFUSÉ** | Ne pas appliquer - erreur factuelle ou obsolète |
| **REMPLACÉ** | Supplanté par une recommandation plus récente |
| **CONFLIT** | Incohérence entre fichiers - décision requise |

---

## Fichier 1: Configuration complète pour Alexandria RAG Server.md

### P1.1 - Transport SSE (REMPLACÉ)
- **Recommandation originale:** `sse-endpoint: /sse`, `sse-message-endpoint: /mcp/message`
- **Statut:** REMPLACÉ
- **Raison:** Le fichier #4 établit clairement que SSE est **déprécié** depuis MCP spec 2025-03-26. HTTP Streamable (`protocol: STREAMABLE`, endpoint unique `/mcp`) est la norme.
- **Action:** Ignorer toute configuration SSE du fichier #1, utiliser fichier #4 comme référence.

### P1.2 - Index IVFFlat via `index-list-size` (REFUSÉ)
- **Recommandation originale:** `alexandria.pgvector.index-list-size: 100`
- **Statut:** REFUSÉ
- **Raison:** Le fichier #16 recommande un index **HNSW** (pas IVFFlat). Langchain4j crée IVFFlat par défaut mais HNSW est supérieur pour RAG. Cette property configure IVFFlat qui ne sera pas utilisé.
- **Action:** Supprimer cette property, créer index HNSW manuellement selon fichier #16.

### P1.3 - Property YAML `metadata-storage` (REFUSÉ)
- **Recommandation originale:** `alexandria.pgvector.metadata-storage: COMBINED_JSONB`
- **Statut:** REFUSÉ
- **Raison:** Cette syntaxe YAML est invalide. `MetadataStorageConfig.combinedJsonb()` est une méthode Java, pas une property Spring Boot.
- **Action:** Supprimer du YAML, configurer uniquement dans `PgVectorConfig.java` via `.metadataStorageConfig(MetadataStorageConfig.combinedJsonb())`.

---

## Fichier 3: Packaging Docker pour Java 25 et Spring Boot 3.5.9.md

### P3.1 - Dimension embedding 1536 (REFUSÉ)
- **Recommandation originale:** `embedding vector(1536)` dans script SQL
- **Statut:** REFUSÉ
- **Raison:** **Erreur factuelle**. BGE-M3 produit des embeddings de **1024 dimensions**, pas 1536 (qui est OpenAI ada-002).
- **Action:** Corriger à `vector(1024)` dans tous les scripts SQL.

### P3.2 - Index HNSW ef_construction=64 (CONFLIT)
- **Recommandation originale:** `WITH (m = 16, ef_construction = 64)`
- **Statut:** CONFLIT
- **Raison:** Le fichier #16 recommande `ef_construction = 128` pour de meilleures performances avec 1024 dimensions.
- **Action:** Utiliser `ef_construction = 128` selon fichier #16.

### P3.3 - PGDATA path PostgreSQL 18 (CONFIRMÉ)
- **Recommandation originale:** `PGDATA: /var/lib/postgresql/18/docker`
- **Statut:** ✅ CONFIRMÉ par recherche R3.1
- **Raison:** Le changement est **réel** - PostgreSQL 18 Docker utilise `/var/lib/postgresql/18/docker` (breaking change Juin 2025)
- **Action:**
  - Volume mount à `/var/lib/postgresql` (PAS `/var/lib/postgresql/data`)
  - Migration requise pour volumes existants de PostgreSQL 17
- **Recherche:** `PGDATA path changed in PostgreSQL 18 Docker images.md`

---

## Fichier 5: MCP Tool Patterns for RAG Servers.md

### P5.1 - Configuration SSE dans exemple YAML (REMPLACÉ)
- **Recommandation originale:** Endpoints `/sse` et `/mcp/message`
- **Statut:** REMPLACÉ
- **Raison:** Même problème que P1.1 - SSE déprécié.
- **Action:** Ignorer section config YAML, utiliser HTTP Streamable.

---

## Fichier 7: Gestion des erreurs MCP.md

### P7.1 - Resilience4j configuration (RÉSOLU)
- **Recommandation originale:** Configuration complète Resilience4j avec circuit breaker
- **Statut:** ✅ RÉSOLU - Conflit résolu par recherche R7.1
- **Raison:** Le fichier #1 utilisait **Spring Retry** (`@Retryable`), le fichier #7 recommandait **Resilience4j**.
- **Décision:** **Resilience4j 2.3.0** choisi
- **Justification:**
  - Support natif Virtual Threads (pas de config manuelle)
  - Spring Retry en mode maintenance only
  - Circuit Breaker intégré pour cold starts RunPod
- **Recherche:** `Resilience4j remporte le match pour Java 25 Virtual Threads.md`

---

## Fichier 11: Stratégies de résilience pour Alexandria RAG Server.md

### P11.1 - Spring Retry (REFUSÉ)
- **Recommandation originale:** Utiliser `@Retryable` avec `@EnableRetry` et Spring Retry 2.0.x
- **Statut:** REFUSÉ
- **Raison:** Recherche comparative approfondie (voir `Resilience4j remporte le match pour Java 25 Virtual Threads.md`):
  1. Spring Retry est en mode **maintenance only** (supplanté par Spring Framework 7)
  2. Nécessite configuration manuelle `RetrySynchronizationManager.setUseThreadLocal(false)` pour Virtual Threads
  3. Pas de Circuit Breaker intégré (critique pour cold starts RunPod 30-60s)
- **Action:** Utiliser **Resilience4j 2.3.0** avec `resilience4j-spring-boot3` starter
- **Configuration retenue:**
  - `@CircuitBreaker` + `@Retry` + `@Bulkhead(SEMAPHORE)` sur InfinityClient
  - Configuration YAML externalisée
  - Support natif Virtual Threads sans configuration

---

## Fichier: Configuration complète pour Alexandria RAG Server.md (suite)

### P1.4 - Parameter JDBC `prepareThreshold=0` (REFUSÉ)
- **Recommandation originale:** `jdbc:postgresql://...?prepareThreshold=0`
- **Statut:** REFUSÉ
- **Raison:** Recherche approfondie R1.2 établit que:
  1. **pgvector n'a PAS de requirement** spécifique pour `prepareThreshold=0`
  2. Désactiver prepared statements coûte **~87% de throughput**
  3. pgvector 0.8.1 a amélioré l'estimation des coûts HNSW
  4. Seul cas valide: legacy PgBouncer < 1.21 en transaction mode
- **Action:** Garder `prepareThreshold=5` (default). Si problèmes index HNSW, utiliser `plan_cache_mode=force_custom_plan`
- **Recherche:** `PostgreSQL JDBC prepareThreshold=0: when to use it and why it matters for pgvector.md`

---

## Résumé des Conflits Inter-Fichiers

| Aspect | Source A | Source B | Résolution |
|--------|----------|----------|------------|
| Transport MCP | Fichier #1 (SSE) | Fichier #4 (HTTP Streamable) | **Fichier #4 gagne** ✅ |
| Type d'index | Fichier #1 (IVFFlat) | Fichier #16 (HNSW) | **Fichier #16 gagne** ✅ |
| Dimension embedding | Fichier #3 (1536) | Tous autres (1024) | **1024 correct** ✅ |
| ef_construction | Fichier #3 (64) | Fichier #16 (128) | **Fichier #16 gagne** ✅ |
| Résilience | Fichier #1 (Spring Retry) | Fichier #7 (Resilience4j) | **Resilience4j 2.3.0** ✅ |
| PGDATA PostgreSQL 18 | Legacy `/data` | Nouveau `/18/docker` | **Nouveau path confirmé** ✅ |
| prepareThreshold | Fichier #1 (=0) | Recherche R1.2 | **Default (=5)** ✅ |
| Spring AI version | Tech-spec (1.1.2) | Recherche R22.2 | **1.1.1** ✅ |
| Sécurité MCP | mcp-server-security | Recherche R22.3 | **Filtre custom** ✅ |

---

## Fichier 22: Sécuriser un serveur MCP Spring AI.md

### P22.1 - Version Spring AI 1.1.2 (REFUSÉ - CRITIQUE)
- **Recommandation originale:** Utiliser `spring-ai.version: 1.1.2` dans le tech-spec
- **Statut:** 🔴 **REFUSÉ - ERREUR FACTUELLE CRITIQUE**
- **Raison:** **Spring AI 1.1.2 N'EXISTE PAS sur Maven Central!**
  - Version actuelle: **1.1.1** (5 décembre 2025)
  - Version précédente: 1.1.0 (12 novembre 2025)
  - 1.1.2 est une version PLANIFIÉE (milestone pour fix Kotlin #5045)
- **Action:** Corriger **immédiatement** tout le tech-spec → `spring-ai.version: 1.1.1`
- **Recherche:** `MCP Server Security compatibility with Spring Boot 3.5.9 and Spring AI.md`

### P22.2 - mcp-server-security 0.0.5 pour production (REFUSÉ)
- **Recommandation originale:** Utiliser `org.springaicommunity:mcp-server-security:0.0.5` pour auth API Key
- **Statut:** REFUSÉ
- **Raison:** Module **NOT PRODUCTION-READY**:
  1. Version 0.0.x = alpha, APIs instables et peuvent changer
  2. Disclaimers explicites: "Work in progress", "Community-driven, not officially endorsed"
  3. **Bug connu:** Issue #2506 - Authentication perdue dans tool execution
  4. Overkill pour use case mono-utilisateur Alexandria
- **Action:**
  - MVP localhost: Pas d'auth (déjà prévu)
  - Si LAN nécessaire: Implémenter filtre custom `OncePerRequestFilter` (~50 lignes)
- **Recherche:** `MCP-Server-Security: A Work-in-Progress Security Library for Spring AI.md`

### P22.3 - OAuth 2.0 pour serveur MCP mono-utilisateur (REFUSÉ)
- **Recommandation originale:** Implémenter OAuth 2.0 avec `McpServerOAuth2Configurer`
- **Statut:** REFUSÉ
- **Raison:** Over-engineering massif pour use case:
  1. Alexandria = serveur mono-utilisateur local
  2. OAuth 2.0 nécessite Authorization Server externe (Keycloak, etc.)
  3. Complexité disproportionnée vs valeur ajoutée
  4. Non pertinent pour déploiement localhost/Docker bridge
- **Action:** Ne jamais implémenter OAuth 2.0 sauf exposition Internet (hors scope MVP)
- **Recherche:** `Sécuriser un serveur MCP Spring AI - guide complet pour mono-utilisateur.md`

---

## Analyse Rapports Partiels (2026-01-05)

### P-MCP.1 - Spring Retry dans exemples erreurs MCP (REFUSÉ)
- **Source:** Gestion des erreurs MCP pour un serveur RAG Spring AI.md
- **Recommandation originale:** Utilisation de `@Retryable` et `timelimiter` Spring Retry
- **Statut:** REFUSÉ
- **Raison:** Décision déjà prise pour Resilience4j 2.3.0 (voir P7.1, P11.1)
- **Action:** Ignorer les exemples Spring Retry, adapter le pattern pour Resilience4j

### P-META.1 - Exemple PgVectorEmbeddingStore avec IVFFlat (REFUSÉ)
- **Source:** Métadonnées RAG optimales pour le projet Alexandria.md
- **Recommandation originale:** `.indexListSize(100)` et dimension `1536`
- **Statut:** REFUSÉ
- **Raison:** Même problème que P1.2 et P3.1 - HNSW manuel + dimension 1024
- **Action:** Ignorer l'exemple de config, utiliser pattern corrigé

### P-ING.1 - Transport SSE dans exemples ingestion (REFUSÉ)
- **Source:** Stratégies d'ingestion pour serveur RAG MCP Alexandria.md
- **Recommandation originale:** `sse-endpoint: /sse` dans config YAML
- **Statut:** REFUSÉ (REMPLACÉ)
- **Raison:** SSE déprécié, utiliser HTTP Streamable
- **Action:** Remplacer par config HTTP Streamable `/mcp`

### P-TIMEOUT.1 - Properties JPA dans exemples timeouts (REFUSÉ)
- **Source:** Optimal timeout configuration for Alexandria RAG pipeline.md
- **Recommandation originale:** `spring.jpa.properties.javax.persistence.query.timeout`
- **Statut:** REFUSÉ
- **Raison:** Alexandria utilise JDBC pur via Langchain4j, pas JPA
- **Action:** Configurer timeouts via JDBC statement_timeout

### P-TIMEOUT.2 - Variable MCP_TOOL_TIMEOUT (REFUSÉ)
- **Source:** Optimal timeout configuration for Alexandria RAG pipeline.md
- **Recommandation originale:** `MCP_TOOL_TIMEOUT: 120000` dans settings.json
- **Statut:** REFUSÉ
- **Raison:** Recherche R4.1 confirme que cette variable n'existe pas et est ignorée
- **Action:** Supprimer de la documentation

### P-TEST.1 - @EnableRetry dans tests (REFUSÉ)
- **Source:** Testing Best Practices for RAG Spring Boot Servers.md
- **Recommandation originale:** `@EnableRetry` et `RetryTemplate` Spring Retry
- **Statut:** REFUSÉ
- **Raison:** Utiliser Resilience4j pour tous les tests de retry
- **Action:** Adapter exemples pour `@Retry` Resilience4j + WireMock scenarios

### P-TEST.2 - SSE transport dans tests E2E (REFUSÉ)
- **Source:** Testing E2E des serveurs MCP avec Spring AI SDK 1.1.2.md
- **Recommandation originale:** `WebFluxSseClientTransport` pour tests
- **Statut:** REFUSÉ (ADAPTER)
- **Raison:** Serveur utilise HTTP Streamable, adapter transport client
- **Action:** Utiliser transport HTTP Streamable dans McpClient.sync()

---

## Points à Supprimer du Tech-Spec

Lors de la rédaction finale du tech-spec, supprimer/corriger:

### Configuration
1. [x] Toute référence au transport SSE → HTTP Streamable
2. [x] Property `index-list-size` (IVFFlat) → HNSW manuel
3. [x] Property YAML `metadata-storage` → config Java
4. [x] Dimension 1536 → 1024
5. [x] ef_construction 64 → 128
6. [x] Paramètre JDBC `prepareThreshold=0` → default (5)
7. [x] Volume mount legacy `/var/lib/postgresql/data` → `/var/lib/postgresql`
8. [x] Dépendance `spring-boot-starter-data-jpa` → starter-jdbc
9. [x] Properties `spring.jpa.*` → JDBC pur
10. [x] Annotations `@Retryable` (Spring Retry) → Resilience4j

### Ajouts Analyse Partiels (2026-01-05)
11. [ ] Exemples timeout JPA → statement_timeout JDBC
12. [ ] Variable MCP_TOOL_TIMEOUT → supprimer
13. [ ] Transport SSE dans tests → HTTP Streamable
14. [ ] @EnableRetry dans tests → @Retry Resilience4j
15. [ ] indexListSize(100) dans exemples → supprimer
16. [ ] WebFluxSseClientTransport tests → adapter transport
17. [ ] timelimiter Spring Retry → Resilience4j TimeLimiter

### Ajouts Analyse Sécurité (2026-01-05)
18. [x] 🔴 **CRITIQUE:** Spring AI 1.1.2 → **1.1.1** (partout dans tech-spec) ✅
19. [x] Supprimer toute référence à mcp-server-security pour MVP ✅ (jamais ajouté)

### Ajouts Analyse Logging & Monitoring (2026-01-05)
20. [x] Refs `org.hibernate.*` dans niveaux de log → Alexandria = JDBC pur
21. [x] ActuatorSecurityConfig avec Spring Security → over-engineering MVP

---

## Fichier 8.1: Logging et Health Checks pour serveur RAG Spring Boot 3.5.x.md

### P8.1 - ActuatorSecurityConfig avec Spring Security (REFUSÉ)
- **Recommandation originale:** Implémenter `ActuatorSecurityConfig` avec `SecurityFilterChain` Spring Security
- **Statut:** REFUSÉ
- **Raison:** Over-engineering pour MVP mono-utilisateur:
  1. Spring Security non prévu dans les dépendances MVP
  2. Actuator sur localhost suffisant pour mono-user
  3. Complexité disproportionnée vs valeur ajoutée
- **Action:** Si besoin LAN, utiliser filtre custom `OncePerRequestFilter` (~50 lignes)

### P8.2 - Références Hibernate dans niveaux de log (REFUSÉ)
- **Recommandation originale:** Configurer `org.hibernate.SQL`, `org.hibernate.orm.jdbc.bind`, `org.hibernate.stat`
- **Statut:** REFUSÉ
- **Raison:** Alexandria utilise JDBC pur via Langchain4j PgVectorEmbeddingStore, pas JPA/Hibernate
- **Action:** Supprimer toutes références Hibernate des niveaux de log

### P8.3 - Timeouts embedding/reranking 5s/15s (CONFLIT RÉSOLU)
- **Recommandation originale:** `timeout-embedding-ms: 5000`, `timeout-reranking-ms: 15000`
- **Statut:** CONFLIT RÉSOLU
- **Raison:** Tech-spec utilise 30s pour cold starts RunPod (recherche R1.3)
- **Action:** Garder timeouts 30s du tech-spec (cold start 10-30s réel)

---

## Fichier 12: Stratégies de mise à jour des documents dans un RAG PostgreSQL pgvector avec Langchain4j.md

### P12.1 - `.indexListSize(100)` dans exemple config (REFUSÉ)
- **Recommandation originale:** `.indexListSize(100)` dans configuration PgVectorEmbeddingStore
- **Statut:** REFUSÉ
- **Raison:** Cette option configure un index **IVFFlat**, pas HNSW. Décision architecturale prise pour HNSW manuel (voir P1.2, P3.2).
- **Action:** Ignorer l'exemple, créer index HNSW manuellement

### P12.2 - Dimension `1536` dans exemple (REFUSÉ)
- **Recommandation originale:** `.dimension(1536)` dans configuration
- **Statut:** REFUSÉ
- **Raison:** Dimension OpenAI ada-002, pas BGE-M3. Alexandria utilise **1024 dimensions**.
- **Action:** Corriger à `.dimension(1024)`

### P12.3 - Modèle `text-embedding-3-small` dans exemple (REFUSÉ)
- **Recommandation originale:** `"embedding_model": "text-embedding-3-small"` dans metadata
- **Statut:** REFUSÉ
- **Raison:** Modèle OpenAI, pas pertinent. Alexandria utilise **BGE-M3**.
- **Action:** Adapter metadata pour utiliser "BAAI/bge-m3"

---
