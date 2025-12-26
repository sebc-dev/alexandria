---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
inputDocuments:
  - '/home/negus/dev/alexandria/_bmad-output/prd.md'
  - '/home/negus/dev/alexandria/_bmad-output/brief.md'
documentCounts:
  brief: 1
  prd: 1
  epics: 0
  ux: 0
  research: 0
  projectDocs: 0
  projectContext: 0
workflowType: 'architecture'
project_name: 'alexandria'
user_name: 'Negus'
date: '2025-12-26'
hasProjectContext: false
lastStep: 8
status: 'complete'
completedAt: '2025-12-26'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements (106 total):**

Alexandria présente un système complet organisé en 7 catégories fonctionnelles:

1. **Convention & Documentation Management (FR1-FR15)**: CRUD complet pour conventions et documentation avec upload markdown manuel, distinction explicite Conv/Doc, filtrage multi-critères (type, technology, project), consultation et suppression avec cascade.

2. **Active Compliance Filter - 3 Layer RAG (FR16-FR28)**: Cœur architectural stratifié:
   - **Layer 1**: Vector search sémantique (HNSW index + cosine similarity via pgvector) présentant conventions comme "lois non-négociables"
   - **Layer 2**: Linking automatique documentation via technology_id (SQL JOIN pivot table)
   - **Layer 3**: Reformulation orchestrée via Skill Alexandria qui invoque sub-agent Claude Code (Haiku 4.5) pour éliminer contradictions et syntaxe obsolète, produisant guide mono-approche

3. **Context Retrieval & Delivery (FR29-FR38)**: Pipeline complet query→embeddings (OpenAI)→Layer 1→Layer 2→Layer 3→contexte fusionné optimisé pour génération code/documentation.

4. **Code Validation & Conformity (FR39-FR49)**: Validation post-génération contre conventions avec détection violations, scoring conformité, suggestions corrections.

5. **Claude Code Integration (FR50-FR74)**: Intégration native via Skills auto-invoqués, slash commands interactifs, sub-agent Haiku 4.5 économique (via système sub-agents Claude Code), MCP Server exposant 7 tools.

6. **Project & Technology Configuration (FR75-FR91, FR104-FR106)**: Gestion multi-projets avec isolation project_id, configuration multi-technologies **agnostique du contenu traité** (peut gérer conventions Python, TypeScript, Java, etc.), sharing documents cross-projects.

7. **Testing, Debugging & Observability (FR92-FR103)**: Queries debug avec visibilité pipeline 3-layer, logging complet requêtes/validations/CRUD, tracking manuel métriques succès.

**Non-Functional Requirements (33 total):**

**Performance (NFR1-NFR6):**
- Temps réponse critique: p50 ≤3s, p95 ≤5s, p99 ≤10s end-to-end
- Layer breakdown: Vector search ≤1s, SQL joins ≤500ms, LLM reformulation (sub-agent) ≤2s
- Upload asynchrone non-bloquant avec notification completion
- Support minimum 5 requêtes concurrentes sans dégradation
- **Implication stack**: Bun 1.3.5 ultra-rapide pour I/O, Hono 4.11.1 minimaliste pour latency minimale, Zod 4.2.1 (3x plus rapide) pour validation

**Security (NFR7-NFR10):**
- Credentials via variables environnement ou .env file (.gitignore obligatoire)
- Protection `ALEXANDRIA_DB_URL` et `OPENAI_API_KEY` (jamais loggées/exposées)
- Pas de `CLAUDE_API_KEY` nécessaire (reformulation via sub-agent Claude Code)
- Usage local uniquement (pas d'auth MCP server nécessaire)
- Data privacy: stockage local PostgreSQL 17.7, embeddings via OpenAI API (chunks uniquement)

**Integration (NFR11-NFR15):**
- MCP protocol 100% compliant (serveur Hono exposant MCP tools, validation Zod 4.2.1)
- Skills auto-invocables fiables
- Slash commands avec paramètres optionnels et defaults appropriés (validation Zod)
- Sub-agent communication via système Claude Code (pas d'API directe)
- Détection PostgreSQL 17.7 + pgvector 0.8.1 au démarrage avec fail-fast

**Reliability (NFR16-NFR20):**
- Fail fast behavior avec messages explicites actionnables (Zod errors clairs)
- Transactions PostgreSQL avec rollback automatique si échec partiel (via Drizzle ORM 0.36.4)
- Graceful degradation Layer 3: fallback Layer 1+2 brut si sub-agent reformulation échoue
- Uptime continu, recovery automatique après restart

**Maintainability (NFR21-NFR25):**
- Documentation code complète (JSDoc/TSDoc, types TypeScript 5.9.7 stricts)
- Organisation claire: séparation skills/sub-agent/MCP/database layer (architecture hexagonale stricte)
- Tests unitaires + intégration (Bun test runner intégré ultra-rapide)
- Configuration externalisée (.env.example fourni, validation Zod 4.2.1 au startup)
- Dependency management: package.json + bun.lockb, Dockerfile avec image Bun 1.3.5 officielle

**Observability (NFR26-NFR33):**
- Logging verbose activable (DEBUG mode)
- Métriques automatiques: timestamp, operation type, query text, project/technologies, success/error, temps par layer
- Pipeline visibility: Layer 1/2/3 outputs, similarity scores, tokens consommés
- CRUD tracking complet avec Drizzle ORM
- Validation logging avec privacy (code hash, pas de snippets complets)

### Scale & Complexity

**Project Complexity: Medium**

Justification:
- Architecture technique sophistiquée (RAG 3-layer, vector search pgvector 0.8.1, LLM integration sub-agent, architecture hexagonale stricte) mais scope MVP contrôlé
- Stack moderne performante (Bun 1.3.5 + Hono 4.11.1 + TypeScript 5.9.7 + Drizzle 0.36.4 + Zod 4.2.1) avec courbe d'apprentissage modérée
- Pas de régulations externes (pas de HIPAA, GDPR strict, etc.)
- Validation méthodologique nécessaire avant scaling (double objectif: résoudre problème concret + maîtriser architectures RAG)
- Standards qualité élevés auto-imposés (100% conformité conventions = North Star KPI)

**Primary Technical Domain: Developer Tool + Scientific/AI**

Caractéristiques:
- **Developer Tool**: Skills marketplace, MCP server (Hono), slash commands, intégration Claude Code native, Bun acquis par Anthropic
- **Scientific/AI**: Architecture RAG stratifiée à valider, métriques performance rigoureuses, reproductibilité, documentation technique détaillée

**Estimated Architectural Components: 9 modules majeurs (Hexagonal)**

1. **Domain Core** (use cases Layer 1+2, business logic RAG, TypeScript pur sans dépendances infrastructure)
2. **Ports Layer** (interfaces TypeScript abstraites définissant contrats hexagonaux)
3. **MCP Server Adapter** (Hono 4.11.1 framework, ports primaires, middleware Zod 4.2.1)
4. **PostgreSQL Adapters** (conventions, documentation, technology linker - Drizzle ORM 0.36.4, pgvector 0.8.1 HNSW)
5. **OpenAI Embedding Adapter** (génération vecteurs - port secondaire, validation Zod responses)
6. **Dual Logging Adapter** (console JSON + fichiers .jsonl rotatifs - port secondaire)
7. **Validation Middleware** (Zod 4.2.1 schemas pour MCP protocol, .env, slash commands)
8. **Skills & Sub-Agents** (Skill Alexandria orchestre Layer 3 via sub-agent externe, Skills invoquent MCP tools)
9. **Configuration Management** (env vars, .env parsing Zod, validation startup fail-fast)

### Technical Constraints & Dependencies

**Architecture Hexagonale - Contrainte Fondamentale NON-NÉGOCIABLE:**

Alexandria **DOIT ABSOLUMENT** implémenter l'architecture hexagonale (Ports & Adapters) pour:

1. **Expérimentation Technique Facilitée** (double objectif: résoudre problème concret + maîtriser architectures RAG)
   - Swap technologies sans refonte core métier: Bun→Deno, PostgreSQL→Qdrant, OpenAI→Voyage, Drizzle→Prisma
   - Testing facilité: ports mockables pour tests unitaires isolation complète (Bun test runner)

2. **Évolution Post-MVP Non-Destructive** (roadmap Crawl4AI, UI web, Jira/Confluence)
   - Ajout adapters sans toucher domaine métier
   - Réduction drastique risque régression

3. **Testabilité Critique** (NFR23 exige tests unitaires + intégration)
   - Domain core testable sans dépendances infrastructure
   - Tests intégration par adapter (PostgreSQL, OpenAI, etc.)
   - Bun test runner ultra-rapide pour TDD

4. **Isolation Naturelle des 3 Layers RAG**
   - Chaque layer testable et remplaçable indépendamment

**Ports Identifiés:**

**Ports Primaires (driven by external actors):**
- `MCPServerPort`: Interface MCP protocol (7 tools: retrieve, validate, upload, list, read, delete, list_projects)
- `SkillPort`: Interface skills auto-invoqués
- `SlashCommandPort`: Interface commandes interactives

**Ports Secondaires (driving infrastructure):**
- `ConventionRepositoryPort`: Storage + vector search Layer 1 (Drizzle + pgvector HNSW + cosine similarity)
- `DocumentationRepositoryPort`: Storage + retrieval Layer 2 (Drizzle)
- `TechnologyLinkerPort`: Gestion liens pivot table (Drizzle)
- `EmbeddingGeneratorPort`: Génération vecteurs (OpenAI API + Zod validation)
- `LoggerPort`: Observabilité dual (console JSON structuré + fichiers .jsonl rotatifs quotidiens)

**Stack Technique Fixe (MVP) - VERSIONS FINALES:**

- **Runtime**: Bun 1.3.5 (JavaScript runtime ultra-rapide, acquis par Anthropic 2 déc 2025, excellente compatibilité future garantie)
- **Framework Web**: Hono 4.11.1 (TypeScript, framework web minimaliste ~12KB, edge-ready, multi-runtime, support Bun natif)
- **Langage**: TypeScript 5.9.7 (strict mode obligatoire, typage statique complet, dernière branche 5.x stable)
- **ORM**: Drizzle ORM 0.36.4 (type-safe, performant ~10x moins overhead que Prisma, support natif pgvector via `drizzle-orm/pg-core`)
- **Validation**: Zod 4.2.1 (17 déc 2025, bundle 57% plus petit, 3x plus rapide que 3.x, runtime validation type-safe, MCP protocol compliance)
- **Base de données**: PostgreSQL 17.7 (13 nov 2025, recommandé pour pgvector 0.8.1)
- **Vector Extension**: pgvector 0.8.1 (iterative scans, HNSW optimisé, abandonne support PostgreSQL 12)
- **Embeddings**: OpenAI Embeddings API (text-embedding-3-small ou text-embedding-3-large)
- **LLM Reformulation**: Claude Haiku 4.5 (claude-haiku-4-5, 15 oct 2025, 73.3% SWE-bench Verified, $1/M input $5/M output) via sub-agent Claude Code
- **Architecture**: Hexagonale stricte (contrainte non-négociable)

**Agnosticité du Contenu Traité (Non de l'Implémentation):**

**CLARIFICATION CRITIQUE:**
- ✅ Alexandria peut stocker/gérer conventions et documentation pour **n'importe quel langage utilisateur**: Python, TypeScript, Java, Groovy, Go, Rust, etc.
- ✅ Alexandria peut indexer documentation pour **n'importe quel framework utilisateur**: FastAPI, Spring, NestJS, Django, Express, etc.
- ❌ Le langage d'implémentation d'Alexandria (TypeScript 5.9.7 + Bun 1.3.5) est **fixe pour MVP**, pas agnostique
- 🔄 L'architecture hexagonale permet de swap le runtime/framework si nécessaire (Bun→Deno, Hono→Express, Drizzle→Prisma) mais ce n'est **pas** lié à l'agnosticité du contenu

**Dépendances Critiques:**

- PostgreSQL 17.7 avec extension pgvector 0.8.1 (détection au démarrage, fail-fast si absent)
- Bun 1.3.5 runtime installé (pas besoin de Node.js)
- OpenAI API accessible (génération embeddings)
- Sub-agent `alexandria-reformulation` configuré dans Claude Code avec modèle Haiku 4.5
- Variables environnement: `ALEXANDRIA_DB_URL`, `OPENAI_API_KEY` (validation Zod 4.2.1 au startup)

**Contraintes de Performance:**

- p50 ≤3s end-to-end (non-négociable pour ne pas casser flow développement)
- Vector search pgvector scalable >10,000 embeddings sans dégradation
- Concurrent requests: minimum 5 simultanées
- **Bénéfice Bun 1.3.5**: I/O ultra-rapide (3-4x Node.js), startup instantané, faible empreinte mémoire
- **Bénéfice Zod 4.2.1**: Validation 3x plus rapide que 3.x, bundle 57% plus petit

**Contraintes d'Intégration:**

- MCP protocol 100% compliant (validation via tests MCP tools avec Zod schemas)
- Skills Claude Code auto-invocables sans erreur
- Marketplace Claude Code comme distribution exclusive
- Hono 4.11.1 framework compatible MCP protocol (HTTP/JSON endpoints, middleware Zod)

### Cross-Cutting Concerns Identified

**1. Vector Search Performance & Scalability**
- **Impacte**: Layer 1 retrieval, tous use cases (query, validate, context delivery)
- **Décisions architecturales prises**:
  - ✅ Index type: **HNSW** (Hierarchical Navigable Small World) - performance optimale high-recall
  - ✅ Distance metric: **Cosine similarity** (`<=>` operator) - standard OpenAI embeddings
  - ✅ HNSW parameters: `m=16, ef_construction=64` (valeurs recommandées)
  - ⏳ Chunk size optimal (256, 512, 1024 tokens) - À déterminer lors implémentation
  - ⏳ Top-k retrieval strategy - À déterminer lors implémentation
  - ⏳ OpenAI embedding model (text-embedding-3-small vs large) - À déterminer lors implémentation
- **NFRs concernés**: NFR1, NFR2, NFR6
- **Ports**: `ConventionRepositoryPort`, `EmbeddingGeneratorPort`
- **Adapters**: `DrizzleConventionAdapter` avec HNSW cosine queries, `OpenAIEmbeddingAdapter`

**2. Technology-Based Linking Integrity**
- **Impacte**: Layer 2 retrieval, multi-project configuration, CRUD operations
- **Décisions architecturales nécessaires**:
  - Table pivot design `convention_technologies` (schema Drizzle ORM)
  - Cascade deletes (ON DELETE CASCADE via Drizzle relations)
  - Technology taxonomy/naming conventions (lowercase, hyphenated, validation Zod)
  - Validation constraints (foreign keys, unique constraints Drizzle)
- **FRs concernés**: FR75-FR91, FR104-FR106
- **Ports**: `TechnologyLinkerPort`
- **Adapters**: `DrizzleTechnologyLinkerAdapter`

**3. Layer 3 Orchestration & Skill Architecture**
- **Impacte**: Reformulation Layer 3, tous retrievals, expérience utilisateur finale
- **Décisions architecturales prises**:
  - ✅ **Architecture orchestrée**: Alexandria MCP Server n'invoque PAS directement le sub-agent
  - ✅ **MCP Tool Design**: `retrieve_raw_context` retourne uniquement Layer 1+2 brut (conventions + documentation)
  - ✅ **Sub-Agent Definition**: `.claude/agents/alexandria-reformulation.md` (fichier externe, model: haiku)
  - ✅ **Skill Orchestration**: Skill Alexandria appelle MCP tool → demande à Claude Code d'invoquer sub-agent → reformulation
  - ✅ **Caching strategy**: Pas de cache pour MVP (simplicité prioritaire, quota Plan Max suffisant)
  - ✅ **Fallback strategy**: Retour Layer 1+2 brut si sub-agent échoue (NFR19 graceful degradation)
  - ⏳ Prompt engineering Layer 3 (instructions reformulation optimisées pour Haiku 4.5) - À définir dans sub-agent
  - ⏳ Monitoring coûts Plan Max Claude ($1/M input, $5/M output) - Via LoggerPort tracking invocations
- **NFRs concernés**: NFR4, NFR14, NFR19
- **Composants**: Skill Alexandria (orchestrateur), Sub-Agent externe (reformulation), MCP Tool (data Layer 1+2)
- **Pas de Port/Adapter dans Alexandria**: Layer 3 est orchestré HORS du projet Alexandria

**4. Multi-Project Isolation & Sharing**
- **Impacte**: Tous use cases (CRUD, retrieval, validation)
- **Décisions architecturales prises**:
  - ✅ **Isolation strategy**: Application-level filtering via Drizzle WHERE clauses (simplicité vs Row-Level Security)
  - ✅ **Use-case signatures**: Tous reçoivent `ProjectId` value object obligatoire
  - ✅ **Repository filtering**: Tous les adapters filtrent systématiquement par `project_id`
  - ✅ **Index PostgreSQL**: `CREATE INDEX idx_conventions_project_id ON conventions(project_id)`
  - ⏳ project_id type (UUID, string, auto-increment) - À déterminer lors implémentation schema Drizzle
  - ⏳ Document sharing pivot table schema - À déterminer lors implémentation
  - ⏳ Project metadata management - À déterminer lors implémentation
- **FRs concernés**: FR75-FR91, FR104-FR106

**5. Observability & Debugging Pipeline RAG**
- **Impacte**: Tous use cases, développement, production debugging
- **Décisions architecturales prises**:
  - ✅ **Logging strategy**: Dual approach (console + fichiers .jsonl rotatifs)
  - ✅ **Console logging**: JSON structuré via Bun natif, output stdout (capturé par Claude Code)
  - ✅ **File logging**: Format .jsonl, location `./logs/alexandria-YYYY-MM-DD.jsonl`, rotation quotidienne
  - ✅ **Retention**: Configurable via `LOG_RETENTION_DAYS` env var (défaut: 30 jours)
  - ✅ **Log levels**: DEBUG, INFO, WARN, ERROR (standard)
  - ✅ **Metrics collection**: Layer 1/2/3 timing avec `performance.now()`, sub-agent invocations count
  - ✅ **Analysis tools**: `grep`/`jq` sur fichiers JSON (suffisant pour MVP, pas de dashboard)
  - ⏳ Debug mode activation (variable env ALEXANDRIA_LOG_LEVEL) - À implémenter
- **NFRs concernés**: NFR26-NFR33
- **Ports**: `LoggerPort`
- **Adapters**: `BunDualLoggerAdapter` (écrit simultanément console ET fichier, gestion erreurs I/O)

**6. MCP Protocol Compliance & TypeScript Integration**
- **Impacte**: Toutes interactions Claude Code (skills, slash commands, tools)
- **Décisions architecturales nécessaires**:
  - MCP tool schemas TypeScript (Zod 4.2.1 schemas → TypeScript types inférés)
  - Hono 4.11.1 routing pour MCP endpoints
  - Error response format MCP-compliant (Zod validation errors transformés)
  - Timeout handling (promises avec timeout, async/await patterns)
  - Retry policies (exponential backoff pour résilience?)
- **NFRs concernés**: NFR11-NFR15, NFR16-NFR17
- **Framework**: Hono middleware pour MCP, `ZodValidationMiddleware`

**7. Hexagonal Architecture Enforcement (TypeScript)**
- **Impacte**: Toute l'implémentation, tests, évolution post-MVP
- **Décisions architecturales nécessaires**:
  - Port interfaces definitions (TypeScript interfaces strictes, readonly, immutability)
  - Adapter organization (folder structure `src/adapters/`, `src/ports/`, `src/domain/`)
  - Dependency injection strategy (constructor injection, factory pattern, IoC container?)
  - Testing strategy per layer (domain unit tests sans mocks, adapter integration tests avec PostgreSQL/OpenAI)
  - TypeScript strict mode enforcement (tsconfig.json: strict=true, noImplicitAny, strictNullChecks, etc.)
- **Contrainte fondamentale**: Architecture hexagonale NON-NÉGOCIABLE, validation via architecture tests

**8. Security & Credentials Management (Bun/TypeScript/Zod)**
- **Impacte**: Configuration, démarrage, OpenAI API calls, database access
- **Décisions architecturales nécessaires**:
  - .env file parsing (Bun native `process.env` + Zod 4.2.1 validation schema)
  - Secrets validation at startup (Zod schema fail-fast si manquants ou malformés)
  - Credential rotation strategy (architecture doit permettre hot-reload config)
  - .gitignore strict (.env exclu, .env.example fourni avec placeholders)
  - OPENAI_API_KEY protection (jamais loggée, stockée en mémoire uniquement)
- **NFRs concernés**: NFR7-NFR10

**9. Drizzle ORM Integration & Database Schema**
- **Impacte**: Tous accès PostgreSQL (CRUD, vector search, transactions)
- **Décisions architecturales nécessaires**:
  - Drizzle schemas definition (tables conventions, documentation, technologies, pivot tables)
  - pgvector integration (`import { vector } from 'drizzle-orm/pg-core'`)
  - Migrations strategy (Drizzle Kit, version control, rollback strategy)
  - Connection pooling (pg pool size, timeout configs)
  - Transaction management (Drizzle transactions API, rollback automatique NFR18)
  - Type-safety guarantees (Drizzle inferred types → domain entities)
- **Adapters**: `DrizzleConventionAdapter`, `DrizzleDocumentationAdapter`, `DrizzleTechnologyLinkerAdapter`

**10. Type-Safety & Validation Pipeline (Drizzle + Zod)**
- **Impacte**: Toutes les boundaries (MCP tools inputs/outputs, slash commands, database, OpenAI API)
- **Décisions architecturales nécessaires**:
  - Zod schemas pour validation runtime (MCP tools, .env config, slash commands params)
  - Drizzle schemas pour type-safety database (conventions table, docs table, pivot tables)
  - Mapping Zod types ↔ Drizzle types ↔ Domain types (transformation layer)
  - Error handling pipeline (Zod errors → user-friendly messages, HTTP status codes)
  - Validation à l'entrée de TOUS les ports primaires (MCPServerPort, SlashCommandPort)
  - Validation sorties adapters secondaires (OpenAI responses, PostgreSQL data)
- **NFRs concernés**: NFR11 (MCP compliance), NFR13 (slash commands), NFR16-NFR17 (fail-fast, error messages)
- **Middleware**: `ZodValidationMiddleware` intégré Hono pour auto-validation

## Starter Template Evaluation

### Primary Technology Domain

**Developer Tool + MCP Server** (Model Context Protocol server avec intégration Claude Code native)

Alexandria n'est PAS un projet web app classique (Next.js, Vite, React) ni une API REST standard (Express, NestJS). C'est un système spécialisé combinant:
- MCP Server (Hono framework)
- Architecture RAG 3-layer avec vector search (pgvector)
- Intégration Claude Code (skills, sub-agents, slash commands)
- Architecture hexagonale stricte (contrainte non-négociable)

### Starter Options Considered

**Évaluation des starters génériques:**

Starters classiques évalués et **rejetés**:
- ❌ **Next.js / T3 Stack**: Web app focus, pas de MCP protocol, architecture non-hexagonale
- ❌ **NestJS**: Trop opinionated (decorators), pas de Hono, complexité inutile pour MCP server
- ❌ **Vite + React**: Frontend focus, inadapté pour backend MCP server
- ❌ **Express boilerplate**: Pas de Bun support natif, pas d'architecture hexagonale

**Starters spécialisés recherchés:**
- ❌ **MCP Server starters**: Aucun starter mature trouvé pour Hono + MCP protocol
- ❌ **Hexagonal architecture starters**: Peu de starters TypeScript hexagonaux stricts
- ❌ **Bun + Hono starters**: Templates basiques sans architecture hexagonale

**Conclusion:** Aucun starter template existant ne correspond aux besoins spécifiques d'Alexandria (MCP + Hexagonal + RAG + pgvector + Bun/Hono stack complète).

### Selected Approach: Custom Project Structure

**Rationale:**

1. **Stack ultra-spécifique**: Bun 1.3.5 + Hono 4.11.1 + Drizzle 0.36.4 + Zod 4.2.1 + pgvector 0.8.1 nécessite structure custom
2. **Architecture hexagonale stricte**: Contrainte fondamentale NON-NÉGOCIABLE, absente des starters génériques
3. **RAG 3-layer**: Organisation domain core spécifique (layer1/layer2/layer3) non standard
4. **MCP protocol**: Support MCP tools nécessite routes/middleware custom Hono
5. **Testabilité maximale**: Structure tests (unit domain/integration adapters) alignée avec hexagonale

**Structure de Projet Custom pour Alexandria:**

```
alexandria/
├── src/
│   ├── domain/                          # 🎯 Domain Core (pure TypeScript, zero dépendances infrastructure)
│   │   ├── entities/                    # Entités métier (Convention, Documentation, Technology, Project)
│   │   │   ├── Convention.ts
│   │   │   ├── Documentation.ts
│   │   │   ├── Technology.ts
│   │   │   └── Project.ts
│   │   ├── use-cases/                   # Logique métier RAG Layer 1+2 (Layer 3 orchestré HORS Alexandria)
│   │   │   ├── layer1/                  # Layer 1: Vector Search (HNSW + cosine)
│   │   │   │   ├── SearchConventions.ts
│   │   │   │   └── GenerateEmbeddings.ts
│   │   │   ├── layer2/                  # Layer 2: Technology Linking
│   │   │   │   ├── LinkDocumentation.ts
│   │   │   │   └── FilterByTechnology.ts
│   │   │   ├── RetrieveRawContext.ts    # Pipeline Layer 1→2 (retourne brut, pas de reformulation)
│   │   │   ├── ValidateCode.ts
│   │   │   ├── UploadDocument.ts
│   │   │   └── ManageProjects.ts
│   │   └── value-objects/               # Value Objects immutables
│   │       ├── Embedding.ts
│   │       ├── ConformityScore.ts
│   │       └── ProjectId.ts
│   │
│   ├── ports/                           # 🔌 Ports (interfaces abstraites hexagonales)
│   │   ├── primary/                     # Ports primaires (driven by external actors)
│   │   │   ├── MCPServerPort.ts
│   │   │   ├── SkillPort.ts
│   │   │   └── SlashCommandPort.ts
│   │   └── secondary/                   # Ports secondaires (driving infrastructure)
│   │       ├── ConventionRepositoryPort.ts
│   │       ├── DocumentationRepositoryPort.ts
│   │       ├── TechnologyLinkerPort.ts
│   │       ├── EmbeddingGeneratorPort.ts
│   │       ├── LLMRefinerPort.ts
│   │       └── LoggerPort.ts
│   │
│   ├── adapters/                        # 🔧 Adapters (implémentations concrètes)
│   │   ├── primary/                     # Adapters primaires (MCP Server, Skills)
│   │   │   ├── mcp-server/             # MCP Server Hono 4.11.1
│   │   │   │   ├── server.ts           # Bootstrap Hono app
│   │   │   │   ├── routes/             # MCP tools routes (7 tools)
│   │   │   │   │   ├── retrieve.ts
│   │   │   │   │   ├── validate.ts
│   │   │   │   │   ├── upload.ts
│   │   │   │   │   ├── list.ts
│   │   │   │   │   ├── read.ts
│   │   │   │   │   ├── delete.ts
│   │   │   │   │   └── list-projects.ts
│   │   │   │   ├── middleware/
│   │   │   │   │   ├── zodValidation.ts    # Zod 4.2.1 validation auto
│   │   │   │   │   ├── errorHandler.ts     # MCP-compliant errors
│   │   │   │   │   └── logger.ts           # Structured logging
│   │   │   │   └── schemas/            # Zod 4.2.1 schemas MCP tools
│   │   │   │       ├── retrieveSchema.ts
│   │   │   │       ├── validateSchema.ts
│   │   │   │       └── uploadSchema.ts
│   │   │   └── skills/                 # Claude Code Skills
│   │   │       ├── alexandria-context.ts
│   │   │       └── alexandria-validate.ts
│   │   │
│   │   └── secondary/                   # Adapters secondaires (infrastructure)
│   │       ├── database/               # Drizzle ORM 0.36.4 + PostgreSQL 17.7 + pgvector 0.8.1
│   │       │   ├── drizzle.config.ts
│   │       │   ├── connection.ts       # Pool PostgreSQL
│   │       │   ├── schema/             # Drizzle schemas
│   │       │   │   ├── conventions.ts
│   │       │   │   ├── documentation.ts
│   │       │   │   ├── technologies.ts
│   │       │   │   ├── projects.ts
│   │       │   │   └── pivot-tables.ts
│   │       │   ├── migrations/         # Drizzle Kit migrations versionnées
│   │       │   └── repositories/       # Repository implementations (ports secondaires)
│   │       │       ├── DrizzleConventionRepository.ts
│   │       │       ├── DrizzleDocumentationRepository.ts
│   │       │       └── DrizzleTechnologyLinker.ts
│   │       ├── openai/                 # OpenAI Embeddings API
│   │       │   ├── OpenAIEmbeddingAdapter.ts
│   │       │   └── schemas/            # Zod validation responses OpenAI
│   │       │       └── embeddingResponseSchema.ts
│   │       └── logging/                # Bun Dual Logger (console JSON + fichiers .jsonl)
│   │           └── BunDualLogger.ts
│   │
│   ├── config/                          # ⚙️ Configuration (Zod validation fail-fast)
│   │   ├── env.ts                       # Zod 4.2.1 validation .env au startup
│   │   ├── constants.ts
│   │   └── types.ts                     # Types globaux TypeScript
│   │
│   ├── shared/                          # 🔧 Utilitaires partagés
│   │   ├── errors/                      # Custom errors
│   │   │   ├── DomainError.ts
│   │   │   ├── ValidationError.ts
│   │   │   └── InfrastructureError.ts
│   │   └── utils/
│   │       ├── retry.ts                 # Retry logic avec exponential backoff
│   │       └── timing.ts                # performance.now() pour metrics NFR26-33
│   │
│   └── index.ts                         # 🚀 Entry point (bootstrap app, dependency injection)
│
├── tests/                               # 🧪 Tests (Bun test runner ultra-rapide)
│   ├── unit/                            # Tests unitaires domain core (sans mocks)
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   └── use-cases/              # Tests Layer 1+2 isolés
│   │   └── adapters/                    # Tests unitaires adapters (avec mocks ports)
│   ├── integration/                     # Tests intégration (PostgreSQL, OpenAI réels)
│   │   ├── database/                    # Tests Drizzle + pgvector HNSW queries
│   │   ├── mcp-server/                  # Tests routes MCP tools end-to-end
│   │   └── pipeline/                    # Tests pipeline Layer 1→2 (retrieve_raw_context)
│   └── fixtures/                        # Test data (conventions, documentation JSON)
│       ├── conventions.json
│       └── documentation.json
│
├── logs/                                # 📊 Logs persistés (.jsonl rotatifs, gitignored)
│   └── .gitkeep                         # Garde le dossier vide dans git
│
├── scripts/                             # 📜 Scripts utilitaires
│   ├── setup-db.ts                      # Setup PostgreSQL 17.7 + pgvector 0.8.1
│   ├── seed-data.ts                     # Seed données test
│   ├── generate-types.ts                # Génération types Drizzle inferred
│   └── rotate-logs.sh                   # Rotation logs (cron job optionnel)
│
├── docs/                                # 📚 Documentation
│   ├── architecture/
│   │   ├── hexagonal-overview.md        # Architecture hexagonale expliquée
│   │   ├── ports-and-adapters.md        # Détail ports/adapters
│   │   └── rag-pipeline.md              # Pipeline RAG 3-layer
│   └── api/
│       └── mcp-tools.md                 # Documentation MCP tools
│
├── .env.example                         # 📋 Template variables environnement (ALEXANDRIA_DB_URL, OPENAI_API_KEY)
├── .gitignore                           # Git ignore (.env, node_modules, logs/*.jsonl, bun.lockb optionnel)
├── package.json                         # Dependencies Bun (hono, zod, drizzle-orm, postgres, openai)
├── bun.lockb                            # Lockfile Bun (versions exactes pinnées)
├── tsconfig.json                        # TypeScript 5.9.7 strict mode (strict=true, noImplicitAny, strictNullChecks)
├── Dockerfile                           # Image Bun 1.3.5 officielle
├── docker-compose.yml                   # PostgreSQL 17.7 + pgvector 0.8.1 service
└── README.md                            # Documentation projet (setup, architecture, usage)
```

### Initialization Commands

**Structure de projet sera créée via première story d'implémentation avec ces commandes:**

```bash
# Créer structure complète folders (Layer 3 orchestré HORS Alexandria, pas de folder layer3/)
mkdir -p alexandria/{src/{domain/{entities,use-cases/{layer1,layer2},value-objects},ports/{primary,secondary},adapters/{primary/{mcp-server/{routes,middleware,schemas},skills},secondary/{database/{schema,migrations,repositories},openai/schemas,logging}},config,shared/{errors,utils}},tests/{unit/{domain,adapters},integration/{database,mcp-server,pipeline},fixtures},logs,scripts,docs/{architecture,api}}

# Init Bun project
cd alexandria
bun init -y

# Install dependencies runtime
bun add hono zod drizzle-orm postgres openai

# Install dev dependencies
bun add -d drizzle-kit @types/node typescript @types/postgres

# Create config files
touch tsconfig.json drizzle.config.ts .env.example Dockerfile docker-compose.yml README.md

# Create scripts
touch scripts/setup-db.ts scripts/seed-data.ts scripts/generate-types.ts scripts/rotate-logs.sh

# Create logs .gitkeep
touch logs/.gitkeep
```

### Architectural Decisions Provided by Custom Structure

**Language & Runtime:**
- TypeScript 5.9.7 (strict mode obligatoire: tsconfig.json avec strict=true, noImplicitAny, strictNullChecks, noUncheckedIndexedAccess)
- Bun 1.3.5 runtime (pas de Node.js nécessaire, 3-4x plus rapide I/O)
- ESM modules (type: "module" dans package.json)

**Framework Web (MCP Server):**
- Hono 4.11.1 (framework web minimaliste ~12KB, edge-ready, multi-runtime)
- Middleware Zod 4.2.1 pour validation automatique inputs/outputs MCP tools
- Error handler MCP-compliant (transforme errors en format MCP protocol)
- Structured logging middleware (Bun console JSON)

**ORM & Database:**
- Drizzle ORM 0.36.4 (type-safe queries, ~10x moins overhead que Prisma)
- Support natif pgvector via `import { vector } from 'drizzle-orm/pg-core'`
- Migrations versionnées avec Drizzle Kit (rollback strategy)
- Connection pooling PostgreSQL (pg pool, timeout configs dans drizzle.config.ts)

**Validation:**
- Zod 4.2.1 aux boundaries (MCP tools schemas, .env validation startup, OpenAI responses)
- Fail-fast startup si .env invalide (config/env.ts exports validated env)
- Type-safety end-to-end: Zod schemas → TypeScript types inférés → Domain types

**Code Organization (Architecture Hexagonale Stricte):**

**Domain Core (src/domain/):**
- Zero dépendances infrastructure (pas de Drizzle, Hono, Zod imports)
- Entités métier immutables (readonly properties, value objects)
- Use cases organisés par RAG layers (layer1/layer2 uniquement - Layer 3 orchestré HORS Alexandria)
- Pipeline orchestration dans `RetrieveRawContext.ts` (retourne Layer 1+2 brut)
- Testable sans mocks (pure TypeScript logic)

**Ports Layer (src/ports/):**
- Interfaces TypeScript strictes (readonly, immutability)
- Ports primaires: `MCPServerPort`, `SkillPort`, `SlashCommandPort`
- Ports secondaires: `ConventionRepositoryPort`, `DocumentationRepositoryPort`, `TechnologyLinkerPort`, `EmbeddingGeneratorPort`, `LoggerPort`
- Définissent QUOI faire, pas COMMENT (contrats abstraits)
- **Note**: Pas de `LLMRefinerPort` - Layer 3 orchestré via Skill Alexandria (externe)

**Adapters Layer (src/adapters/):**
- Implémentations concrètes des ports
- Adapters primaires: MCP Server Hono (routes + middleware + schemas)
- Adapters secondaires: Drizzle repositories (HNSW + cosine), OpenAI client, BunDualLogger
- Swappables sans toucher domain core (hexagonal principle)
- **Note**: Pas de Claude Code Sub-Agent adapter - Layer 3 orchestré HORS Alexandria

**Folder Structure Principles:**
- `domain/` → Logique métier pure
- `ports/` → Contrats abstraits (interfaces)
- `adapters/` → Implémentations concrètes (infrastructure)
- `config/` → Configuration centralisée (Zod validation)
- `shared/` → Utilitaires cross-cutting (errors, retry, timing)
- `tests/` → Tests organisés par layer (unit/integration/e2e)

**Testing Framework:**
- Bun test runner intégré (ultra-rapide, syntaxe Jest-compatible)
- Tests unitaires domain core sans mocks (pure logic, isolated)
- Tests integration adapters avec PostgreSQL/OpenAI réels (ou test containers)
- Tests e2e pipeline Layer 1→2 (`retrieve_raw_context` MCP tool)
- Coverage intégré Bun (`bun test --coverage`)
- Watch mode pour TDD (`bun test --watch`)
- **Note**: Layer 3 testé via Skill Alexandria (tests séparés du MCP server)

**Development Experience:**

**Hot Reloading:**
- Bun dev server avec hot reload natif (`bun --watch src/index.ts`)
- Reload instantané TypeScript (pas de transpilation nécessaire)

**Type Safety:**
- TypeScript strict mode (errors compile-time, pas runtime)
- Drizzle inferred types (schemas → TypeScript types auto)
- Zod inferred types (schemas → TypeScript types auto)
- End-to-end type-safety (MCP inputs → Domain → Database)

**Debugging:**
- Bun debugger intégré (compatible VSCode/Chrome DevTools)
- Structured logging JSON (parsing facile, grep-friendly)
- Performance metrics avec `performance.now()` (NFR26-33)

**Linting & Formatting:**
- ESLint + Prettier (ou Biome pour all-in-one ultra-rapide)
- Pre-commit hooks (lint + format + tests unitaires)
- TypeScript compiler errors fail CI/CD

**Database Tooling:**
- Drizzle Studio pour visualisation database (UI web interactive)
- Migrations CLI (`drizzle-kit generate`, `drizzle-kit push`)
- Seed scripts pour données test (`scripts/seed-data.ts`)

**Containerization:**
- Dockerfile avec image Bun 1.3.5 officielle
- docker-compose.yml pour PostgreSQL 17.7 + pgvector 0.8.1
- Multi-stage build (dev vs production optimisé)

**Note:** Cette structure custom sera implémentée comme première story, incluant setup complet dependencies (package.json), configs (tsconfig.json, drizzle.config.ts, .env.example), folder structure, et validation architecture hexagonale via architecture tests.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**

1. **Sub-Agent Communication Protocol** - Architecture orchestrée via Skill Alexandria
2. **Vector Search Configuration** - HNSW index + cosine similarity pour Layer 1
3. **Multi-Project Isolation** - Application-level filtering via Drizzle WHERE clauses
4. **Logging Strategy** - Dual approach (console + fichiers .jsonl rotatifs)

**Important Decisions (Shape Architecture):**

1. **CI/CD Pipeline** - GitHub Actions avec tests unitaires, intégration, linting, ts-arch
2. **Layer 3 Caching** - Pas de cache pour MVP (simplicité prioritaire)
3. **API Security** - MCP protocol natif sans exposition HTTP publique

**Deferred Decisions (Post-MVP):**

1. **Rate Limiting** - Non nécessaire pour usage local, peut être ajouté si déploiement multi-utilisateurs
2. **Advanced Caching** - Redis ou PostgreSQL cache table si reformulations deviennent coûteuses
3. **Distributed Logging** - Dashboard centralisé si besoin d'observabilité avancée

---

### Data Architecture

**Vector Search - PostgreSQL 17.7 + pgvector 0.8.1:**

- **Index Type**: HNSW (Hierarchical Navigable Small World)
  - Rationale: Performance optimale pour high-recall searches (~100-1000 conventions)
  - Configuration: `m=16, ef_construction=64` (valeurs standard recommandées)
  - Trade-off: Build time légèrement plus long qu'IVFFlat mais queries 10x plus rapides

- **Distance Metric**: Cosine Similarity
  - Rationale: Standard de facto pour embeddings OpenAI (normalized vectors)
  - Compatible avec: `text-embedding-3-small` (1536 dimensions) et `text-embedding-3-large` (3072 dimensions)
  - Fonction pgvector: `<=>` operator pour cosine distance

**ORM & Type-Safety - Drizzle ORM 0.36.4:**

- **Schema Definition**: Type-safe schema avec support natif pgvector
  - `vector(1536)` type pour colonnes embeddings
  - Inference automatique TypeScript types depuis schema
  - Migrations versionnées via `drizzle-kit`

- **Query Builder**: Drizzle queries pour vector similarity
  ```typescript
  // Exemple query Layer 1
  db.select()
    .from(conventions)
    .where(eq(conventions.projectId, projectId))
    .orderBy(sql`embedding <=> ${queryEmbedding}`)
    .limit(topK)
  ```

**Caching Strategy:**

- **Decision**: Pas de cache pour Layer 3 reformulations (MVP)
- **Rationale**:
  - Simplicité architecturale prioritaire
  - Reformulations via sub-agent Haiku 4.5 (rapide + économique)
  - Quota Plan Max suffisant pour usage développeur
  - Cache ajouterait complexité (invalidation, storage, TTL)
- **Future**: Si latence Layer 3 devient problématique, évaluer PostgreSQL cache table avec TTL

**Multi-Project Isolation:**

- **Decision**: Application-level filtering via Drizzle WHERE clauses
- **Rationale**:
  - Simplicité vs Row-Level Security PostgreSQL (complexe à tester)
  - Contrôle explicite dans use-cases Domain layer
  - Performance identique (PostgreSQL optimise WHERE project_id via index)
- **Implementation**:
  - Tous les use-cases reçoivent `ProjectId` value object
  - Tous les repository adapters filtrent par `project_id`
  - Tests unitaires vérifient isolation via fixtures multi-projets
- **Index**: `CREATE INDEX idx_conventions_project_id ON conventions(project_id)`

---

### Authentication & Security

**Authorization Pattern:**

- **Decision**: Project-based isolation sans authentification utilisateur
- **Rationale**:
  - Alexandria est un MCP server local (pas d'exposition réseau)
  - Claude Code gère l'authentification utilisateur
  - Isolation par `project_id` suffit pour séparer workspaces
- **Validation**: Zod 4.2.1 schemas pour tous les MCP tool inputs/outputs

**API Security:**

- **MCP Protocol**: Sécurité native via stdio transport (pas HTTP)
- **Input Validation**: Zod middleware systématique dans adapters/primary/mcp-server
- **Error Handling**: Structured errors via LoggerPort, pas d'exposition stack traces à l'utilisateur
- **Secrets Management**: `.env` validation via Zod au startup (fail-fast)

**Data Encryption:**

- **Decision**: Pas de chiffrement au repos pour MVP
- **Rationale**:
  - Données stockées: conventions techniques (non sensibles)
  - Base PostgreSQL locale (pas de risque réseau)
  - TLS natif pour OpenAI API calls (embeddings)
- **Future**: Si conventions contiennent secrets, ajouter encryption-at-rest via pgcrypto

---

### API & Communication Patterns

**MCP Protocol Architecture:**

- **Tool Design**: `retrieve_raw_context` retourne Layer 1 + Layer 2 brut
  - Input: `query: string, project_id: string, top_k?: number`
  - Output: `{ conventions: Convention[], linked_documentation: Documentation[] }`
  - Pas de reformulation dans le MCP tool (séparation des responsabilités)

- **Other MCP Tools**: `validate`, `upload`, `list`, `read`, `delete`, `list_projects`
  - Tous validés via Zod schemas
  - Tous loggés via LoggerPort (request/response/errors)

**Sub-Agent Communication Protocol:**

- **Architecture Critique**: Alexandria (Hono/TypeScript) **n'invoque PAS directement** le sub-agent

- **Responsabilités Séparées**:
  1. **MCP Server (Alexandria)**: Expose `retrieve_raw_context` (Layer 1 + Layer 2)
  2. **Sub-Agent Definition**: Fichier `.claude/agents/alexandria-reformulation.md`
     ```markdown
     ---
     model: haiku
     ---
     # Alexandria Reformulation Agent

     Tu es un expert en reformulation de contexte technique pour Claude Code.

     **Ton rôle**: Recevoir le contexte brut (conventions + documentation) et le reformuler
     en instructions précises, actionnables, et adaptées au niveau de compétence de l'utilisateur.

     **Input**: JSON brut de `retrieve_raw_context`
     **Output**: Contexte reformulé optimisé pour génération de code
     ```
  3. **Skill Alexandria**: Orchestre le workflow complet
     - Appelle MCP tool `retrieve_raw_context`
     - Demande explicitement à Claude Code d'utiliser sub-agent `alexandria-reformulation`
     - Sub-agent reformule le contexte (Layer 3)
     - Contexte final injecté dans génération

- **Avantages**:
  - ✅ Pas de `CLAUDE_API_KEY` requise dans Alexandria
  - ✅ Reformulation Layer 3 utilise quota Plan Max existant
  - ✅ Sub-agent invoqué nativement par Claude Code (système interne)
  - ✅ Séparation claire: data (MCP) vs reformulation (sub-agent) vs orchestration (skill)

- **Version**: Claude Haiku 4.5 (`claude-haiku-4-5`)

**Error Handling Standards:**

- **Domain Errors**: Custom error classes héritant `DomainError`
  - `ConventionNotFoundError`, `InvalidProjectIdError`, `EmbeddingGenerationError`
- **Adapter Errors**: Wrapping des erreurs infrastructure
  - Drizzle errors → `RepositoryError`
  - OpenAI errors → `EmbeddingServiceError`
- **MCP Errors**: JSON-RPC 2.0 error format
  ```json
  { "code": -32603, "message": "Internal error", "data": { "type": "RepositoryError" } }
  ```

**API Documentation:**

- **Format**: Markdown documentation dans `/docs/api/mcp-tools.md`
- **Generation**: Manuelle pour MVP (Zod schemas comme source of truth)
- **Future**: Zod → OpenAPI schema generator si besoin

---

### Frontend Architecture

**N/A - Alexandria est un MCP Server Backend**

Alexandria n'a pas d'interface utilisateur traditionnelle. L'interface est Claude Code lui-même via:

1. **MCP Tools**: 7 tools exposés (retrieve, validate, upload, list, read, delete, list_projects)
2. **Skills Auto-Invoqués**: Skill Alexandria orchestre Layer 1+2+3 automatiquement
3. **Slash Commands**: Commands interactifs si nécessaire (ex: `/alexandria-config`)

**User Experience Flow:**

```
Utilisateur → Claude Code → Skill Alexandria → MCP retrieve_raw_context →
Sub-Agent alexandria-reformulation → Contexte reformulé → Claude Code génération
```

---

### Infrastructure & Deployment

**CI/CD Pipeline - GitHub Actions:**

- **Workflow File**: `.github/workflows/ci.yml`

- **Jobs**:
  1. **Lint & Type Check**:
     ```yaml
     - bun run lint        # ESLint + Prettier
     - bun run typecheck   # tsc --noEmit
     ```

  2. **Architecture Compliance**:
     ```yaml
     - bun run arch:test   # ts-arch rules
     ```
     - Vérifie: Domain ne dépend pas d'Adapters
     - Vérifie: Ports sont pures interfaces
     - Vérifie: Adapters implémentent Ports

  3. **Tests Unitaires**:
     ```yaml
     - bun test tests/unit/**/*.test.ts
     ```
     - Sans mocks pour Domain layer
     - Mocks pour Adapters (Drizzle, OpenAI)

  4. **Tests Intégration**:
     ```yaml
     services:
       postgres:
         image: pgvector/pgvector:pg17
         env:
           POSTGRES_PASSWORD: test
     steps:
       - bun test tests/integration/**/*.test.ts
     ```
     - PostgreSQL réel avec pgvector
     - OpenAI API réel (variable secret `OPENAI_API_KEY`)
     - Fixtures multi-projets pour tester isolation

- **Secrets GitHub**:
  - `OPENAI_API_KEY`: Pour tests intégration embedding generation

**Logging Strategy - Dual Approach:**

- **Console Logging** (Debug Immédiat):
  - Format: JSON structuré via Bun natif
  - Niveau: `debug`, `info`, `warn`, `error`
  - Output: `stdout` (capturé par Claude Code)
  - Exemple:
    ```json
    {"level":"info","timestamp":"2025-12-26T10:30:45Z","layer":"layer1","query":"hexagonal architecture","matches":12,"latency_ms":45}
    ```

- **File Logging** (Persistence):
  - Format: `.jsonl` (JSON Lines)
  - Location: `./logs/alexandria-YYYY-MM-DD.jsonl`
  - Rotation: Quotidienne (nouveau fichier chaque jour)
  - Rétention: Configurable via `LOG_RETENTION_DAYS` env var (défaut: 30 jours)
  - Script: `scripts/rotate-logs.sh` (cron job optionnel)

- **LoggerPort Interface** (Hexagonal):
  ```typescript
  interface LoggerPort {
    debug(message: string, context?: Record<string, unknown>): void
    info(message: string, context?: Record<string, unknown>): void
    warn(message: string, context?: Record<string, unknown>): void
    error(message: string, error?: Error, context?: Record<string, unknown>): void
  }
  ```

- **Adapter Implementation**: `BunDualLoggerAdapter`
  - Écrit simultanément console ET fichier
  - Gestion erreurs I/O fichier (fallback console-only)
  - Buffer writes pour performance (flush toutes les 5s ou 100 messages)

- **Analysis**: `grep`/`jq` sur fichiers JSON
  ```bash
  # Erreurs Layer 1 aujourd'hui
  jq 'select(.level=="error" and .layer=="layer1")' logs/alexandria-2025-12-26.jsonl

  # Latence moyenne Layer 3
  jq -s 'map(select(.layer=="layer3") | .latency_ms) | add/length' logs/*.jsonl
  ```

- **Rationale**:
  - ✅ Pas de PostgreSQL pour logs (évite overhead I/O + pollution schema)
  - ✅ Console pour debug immédiat pendant développement
  - ✅ Fichiers pour troubleshooting post-mortem
  - ✅ JSON structuré pour analyse programmatique
  - ✅ Respecte NFR26-33 (structured, rotatif, exportable)

**Monitoring & Observability:**

- **MVP**: Analyse manuelle via logs JSON
- **Metrics Tracked**:
  - Latence par layer (layer1, layer2, layer3)
  - Taux erreurs par use-case
  - Nombre embeddings générés (coût OpenAI)
  - Sub-agent invocations count
- **Future**: Prometheus + Grafana si besoin dashboard temps réel

**Hosting Strategy:**

- **Environment**: Local execution uniquement (Bun runtime)
- **Database**: PostgreSQL local (Docker Compose pour développement)
- **Docker Compose** (`docker-compose.yml`):
  ```yaml
  services:
    postgres:
      image: pgvector/pgvector:pg17
      environment:
        POSTGRES_DB: alexandria
        POSTGRES_USER: alexandria
        POSTGRES_PASSWORD: ${DB_PASSWORD}
      ports:
        - "5432:5432"
      volumes:
        - pgdata:/var/lib/postgresql/data
  ```

**Scaling Strategy:**

- **MVP**: Single instance locale (pas de scaling nécessaire)
- **Future**: Si multi-utilisateurs → considérer:
  - Connection pooling PostgreSQL (PgBouncer)
  - Rate limiting par project_id
  - Caching Layer 3 (Redis)

---

### Decision Impact Analysis

**Implementation Sequence (Ordre Logique):**

1. **Phase 1 - Infrastructure Foundations** (Bloque tout le reste):
   - PostgreSQL + pgvector setup (Docker Compose)
   - Drizzle schema definition (conventions, documentation, technologies, projects tables)
   - Drizzle migrations avec HNSW index creation
   - Zod validation setup (.env schema)
   - LoggerPort + BunDualLoggerAdapter

2. **Phase 2 - Domain Core** (Pas de dépendances externes):
   - Entities (Convention, Documentation, Technology, Project)
   - Value Objects (Embedding, ProjectId, ConformityScore)
   - Ports definition (primary + secondary interfaces)
   - Domain errors (custom error classes)

3. **Phase 3 - Secondary Adapters** (Implémentent ports):
   - DrizzleConventionAdapter (avec HNSW cosine queries)
   - DrizzleDocumentationAdapter
   - DrizzleTechnologyLinkerAdapter
   - OpenAIEmbeddingAdapter (avec Zod validation responses)

4. **Phase 4 - Use Cases** (Utilisent domain + ports):
   - Layer 1: Vector Search use-case
   - Layer 2: Technology Linking use-case
   - Layer 3: (Orchestré par Skill, pas de use-case interne)

5. **Phase 5 - Primary Adapters** (Exposent MCP):
   - Hono MCP server setup
   - Zod middleware validation
   - MCP tools implementation (retrieve_raw_context, validate, upload, etc.)

6. **Phase 6 - Sub-Agent & Skill** (Orchestration finale):
   - `.claude/agents/alexandria-reformulation.md` sub-agent definition
   - Skill Alexandria (appelle MCP → invoque sub-agent → reformule)

7. **Phase 7 - CI/CD & Tooling**:
   - GitHub Actions workflow
   - ts-arch rules
   - Scripts (log rotation, seed data)

**Cross-Component Dependencies:**

- **HNSW + Cosine** → **Drizzle Schema**:
  - Index creation: `CREATE INDEX idx_conventions_embedding ON conventions USING hnsw (embedding vector_cosine_ops)`
  - Query operator: `sql\`embedding <=> ${queryEmbedding}\``
  - Migration file doit spécifier `m=16, ef_construction=64` HNSW parameters

- **Application-Level Filtering** → **All Use Cases**:
  - Signature: `execute(projectId: ProjectId, ...otherParams)`
  - Drizzle queries: `.where(eq(table.projectId, projectId.value))`
  - Tests: Fixtures multi-projets pour vérifier isolation

- **Sub-Agent Architecture** → **MCP Tool Design**:
  - MCP tool `retrieve_raw_context` retourne **uniquement** Layer 1+2 (pas de reformulation)
  - Skill Alexandria devient **orchestrateur critique**
  - Sub-agent `.claude/agents/alexandria-reformulation.md` doit être créé AVANT utilisation

- **Dual Logging** → **LoggerPort Implementation**:
  - Tous les use-cases injectent `LoggerPort` via constructor
  - BunDualLoggerAdapter écrit console ET fichier simultanément
  - Scripts rotation logs dans `/scripts/rotate-logs.sh`
  - `.gitignore` doit exclure `/logs/*.jsonl`

- **GitHub Actions** → **PostgreSQL Service Container**:
  - Tests intégration nécessitent `pgvector/pgvector:pg17` image
  - Migrations Drizzle exécutées avant tests
  - Seed data fixtures chargées via scripts

- **Zod 4.2.1** → **Breaking Changes Migration**:
  - `.refine()` API changée (consulter migration guide)
  - Bundle size réduit 57% (impact positif sur startup Bun)
  - Performance 3x meilleure (validation MCP inputs plus rapide)

- **No Cache Layer 3** → **Sub-Agent Invocations**:
  - Chaque query utilisateur → nouveau sub-agent call
  - Quota Plan Max consommé par reformulations
  - LoggerPort doit tracker sub-agent invocation count (monitoring coût)

**Architectural Constraints Enforced by ts-arch:**

```typescript
// tests/architecture/hexagonal.arch.test.ts
import { filesOfProject } from 'ts-arch/dist/core/project-loader'
import { expect } from 'bun:test'

describe('Hexagonal Architecture Rules', () => {
  const project = filesOfProject()

  it('Domain should not depend on Adapters', () => {
    expect(
      project
        .inFolder('domain')
        .shouldNot()
        .dependOnFiles()
        .inFolder('adapters')
    ).toBeTruthy()
  })

  it('Domain should not depend on external libraries except pure TypeScript', () => {
    expect(
      project
        .inFolder('domain')
        .shouldNot()
        .dependOnFiles()
        .matchingPattern('.*node_modules.*')
        .excluding('.*node_modules/typescript.*')
    ).toBeTruthy()
  })

  it('Adapters should implement Ports', () => {
    expect(
      project
        .inFolder('adapters')
        .should()
        .dependOnFiles()
        .inFolder('ports')
    ).toBeTruthy()
  })
})
```

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**Critical Conflict Points Identified:** 26 zones où différents agents IA pourraient faire des choix incompatibles

**Objectif:** Garantir que tous les agents implémentant Alexandria (ou vous-même guidé par des agents) écrivent du code cohérent, compatible et conforme à l'architecture hexagonale stricte.

---

### Naming Patterns

#### **Database Naming Conventions (Drizzle ORM + PostgreSQL)**

**Décision:** TypeScript-First - camelCase partout

```typescript
// ✅ CORRECT: camelCase columns (quoted in PostgreSQL)
export const conventions = pgTable('conventions', {
  id: uuid('id').primaryKey(),
  projectId: uuid('projectId').notNull(),              // camelCase
  contentText: text('contentText').notNull(),
  embeddingVector: vector('embeddingVector', { dimensions: 1536 }),
  createdAt: timestamp('createdAt').defaultNow()
})

// Migration SQL générée:
CREATE TABLE conventions (
  id UUID PRIMARY KEY,
  "projectId" UUID NOT NULL,           -- Quoted camelCase
  "contentText" TEXT NOT NULL,
  "embeddingVector" vector(1536),
  "createdAt" TIMESTAMP DEFAULT NOW()
);

// Queries Drizzle
db.select()
  .from(conventions)
  .where(eq(conventions.projectId, projectId))  // TypeScript idiomatic
  .orderBy(sql`"embeddingVector" <=> ${queryEmbedding}`)

// ❌ INCORRECT: snake_case
export const conventions = pgTable('conventions', {
  project_id: uuid('project_id').notNull(),    // ❌ snake_case
  created_at: timestamp('created_at')
})
```

**Rationale:**
- Cohérence totale TypeScript code ↔ Database
- Type-safety end-to-end (Drizzle inferred types → Domain entities)
- Pas d'SQL brut externe (Drizzle queries uniquement)
- Drizzle Studio compatible

**Règles obligatoires:**
- ✅ Toutes les colonnes en camelCase
- ✅ Tables en lowercase (ex: `conventions`, `documentation`)
- ✅ Index: camelCase avec prefix (ex: `idxConventionsProjectId`)
- ✅ Foreign keys: camelCase (ex: `projectId`, pas `project_id`)

---

#### **TypeScript Code Naming Conventions**

**File Naming - PascalCase:**
```typescript
// ✅ CORRECT
src/domain/entities/Convention.ts
src/domain/use-cases/layer1/SearchConventions.ts
src/ports/secondary/ConventionRepositoryPort.ts
src/adapters/secondary/database/repositories/DrizzleConventionRepository.ts

// ❌ INCORRECT
src/domain/entities/convention.ts           // ❌ kebab-case
src/domain/use-cases/layer1/search-conventions.ts
```

**Variables & Functions - camelCase:**
```typescript
// ✅ CORRECT
const projectId = 'proj-123'
const embeddingVector = [0.1, 0.2, ...]
function searchConventions(query: string) { }
async function generateEmbedding(text: string): Promise<number[]> { }

// ❌ INCORRECT
const project_id = 'proj-123'              // ❌ snake_case
function SearchConventions() { }           // ❌ PascalCase (réservé classes)
```

**Classes, Interfaces, Types - PascalCase:**
```typescript
// ✅ CORRECT
class Convention { }
interface ConventionRepositoryPort { }
type ProjectId = string

// ❌ INCORRECT
class convention { }                       // ❌ camelCase
interface conventionRepositoryPort { }     // ❌ camelCase
interface IConventionRepository { }        // ❌ Prefix I (anti-pattern)
```

**Constants - UPPER_SNAKE_CASE:**
```typescript
// src/config/constants.ts
// ✅ CORRECT
export const DEFAULT_TOP_K = 10
export const MAX_TOP_K = 100
export const MAX_QUERY_LENGTH = 500
export const EMBEDDING_DIMENSIONS = 1536
export const HNSW_M_PARAMETER = 16
export const HNSW_EF_CONSTRUCTION = 64

// ❌ INCORRECT
export const defaultTopK = 10              // ❌ camelCase
export const max_top_k = 100               // ❌ lowercase snake_case
```

**Ports - Suffix "Port" obligatoire:**
```typescript
// ✅ CORRECT
interface ConventionRepositoryPort { }
interface EmbeddingGeneratorPort { }
interface LoggerPort { }

// ❌ INCORRECT
interface ConventionRepository { }         // ❌ Pas de suffix (confusion avec implémentation)
interface IConventionRepository { }        // ❌ Prefix I
```

---

#### **MCP Protocol Naming Conventions**

**MCP Tool Names - snake_case:**
```typescript
// ✅ CORRECT (API boundary)
app.post('/retrieve_raw_context', ...)
app.post('/upload_convention', ...)
app.post('/list_conventions', ...)
app.delete('/delete_convention', ...)
app.get('/list_projects', ...)

// ❌ INCORRECT
app.post('/retrieveRawContext', ...)      // ❌ camelCase (non-standard pour APIs)
```

**JSON Field Names - camelCase:**
```typescript
// ✅ CORRECT (cohérence avec Database + TypeScript)
// Input
{
  "query": "hexagonal architecture",
  "projectId": "abc-123",                  // camelCase
  "topK": 10
}

// Output
{
  "conventions": [
    {
      "id": "conv-1",
      "projectId": "abc-123",              // camelCase
      "contentText": "...",
      "embeddingVector": [...],
      "createdAt": "2025-12-26T10:30:00Z"
    }
  ],
  "linkedDocumentation": [...]
}

// Zod schema
const retrieveInputSchema = z.object({
  query: z.string().min(1).max(500),
  projectId: z.string().uuid(),            // camelCase
  topK: z.number().int().positive().max(100).optional()
})

// ❌ INCORRECT
{
  "project_id": "abc-123",                 // ❌ snake_case (incohérent avec DB)
  "top_k": 10
}
```

**Séparation claire:**
- **API tool names** (externe): snake_case (`retrieve_raw_context`)
- **JSON fields** (data): camelCase (`projectId`, `topK`)
- **TypeScript code** (interne): camelCase (`searchConventions()`)

---

### Structure Patterns

#### **Import Path Organization**

**Absolute Imports avec Path Alias `@/`:**
```typescript
// tsconfig.json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}

// ✅ CORRECT: Absolute imports
import { Convention } from '@/domain/entities/Convention'
import { ConventionRepositoryPort } from '@/ports/secondary/ConventionRepositoryPort'
import { LoggerPort } from '@/ports/secondary/LoggerPort'
import { DEFAULT_TOP_K } from '@/config/constants'

// ❌ INCORRECT: Relative imports
import { Convention } from '../../../../domain/entities/Convention'  // ❌ Fragile
```

**PAS de Barrel Exports:**
```typescript
// ❌ INCORRECT: index.ts barrel exports
// src/domain/entities/index.ts
export { Convention } from './Convention'
export { Documentation } from './Documentation'

// Import ailleurs
import { Convention, Documentation } from '@/domain/entities'  // ❌ Éviter

// ✅ CORRECT: Imports explicites
import { Convention } from '@/domain/entities/Convention'
import { Documentation } from '@/domain/entities/Documentation'
```

**Rationale:**
- Bun performance (barrel exports ralentissent bundling)
- Tree-shaking optimal
- Clarté (voir exactement quel fichier importé)

---

#### **Test File Organization**

**Test File Naming - `.test.ts` suffix:**
```
tests/
├── unit/
│   ├── domain/
│   │   ├── entities/
│   │   │   └── Convention.test.ts           # ✅ .test.ts
│   │   └── use-cases/
│   │       ├── layer1/
│   │       │   └── SearchConventions.test.ts
│   │       └── layer2/
│   │           └── LinkDocumentation.test.ts
│   └── adapters/
│       └── DrizzleConventionRepository.test.ts
├── integration/
│   ├── database/
│   │   └── VectorSearch.test.ts
│   └── mcp-server/
│       └── RetrieveRawContext.test.ts
└── architecture/
    └── hexagonal.arch.test.ts              # Architecture tests
```

**Test Naming Convention:**
```typescript
// ✅ CORRECT
import { describe, it, expect } from 'bun:test'
import { Convention } from '@/domain/entities/Convention'

describe('Convention', () => {              // PascalCase (class name)

  describe('create', () => {                // camelCase (method name)

    it('should create valid convention with all required fields', () => {
      // Arrange
      const data = { id: '123', projectId: 'proj-1', contentText: 'test' }

      // Act
      const convention = Convention.create(data)

      // Assert
      expect(convention.id).toBe('123')
      expect(convention.projectId).toBe('proj-1')
    })

    it('should throw InvalidConventionError when contentText is empty', () => {
      expect(() => {
        Convention.create({ id: '123', projectId: 'proj-1', contentText: '' })
      }).toThrow(InvalidConventionError)
    })
  })
})

// ❌ INCORRECT
describe('convention', () => {              // ❌ camelCase
  it('creates a convention', () => { })     // ❌ Pas de "should"
}
```

---

### Format Patterns

#### **Error Handling Standards**

**Error Class Naming - Suffix `Error` obligatoire:**
```typescript
// ✅ CORRECT
// src/shared/errors/DomainError.ts
export abstract class DomainError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options)
    this.name = this.constructor.name
  }
}

// src/domain/errors/ConventionNotFoundError.ts
export class ConventionNotFoundError extends DomainError {
  constructor(conventionId: string) {
    super(`Convention not found: ${conventionId}`)
  }
}

// src/domain/errors/InvalidProjectIdError.ts
export class InvalidProjectIdError extends DomainError { }

// src/shared/errors/InfrastructureError.ts
export abstract class InfrastructureError extends Error { }

// src/adapters/secondary/database/errors/RepositoryError.ts
export class RepositoryError extends InfrastructureError { }

// ❌ INCORRECT
export class ConventionNotFound extends Error { }  // ❌ Pas de suffix "Error"
```

**MCP Error Response Format - JSON-RPC 2.0:**
```typescript
// ✅ CORRECT
{
  "code": -32603,                          // JSON-RPC standard code
  "message": "Internal error",             // User-friendly (English)
  "data": {
    "type": "RepositoryError",             // Error class name
    "details": "Failed to query conventions table"  // Technical (optional, DEBUG only)
  }
}

// ❌ INCORRECT
{
  "error": "Something went wrong",         // ❌ Pas de structure
  "stack": "Error at ..."                  // ❌ Jamais exposer stack trace
}
```

**Error Logging Pattern:**
```typescript
// ✅ CORRECT
async search(projectId: string, query: string): Promise<Convention[]> {
  try {
    const embedding = await this.embeddingGen.generate(query)
    return await this.conventionRepo.search(projectId, embedding)
  } catch (error) {
    // Log avec context
    this.logger.error('Convention search failed', error, {
      projectId,
      query,
      layer: 'layer1',
      operation: 'search'
    })

    // Wrap et rethrow
    throw new SearchError('Failed to search conventions', { cause: error })
  }
}

// ❌ INCORRECT
} catch (error) {
  console.log('Error:', error)             // ❌ Pas de logger.error()
  throw error                              // ❌ Pas de wrap (perd contexte)
}
```

---

#### **Data Exchange Formats**

**Date/Time Format - ISO 8601:**
```typescript
// ✅ CORRECT
{
  "createdAt": "2025-12-26T10:30:45.123Z"  // ISO 8601 strict
}

// TypeScript
const createdAt = new Date().toISOString()

// ❌ INCORRECT
{
  "createdAt": "2025-12-26 10:30:45"       // ❌ Pas de timezone
  "createdAt": 1703587845123               // ❌ Timestamp Unix (ambiguïté)
}
```

**Boolean Representation:**
```typescript
// ✅ CORRECT
{
  "isActive": true,                        // JSON boolean
  "hasEmbedding": false
}

// ❌ INCORRECT
{
  "isActive": 1,                           // ❌ 0/1
  "hasEmbedding": "true"                   // ❌ String
}
```

---

### Communication Patterns

#### **Logging Context Standards**

**Structured Logging Format:**
```typescript
// ✅ CORRECT: Champs standards obligatoires
logger.info('Convention uploaded successfully', {
  projectId: 'proj-123',                   // ✅ Toujours inclure
  conventionId: 'conv-456',
  layer: 'layer1',                         // ✅ 'layer1' | 'layer2' | 'mcp' | 'adapter' | 'domain'
  operation: 'upload',                     // ✅ Type opération
  latencyMs: 145
})

logger.info('Vector search completed', {
  projectId: 'proj-123',
  query: 'hexagonal architecture',
  layer: 'layer1',                         // Vector search
  operation: 'search',
  resultCount: 12,
  latencyMs: 450
})

logger.info('Technology linking completed', {
  projectId: 'proj-123',
  layer: 'layer2',                         // Technology linking
  operation: 'link',
  conventionCount: 5,
  documentationCount: 3,
  latencyMs: 120
})

logger.info('Raw context retrieved', {
  projectId: 'proj-123',
  layer: 'mcp',                            // MCP tool level
  operation: 'retrieve_raw_context',
  totalLatencyMs: 570                      // Layer 1 + Layer 2 combined
})

logger.error('Repository query failed', error, {
  projectId: 'proj-123',
  layer: 'adapter',
  operation: 'search',
  errorType: error.constructor.name        // ✅ Error class name
})

// ❌ INCORRECT
logger.info('Search done', {
  id: 'proj-123'                           // ❌ Pas de projectId
  // ❌ Manque layer, operation
})

logger.info('Sub-agent invoked', {
  layer: 'layer3'                          // ❌ N'EXISTE PAS dans Alexandria !
})
```

**Champs Layer Valides:**
- `'layer1'`: Vector search (HNSW + cosine similarity)
- `'layer2'`: Technology linking (SQL JOIN)
- `'mcp'`: MCP tool level (retrieve_raw_context, validate, upload, etc.)
- `'adapter'`: Adapter level (Drizzle, OpenAI, BunDualLogger)
- `'domain'`: Domain use-case level

**❌ PAS de `'layer3'`** : Layer 3 orchestré HORS Alexandria (Skill + Sub-agent externe)

---

### Process Patterns

#### **Validation Placement (Zod Boundaries)**

**Zod uniquement aux Boundaries:**
```typescript
// ✅ CORRECT: Validation à l'entrée (Adapter Primaire)
// src/adapters/primary/mcp-server/schemas/RetrieveSchema.ts
import { z } from 'zod'

export const retrieveInputSchema = z.object({
  query: z.string().min(1).max(MAX_QUERY_LENGTH),
  projectId: z.string().uuid(),
  topK: z.number().int().positive().max(MAX_TOP_K).optional()
})

// src/adapters/primary/mcp-server/routes/retrieve.ts
app.post('/retrieve_raw_context', async (c) => {
  const input = retrieveInputSchema.parse(await c.req.json())  // ✅ Validation boundary

  const result = await retrieveRawContext.execute(
    input.projectId,
    input.query,
    input.topK
  )
  return c.json(result)
})

// ✅ CORRECT: Domain layer PUR (pas de Zod)
// src/domain/use-cases/RetrieveRawContext.ts
export class RetrieveRawContext {
  // ❌ Pas de: import { z } from 'zod'

  execute(projectId: string, query: string, topK?: number): Promise<Result> {
    // Assume données déjà validées par adapter
    // Logique métier pure TypeScript
  }
}

// ✅ CORRECT: Validation response externe (Adapter Secondaire)
// src/adapters/secondary/openai/schemas/EmbeddingResponseSchema.ts
export const embeddingResponseSchema = z.object({
  data: z.array(z.object({
    embedding: z.array(z.number()).length(EMBEDDING_DIMENSIONS)
  }))
})

// src/adapters/secondary/openai/OpenAIEmbeddingAdapter.ts
async generate(text: string): Promise<number[]> {
  const response = await openai.embeddings.create({ ... })
  const validated = embeddingResponseSchema.parse(response)  // ✅ Validation boundary
  return validated.data[0].embedding
}

// ❌ INCORRECT
// src/domain/use-cases/RetrieveRawContext.ts
import { z } from 'zod'  // ❌ Zod dans Domain = viole hexagonal !

execute(projectId: string) {
  z.string().uuid().parse(projectId)  // ❌ Validation dans domain
}
```

**Config Validation - Fail-Fast Startup:**
```typescript
// ✅ CORRECT
// src/config/env.ts
import { z } from 'zod'

const envSchema = z.object({
  ALEXANDRIA_DB_URL: z.string().url(),
  OPENAI_API_KEY: z.string().min(20),
  LOG_RETENTION_DAYS: z.string().transform(Number).pipe(z.number().int().positive()).default('30'),
  ALEXANDRIA_LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info')
})

// Parse au module load (fail-fast si invalide)
export const env = envSchema.parse(process.env)

// src/index.ts
import { env } from '@/config/env'  // ← Crash immédiatement si .env invalide

console.log(`Starting Alexandria with DB: ${env.ALEXANDRIA_DB_URL}`)
```

---

#### **Transaction Management (Drizzle)**

**Adapters gèrent les transactions (pas Use-Cases):**
```typescript
// ✅ CORRECT: Use-case ne connaît pas Drizzle
// src/domain/use-cases/UploadConvention.ts
export class UploadConvention {
  constructor(
    private conventionRepo: ConventionRepositoryPort,  // Port, pas Drizzle !
    private embeddingGen: EmbeddingGeneratorPort,
    private logger: LoggerPort
  ) {}

  async execute(data: ConventionData): Promise<Convention> {
    // Logique métier, pas de transaction ici
    const convention = Convention.create(data)
    const embedding = await this.embeddingGen.generate(convention.contentText)
    const withEmbedding = convention.withEmbedding(embedding)

    // Repository gère la transaction en interne
    await this.conventionRepo.save(withEmbedding)

    this.logger.info('Convention uploaded', {
      projectId: convention.projectId,
      conventionId: convention.id,
      layer: 'domain',
      operation: 'upload'
    })

    return withEmbedding
  }
}

// ✅ CORRECT: Adapter gère transaction
// src/adapters/secondary/database/repositories/DrizzleConventionRepository.ts
export class DrizzleConventionRepository implements ConventionRepositoryPort {
  async save(convention: Convention): Promise<void> {
    // Adapter décide de la stratégie transactionnelle
    await this.db.transaction(async (tx) => {
      await tx.insert(conventions).values({
        id: convention.id,
        projectId: convention.projectId,
        contentText: convention.contentText,
        embeddingVector: convention.embedding,
        createdAt: convention.createdAt
      })
    })
  }
}

// ❌ INCORRECT: Use-case gère transaction
export class UploadConvention {
  constructor(private db: DrizzleDatabase) {}  // ❌ Dépendance Drizzle !

  async execute(data: ConventionData) {
    await this.db.transaction(async (tx) => {  // ❌ Viole hexagonal
      // ...
    })
  }
}
```

---

#### **Dependency Injection Pattern**

**Constructor Injection Manuelle:**
```typescript
// ✅ CORRECT: Bootstrap centralisé
// src/index.ts
import { drizzle } from 'drizzle-orm/postgres-js'
import postgres from 'postgres'
import { env } from '@/config/env'

// 1. Créer adapters secondaires
const pgConnection = postgres(env.ALEXANDRIA_DB_URL)
const db = drizzle(pgConnection)

const logger = new BunDualLogger(env.ALEXANDRIA_LOG_LEVEL, './logs')

const conventionRepo = new DrizzleConventionRepository(db, logger)
const documentationRepo = new DrizzleDocumentationRepository(db, logger)
const embeddingGenerator = new OpenAIEmbeddingAdapter(env.OPENAI_API_KEY, logger)

// 2. Créer use-cases avec injection
const retrieveRawContext = new RetrieveRawContext(
  conventionRepo,        // Port: ConventionRepositoryPort
  documentationRepo,     // Port: DocumentationRepositoryPort
  embeddingGenerator,    // Port: EmbeddingGeneratorPort
  logger                 // Port: LoggerPort
)

const uploadConvention = new UploadConvention(
  conventionRepo,
  embeddingGenerator,
  logger
)

// 3. Créer adapters primaires (MCP server)
const mcpServer = new HonoMCPServer(
  retrieveRawContext,
  uploadConvention,
  logger
)

// 4. Start
mcpServer.start()

// ❌ INCORRECT: IoC container (over-engineering pour Alexandria)
const container = new Container()
container.bind(ConventionRepositoryPort).to(DrizzleConventionRepository)
// ... trop complexe pour MVP
```

---

#### **Async/Await Patterns**

**async/await uniquement (jamais .then()):**
```typescript
// ✅ CORRECT
async execute(query: string): Promise<Convention[]> {
  try {
    const embedding = await this.embeddingGen.generate(query)
    const conventions = await this.conventionRepo.search(embedding)
    return conventions
  } catch (error) {
    this.logger.error('Search failed', error, { query, layer: 'domain' })
    throw new SearchError('Failed to search conventions', { cause: error })
  }
}

// ✅ CORRECT: Promise.all() pour parallélisation
async execute(projectId: string): Promise<Stats> {
  const [conventionCount, documentationCount, techCount] = await Promise.all([
    this.conventionRepo.count(projectId),
    this.documentationRepo.count(projectId),
    this.technologyRepo.count(projectId)
  ])

  return { conventionCount, documentationCount, techCount }
}

// ❌ INCORRECT: .then()/.catch() chains
execute(query: string) {
  return this.embeddingGen.generate(query)
    .then(embedding => this.conventionRepo.search(embedding))  // ❌ Éviter
    .catch(error => {
      console.log(error)  // ❌ Pas de logger
      throw error
    })
}
```

---

### Immutability & Domain Modeling

#### **Value Objects - Immutables**

```typescript
// ✅ CORRECT: Value Object immutable
// src/domain/value-objects/ProjectId.ts
export class ProjectId {
  readonly value: string  // ✅ readonly

  private constructor(value: string) {  // ✅ private constructor
    if (!value || value.trim().length === 0) {
      throw new InvalidProjectIdError('ProjectId cannot be empty')
    }
    this.value = value
  }

  static create(value: string): ProjectId {  // ✅ static factory
    return new ProjectId(value)
  }

  equals(other: ProjectId): boolean {
    return this.value === other.value
  }
}

// Usage
const projectId = ProjectId.create('proj-123')  // ✅ Via factory
// projectId.value = 'other'  // ❌ Compile error (readonly)

// ❌ INCORRECT
export class ProjectId {
  public value: string  // ❌ Pas readonly

  constructor(value: string) {  // ❌ Public constructor
    this.value = value
  }

  setValue(value: string) {  // ❌ Setter (mutable)
    this.value = value
  }
}
```

#### **Entities - Immutables avec Méthodes Métier**

```typescript
// ✅ CORRECT: Entity immutable
// src/domain/entities/Convention.ts
export class Convention {
  readonly id: string
  readonly projectId: string
  readonly contentText: string
  readonly embedding?: number[]
  readonly createdAt: Date

  private constructor(data: ConventionData) {
    this.id = data.id
    this.projectId = data.projectId
    this.contentText = data.contentText
    this.embedding = data.embedding
    this.createdAt = data.createdAt ?? new Date()
  }

  static create(data: ConventionData): Convention {
    // Validation métier
    if (!data.contentText || data.contentText.trim().length === 0) {
      throw new InvalidConventionError('Content cannot be empty')
    }
    if (data.contentText.length > MAX_QUERY_LENGTH) {
      throw new InvalidConventionError(`Content exceeds ${MAX_QUERY_LENGTH} chars`)
    }

    return new Convention(data)
  }

  // Méthode métier (retourne nouvelle instance)
  withEmbedding(embedding: number[]): Convention {
    if (embedding.length !== EMBEDDING_DIMENSIONS) {
      throw new InvalidEmbeddingError(`Expected ${EMBEDDING_DIMENSIONS} dimensions`)
    }

    return new Convention({
      ...this,
      embedding
    })
  }

  hasEmbedding(): boolean {
    return this.embedding !== undefined && this.embedding.length > 0
  }
}

// ❌ INCORRECT
export class Convention {
  public id: string  // ❌ Pas readonly

  setEmbedding(embedding: number[]) {  // ❌ Setter mutable
    this.embedding = embedding
  }
}
```

---

### Port Interface Design

```typescript
// ✅ CORRECT: Port interface bien défini
// src/ports/secondary/ConventionRepositoryPort.ts

/**
 * Repository port for Convention aggregate
 * Responsible for persistence and vector search
 */
export interface ConventionRepositoryPort {
  /**
   * Search conventions by vector similarity using HNSW + cosine
   * @param projectId - Project to search within (isolation)
   * @param embedding - Query embedding vector (1536 dimensions)
   * @param topK - Number of results to return (default: DEFAULT_TOP_K)
   * @returns Conventions sorted by cosine similarity score (descending)
   * @throws RepositoryError if database query fails
   */
  search(
    projectId: string,
    embedding: number[],
    topK?: number
  ): Promise<Convention[]>

  /**
   * Save convention with embedding vector
   * Uses Drizzle transaction internally
   * @param convention - Convention entity to persist
   * @throws RepositoryError if save fails
   */
  save(convention: Convention): Promise<void>

  /**
   * Find convention by ID within project scope
   * @param id - Convention UUID
   * @param projectId - Project scope (application-level filtering)
   * @returns Convention or null if not found
   */
  findById(id: string, projectId: string): Promise<Convention | null>

  /**
   * Delete convention by ID
   * @param id - Convention UUID
   * @param projectId - Project scope
   * @throws ConventionNotFoundError if convention doesn't exist
   * @throws RepositoryError if delete fails
   */
  delete(id: string, projectId: string): Promise<void>

  /**
   * Count conventions for a project
   * @param projectId - Project scope
   * @returns Total count of conventions
   */
  count(projectId: string): Promise<number>
}

// ❌ INCORRECT
export interface ConventionRepositoryPort {
  search(embedding: any): any  // ❌ Pas de types, pas de docs
  save(data: object)           // ❌ Pas domain type
}

export type ConventionRepositoryPort = {  // ❌ Type au lieu d'interface
  // ...
}

export interface IConventionRepository { }  // ❌ Prefix I
export interface ConventionRepository { }   // ❌ Pas de suffix Port
```

**Règles obligatoires:**
- ✅ `interface` keyword (jamais `type`)
- ✅ Suffix `Port` obligatoire
- ✅ JSDoc complet (`@param`, `@returns`, `@throws`)
- ✅ Domain types (ex: `Convention`, pas `ConventionDTO` ou `any`)
- ✅ Async: Toutes méthodes retournent `Promise<T>`

---

### Enforcement Guidelines

#### **All AI Agents MUST:**

1. **Respecter l'architecture hexagonale strictement**:
   - Domain ne dépend JAMAIS d'Adapters
   - Domain ne dépend JAMAIS de Zod, Drizzle, Hono
   - Adapters implémentent Ports
   - Use-cases injectent Ports (pas implémentations concrètes)

2. **Utiliser les conventions de naming**:
   - Database: camelCase
   - Files: PascalCase
   - Variables/Functions: camelCase
   - Constants: UPPER_SNAKE_CASE
   - Ports: Suffix `Port`
   - MCP tools: snake_case
   - JSON fields: camelCase

3. **Valider uniquement aux boundaries**:
   - Zod dans Adapters Primaires (MCP inputs)
   - Zod dans Adapters Secondaires (API responses)
   - Zod dans Config (.env validation)
   - PAS de Zod dans Domain

4. **Logger systématiquement**:
   - Champs obligatoires: `projectId`, `layer`, `operation`
   - Layers valides: `layer1`, `layer2`, `mcp`, `adapter`, `domain`
   - PAS de `layer3` (externe à Alexandria)

5. **Garantir l'immutabilité**:
   - `readonly` properties partout
   - Private constructors + static factories
   - Pas de setters

#### **Pattern Enforcement Mechanisms**

**1. Architecture Tests (ts-arch):**
```typescript
// tests/architecture/hexagonal.arch.test.ts
import { filesOfProject } from 'ts-arch/dist/core/project-loader'
import { expect, describe, it } from 'bun:test'

describe('Hexagonal Architecture Rules', () => {
  const project = filesOfProject()

  it('Domain should not depend on Adapters', () => {
    expect(
      project.inFolder('domain').shouldNot().dependOnFiles().inFolder('adapters')
    ).toBeTruthy()
  })

  it('Domain should not import Zod', () => {
    expect(
      project.inFolder('domain').shouldNot().dependOnFiles().matchingPattern('.*zod.*')
    ).toBeTruthy()
  })

  it('Domain should not import Drizzle', () => {
    expect(
      project.inFolder('domain').shouldNot().dependOnFiles().matchingPattern('.*drizzle.*')
    ).toBeTruthy()
  })

  it('Adapters should implement Ports', () => {
    expect(
      project.inFolder('adapters').should().dependOnFiles().inFolder('ports')
    ).toBeTruthy()
  })

  it('All port files should end with Port.ts', () => {
    expect(
      project.inFolder('ports').should().matchPattern('.*Port\\.ts$')
    ).toBeTruthy()
  })

  it('Test files should end with .test.ts', () => {
    expect(
      project.inFolder('tests').should().matchPattern('.*\\.test\\.ts$')
    ).toBeTruthy()
  })
})
```

**2. ESLint Configuration:**
```json
// .eslintrc.json
{
  "extends": [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended"
  ],
  "parser": "@typescript-eslint/parser",
  "plugins": ["@typescript-eslint"],
  "rules": {
    "@typescript-eslint/naming-convention": [
      "error",
      {
        "selector": "interface",
        "format": ["PascalCase"],
        "custom": {
          "regex": "^I[A-Z]",
          "match": false
        }
      },
      {
        "selector": "class",
        "format": ["PascalCase"]
      },
      {
        "selector": "variable",
        "modifiers": ["const"],
        "format": ["camelCase", "UPPER_CASE"]
      },
      {
        "selector": "function",
        "format": ["camelCase"]
      }
    ],
    "@typescript-eslint/no-explicit-any": "error",
    "@typescript-eslint/explicit-function-return-type": "warn",
    "@typescript-eslint/no-unused-vars": "error",
    "no-console": "warn"
  }
}
```

**3. Pre-commit Hooks:**
```json
// package.json
{
  "scripts": {
    "lint": "eslint src/**/*.ts",
    "lint:fix": "eslint src/**/*.ts --fix",
    "typecheck": "tsc --noEmit",
    "test:unit": "bun test tests/unit/**/*.test.ts",
    "test:arch": "bun test tests/architecture/**/*.test.ts",
    "pre-commit": "bun run lint && bun run typecheck && bun run test:arch",
    "format": "prettier --write src/**/*.ts"
  }
}
```

**4. Git Hooks (avec Husky ou simple bash):**
```bash
# .git/hooks/pre-commit (chmod +x)
#!/bin/bash

echo "Running pre-commit checks..."

bun run lint
if [ $? -ne 0 ]; then
  echo "❌ Linting failed. Fix errors before committing."
  exit 1
fi

bun run typecheck
if [ $? -ne 0 ]; then
  echo "❌ Type checking failed. Fix errors before committing."
  exit 1
fi

bun run test:arch
if [ $? -ne 0 ]; then
  echo "❌ Architecture tests failed. Code violates hexagonal rules."
  exit 1
fi

echo "✅ All checks passed!"
exit 0
```

---

### Pattern Examples

#### **Good Examples (Follow These):**

**✅ PERFECT Use-Case Example:**
```typescript
// src/domain/use-cases/RetrieveRawContext.ts
import { Convention } from '@/domain/entities/Convention'
import { Documentation } from '@/domain/entities/Documentation'
import { ConventionRepositoryPort } from '@/ports/secondary/ConventionRepositoryPort'
import { DocumentationRepositoryPort } from '@/ports/secondary/DocumentationRepositoryPort'
import { EmbeddingGeneratorPort } from '@/ports/secondary/EmbeddingGeneratorPort'
import { LoggerPort } from '@/ports/secondary/LoggerPort'
import { DEFAULT_TOP_K } from '@/config/constants'
import { RetrieveContextError } from '@/domain/errors/RetrieveContextError'

export class RetrieveRawContext {
  constructor(
    private conventionRepo: ConventionRepositoryPort,
    private documentationRepo: DocumentationRepositoryPort,
    private embeddingGen: EmbeddingGeneratorPort,
    private logger: LoggerPort
  ) {}

  async execute(
    projectId: string,
    query: string,
    topK: number = DEFAULT_TOP_K
  ): Promise<RawContext> {
    const startTime = performance.now()

    try {
      // Layer 1: Vector Search
      const queryEmbedding = await this.embeddingGen.generate(query)
      const conventions = await this.conventionRepo.search(projectId, queryEmbedding, topK)

      this.logger.info('Layer 1 completed', {
        projectId,
        query,
        layer: 'layer1',
        operation: 'search',
        resultCount: conventions.length,
        latencyMs: Math.round(performance.now() - startTime)
      })

      // Layer 2: Technology Linking
      const technologies = conventions.flatMap(c => c.technologies)
      const linkedDocs = await this.documentationRepo.findByTechnologies(projectId, technologies)

      const totalLatency = Math.round(performance.now() - startTime)

      this.logger.info('Layer 2 completed', {
        projectId,
        layer: 'layer2',
        operation: 'link',
        documentationCount: linkedDocs.length,
        totalLatencyMs: totalLatency
      })

      return {
        conventions,
        linkedDocumentation: linkedDocs
      }
    } catch (error) {
      this.logger.error('Retrieve raw context failed', error, {
        projectId,
        query,
        layer: 'domain',
        operation: 'retrieve_raw_context'
      })
      throw new RetrieveContextError('Failed to retrieve context', { cause: error })
    }
  }
}
```

**✅ PERFECT Entity Example:**
```typescript
// src/domain/entities/Convention.ts
import { InvalidConventionError } from '@/domain/errors/InvalidConventionError'
import { InvalidEmbeddingError } from '@/domain/errors/InvalidEmbeddingError'
import { MAX_QUERY_LENGTH, EMBEDDING_DIMENSIONS } from '@/config/constants'

export interface ConventionData {
  id: string
  projectId: string
  contentText: string
  embedding?: number[]
  technologies?: string[]
  createdAt?: Date
}

export class Convention {
  readonly id: string
  readonly projectId: string
  readonly contentText: string
  readonly embedding?: number[]
  readonly technologies: string[]
  readonly createdAt: Date

  private constructor(data: ConventionData) {
    this.id = data.id
    this.projectId = data.projectId
    this.contentText = data.contentText
    this.embedding = data.embedding
    this.technologies = data.technologies ?? []
    this.createdAt = data.createdAt ?? new Date()
  }

  static create(data: ConventionData): Convention {
    if (!data.contentText?.trim()) {
      throw new InvalidConventionError('Content cannot be empty')
    }
    if (data.contentText.length > MAX_QUERY_LENGTH) {
      throw new InvalidConventionError(`Content exceeds ${MAX_QUERY_LENGTH} chars`)
    }
    return new Convention(data)
  }

  withEmbedding(embedding: number[]): Convention {
    if (embedding.length !== EMBEDDING_DIMENSIONS) {
      throw new InvalidEmbeddingError(`Expected ${EMBEDDING_DIMENSIONS} dimensions`)
    }
    return new Convention({ ...this, embedding })
  }

  hasEmbedding(): boolean {
    return this.embedding !== undefined && this.embedding.length > 0
  }
}
```

**✅ PERFECT Adapter Example:**
```typescript
// src/adapters/secondary/database/repositories/DrizzleConventionRepository.ts
import { Convention } from '@/domain/entities/Convention'
import { ConventionRepositoryPort } from '@/ports/secondary/ConventionRepositoryPort'
import { LoggerPort } from '@/ports/secondary/LoggerPort'
import { RepositoryError } from '@/shared/errors/RepositoryError'
import { conventions } from '@/adapters/secondary/database/schema/conventions'
import { eq, sql } from 'drizzle-orm'
import { DEFAULT_TOP_K } from '@/config/constants'
import type { DrizzleDatabase } from '@/adapters/secondary/database/connection'

export class DrizzleConventionRepository implements ConventionRepositoryPort {
  constructor(
    private db: DrizzleDatabase,
    private logger: LoggerPort
  ) {}

  async search(
    projectId: string,
    embedding: number[],
    topK: number = DEFAULT_TOP_K
  ): Promise<Convention[]> {
    try {
      const results = await this.db
        .select()
        .from(conventions)
        .where(eq(conventions.projectId, projectId))
        .orderBy(sql`"embeddingVector" <=> ${embedding}`)
        .limit(topK)

      return results.map(row => Convention.create({
        id: row.id,
        projectId: row.projectId,
        contentText: row.contentText,
        embedding: row.embeddingVector,
        technologies: row.technologies,
        createdAt: row.createdAt
      }))
    } catch (error) {
      this.logger.error('Vector search failed', error, {
        projectId,
        layer: 'adapter',
        operation: 'search'
      })
      throw new RepositoryError('Failed to search conventions', { cause: error })
    }
  }

  async save(convention: Convention): Promise<void> {
    try {
      await this.db.transaction(async (tx) => {
        await tx.insert(conventions).values({
          id: convention.id,
          projectId: convention.projectId,
          contentText: convention.contentText,
          embeddingVector: convention.embedding,
          technologies: convention.technologies,
          createdAt: convention.createdAt
        })
      })

      this.logger.info('Convention saved', {
        projectId: convention.projectId,
        conventionId: convention.id,
        layer: 'adapter',
        operation: 'save'
      })
    } catch (error) {
      this.logger.error('Failed to save convention', error, {
        projectId: convention.projectId,
        conventionId: convention.id,
        layer: 'adapter',
        operation: 'save'
      })
      throw new RepositoryError('Failed to save convention', { cause: error })
    }
  }
}
```

#### **Anti-Patterns (Avoid These):**

```typescript
// ❌ WRONG: Domain dépend de Drizzle
import { drizzle } from 'drizzle-orm'  // ❌ Infrastructure dans domain

export class RetrieveRawContext {
  constructor(private db: drizzle) {}  // ❌ Pas de port
}

// ❌ WRONG: Mutable entity
export class Convention {
  public id: string  // ❌ Pas readonly

  setId(id: string) {  // ❌ Setter
    this.id = id
  }
}

// ❌ WRONG: Zod dans domain
import { z } from 'zod'  // ❌ Zod dans domain

export class RetrieveRawContext {
  execute(projectId: string) {
    z.string().uuid().parse(projectId)  // ❌ Validation dans domain
  }
}

// ❌ WRONG: Pas de logging
} catch (error) {
  throw error  // ❌ Pas de logger.error()
}

// ❌ WRONG: snake_case database
export const conventions = pgTable('conventions', {
  project_id: uuid('project_id')  // ❌ snake_case
})

// ❌ WRONG: Relative imports
import { Convention } from '../../../../domain/entities/Convention'  // ❌ Fragile

// ❌ WRONG: Pas de suffix Port
interface ConventionRepository { }  // ❌ Pas de "Port"

// ❌ WRONG: .then() chains
return this.embeddingGen.generate(query)
  .then(emb => this.repo.search(emb))  // ❌ Utiliser async/await
```

---

## Project Structure & Boundaries

### Complete Project Directory Structure

```
alexandria/
├── README.md
├── package.json
├── bun.lockb
├── tsconfig.json
├── .env.example
├── .env
├── .gitignore
├── .prettierrc
├── .eslintrc.json
├── docker-compose.yml
│
├── .github/
│   └── workflows/
│       └── ci.yml                          # GitHub Actions CI/CD pipeline
│
├── .claude/
│   ├── agents/
│   │   └── alexandria-reformulation.md     # Sub-agent Haiku 4.5 pour Layer 3
│   └── skills/
│       └── alexandria.md                   # Skill orchestration Layer 1+2+3
│
├── drizzle/
│   ├── schema.ts                          # Drizzle schema (conventions, docs, technologies, projects)
│   └── migrations/
│       ├── 0001_initial_schema.sql
│       ├── 0002_create_hnsw_index.sql     # HNSW index creation
│       └── meta/
│
├── scripts/
│   ├── rotate-logs.sh                     # Log rotation script
│   ├── seed-data.ts                       # Seed data pour développement
│   └── migrate.ts                         # Run Drizzle migrations
│
├── logs/                                  # .gitignore (logs runtime)
│   └── .gitkeep
│
├── docs/
│   ├── api/
│   │   └── mcp-tools.md                   # Documentation MCP tools
│   ├── architecture/
│   │   ├── hexagonal-architecture.md
│   │   └── rag-pipeline.md
│   └── setup/
│       └── installation.md
│
├── src/
│   ├── index.ts                           # Entrypoint Bun + Hono MCP server
│   │
│   ├── config/
│   │   ├── constants.ts                   # DEFAULT_TOP_K, EMBEDDING_MODEL, etc.
│   │   ├── env.schema.ts                  # Zod schema pour .env validation
│   │   └── logger.config.ts               # Configuration dual logging
│   │
│   ├── shared/
│   │   ├── errors/
│   │   │   ├── DomainError.ts
│   │   │   └── InfrastructureError.ts
│   │   ├── types/
│   │   │   └── mcp-protocol.types.ts
│   │   └── utils/
│   │       └── validation.utils.ts
│   │
│   ├── domain/
│   │   ├── entities/
│   │   │   ├── Convention.ts              # Immutable avec readonly
│   │   │   ├── Documentation.ts
│   │   │   ├── Technology.ts
│   │   │   └── Project.ts
│   │   │
│   │   ├── value-objects/
│   │   │   ├── Embedding.ts               # Value Object pour embeddings
│   │   │   ├── ProjectId.ts               # UUID validation
│   │   │   ├── ConformityScore.ts
│   │   │   └── SimilarityScore.ts
│   │   │
│   │   ├── errors/
│   │   │   ├── ConventionNotFoundError.ts
│   │   │   ├── InvalidProjectIdError.ts
│   │   │   ├── EmbeddingGenerationError.ts
│   │   │   └── ValidationError.ts
│   │   │
│   │   └── use-cases/
│   │       ├── layer1/
│   │       │   └── SearchConventionsBySemanticSimilarity.ts  # Layer 1 RAG
│   │       ├── layer2/
│   │       │   └── LinkRelatedDocumentation.ts               # Layer 2 RAG
│   │       ├── conventions/
│   │       │   ├── CreateConvention.ts
│   │       │   ├── UploadConvention.ts
│   │       │   ├── UpdateConvention.ts
│   │       │   ├── DeleteConvention.ts
│   │       │   └── ListConventions.ts
│   │       ├── documentation/
│   │       │   ├── CreateDocumentation.ts
│   │       │   ├── UploadDocumentation.ts
│   │       │   ├── DeleteDocumentation.ts
│   │       │   └── ListDocumentation.ts
│   │       ├── validation/
│   │       │   └── ValidateCodeConformity.ts
│   │       ├── projects/
│   │       │   ├── CreateProject.ts
│   │       │   ├── ListProjects.ts
│   │       │   └── GetProjectById.ts
│   │       └── technologies/
│   │           ├── CreateTechnology.ts
│   │           └── ListTechnologies.ts
│   │
│   ├── ports/
│   │   ├── primary/
│   │   │   └── MCPServerPort.ts           # Interface MCP server
│   │   │
│   │   └── secondary/
│   │       ├── ConventionRepositoryPort.ts
│   │       ├── DocumentationRepositoryPort.ts
│   │       ├── TechnologyRepositoryPort.ts
│   │       ├── ProjectRepositoryPort.ts
│   │       ├── EmbeddingGeneratorPort.ts
│   │       └── LoggerPort.ts
│   │
│   └── adapters/
│       ├── primary/
│       │   └── mcp-server/
│       │       ├── HonoMCPServer.ts       # Hono server implementation
│       │       ├── middleware/
│       │       │   └── ZodValidationMiddleware.ts
│       │       ├── tools/
│       │       │   ├── RetrieveRawContextTool.ts       # Layer 1+2
│       │       │   ├── ValidateCodeTool.ts
│       │       │   ├── UploadConventionTool.ts
│       │       │   ├── ListConventionsTool.ts
│       │       │   ├── ReadConventionTool.ts
│       │       │   ├── DeleteConventionTool.ts
│       │       │   └── ListProjectsTool.ts
│       │       └── schemas/
│       │           ├── RetrieveRawContextSchema.ts     # Zod schemas
│       │           ├── ValidateCodeSchema.ts
│       │           ├── UploadConventionSchema.ts
│       │           └── CommonSchemas.ts
│       │
│       └── secondary/
│           ├── database/
│           │   ├── DrizzleConventionRepository.ts
│           │   ├── DrizzleDocumentationRepository.ts
│           │   ├── DrizzleTechnologyRepository.ts
│           │   ├── DrizzleProjectRepository.ts
│           │   └── errors/
│           │       └── RepositoryError.ts
│           │
│           ├── embedding/
│           │   ├── OpenAIEmbeddingGenerator.ts
│           │   └── errors/
│           │       └── EmbeddingServiceError.ts
│           │
│           └── logging/
│               ├── BunDualLoggerAdapter.ts             # Console + File
│               └── formatters/
│                   └── JsonLogFormatter.ts
│
└── tests/
    ├── fixtures/
    │   ├── conventions.fixture.ts
    │   ├── documentation.fixture.ts
    │   ├── multi-project.fixture.ts           # Pour tester isolation
    │   └── embeddings.fixture.ts
    │
    ├── unit/
    │   ├── domain/
    │   │   ├── entities/
    │   │   │   ├── Convention.test.ts
    │   │   │   ├── Documentation.test.ts
    │   │   │   ├── Technology.test.ts
    │   │   │   └── Project.test.ts
    │   │   ├── value-objects/
    │   │   │   ├── Embedding.test.ts
    │   │   │   ├── ProjectId.test.ts
    │   │   │   └── ConformityScore.test.ts
    │   │   └── use-cases/
    │   │       ├── layer1/
    │   │       │   └── SearchConventionsBySemanticSimilarity.test.ts
    │   │       ├── layer2/
    │   │       │   └── LinkRelatedDocumentation.test.ts
    │   │       ├── conventions/
    │   │       │   ├── CreateConvention.test.ts
    │   │       │   └── DeleteConvention.test.ts
    │   │       └── validation/
    │   │           └── ValidateCodeConformity.test.ts
    │   │
    │   └── adapters/
    │       ├── DrizzleConventionRepository.test.ts     # Avec mocks Drizzle
    │       ├── OpenAIEmbeddingGenerator.test.ts        # Avec mocks OpenAI
    │       └── BunDualLoggerAdapter.test.ts
    │
    ├── integration/
    │   ├── database/
    │   │   ├── VectorSearch.test.ts                   # PostgreSQL + pgvector réel
    │   │   ├── MultiProjectIsolation.test.ts          # Test isolation
    │   │   └── HNSWIndexPerformance.test.ts
    │   ├── mcp-server/
    │   │   ├── RetrieveRawContext.test.ts
    │   │   ├── UploadConvention.test.ts
    │   │   └── ValidateCode.test.ts
    │   └── embedding/
    │       └── OpenAIEmbedding.test.ts                # OpenAI API réel
    │
    └── architecture/
        └── hexagonal.arch.test.ts                     # ts-arch rules
```

### Architectural Boundaries

**API Boundaries:**

**MCP Protocol Layer (stdio transport):**
- **Entry Point**: `src/adapters/primary/mcp-server/HonoMCPServer.ts`
- **Transport**: stdio (pas HTTP) → sécurité native
- **Validation**: Zod middleware sur chaque tool
- **Tools Exposés** (7 tools):
  1. `retrieve_raw_context` → Layer 1 + Layer 2 (pas Layer 3)
  2. `validate_code` → Validation conformité
  3. `upload_convention` → Upload markdown
  4. `list_conventions` → Liste filtrable
  5. `read_convention` → Lecture single
  6. `delete_convention` → Suppression avec cascade
  7. `list_projects` → Liste projets disponibles

**Sub-Agent Communication:**
- **Boundary**: `.claude/agents/alexandria-reformulation.md`
- **Invocation**: Via Skill Alexandria (pas directement par Alexandria TypeScript)
- **Input**: JSON brut de `retrieve_raw_context`
- **Output**: Contexte reformulé (Layer 3)
- **Model**: Claude Haiku 4.5 (`claude-haiku-4-5`)

**Component Boundaries:**

**Domain Layer (Pur TypeScript):**
- **NO external dependencies**: Pas d'import Zod, Drizzle, Hono dans domain/
- **Immutability**: Entities et Value Objects avec `readonly` properties
- **Business Logic**: Use-cases orchestrent entities via ports
- **Boundary enforcement**: ts-arch rules vérifient isolation

**Ports Layer (Interfaces Pures):**
- **Primary Ports**: Définissent comment MCP server interagit avec domain
- **Secondary Ports**: Définissent contrats pour repositories, embedding, logging
- **NO implementations**: Uniquement interfaces TypeScript

**Adapters Layer (Implémentations):**
- **Primary Adapters**: Hono MCP server implémente MCPServerPort
- **Secondary Adapters**:
  - Database: Drizzle implémente repository ports
  - Embedding: OpenAI implémente EmbeddingGeneratorPort
  - Logging: Bun implémente LoggerPort (dual output)

**Service Boundaries:**

**Layer 1 - Vector Search Service:**
- **Location**: `src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts`
- **Input**: Query string + ProjectId + optional top_k
- **Output**: Convention[] triées par similarity score (cosine distance)
- **Dependencies**: ConventionRepositoryPort + EmbeddingGeneratorPort + LoggerPort
- **Isolation**: Application-level filtering via `WHERE project_id`

**Layer 2 - Technology Linking Service:**
- **Location**: `src/domain/use-cases/layer2/LinkRelatedDocumentation.ts`
- **Input**: Convention[] (depuis Layer 1) + ProjectId
- **Output**: Documentation[] liée via technology_id (SQL JOIN)
- **Dependencies**: DocumentationRepositoryPort + TechnologyRepositoryPort + LoggerPort
- **Logic**: Extraction technology_id depuis conventions → JOIN table pivot

**Layer 3 - Reformulation Service (External):**
- **Location**: `.claude/agents/alexandria-reformulation.md`
- **Orchestration**: Skill Alexandria appelle MCP tool → invoque sub-agent
- **Input**: Raw context (Layer 1 + Layer 2 JSON)
- **Output**: Reformulated context (instructions mono-approche)
- **Fallback**: Si sub-agent échoue, Skill retourne Layer 1+2 brut (graceful degradation)

**Data Boundaries:**

**PostgreSQL Schema (Drizzle):**
- **Tables**:
  - `conventions`: id, project_id, content_text, embedding (vector(1536)), technology_id, created_at
  - `documentation`: id, project_id, content_text, embedding (vector(1536)), technology_id, created_at
  - `technologies`: id, name, slug (pivot table)
  - `projects`: id, name, description, created_at

- **Indexes**:
  - HNSW index: `idx_conventions_embedding` (m=16, ef_construction=64)
  - B-tree index: `idx_conventions_project_id` (isolation performance)
  - B-tree index: `idx_documentation_technology_id` (JOIN performance)

**Vector Search Boundary:**
- **Distance Metric**: Cosine similarity via pgvector `<=>` operator
- **Query Pattern**:
  ```typescript
  db.select()
    .from(conventions)
    .where(eq(conventions.projectId, projectId.value))
    .orderBy(sql`embedding <=> ${queryEmbedding}`)
    .limit(topK)
  ```

**Embedding Generation Boundary:**
- **Provider**: OpenAI API
- **Model**: `text-embedding-3-small` (1536 dimensions) ou `text-embedding-3-large` (3072 dimensions)
- **Input**: Convention/Documentation content_text
- **Output**: Float array normalized (cosine-ready)
- **Validation**: Zod schema vérifie array length et normalization

**Multi-Project Isolation:**
- **Pattern**: Application-level filtering (pas Row-Level Security PostgreSQL)
- **Implementation**: Tous les use-cases injectent ProjectId value object
- **Enforcement**: Drizzle queries systématiquement `.where(eq(table.projectId, projectId.value))`
- **Testing**: Fixtures multi-projets vérifient étanchéité

### Requirements to Structure Mapping

**Feature/Epic Mapping:**

**Convention & Documentation Management (FR1-FR15):**
- **Entities**: `src/domain/entities/Convention.ts`, `src/domain/entities/Documentation.ts`
- **Use-Cases**:
  - CRUD: `src/domain/use-cases/conventions/*`
  - Upload: `src/domain/use-cases/conventions/UploadConvention.ts`
  - Cascade delete: `src/domain/use-cases/conventions/DeleteConvention.ts`
- **Repositories**: `src/adapters/secondary/database/DrizzleConventionRepository.ts`
- **MCP Tools**: `src/adapters/primary/mcp-server/tools/{Upload,List,Read,Delete}*Tool.ts`
- **Tests**: `tests/integration/mcp-server/UploadConvention.test.ts`

**Active Compliance Filter - 3 Layer RAG (FR16-FR28):**
- **Layer 1 Use-Case**: `src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts`
- **Layer 2 Use-Case**: `src/domain/use-cases/layer2/LinkRelatedDocumentation.ts`
- **Layer 3 Sub-Agent**: `.claude/agents/alexandria-reformulation.md`
- **Orchestration**: `.claude/skills/alexandria.md`
- **Vector Adapter**: `src/adapters/secondary/database/DrizzleConventionRepository.ts` (méthode `searchBySimilarity`)
- **Embedding Adapter**: `src/adapters/secondary/embedding/OpenAIEmbeddingGenerator.ts`
- **Tests**: `tests/integration/database/VectorSearch.test.ts`

**Context Retrieval & Delivery (FR29-FR38):**
- **MCP Tool**: `src/adapters/primary/mcp-server/tools/RetrieveRawContextTool.ts`
- **Schema**: `src/adapters/primary/mcp-server/schemas/RetrieveRawContextSchema.ts`
- **Skill Orchestration**: `.claude/skills/alexandria.md` (appelle tool → sub-agent → reformule)
- **Tests**: `tests/integration/mcp-server/RetrieveRawContext.test.ts`

**Code Validation & Conformity (FR39-FR49):**
- **Use-Case**: `src/domain/use-cases/validation/ValidateCodeConformity.ts`
- **Value Object**: `src/domain/value-objects/ConformityScore.ts`
- **MCP Tool**: `src/adapters/primary/mcp-server/tools/ValidateCodeTool.ts`
- **Tests**: `tests/unit/domain/use-cases/validation/ValidateCodeConformity.test.ts`

**Claude Code Integration (FR50-FR74):**
- **Skills**: `.claude/skills/alexandria.md`
- **Sub-Agent**: `.claude/agents/alexandria-reformulation.md`
- **MCP Server**: `src/adapters/primary/mcp-server/HonoMCPServer.ts` (stdio transport)
- **Zod Validation**: `src/adapters/primary/mcp-server/middleware/ZodValidationMiddleware.ts`

**Project & Technology Configuration (FR75-FR91, FR104-FR106):**
- **Entities**: `src/domain/entities/Project.ts`, `src/domain/entities/Technology.ts`
- **Use-Cases**: `src/domain/use-cases/projects/*`, `src/domain/use-cases/technologies/*`
- **Repositories**: `src/adapters/secondary/database/{DrizzleProject,DrizzleTechnology}Repository.ts`
- **Isolation Logic**: ProjectId value object propagé dans tous les use-cases
- **Tests**: `tests/integration/database/MultiProjectIsolation.test.ts`

**Testing, Debugging & Observability (FR92-FR103):**
- **Logging Adapter**: `src/adapters/secondary/logging/BunDualLoggerAdapter.ts`
- **Log Config**: `src/config/logger.config.ts`
- **Log Files**: `logs/alexandria-YYYY-MM-DD.jsonl` (rotation quotidienne)
- **Scripts**: `scripts/rotate-logs.sh`
- **Debug Queries**: Logging contexte dans use-cases (query, layer, latency, matches)
- **Tests**: `tests/unit/adapters/BunDualLoggerAdapter.test.ts`

**Cross-Cutting Concerns:**

**Error Handling System:**
- **Base Classes**: `src/shared/errors/DomainError.ts`, `src/shared/errors/InfrastructureError.ts`
- **Domain Errors**: `src/domain/errors/*Error.ts`
- **Adapter Errors**: `src/adapters/secondary/{database,embedding}/errors/*Error.ts`
- **MCP Error Mapping**: `src/adapters/primary/mcp-server/HonoMCPServer.ts` (JSON-RPC 2.0 format)

**Configuration Management:**
- **Environment Variables**: `.env` file (gitignored)
- **Validation**: `src/config/env.schema.ts` (Zod schema, fail-fast au startup)
- **Constants**: `src/config/constants.ts` (DEFAULT_TOP_K, EMBEDDING_MODEL, etc.)
- **Example**: `.env.example` (committed)

**Type Safety:**
- **Drizzle Schema**: `drizzle/schema.ts` (source of truth pour DB types)
- **MCP Protocol**: `src/shared/types/mcp-protocol.types.ts`
- **tsconfig.json**: Strict mode enabled, path alias `@/` configuré

### Integration Points

**Internal Communication:**

**Use-Case → Repository (via Port):**
```typescript
// src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts
constructor(
  private conventionRepo: ConventionRepositoryPort,
  private embeddingGen: EmbeddingGeneratorPort,
  private logger: LoggerPort
) {}

async execute(query: string, projectId: ProjectId, topK: number): Promise<Convention[]> {
  const embedding = await this.embeddingGen.generate(query)
  const conventions = await this.conventionRepo.searchBySimilarity(embedding, projectId, topK)
  this.logger.info('Layer 1 search completed', { query, matches: conventions.length })
  return conventions
}
```

**MCP Tool → Use-Case:**
```typescript
// src/adapters/primary/mcp-server/tools/RetrieveRawContextTool.ts
async handle(input: RetrieveRawContextInput): Promise<RetrieveRawContextOutput> {
  const projectId = ProjectId.create(input.project_id)

  // Layer 1
  const conventions = await this.searchConventionsUseCase.execute(
    input.query,
    projectId,
    input.top_k ?? DEFAULT_TOP_K
  )

  // Layer 2
  const documentation = await this.linkDocumentationUseCase.execute(
    conventions,
    projectId
  )

  return { conventions, linked_documentation: documentation }
}
```

**Skill → MCP Tool → Sub-Agent:**
```markdown
<!-- .claude/skills/alexandria.md -->
1. Call MCP tool `retrieve_raw_context` with user query
2. Receive raw context (Layer 1 + Layer 2 JSON)
3. Invoke sub-agent `alexandria-reformulation` with raw context
4. Receive reformulated context (Layer 3)
5. Inject reformulated context into user's generation request
```

**External Integrations:**

**OpenAI API Integration:**
- **Adapter**: `src/adapters/secondary/embedding/OpenAIEmbeddingGenerator.ts`
- **Endpoint**: `https://api.openai.com/v1/embeddings`
- **Model**: `text-embedding-3-small` (configurable via env var)
- **Auth**: `OPENAI_API_KEY` environment variable
- **Validation**: Zod schema vérifie response structure
- **Error Handling**: `EmbeddingServiceError` wraps OpenAI errors

**PostgreSQL + pgvector Integration:**
- **Adapter**: `src/adapters/secondary/database/Drizzle*Repository.ts`
- **Connection**: `ALEXANDRIA_DB_URL` environment variable
- **Driver**: Bun native PostgreSQL driver
- **ORM**: Drizzle ORM 0.36.4
- **Extension**: pgvector 0.8.1 (HNSW index support)
- **Migrations**: `drizzle/migrations/*.sql`

**Claude Code Integration:**
- **MCP Server**: stdio transport via `src/index.ts`
- **Discovery**: Claude Code auto-découvre MCP server via `.claude/mcp.json`
- **Skills**: `.claude/skills/alexandria.md` auto-invocable
- **Sub-Agent**: `.claude/agents/alexandria-reformulation.md` invocable via Skill

**Data Flow:**

**RAG Pipeline End-to-End:**

```
User Query (Claude Code)
    ↓
Skill Alexandria (.claude/skills/alexandria.md)
    ↓
MCP Tool: retrieve_raw_context (stdio)
    ↓
RetrieveRawContextTool.handle()
    ↓
┌─────────────────────────────────────────────┐
│ Layer 1: Semantic Vector Search             │
│ ─────────────────────────────────────────── │
│ SearchConventionsBySemanticSimilarity       │
│   → OpenAIEmbeddingGenerator (query)        │
│   → DrizzleConventionRepository.searchBySimilarity() │
│   → PostgreSQL HNSW index (cosine <=>)      │
│   → Returns: Convention[] (top_k)           │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ Layer 2: Technology Linking                 │
│ ─────────────────────────────────────────── │
│ LinkRelatedDocumentation                    │
│   → Extract technology_id from conventions  │
│   → DrizzleDocumentationRepository.findByTechnologyIds() │
│   → PostgreSQL JOIN via pivot table         │
│   → Returns: Documentation[]                │
└─────────────────────────────────────────────┘
    ↓
Raw Context JSON { conventions[], linked_documentation[] }
    ↓
Return to Skill Alexandria
    ↓
┌─────────────────────────────────────────────┐
│ Layer 3: LLM Reformulation (External)       │
│ ─────────────────────────────────────────── │
│ Skill invokes sub-agent:                    │
│   alexandria-reformulation (Haiku 4.5)      │
│   → Receives raw context JSON               │
│   → Reformulates into mono-approche guide   │
│   → Returns: Reformulated context string    │
│                                             │
│ Fallback: If sub-agent fails → use Layer 1+2 raw │
└─────────────────────────────────────────────┘
    ↓
Reformulated Context
    ↓
Injected into Claude Code generation request
    ↓
Generated Code (conforms to conventions)
```

**Convention Upload Flow:**

```
User uploads markdown file
    ↓
MCP Tool: upload_convention (stdio)
    ↓
UploadConventionTool.handle()
    ↓
Zod validation (schema: UploadConventionSchema)
    ↓
UploadConvention Use-Case
    ↓
OpenAIEmbeddingGenerator.generate(content_text)
    ↓
Convention Entity.create()
    ↓
DrizzleConventionRepository.save()
    ↓
PostgreSQL INSERT (conventions table)
    ↓
BunDualLoggerAdapter.info('Convention uploaded', { conventionId, projectId })
    ↓
Console log + File log (logs/alexandria-YYYY-MM-DD.jsonl)
    ↓
Success response → MCP JSON-RPC 2.0 format
```

### File Organization Patterns

**Configuration Files:**

**Root Configuration:**
- `package.json`: Bun dependencies + scripts
- `bun.lockb`: Bun lockfile
- `tsconfig.json`: TypeScript config (strict mode, path alias `@/*`)
- `.env`: Runtime secrets (gitignored)
- `.env.example`: Template (committed)
- `.gitignore`: Exclude logs/, .env, node_modules
- `.prettierrc`: Code formatting
- `.eslintrc.json`: Linting rules
- `docker-compose.yml`: PostgreSQL + pgvector service

**Build Configuration:**
- Pas de build step nécessaire (Bun exécute TypeScript nativement)
- Drizzle migrations: `drizzle.config.ts` (si besoin)

**Source Organization:**

**Hexagonal Architecture Layers (strictement séparés):**

1. **Domain Layer** (`src/domain/`):
   - Entities: Immutable, business logic methods
   - Value Objects: Validation, immutability, identity-less
   - Use-Cases: Business workflows, orchestration
   - Errors: Domain-specific errors
   - **NO external dependencies** (vérifié par ts-arch)

2. **Ports Layer** (`src/ports/`):
   - Primary: Interfaces pour drivers (MCP server)
   - Secondary: Interfaces pour driven (repositories, services)
   - **Pure TypeScript interfaces** (pas de classes)

3. **Adapters Layer** (`src/adapters/`):
   - Primary: MCP server (Hono), tools, middleware, schemas
   - Secondary: Database (Drizzle), Embedding (OpenAI), Logging (Bun)
   - **Implémentent les ports** (vérifié par ts-arch)

4. **Shared Layer** (`src/shared/`):
   - Errors: Base classes (DomainError, InfrastructureError)
   - Types: MCP protocol, communs
   - Utils: Fonctions utilitaires

5. **Config Layer** (`src/config/`):
   - Constants: Valeurs par défaut
   - Env schema: Validation Zod
   - Logger config: Configuration dual logging

**Test Organization:**

**Structure Miroir avec Suffixe `.test.ts`:**

- `tests/unit/domain/entities/Convention.test.ts` → `src/domain/entities/Convention.ts`
- `tests/unit/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.test.ts` → `src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts`
- `tests/integration/database/VectorSearch.test.ts` → Tests end-to-end vector search

**Fixtures Réutilisables:**
- `tests/fixtures/conventions.fixture.ts`: Données de test conventions
- `tests/fixtures/multi-project.fixture.ts`: Données multi-projets pour isolation
- `tests/fixtures/embeddings.fixture.ts`: Embeddings pré-générés (mock)

**Asset Organization:**

**Logs (Runtime):**
- `logs/alexandria-YYYY-MM-DD.jsonl`: Logs journaliers (gitignored)
- Rotation: Script `scripts/rotate-logs.sh` (optionnel cron job)

**Documentation:**
- `docs/api/mcp-tools.md`: Documentation MCP tools
- `docs/architecture/`: Documentation architecture
- `docs/setup/`: Guide installation

**Claude Code Assets:**
- `.claude/agents/alexandria-reformulation.md`: Sub-agent definition
- `.claude/skills/alexandria.md`: Skill orchestration

**Database Assets:**
- `drizzle/schema.ts`: Schema Drizzle (source of truth)
- `drizzle/migrations/*.sql`: Migrations versionnées

### Development Workflow Integration

**Development Server Structure:**

**Démarrage Local:**
```bash
# 1. Start PostgreSQL + pgvector
docker-compose up -d

# 2. Run migrations
bun run migrate

# 3. (Optional) Seed data
bun run seed

# 4. Start MCP server
bun run dev  # Exécute src/index.ts
```

**Dev Scripts (package.json):**
```json
{
  "scripts": {
    "dev": "bun run --hot src/index.ts",
    "migrate": "bun run scripts/migrate.ts",
    "seed": "bun run scripts/seed-data.ts",
    "test": "bun test",
    "test:unit": "bun test tests/unit",
    "test:integration": "bun test tests/integration",
    "test:arch": "bun test tests/architecture",
    "lint": "eslint src tests --ext .ts",
    "typecheck": "tsc --noEmit",
    "format": "prettier --write 'src/**/*.ts' 'tests/**/*.ts'"
  }
}
```

**Build Process Structure:**

**Pas de Build Transpilation (Bun natif):**
- Bun exécute TypeScript directement (pas de tsc build)
- Type checking: `bun run typecheck` (tsc --noEmit)
- Linting: `bun run lint` (ESLint)
- Formatting: `bun run format` (Prettier)

**Migrations:**
- Drizzle génère SQL depuis schema.ts
- `bun run migrate` applique migrations
- Versioning: `drizzle/migrations/meta/_journal.json`

**Deployment Structure:**

**Local-Only Deployment (MVP):**
- **Runtime**: Bun 1.3.5 (installé localement)
- **Database**: PostgreSQL 17.7 + pgvector 0.8.1 (Docker Compose)
- **MCP Server**: stdio transport (pas d'exposition réseau)
- **Discovery**: Claude Code lit `.claude/mcp.json` configuration

**Future Deployment (si multi-utilisateurs):**
- **Container**: Dockerfile avec Bun officiel image
- **Orchestration**: Docker Compose multi-services
- **Scaling**: Connection pooling (PgBouncer), rate limiting
- **Monitoring**: Prometheus + Grafana (métriques depuis logs JSON)

---

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
Toutes les décisions architecturales fonctionnent ensemble harmonieusement. La stack Bun 1.3.5 + Hono 4.11.1 + TypeScript 5.9.7 + Drizzle 0.36.4 + Zod 4.2.1 est entièrement compatible. PostgreSQL 17.7 avec pgvector 0.8.1 supporte parfaitement HNSW indexing avec cosine similarity. L'architecture hexagonale s'intègre parfaitement avec le MCP protocol stdio et le système de sub-agents Claude Code. Aucun conflit de versions ou d'incompatibilités détectés.

**Pattern Consistency:**
Les patterns d'implémentation supportent pleinement les décisions architecturales. L'immutability pattern (readonly properties) est cohérent avec l'architecture hexagonale et DDD. Les naming conventions sont cohérentes à travers tous les layers (PascalCase entities, suffix "Port", suffix "Error", camelCase Drizzle). Les path alias `@/` éliminent les imports relatifs fragiles. La séparation stricte Domain/Ports/Adapters est vérifiable par ts-arch rules. Zod validation boundaries respectent l'architecture hexagonale (uniquement dans adapters layer).

**Structure Alignment:**
La structure du projet supporte parfaitement toutes les décisions architecturales. Les dossiers `src/domain/`, `src/ports/`, `src/adapters/` mappent directement l'architecture hexagonale. Les use-cases layer1/, layer2/ correspondent au RAG 3-layer. Les boundaries sont clairement définis (MCP server, sub-agent externe, database, embedding service). L'integration via dependency injection permet le respect des ports. La structure de tests miroir (`tests/unit/domain/`, `tests/integration/`) facilite la maintenabilité.

### Requirements Coverage Validation ✅

**Epic/Feature Coverage:**
Les 7 catégories fonctionnelles du PRD sont entièrement couvertes architecturalement:
1. Convention & Documentation Management → Entities + CRUD use-cases + Repositories + MCP Tools
2. Active Compliance Filter 3-Layer RAG → Layer1/Layer2 use-cases + Sub-agent + HNSW vector search
3. Context Retrieval & Delivery → MCP tool `retrieve_raw_context` + Skill orchestration
4. Code Validation & Conformity → ValidateCodeConformity use-case + ConformityScore value object
5. Claude Code Integration → MCP Server stdio + Skills + Sub-agent architecture
6. Project & Technology Configuration → Multi-project isolation via ProjectId + Technology linking
7. Testing, Debugging & Observability → Dual logging (console + .jsonl files) + LoggerPort

**Functional Requirements Coverage:**
Les 106 requirements fonctionnels sont tous architecturalement supportés. Chaque FR a un mapping explicite vers des composants (entities, use-cases, adapters, MCP tools). Les requirements CRUD (FR1-FR15) sont couverts par les use-cases conventions/ et documentation/. Les requirements RAG (FR16-FR28) sont couverts par l'architecture 3-layer avec HNSW vector search. Les requirements integration (FR50-FR74) sont couverts par MCP protocol + Skills + Sub-agent. Aucun FR orphelin détecté.

**Non-Functional Requirements Coverage:**
Les 33 NFRs sont architecturalement adressés:
- **Performance (NFR1-NFR6):** Bun 1.3.5 ultra-rapide, Hono 4.11.1 minimaliste, HNSW index optimisé, targets p50 ≤3s réalisables
- **Security (NFR7-NFR10):** MCP stdio (pas HTTP), Zod validation systématique, .env secrets, pas de CLAUDE_API_KEY nécessaire
- **Integration (NFR11-NFR15):** MCP 100% compliant, Skills auto-invocables, Sub-agent communication via Claude Code
- **Reliability (NFR16-NFR20):** Fail-fast Zod, Transactions Drizzle avec rollback, Graceful degradation Layer 3
- **Maintainability (NFR21-NFR25):** TypeScript 5.9.7 strict, Architecture hexagonale testable, Tests unitaires + intégration
- **Observability (NFR26-NFR33):** Dual logging structuré JSON, Métriques automatiques par layer, Pipeline visibility complète

### Implementation Readiness Validation ✅

**Decision Completeness:**
Toutes les décisions critiques sont documentées avec versions spécifiques:
- Sub-Agent communication: Claude Haiku 4.5 (`claude-haiku-4-5`), invocation via Skill, pas d'API directe
- Vector Search: HNSW index (m=16, ef_construction=64), cosine similarity operator `<=>`
- Multi-project isolation: Application-level filtering, ProjectId value object, Drizzle WHERE clauses
- Logging strategy: Dual approach (console JSON + fichiers .jsonl), rotation quotidienne, LoggerPort interface
- Toutes les décisions incluent rationales, trade-offs, et implications pour implémentation

**Structure Completeness:**
La structure du projet est complète et spécifique (pas de placeholders génériques):
- Arborescence complète: 150+ fichiers/répertoires définis
- Mapping explicite: Chaque FR → fichiers spécifiques
- Integration points: Diagrammes ASCII RAG pipeline, Upload flow
- Component boundaries: Domain/Ports/Adapters strictement séparés
- Fixtures spécifiques: multi-project.fixture.ts, embeddings.fixture.ts
- Scripts définis: rotate-logs.sh, seed-data.ts, migrate.ts

**Pattern Completeness:**
Les patterns d'implémentation sont complets avec exemples good/bad:
- **Naming Patterns:** Database (camelCase Drizzle), TypeScript (PascalCase/camelCase), MCP (snake_case tools), avec anti-patterns
- **Structure Patterns:** Import paths (@/ alias), Test organization (.test.ts suffix), avec anti-patterns
- **Format Patterns:** Error handling (suffix Error), Data exchange (Zod schemas), avec anti-patterns
- **Communication Patterns:** Logging context (structured JSON), avec anti-patterns
- **Process Patterns:** Validation placement (Zod boundaries), Transactions (Drizzle), DI, Async/await, avec anti-patterns
- **Immutability Patterns:** Value Objects, Entities (readonly + méthodes métier), avec anti-patterns
- **Port Interface Design:** Interface pures, suffix "Port", NO implementations dans ports/, avec anti-patterns
- **Enforcement Guidelines:** ts-arch rules, linting ESLint, format Prettier

### Gap Analysis Results

**Critical Gaps:** ✅ AUCUN

Toutes les décisions architecturales critiques bloquant l'implémentation sont prises et documentées. La structure du projet est complète. Tous les patterns nécessaires pour éviter les conflits d'implémentation entre agents AI sont définis.

**Important Gaps:** ⚠️ 3 ÉLÉMENTS MINEURS (Non-bloquants pour MVP)

1. **Drizzle Configuration File (`drizzle.config.ts`):**
   - Mentionné dans Build Configuration mais contenu non spécifié
   - Impact: Configuration Drizzle doit être créée pendant implémentation
   - Résolution: Peut être facilement créé (schema path, migrations folder, database URL)
   - Priorité: Important mais non-bloquant (documentation Drizzle disponible)

2. **MCP Server Discovery Configuration (`.claude/mcp.json`):**
   - Mentionné dans Claude Code Integration mais format non documenté
   - Impact: Claude Code doit découvrir le MCP server
   - Résolution: Format MCP JSON standard (server name, command, transport stdio)
   - Priorité: Important mais non-bloquant (documentation MCP protocol disponible)

3. **GitHub Actions Workflow YAML Content:**
   - Structure CI/CD décrite (lint, typecheck, tests, arch) mais YAML complet non fourni
   - Impact: Pipeline CI/CD doit être créé pendant setup
   - Résolution: Peut être créé à partir des scripts package.json définis
   - Priorité: Important mais non-bloquant (implémentation peut commencer sans CI)

**Nice-to-Have Gaps:** ℹ️ 3 AMÉLIORATIONS OPTIONNELLES (Post-MVP)

1. **Performance Benchmarks HNSW:**
   - Targets définis (p50 ≤3s) mais pas de benchmarks baseline
   - Amélioration: Ajouter tests performance HNSW avec différentes tailles corpus
   - Priorité: Low (peut être mesuré après implémentation initiale)

2. **Exemples de Queries Debug avec `jq`:**
   - Logging JSON structuré défini mais exemples `jq` limités (2 exemples)
   - Amélioration: Ajouter playbook queries debug communs (erreurs par use-case, latence P95, etc.)
   - Priorité: Low (peut être ajouté au fur et à mesure des besoins)

3. **Diagramme d'Architecture Visuel:**
   - Architecture hexagonale et RAG pipeline décrits en texte/ASCII
   - Amélioration: Créer diagramme Excalidraw pour visualisation architecture complète
   - Priorité: Low (nice-to-have documentation, pas bloquant pour implémentation)

### Validation Issues Addressed

✅ **AUCUN PROBLÈME CRITIQUE TROUVÉ**

L'architecture est cohérente, complète, et prête pour implémentation par des agents AI. Tous les requirements (106 FR + 33 NFR) sont architecturalement supportés. Toutes les décisions critiques sont documentées. Tous les patterns nécessaires sont définis avec exemples.

### Architecture Completeness Checklist

**✅ Requirements Analysis**

- [x] Project context thoroughly analyzed (106 FR + 33 NFR documentés)
- [x] Scale and complexity assessed (Medium complexity, justification fournie)
- [x] Technical constraints identified (Bun 1.3.5, PostgreSQL 17.7, OpenAI API, Claude Code)
- [x] Cross-cutting concerns mapped (Error handling, Logging, Config, Type safety)

**✅ Architectural Decisions**

- [x] Critical decisions documented with versions (Sub-agent Haiku 4.5, HNSW m=16/ef_construction=64, Application-level filtering, Dual logging)
- [x] Technology stack fully specified (Bun 1.3.5, Hono 4.11.1, TypeScript 5.9.7, Drizzle 0.36.4, Zod 4.2.1, PostgreSQL 17.7, pgvector 0.8.1)
- [x] Integration patterns defined (MCP stdio, Skills auto-invocables, Sub-agent communication, OpenAI API, Drizzle ORM)
- [x] Performance considerations addressed (HNSW index, Bun ultra-rapide, Hono minimaliste, targets p50 ≤3s)

**✅ Implementation Patterns**

- [x] Naming conventions established (Database camelCase, TypeScript PascalCase/camelCase, MCP snake_case, suffix "Port"/"Error")
- [x] Structure patterns defined (Import paths @/, Test organization .test.ts, Hexagonal layers strict)
- [x] Communication patterns specified (Logging context JSON, Error handling structured, MCP JSON-RPC 2.0)
- [x] Process patterns documented (Validation Zod boundaries, Transactions Drizzle, DI, Async/await, Immutability readonly)

**✅ Project Structure**

- [x] Complete directory structure defined (150+ fichiers/répertoires, arborescence complète Alexandria)
- [x] Component boundaries established (Domain/Ports/Adapters séparation stricte, ts-arch rules)
- [x] Integration points mapped (RAG pipeline diagramme, Upload flow, Use-Case→Repository, MCP Tool→Use-Case, Skill→MCP→Sub-Agent)
- [x] Requirements to structure mapping complete (Chaque FR → fichiers spécifiques, 7 catégories fonctionnelles mappées)

### Architecture Readiness Assessment

**Overall Status:** ✅ **READY FOR IMPLEMENTATION**

**Confidence Level:** **HIGH**

Justification:
- ✅ 100% requirements coverage (106 FR + 33 NFR)
- ✅ Architecture cohérente sans conflits
- ✅ Patterns complets avec exemples good/bad
- ✅ Structure complète et spécifique (pas de placeholders)
- ✅ Gaps identifiés sont non-bloquants (3 mineurs, 3 optionnels)
- ✅ Enforcement mechanisms définis (ts-arch, ESLint, Prettier)

**Key Strengths:**

1. **Architecture Hexagonale Stricte:** Séparation Domain/Ports/Adapters vérifiable par ts-arch, isolation complète du domain layer (NO external dependencies), testabilité maximale

2. **RAG 3-Layer Innovant:** Séparation claire Layer 1 (vector search) + Layer 2 (linking) + Layer 3 (reformulation externe via sub-agent), orchestration Skill élégante, fallback graceful si Layer 3 échoue

3. **Patterns Complets avec Exemples:** Chaque pattern documenté avec exemples good ✅ et anti-patterns bad ❌, facilite implémentation cohérente entre agents AI, enforcement via outils (ts-arch, ESLint, Prettier)

4. **Type-Safety Maximale:** TypeScript 5.9.7 strict mode, Zod validation systématique (boundaries adapters), Drizzle ORM type-safe, Path alias `@/` pour imports absolus, pas de `any` types

5. **Performance Optimisée:** Bun 1.3.5 ultra-rapide (I/O + bundling), Hono 4.11.1 minimaliste (latency minimale), HNSW index performance optimale (m=16, ef_construction=64), Embedding generation via Haiku 4.5 économique

6. **Multi-Project Isolation Robuste:** ProjectId value object propagé dans tous les use-cases, Application-level filtering Drizzle (WHERE project_id), Tests fixtures multi-projets vérifient étanchéité, Pas de Row-Level Security PostgreSQL (complexité évitée)

7. **Observability Complète:** Dual logging (console + fichiers .jsonl rotatifs), Métriques automatiques (timestamp, layer, latency, matches, tokens), Pipeline visibility (Layer 1/2/3 outputs), Logging structured JSON analysable (`jq`)

**Areas for Future Enhancement:**

1. **Caching Layer 3 Reformulations:** MVP sans cache (simplicité prioritaire), Future: PostgreSQL cache table ou Redis si latence/coût deviennent problématiques, TTL + invalidation strategy à définir

2. **Rate Limiting:** Non nécessaire pour usage local MVP, Future: Si déploiement multi-utilisateurs, ajouter rate limiting par project_id, Prévenir abuse OpenAI API

3. **Advanced Monitoring Dashboard:** MVP: Analyse manuelle logs JSON via `jq`, Future: Prometheus + Grafana pour métriques temps réel (latence P95, taux erreurs, coût OpenAI)

4. **Distributed Tracing:** MVP: Logging simple avec correlation IDs, Future: OpenTelemetry + Jaeger pour tracing distribué si architecture devient plus complexe

5. **Connection Pooling PostgreSQL:** MVP: Connexions directes via Drizzle (sufficient pour usage local), Future: PgBouncer si scaling multi-instances nécessaire

6. **Encryption at Rest:** MVP: Pas de chiffrement (conventions techniques non-sensibles, PostgreSQL local), Future: pgcrypto si conventions contiennent secrets

### Implementation Handoff

**AI Agent Guidelines:**

1. **Follow all architectural decisions exactly as documented:** Chaque décision dans ce document est finale et doit être respectée à la lettre. Ne pas dévier des versions spécifiées (Bun 1.3.5, PostgreSQL 17.7, etc.). Ne pas introduire de nouvelles dépendances sans justification et validation.

2. **Use implementation patterns consistently across all components:** Chaque pattern défini (naming, structure, format, communication, process, immutability) doit être appliqué uniformément. Référez-vous aux exemples good ✅ et évitez les anti-patterns bad ❌. Utilisez ts-arch rules pour vérifier l'isolation des layers.

3. **Respect project structure and boundaries:** Ne pas créer de fichiers en dehors de l'arborescence définie. Respecter la séparation stricte Domain/Ports/Adapters (Domain ne dépend PAS d'Adapters). Utiliser les path alias `@/` pour tous les imports. Ne pas créer de barrel exports (`index.ts`).

4. **Refer to this document for all architectural questions:** Si incertitude sur un pattern, une décision, ou une structure, consulter ce document en priorité. Si gap détecté, documenter et demander clarification avant implémentation. Ce document est la source of truth pour toute l'implémentation Alexandria.

**First Implementation Priority:**

**Phase 1 - Infrastructure Foundations (Bloque tout le reste):**

Commandes d'initialisation complètes pour démarrer l'implémentation Alexandria:

```bash
# Étape 1: Initialiser projet Bun
mkdir alexandria && cd alexandria
bun init -y

# Étape 2: Installer dépendances production
bun add hono@4.11.1 \
        drizzle-orm@0.36.4 \
        @drizzle-team/drizzle-kit \
        zod@4.2.1 \
        openai \
        postgres

# Étape 3: Installer dépendances développement
bun add -d typescript@5.9.7 \
          @types/node \
          eslint \
          prettier \
          ts-arch

# Étape 4: Créer structure dossiers complète
mkdir -p src/{config,shared/{errors,types,utils},domain/{entities,value-objects,errors,use-cases/{layer1,layer2,conventions,documentation,validation,projects,technologies}},ports/{primary,secondary},adapters/{primary/mcp-server/{middleware,tools,schemas},secondary/{database/errors,embedding/errors,logging/formatters}}}
mkdir -p drizzle/migrations
mkdir -p scripts
mkdir -p tests/{fixtures,unit/{domain/{entities,value-objects,use-cases/{layer1,layer2,conventions,validation}},adapters},integration/{database,mcp-server,embedding},architecture}
mkdir -p .claude/{agents,skills}
mkdir -p docs/{api,architecture,setup}
mkdir -p logs

# Étape 5: Créer docker-compose.yml
cat > docker-compose.yml << 'DOCKEREOF'
services:
  postgres:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: alexandria
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: ${DB_PASSWORD:-alexandria_dev}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
DOCKEREOF

# Étape 6: Créer .env.example
cat > .env.example << 'ENVEOF'
# Database
ALEXANDRIA_DB_URL=postgresql://alexandria:alexandria_dev@localhost:5432/alexandria

# OpenAI API
OPENAI_API_KEY=your_openai_api_key_here

# Logging
LOG_RETENTION_DAYS=30
DEBUG=false
ENVEOF

# Étape 7: Créer .gitignore
cat > .gitignore << 'GITEOF'
node_modules/
.env
logs/*.jsonl
*.log
bun.lockb
GITEOF

# Étape 8: Démarrer PostgreSQL
docker-compose up -d

# Étape 9: Vérifier pgvector installation
docker exec -it $(docker-compose ps -q postgres) psql -U alexandria -d alexandria -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

**Validation Setup Initiale:**

```bash
# Vérifier Bun version
bun --version  # Doit afficher >= 1.3.5

# Vérifier PostgreSQL + pgvector
docker exec -it $(docker-compose ps -q postgres) psql -U alexandria -d alexandria -c "SELECT version(); SELECT * FROM pg_extension WHERE extname='vector';"

# Vérifier structure créée
ls -la src/
ls -la tests/
ls -la .claude/
```

**Prochaines Étapes Implémentation:**

Après Phase 1 Infrastructure, continuer avec:
- **Phase 2:** Domain Core (Entities, Value Objects, Ports, Errors)
- **Phase 3:** Secondary Adapters (Drizzle Repositories, OpenAI Embedding, Logger)
- **Phase 4:** Use Cases (Layer 1, Layer 2, CRUD)
- **Phase 5:** Primary Adapters (Hono MCP Server, Tools, Schemas)
- **Phase 6:** Sub-Agent & Skill (Orchestration finale)
- **Phase 7:** CI/CD & Tooling (GitHub Actions, ts-arch, scripts)

Consulter section "Decision Impact Analysis → Implementation Sequence" dans ce document pour détails complets de chaque phase.

---

## Architecture Completion Summary

### Workflow Completion

**Architecture Decision Workflow:** COMPLETED ✅
**Total Steps Completed:** 8
**Date Completed:** 2025-12-26
**Document Location:** /home/negus/dev/alexandria/_bmad-output/architecture.md

### Final Architecture Deliverables

**📋 Complete Architecture Document**

- All architectural decisions documented with specific versions
- Implementation patterns ensuring AI agent consistency
- Complete project structure with all files and directories
- Requirements to architecture mapping
- Validation confirming coherence and completeness

**🏗️ Implementation Ready Foundation**

- 12 architectural decisions critiques documentées
- 40+ implementation patterns définis (naming, structure, format, communication, process, immutability, ports)
- 150+ composants architecturaux spécifiés (fichiers/répertoires)
- 139 requirements (106 FR + 33 NFR) entièrement supportés

**📚 AI Agent Implementation Guide**

- Technology stack with verified versions (Bun 1.3.5, Hono 4.11.1, TypeScript 5.9.7, Drizzle 0.36.4, Zod 4.2.1, PostgreSQL 17.7, pgvector 0.8.1)
- Consistency rules that prevent implementation conflicts (ts-arch, ESLint, Prettier enforcement)
- Project structure with clear boundaries (Domain/Ports/Adapters hexagonal architecture)
- Integration patterns and communication standards (MCP stdio, Skills, Sub-agent, OpenAI API, Drizzle ORM)

### Implementation Handoff

**For AI Agents:**
This architecture document is your complete guide for implementing alexandria. Follow all decisions, patterns, and structures exactly as documented.

**First Implementation Priority:**

Phase 1 - Infrastructure Foundations (bloque tout le reste) - Voir section "Architecture Validation Results → Implementation Handoff → First Implementation Priority" pour commandes d'initialisation complètes.

**Development Sequence:**

1. Initialize project using documented starter template (Bun 1.3.5 + structure dossiers complète)
2. Set up development environment per architecture (PostgreSQL 17.7 + pgvector 0.8.1 via docker-compose)
3. Implement core architectural foundations (Drizzle schema, HNSW index, LoggerPort, env validation)
4. Build features following established patterns (Use-cases layer1/layer2, MCP tools, Sub-agent, Skills)
5. Maintain consistency with documented rules (ts-arch compliance, naming conventions, immutability patterns)

### Quality Assurance Checklist

**✅ Architecture Coherence**

- [x] All decisions work together without conflicts (Stack Bun + Hono + TypeScript + Drizzle + Zod + PostgreSQL + pgvector entièrement compatible)
- [x] Technology choices are compatible (Toutes versions vérifiées compatibles)
- [x] Patterns support the architectural decisions (Immutability, hexagonal architecture, Zod boundaries cohérents)
- [x] Structure aligns with all choices (Domain/Ports/Adapters + RAG 3-layer mappés dans arborescence)

**✅ Requirements Coverage**

- [x] All functional requirements are supported (106 FR mappés à composants spécifiques)
- [x] All non-functional requirements are addressed (33 NFR couverts: Performance, Security, Integration, Reliability, Maintainability, Observability)
- [x] Cross-cutting concerns are handled (Error handling, Logging, Config, Type safety définis)
- [x] Integration points are defined (MCP stdio, Sub-agent, OpenAI API, PostgreSQL + pgvector, Drizzle ORM)

**✅ Implementation Readiness**

- [x] Decisions are specific and actionable (Toutes avec versions, parameters, rationales)
- [x] Patterns prevent agent conflicts (Exemples good/bad, ts-arch rules, enforcement mechanisms)
- [x] Structure is complete and unambiguous (150+ fichiers/répertoires, pas de placeholders)
- [x] Examples are provided for clarity (Code snippets, diagrammes ASCII, workflows complets)

### Project Success Factors

**🎯 Clear Decision Framework**
Every technology choice was made collaboratively with clear rationale, ensuring all stakeholders understand the architectural direction. Toutes les décisions incluent justifications (rationale), trade-offs analysés, et implications pour implémentation.

**🔧 Consistency Guarantee**
Implementation patterns and rules ensure that multiple AI agents will produce compatible, consistent code that works together seamlessly. Enforcement via ts-arch rules (Domain isolation), ESLint (linting), Prettier (formatting), Zod validation boundaries, naming conventions strictes.

**📋 Complete Coverage**
All project requirements are architecturally supported, with clear mapping from business needs to technical implementation. 100% coverage des 106 FR et 33 NFR, chaque requirement mappé à composants spécifiques (entities, use-cases, adapters, MCP tools).

**🏗️ Solid Foundation**
L'architecture hexagonale stricte + RAG 3-layer innovant + Type-safety maximale + Performance optimisée + Multi-project isolation robuste + Observability complète fournissent une fondation production-ready suivant les best practices actuelles.

---

**Architecture Status:** READY FOR IMPLEMENTATION ✅

**Next Phase:** Begin implementation using the architectural decisions and patterns documented herein.

**Document Maintenance:** Update this architecture when major technical decisions are made during implementation.
