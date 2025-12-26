# Project Context Analysis

## Requirements Overview

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

## Scale & Complexity

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

## Technical Constraints & Dependencies

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

## Cross-Cutting Concerns Identified

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
