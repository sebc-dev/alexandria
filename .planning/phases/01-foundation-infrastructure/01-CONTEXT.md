# Phase 1: Foundation & Infrastructure - Context

**Gathered:** 2026-02-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Set up the project skeleton: Docker Compose stack with PostgreSQL+pgvector, Spring Boot 3.5 application with dual-profile (web + stdio), ONNX embedding generation, and Flyway-managed database schema. This is the base layer everything else builds on. No crawling, no search logic, no MCP tools — just the infrastructure that makes those possible.

Reference architecture: `docs/architecture.md` — adopt its structure and patterns.

</domain>

<decisions>
## Implementation Decisions

### Structure de packages
- Adopter l'arborescence hybride pragmatique de `docs/architecture.md` telle quelle
- Group ID Maven : `dev.alexandria`
- Packages par feature : `ingestion/`, `search/`, `source/`, `document/`, `mcp/`, `api/`, `config/`
- Inclure le package `api/` (REST admin) des le v1 — endpoints REST en plus du MCP
- Interfaces uniquement aux frontieres d'integration : `ContentCrawler` pour Crawl4AI + interfaces LangChain4j (`EmbeddingModel`, `EmbeddingStore`)
- Pas de pattern ServiceImpl — classes concretes sauf polymorphisme reel
- Records Java 21 pour tous les objets de transit immuables

### Docker Compose topology
- 3 services uniquement : app Java, Crawl4AI, PostgreSQL
- PostgreSQL 16 (pas 17 — eviter le bug halfvec `avg_width` potentiel)
- Image pgvector : `pgvector/pgvector:pg16`
- Ports exposes sur l'hote : PostgreSQL 5432 uniquement (pour debug/admin)
- App Java et Crawl4AI restent internes au reseau Docker
- Volume nomme pour la persistence des donnees PostgreSQL entre restarts
- Health checks pour chaque service

### Schema DB & migrations
- Flyway pour les migrations (standard Spring Boot, fichiers SQL versiones)
- 3 tables initiales : `document_chunks`, `sources`, `ingestion_state`
- Colonnes embedding en `vector(384)` (precision maximale, pas halfvec)
- Index HNSW et GIN pour FTS crees dans les migrations Flyway

### Profils Spring Boot
- JAR unique, 2 profils de lancement : `web` (REST + MCP SSE) et `stdio` (MCP Claude Code)
- Maven comme outil de build
- Virtual threads activees pour l'I/O (crawl, DB writes), pool platform threads pour ONNX
- Spring Boot banner et console logging desactives en mode stdio (pitfall MCP)

### Claude's Discretion
- Strategie de logging MCP (fichier log vs stderr vs les deux)
- Strategie de creation d'index HNSW (dans Flyway migration vs apres chargement initial)
- Exact spacing, nommage des fichiers de migration Flyway
- Configuration des health checks Docker (intervalles, retries, timeouts)
- Parametres HNSW (m, ef_construction) — valeurs par defaut pgvector sauf besoin specifique

</decisions>

<specifics>
## Specific Ideas

- L'architecture complete est documentee dans `docs/architecture.md` — utiliser comme reference principale pour les patterns d'implementation (injection constructeur, pas de @Autowired sur champs, services < 200 lignes, etc.)
- La pile `spring-ai-starter-mcp-server-webmvc` gere nativement le dual transport MCP (stdio + SSE)
- Le rapport `docs/stack.md` documente les choix de stack et versions cibles (LangChain4j 1.11.0, Spring Boot 3.5.10, MCP Java SDK 0.17.2)
- La recherche `.planning/research/PITFALLS.md` documente les pieges a eviter des cette phase (ONNX native memory, virtual thread pinning, Docker memory limits)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-foundation-infrastructure*
*Context gathered: 2026-02-14*
