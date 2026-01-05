# Recherches Complémentaires - Analyse des Recommandations

**Créé:** 2026-01-05
**Mis à jour:** 2026-01-05 (ajout R-TS.1-R-TS.3 Validation Cohérence)
**Objectif:** Centraliser les points nécessitant des investigations supplémentaires avant implémentation

---

## Légende Priorité

| Priorité | Description |
|----------|-------------|
| 🔴 **BLOQUANT** | Doit être résolu avant implémentation |
| 🟠 **IMPORTANT** | Impact significatif sur l'architecture |
| 🟡 **MODÉRÉ** | Optimisation ou clarification |
| 🟢 **FAIBLE** | Nice-to-have, peut être reporté |

---

## Fichier 1: Configuration complète pour Alexandria RAG Server.md

### R1.1 - Syntaxe `.env` Spring Boot 3.5.x
- **Priorité:** 🟠 IMPORTANT
- **Question:** La syntaxe `spring.config.import: optional:file:.env[.properties]` fonctionne-t-elle nativement dans Spring Boot 3.5.x ou nécessite-t-elle une dépendance (spring-dotenv) ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Spring Boot 3.5.x does support .env files natively—with significant caveats.md`
- **Résolution:**
  - La syntaxe native `optional:file:.env[.properties]` **FONCTIONNE** mais sans relaxed binding
  - Les clés doivent être en format `dot.notation` (ex: `spring.datasource.url`)
  - Format `UPPERCASE_UNDERSCORE` (ex: `SPRING_DATASOURCE_URL`) **NON SUPPORTÉ** nativement
  - **Recommandation:** Utiliser **spring-dotenv 5.1.0** pour support complet `.env` standard

### R1.2 - Paramètre JDBC `prepareThreshold=0`
- **Priorité:** 🟡 MODÉRÉ
- **Question:** Pourquoi désactiver les prepared statements côté serveur PostgreSQL ? Est-ce spécifique à pgvector ou une erreur ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `PostgreSQL JDBC prepareThreshold=0: when to use it and why it matters for pgvector.md`
- **Résolution:**
  - **pgvector n'a PAS de requirement spécifique** pour `prepareThreshold=0`
  - Désactiver coûte **~87% de throughput** - anti-pattern sauf legacy PgBouncer <1.21
  - pgvector 0.8.1 a amélioré l'estimation des coûts HNSW
  - **Recommandation:** Garder `prepareThreshold=5` (default). Si problèmes index HNSW, utiliser `plan_cache_mode=force_custom_plan`

### R1.3 - Timeouts RunPod réels
- **Priorité:** 🟡 MODÉRÉ
- **Question:** Quel est le temps de cold start réel de l'endpoint RunPod Infinity ? 300s read timeout est-il justifié ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `RunPod Serverless cold starts: Optimizing timeouts for RAG inference.md`
- **Résolution:**
  - **Cold start BGE-M3 + reranker:** 10-30s (modèles baked in Docker + FlashBoot)
  - **Worst case:** 60+ secondes (modèles téléchargés au démarrage)
  - **Max HTTP timeout RunPod:** 300s supporté via `?wait=300000`
  - **Recommandation:** Read timeout 120-180s + Resilience4j retry (6 attempts, exponential backoff)

---

## Fichier 2: Configuration par variables d'environnement dans Spring Boot 3.5.x.md

### R2.1 - Version spring-dotenv compatible Spring Boot 3.5.x
- **Priorité:** 🟡 MODÉRÉ
- **Question:** La version `5.0.1` de `springboot3-dotenv` est-elle compatible avec Spring Boot 3.5.x ? Y a-t-il une version plus récente ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Spring-dotenv 5.1.0: complete guide for Spring Boot 3.5.x.md`
- **Résolution:**
  - **Version actuelle:** 5.1.0 (Jan 2, 2026)
  - **Artifact:** `me.paulschwarz:springboot3-dotenv:5.1.0`
  - **Compatibilité:** Spring Boot 3.5.x + Java 25 confirmée
  - **BOM disponible:** `me.paulschwarz:spring-dotenv-bom:5.1.0`

### R2.2 - Stack persistence Alexandria: JPA ou JDBC pur ?
- **Priorité:** 🟠 IMPORTANT
- **Question:** Alexandria utilise-t-elle Hibernate/JPA ou JDBC pur avec Langchain4j PgVectorEmbeddingStore ? La config `jpa.hibernate.ddl-auto` est-elle pertinente ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Langchain4j PgVectorEmbeddingStore uses pure JDBC, not JPA.md`
- **Résolution:**
  - **Langchain4j PgVectorEmbeddingStore = JDBC PUR** via `com.pgvector:pgvector` + PostgreSQL JDBC driver
  - **Aucun starter officiel** `langchain4j-pgvector-spring-boot-starter` (GitHub Issue #2102 ouvert)
  - **Actions requises:**
    - ❌ Supprimer `spring-boot-starter-data-jpa` si pas d'autres entités JPA
    - ✅ Garder `spring-boot-starter-jdbc` pour DataSource/HikariCP
    - ❌ Supprimer toutes properties `spring.jpa.*`
    - ✅ Créer `@Configuration` manuelle avec bean `PgVectorEmbeddingStore`

---

## Fichier 3: Packaging Docker pour Java 25 et Spring Boot 3.5.9.md

### R3.1 - PGDATA PostgreSQL 18 dans image pgvector
- **Priorité:** 🟠 IMPORTANT
- **Question:** L'image `pgvector/pgvector:0.8.1-pg18` utilise-t-elle vraiment `/var/lib/postgresql/18/docker` comme PGDATA ou le path standard `/var/lib/postgresql/data` ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `PGDATA path changed in PostgreSQL 18 Docker images.md`
- **Résolution:**
  - **PGDATA PostgreSQL 18:** `/var/lib/postgresql/18/docker` (CHANGÉ!)
  - **Volume mount:** `/var/lib/postgresql` (PAS `/var/lib/postgresql/data`)
  - **Breaking change** introduit en PostgreSQL 18 (Juin 2025)
  - **Migration requise** pour volumes existants de PostgreSQL 17

### R3.2 - Syntax Docker Compose depends_on condition
- **Priorité:** 🟢 FAIBLE
- **Question:** La syntaxe `depends_on: service_healthy: condition` est-elle toujours supportée dans Docker Compose v2.x (2025+) ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Docker Compose v2.x health check dependency syntax guide.md`
- **Résolution:**
  - **Syntaxe stable et recommandée** dans Compose v2.x/v5.x
  - **Conditions valides:** `service_started`, `service_healthy`, `service_completed_successfully`
  - **Nouveautés v2.17+:** `restart: true`, `required: false`
  - Aucune dépréciation prévue

---

## Fichier 4: Support complet dans Spring AI MCP SDK 1.1.2.md

### R4.1 - Variables d'environnement timeout Claude Code
- **Priorité:** 🟡 MODÉRÉ
- **Question:** Les variables `MCP_TIMEOUT` et `MCP_TOOL_TIMEOUT` mentionnées sont-elles documentées officiellement par Anthropic pour Claude Code ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `MCP timeout environment variables in Claude Code: one documented, one not.md`
- **Résolution:**
  - **`MCP_TIMEOUT`:** Documenté officiellement - contrôle startup/connection timeout
  - **`MCP_TOOL_TIMEOUT`:** N'EXISTE PAS - non fonctionnel, ignoré par Claude Code
  - **Bug connu:** Hard cap 60s sur MCP_TIMEOUT (GitHub #7575)
  - **Alternative:** `spring.ai.mcp.client.request-timeout` côté serveur

### R4.2 - API McpSyncServerExchange pour notifications de progression
- **Priorité:** 🟡 MODÉRÉ
- **Question:** L'API `McpSyncServerExchange.progressNotification()` est-elle stable dans Spring AI MCP 1.1.2 ? Quelle est la signature exacte ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Spring AI MCP SDK 1.1.2 progress notification API.md`
- **Résolution:**
  - **`McpSyncServerExchange`:** DEPRECATED mais fonctionnel
  - **Recommandé:** `McpSyncRequestContext` avec `context.progress()`
  - **Signature:** `void progressNotification(ProgressNotification notification)`
  - **Token:** Via `@McpProgressToken String progressToken` (peut être null)
  - **Champs:** `progressToken`, `progress` (double), `total` (Double), `message` (String)

---

## Fichiers 5-7: MCP Tools, Response Formats, Error Handling

### R5.1 - Package imports Spring AI MCP annotations
- **Priorité:** 🟠 IMPORTANT
- **Question:** Les imports exacts pour `@McpTool`, `@McpToolParam`, `CallToolResult` - sont-ils dans `io.modelcontextprotocol.server.*` ou `org.springframework.ai.mcp.*` ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Spring AI MCP SDK 1.1.2 GA: Correct Package Imports.md`
- **Résolution:**
  - **DEUX SYSTÈMES D'ANNOTATIONS:**
    1. **Standard `@Tool`:** `org.springframework.ai.tool.annotation.Tool` - auto-converti en MCP
    2. **MCP-spécifique `@McpTool`:** `org.springaicommunity.mcp.annotation.McpTool` - features avancées
  - **`CallToolResult`:** `io.modelcontextprotocol.spec.McpSchema.CallToolResult`
  - **Recommandation Alexandria:** `@McpTool` pour progress notifications sur ingestion longue

### R5.2 - McpSyncRequestContext vs McpSyncServerExchange
- **Priorité:** 🟡 MODÉRÉ
- **Question:** Les fichiers 5-7 mentionnent `McpSyncRequestContext` tandis que le fichier 4 mentionne `McpSyncServerExchange`. Quel est le nom correct de la classe ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Spring AI MCP SDK 1.1.x request context class reference.md`
- **Résolution:**
  - **`McpSyncRequestContext`:** RECOMMANDÉ - interface unifiée stateful/stateless
  - **`McpSyncServerExchange`:** DEPRECATED - legacy, encore fonctionnel
  - **Package:** `org.springframework.ai.mcp.server.McpSyncRequestContext`
  - **Injection:** Paramètre auto-injecté dans méthodes `@McpTool`

### R7.1 - Resilience4j vs Spring Retry
- **Priorité:** 🔴 BLOQUANT
- **Question:** Le fichier 1 utilise Spring Retry (`@Retryable`), le fichier 7 recommande Resilience4j. Quelle stratégie adopter ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Resilience4j remporte le match pour Java 25 Virtual Threads.md`
- **Justification:**
  - Support natif Virtual Threads (pas de config manuelle vs `RetrySynchronizationManager` pour Spring Retry)
  - Spring Retry en mode **maintenance only** (supplanté par Spring Framework 7)
  - Circuit Breaker intégré essentiel pour cold starts RunPod (30-60s)
  - Configuration YAML externalisée sans recompilation
- **Décision:** **Resilience4j 2.3.0**

### R7.2 - Property spring.ai.tools.throw-exception-on-error
- **Priorité:** 🟡 MODÉRÉ
- **Question:** La property `spring.ai.tools.throw-exception-on-error=false` existe-t-elle dans Spring AI 1.1.2 ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Spring AI 1.1.2 tool exception handling explained.md`
- **Résolution:**
  - **EXISTE** avec default `false` (erreurs envoyées au modèle AI)
  - **MCP tools:** Mécanisme séparé via mcp-annotations → `CallToolResult.isError(true)`
  - **Recommandation:** Garder default, retourner messages d'erreur descriptifs

---

## Résumé Global

### État des recherches

| Catégorie | Questions | Résolues | En attente |
|-----------|-----------|----------|------------|
| Configuration | R1.1, R1.2, R1.3 | 3/3 ✅ | - |
| Variables d'environnement | R2.1, R2.2 | 2/2 ✅ | - |
| Docker/Packaging | R3.1, R3.2 | 2/2 ✅ | - |
| MCP SDK | R4.1, R4.2 | 2/2 ✅ | - |
| MCP Tools | R5.1, R5.2, R7.1, R7.2 | 4/4 ✅ | - |
| Sécurité | R22.1, R22.2, R22.3 | 3/3 ✅ | - |
| **Validation Tech-Spec** | R-TS.1, R-TS.2, R-TS.3 | 3/3 ✅ | - |
| **TOTAL** | **18** | **18/18 ✅** | **0** |

### Décisions architecturales clés

1. **Persistence:** JDBC pur (pas JPA) - supprimer `spring-boot-starter-data-jpa`
2. **Résilience:** Resilience4j 2.3.0 (pas Spring Retry)
3. **Annotations MCP:** `@McpTool` de `org.springaicommunity.mcp.annotation`
4. **Context MCP:** `McpSyncRequestContext` (pas deprecated `McpSyncServerExchange`)
5. **Dotenv:** spring-dotenv 5.1.0 pour support complet `.env`
6. **PGDATA:** Volume mount à `/var/lib/postgresql` pour PostgreSQL 18
7. **Spring AI:** Version **1.1.2** GA (9 décembre 2025)
8. **Sécurité MVP:** Pas d'auth (localhost). Si LAN: filtre custom `OncePerRequestFilter`

---

## Fichier 22: Sécuriser un serveur MCP Spring AI - guide complet pour mono-utilisateur.md

### R22.1 - Version actuelle mcp-server-security
- **Priorité:** 🟡 MODÉRÉ
- **Question:** Le module `org.springaicommunity:mcp-server-security:0.0.5` est-il la version actuelle ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `MCP-Server-Security: A Work-in-Progress Security Library for Spring AI.md`
- **Résolution:**
  - **Version 0.0.5** confirmée (27 novembre 2025) - version courante Maven Central
  - Repository: `github.com/spring-ai-community/mcp-security` (57 stars, 4 contributors)
  - **STATUT: NOT PRODUCTION-READY** (0.0.x = alpha, APIs instables)
  - Disclaimers explicites: "Work in progress", "Community-driven, not officially endorsed"

### R22.2 - Compatibilité mcp-server-security avec Spring Boot 3.5.9
- **Priorité:** 🟠 IMPORTANT
- **Question:** Le module `mcp-server-security` est-il testé et compatible avec Spring Boot 3.5.9 et Spring AI 1.1.2 ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `MCP Server Security compatibility with Spring Boot 3.5.9 and Spring AI.md`
- **Résolution:**
  - ✅ Compatible Spring Boot 3.5.9 (hérite du BOM parent)
  - ⚠️ **CRITIQUE: Spring AI 1.1.2 N'EXISTE PAS!** Version actuelle: **1.1.1** (5 déc 2025)
  - ✅ Pas de conflit documenté avec Langchain4j 1.10.0
  - **Contraintes:** WebMVC only (pas WebFlux), JWT only, HTTP Streamable only
  - **Bug connu:** Issue #2506 - Authentication perdue dans tool execution
- **Action requise:** Corriger tech-spec → Spring AI **1.1.1** (pas 1.1.2)

### R22.3 - Alternative filtre Spring Security manuel
- **Priorité:** 🟡 MODÉRÉ
- **Question:** Est-il préférable d'implémenter un filtre API Key manuel avec Spring Security ?
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Spring Security API key authentication for MCP servers.md`
- **Résolution:**
  - **RECOMMANDÉ:** Filtre custom `OncePerRequestFilter` (~50 lignes)
  - Plus stable que mcp-server-security (pas de dépendance 0.0.x)
  - Contrôle total sur la logique d'authentification
  - **Pattern critique:** Timing-safe comparison avec `MessageDigest.isEqual()`
  - **Positionnement:** Avant `AnonymousAuthenticationFilter`
  - Code exemple complet fourni avec `SecurityFilterChain` Spring Security 6.x
- **Décision Alexandria:** Filtre custom pour MVP si auth LAN nécessaire

---

## Fichier Tech-Spec: Validation de Cohérence (2026-01-05)

### R-TS.1 - Testcontainers 2.x artifact ID PostgreSQL
- **Priorité:** 🟠 IMPORTANT
- **Question:** Quel est l'artifact ID correct pour PostgreSQL dans Testcontainers 2.0.3 ? La tech-spec utilise `testcontainers-postgresql` mais Testcontainers 2.x a changé la structure des modules.
- **Contexte:** Ligne ~1656 de tech-spec-wip.md
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `Testcontainers 2.0.3 PostgreSQL artifact is testcontainers-postgresql.md`
- **Résolution:**
  - **`testcontainers-postgresql` est CORRECT** pour Testcontainers 2.x
  - Inversion de ce qu'on pensait: **1.x** utilisait `postgresql`, **2.x** utilise `testcontainers-postgresql`
  - **Package Java changé:** `org.testcontainers.postgresql.PostgreSQLContainer` (plus `org.testcontainers.containers.*`)
  - BOM disponible: `testcontainers-bom:2.0.3`
- **Action tech-spec:** Aucune correction artifact. Ajouter note sur import Java modifié

### R-TS.2 - Spring AI MCP SDK 1.1.x - Classe transport HTTP Streamable client
- **Priorité:** 🟠 IMPORTANT
- **Question:** Quel est le nom exact de la classe pour créer un client MCP HTTP Streamable côté test ? La tech-spec utilise `StreamableHttpMcpTransport.builder()` mais ce nom semble hypothétique.
- **Contexte:** Ligne ~1931 de tech-spec-wip.md (McpTestSupport)
- **Statut:** [x] ✅ RÉSOLU
- **Recherche:** `MCP HTTP Streamable Client Transport in Spring AI 1.1.2.md`
- **Résolution:**
  - **`StreamableHttpMcpTransport` N'EXISTE PAS** — classe hypothétique
  - **Classe correcte:** `HttpClientStreamableHttpTransport`
  - **Package:** `io.modelcontextprotocol.client.transport`
  - **Builder:** `HttpClientStreamableHttpTransport.builder("http://localhost:8080").endpoint("/mcp").build()`
  - **Alternative WebFlux:** `WebClientStreamableHttpTransport` (applications réactives)
  - **SDK:** MCP Java SDK `io.modelcontextprotocol.sdk:mcp:0.17.0` (inclus via Spring AI)
- **Action tech-spec:** Corriger le snippet McpTestSupport.java avec la bonne classe et imports

### R-TS.3 - Spring AI version: 1.1.1 vs 1.1.2
- **Priorité:** 🔴 BLOQUANT
- **Question:** La tech-spec utilise Spring AI 1.1.2 partout, mais R22.2 a établi que 1.1.2 n'existe pas (version actuelle: 1.1.1). Faut-il corriger toute la tech-spec ?
- **Contexte:** Frontmatter + multiples références dans tech-spec-wip.md
- **Statut:** [x] ✅ RÉSOLU
- **Résolution:**
  - **Spring AI 1.1.2 GA** publiée le **9 décembre 2025**
  - La tech-spec utilise la bonne version — aucune correction requise
  - R22.2 était basée sur des données du 5 décembre (avant la release)

---

## Items Reportés à v2 (Post-MVP)

### Liste des fonctionnalités reportées (2026-01-05)

| # | Feature | Source | Raison du report |
|---|---------|--------|------------------|
| v2.1 | Hybrid search BM25 + vector | Handling no results.md | Over-engineering MVP |
| v2.2 | Query suggestions basées sur corpus | Handling no results.md | Complexité |
| v2.3 | Relevance-gap filtering | Optimal MCP tool response formats.md | Over-engineering MVP |
| v2.4 | Pagination résultats MCP | Optimal MCP tool response formats.md | Complexité |
| v2.5 | Interface SPI custom parsers | Formats de documents.md | Utiliser Langchain4j natif |
| v2.6 | Support PDF / AsciiDoc | Formats de documents.md | Phase 2 |
| v2.7 | Rate limiter Resilience4j | Stratégies d'ingestion.md | Simplifier MVP |
| v2.8 | Worker warming RunPod | Optimal timeout configuration.md | Coût $0.50-2/day |
| v2.9 | Retrieval quality metrics CI/CD | Testing Best Practices.md | P@5, R@10, MRR avancé |
| v2.10 | Claude Code headless E2E | Testing E2E MCP.md | CI/CD avancé |
| v2.11 | MCP Inspector validation | Testing E2E MCP.md | Tooling externe |
| v2.12 | Détection doublons content_hash | Stratégies d'ingestion.md | Non critique MVP |
| v2.13 | RagStatsInfoContributor | Logging et Health Checks.md | Nice-to-have stats /actuator/info |
| v2.14 | Port Actuator séparé 8081 | Logging et Health Checks.md | Évaluer besoin mono-user |
| v2.15 | Soft delete avec is_active | Stratégies mise à jour documents.md | Over-engineering MVP |
| v2.16 | Garbage collection chunks orphelins | Stratégies mise à jour documents.md | Logique applicative requise |

### Critères de réintégration v2

Pour chaque item, évaluer avant réintégration:
1. **Valeur utilisateur** - Impact réel sur l'expérience
2. **Complexité d'implémentation** - Effort vs bénéfice
3. **Dépendances** - Prérequis MVP complétés
4. **Risques** - Stabilité, maintenance, dette technique

---
