# Developer Tool Specific Requirements

## Project-Type Overview

Alexandria est un **Developer Tool** conçu pour s'intégrer nativement dans l'écosystème Claude Code. Contrairement aux outils classiques nécessitant des interfaces séparées, Alexandria fonctionne entièrement via le Marketplace Claude Code et son serveur MCP, permettant une expérience fluide et intégrée directement dans le workflow de développement.

**Philosophie d'intégration:**
- Zéro friction d'installation (tout via Marketplace)
- Zéro interface externe (100% dans Claude Code)
- Zéro configuration manuelle complexe (MCP tools + slash commands)

## Technical Architecture Considerations

**Agnosticité du Contenu Traité (Non de l'Implémentation)**

Alexandria est conçu pour **traiter** des conventions et documentation pour n'importe quel langage de programmation ou stack technologique:

- **Contenu traité agnostique**: Peut stocker et gérer conventions pour Python, TypeScript, Java, Groovy, Go, Rust, etc.
- **Framework agnostique**: Documentation FastAPI, Spring, NestJS, Django, Express, etc.
- **Domaine agnostique**: Backend, frontend, mobile, DevOps, data science, etc.

**Implémentation d'Alexandria (Stack Technique Fixe - MVP):**

- **Runtime**: Bun 1.3.5 (JavaScript runtime ultra-rapide, acquis par Anthropic déc 2025, 3-4x plus performant que Node.js)
- **Framework Web**: Hono 4.11.1 (TypeScript, framework web minimaliste ~12KB, edge-ready, multi-runtime)
- **Langage**: TypeScript 5.9.7 (strict mode, typage statique complet, dernière branche 5.x stable)
- **ORM**: Drizzle ORM 0.36.4 (type-safe, performant, support natif pgvector via `drizzle-orm/pg-core`)
- **Validation**: Zod 4.2.1 (17 déc 2025, bundle 57% plus petit, 3x plus rapide, runtime validation type-safe, MCP protocol compliance)
- **Base de données**: PostgreSQL 17.7 (13 nov 2025) avec extension pgvector 0.8.1 (iterative scans, HNSW optimisé)
- **Embeddings**: OpenAI Embeddings API (text-embedding-3-small ou text-embedding-3-large)
- **LLM Reformulation**: Claude Haiku 4.5 (claude-haiku-4-5, 15 oct 2025, 73.3% SWE-bench, $1/M input $5/M output) via sub-agent Claude Code (Layer 3)
- **Architecture**: Hexagonale (permet expérimentation et swap de stack si besoin)

**Notes importantes:**
- Bun acquis par Anthropic (2 déc 2025): excellente compatibilité future garantie avec Claude Code
- Zod 4.2.1 choisi (dernière version, breaking changes depuis 3.x mais performances 3x meilleures, bundle 57% plus petit)
- PostgreSQL 17.7 recommandé pour pgvector 0.8.1 (abandonne support PostgreSQL 12)
- Claude Haiku 4.5 significativement meilleur que 3.5 (73.3% SWE-bench Verified)
- Drizzle ORM 0.36.4 stable, v1.0 en pré-release (fév 2025) à surveiller pour migration future

**Principe fondamental:** Ce qui compte n'est pas le langage **d'implémentation** d'Alexandria, mais sa capacité à gérer conventions + documentation de manière structurée pour **n'importe quel langage de projet utilisateur**. Le système RAG stratifié fonctionne indépendamment des technologies des projets traités.

**Gestion Multi-Technologie:**

Via la table pivot `convention_technologies`:
- Une convention peut déclarer plusieurs technologies: `["python", "fastapi", "pydantic"]`
- Une documentation appartient à une technologie: `technology_id: "fastapi"`
- Le linking automatique fonctionne pour toute combinaison de technologies

**Architecture Hexagonale (Ports & Adapters) - Contrainte Fondamentale**

Alexandria **DOIT** être implémenté selon l'architecture hexagonale pour garantir:

**Objectifs de l'Hexagonale:**

1. **Expérimentation Technique Facilitée**
   - Double objectif du projet: résoudre le problème concret ET maîtriser les architectures RAG
   - Permet de swap les technologies d'implémentation sans refonte du core métier
   - Exemples de swaps possibles:
     - PostgreSQL+pgvector → Qdrant/Weaviate/Chroma
     - Claude Haiku reformulation → GPT-4/Mistral/Llama local
     - Bun+Hono → Node.js+Express, Deno, Python+FastAPI
     - Embeddings OpenAI → Voyage/Cohere/local (sentence-transformers)

2. **Évolution Post-MVP Non-Destructive**
   - Roadmap prévoit changements technologiques (Crawl4AI, UI web, Jira/Confluence)
   - Ajout de nouveaux adapters sans toucher au domaine métier
   - Réduction drastique du risque de régression

3. **Testabilité Critique**
   - NFR23 exige tests unitaires et d'intégration
   - Ports mockables pour tester la logique métier isolément
   - Tests d'intégration par adapter (PostgreSQL, Qdrant, etc.)
   - Validation pipeline Layer 1→2→3 indépendamment

4. **Isolation Naturelle des 3 Layers RAG**
   - Layer 1, 2, 3 correspondent à des ports distincts du domaine
   - Chaque layer testable et remplaçable indépendamment

**Ports Primaires Identifiés (Driven by External Actors):**

- `MCPServerPort`: Interface MCP protocol (7 tools: retrieve, validate, upload, list, read, delete, list_projects)
- `SkillPort`: Interface skills auto-invoqués par Claude Code
- `SlashCommandPort`: Interface commandes interactives utilisateur

**Ports Secondaires Identifiés (Driving Infrastructure):**

- `ConventionRepositoryPort`: Storage + vector search conventions (Layer 1)
- `DocumentationRepositoryPort`: Storage + retrieval documentation technique (Layer 2)
- `TechnologyLinkerPort`: Gestion liens convention ↔ technologies via pivot table
- `EmbeddingGeneratorPort`: Génération embeddings vectoriels (OpenAI, Voyage, local)
- `LLMRefinerPort`: Reformulation LLM Layer 3 via sub-agent Claude Code (pas d'API directe)
- `LoggerPort`: Observabilité et metrics (NFR26-33)

**Adapters Initiaux (MVP - Bun/Hono/TypeScript + Drizzle + Zod):**

- `DrizzleConventionAdapter` (implémente ConventionRepositoryPort avec Drizzle ORM + pgvector, schemas Zod pour validation)
- `DrizzleDocumentationAdapter` (implémente DocumentationRepositoryPort avec Drizzle ORM)
- `DrizzleTechnologyLinkerAdapter` (implémente TechnologyLinkerPort avec Drizzle ORM, gestion pivot table)
- `ClaudeCodeSubAgentAdapter` (implémente LLMRefinerPort - invoque sub-agent `alexandria-reformulation` configuré avec Haiku)
- `OpenAIEmbeddingAdapter` (implémente EmbeddingGeneratorPort avec OpenAI Embeddings API, validation Zod responses)
- `BunConsoleLoggerAdapter` (implémente LoggerPort avec structured logging JSON)
- `ZodValidationMiddleware` (validation MCP tools inputs/outputs, configuration .env, slash commands parameters)

**Adapters Futurs (Expérimentation Post-MVP):**

- `QdrantVectorAdapter`, `ChromaVectorAdapter`, `WeaviateVectorAdapter` (alternatives vector stores)
- Adapters reformulation alternatifs (sub-agents avec différents modèles ou API directes si besoin)
- `VoyageEmbeddingAdapter`, `CohereEmbeddingAdapter` (alternatives embeddings API)
- `LocalEmbeddingAdapter` (sentence-transformers via ONNX ou transformers.js, pas d'API externe)
- `PrometheusMetricsAdapter`, `DatadogAdapter` (alternatives observabilité)
- Runtime alternatifs: Node.js, Deno, Python+FastAPI (si swap nécessaire)

**Bénéfices Concrets pour KPIs:**

- **Maintenabilité** (NFR21-25): Code découplé, documentation claire des responsabilités
- **Tests** (NFR23): Coverage facilité par isolation des ports
- **Expérimentation**: Swap rapide de stack pour comparer performances/coûts
- **Évolution**: Roadmap post-MVP implémentable sans dette technique

**Rôle de Drizzle ORM dans l'Architecture Hexagonale:**

Drizzle ORM est utilisé **exclusivement dans les adapters** (ports secondaires), jamais dans le domain core:

**Bénéfices Drizzle:**
- Type-safety complète: Schemas Drizzle → Types TypeScript inférés
- Queries type-safe: Autocomplétion complète, erreurs compile-time
- Support pgvector natif: Requêtes vectorielles optimisées
- Migrations avec Drizzle Kit: Gestion schéma versionnée
- Performance: ~10x moins d'overhead que Prisma
- Bun compatible: Excellent support, zero config

**Adapters utilisant Drizzle:**
- `DrizzleConventionAdapter`: CRUD conventions + vector search Layer 1
- `DrizzleDocumentationAdapter`: CRUD documentation technique
- `DrizzleTechnologyLinkerAdapter`: Gestion pivot table `convention_technologies`

**Principe hexagonal respecté:**
Le domain core **ne connaît pas Drizzle**, il utilise uniquement les ports abstraits (`ConventionRepositoryPort`, etc.). Si swap vers Prisma/TypeORM nécessaire, seuls les adapters changent.

**Rôle de Zod dans l'Architecture Hexagonale:**

Zod assure la **validation aux boundaries** (entrées/sorties du système):

**Cas d'usage critiques:**

1. **MCP Protocol Compliance (NFR11)**
   - Validation inputs MCP tools (retrieve, validate, upload, list, read, delete)
   - Validation outputs format JSON conforme
   - Schemas Zod → Types TypeScript (type-safety end-to-end)

2. **Configuration Validation (NFR7 - Fail Fast)**
   - Validation `.env` au startup (ALEXANDRIA_DB_URL, OPENAI_API_KEY)
   - Erreur immédiate si credentials manquantes ou invalides
   - Types inférés pour config utilisée dans l'app

3. **Slash Commands Parameters (NFR13)**
   - Validation paramètres optionnels avec defaults
   - Messages d'erreur clairs si invalides
   - Type-safe command routing

4. **API Boundaries (Ports Primaires)**
   - Validation inputs à l'entrée des ports (`MCPServerPort`, `SlashCommandPort`)
   - Garantit contrat des ports respecté
   - Prévention injections et données malformées

5. **External Services Validation (Ports Secondaires)**
   - Validation réponses OpenAI API (embeddings format)
   - Validation données PostgreSQL avant insertion
   - Detection erreurs API early

**Middleware Zod:**
`ZodValidationMiddleware` intégré dans Hono pour validation automatique requêtes/réponses MCP.

**Principe hexagonal respecté:**
Zod valide aux **frontières** uniquement (ports), le domain core travaille avec types TypeScript déjà validés.

**Distribution & Installation**

**Marketplace Claude Code - Point d'Entrée Unique:**

Tout passe par le Marketplace Claude Code pour garantir une installation simplifiée:

1. **Skills Alexandria:**
   - `alexandria-context` (skill principal)
   - `alexandria-validate` (skill de validation)

2. **Slash Commands:**
   - `/alexandria-query`
   - `/alexandria-validate`
   - `/alexandria-config`
   - `/alexandria-list`
   - `/alexandria-read`
   - `/alexandria-delete`

3. **Sub-Agent:**
   - `alexandria-reformulation` (Agent Haiku Layer 3)

4. **MCP Server:**
   - `alexandria-mcp-server` avec ses tools

**Skills Crawl4AI & DocLing:**

Skills complémentaires (post-MVP) disponibles via Marketplace:
- Contiennent les images Docker nécessaires
- Permettent aux développeurs de récupérer et lancer les containers
- Installation automatisée via le Marketplace

**Aucun Package Manager Externe:**

- ❌ Pas de `npm install alexandria`
- ❌ Pas de `pip install alexandria`
- ✅ Installation 100% via Marketplace Claude Code

**Configuration Requise:**

Variables d'environnement minimales:
- `ALEXANDRIA_DB_URL` - Connexion PostgreSQL + pgvector (ex: `postgresql://user:pass@localhost:5432/alexandria`)
- `OPENAI_API_KEY` - Clé API OpenAI pour génération embeddings
- Configuration additionnelle via fichier `.env` ou MCP tool `alexandria_config`

**Notes:**
- Pas besoin de `CLAUDE_API_KEY` car la reformulation Layer 3 utilise un sub-agent Claude Code (`alexandria-reformulation`) invoqué via le système de sub-agents, pas d'appel API direct
- Runtime: Bun (compatible avec ecosystem npm/node, installation via `bun install`)

## Integration Architecture

**Skills Alexandria**

**1. `alexandria-context` (Skill Principal - Auto-Invoqué)**

- **Trigger:** Auto-invoqué par Claude Code pendant génération de code/documentation
- **Flow:**
  1. Reçoit requête contextuelle de Claude Code
  2. Appelle MCP tool `alexandria_retrieve_context`
  3. Délègue au sub-agent `alexandria-reformulation` (Layer 3)
  4. Retourne contexte fusionné à Claude Code
- **Usage:** Transparent pour l'utilisateur, fonctionne automatiquement

**2. `alexandria-validate` (Skill de Validation Post-Code)**

- **Trigger:** Invoqué après génération de code pour validation
- **Flow:**
  1. Reçoit code généré
  2. Appelle MCP tool `alexandria_validate_code`
  3. Compare code contre conventions projet
  4. Retourne rapport de conformité
- **Usage:** Auto-invoqué ou manuel via slash command

**Slash Commands**

**1. `/alexandria-query <question>` (Test & Debug)**

- **Objectif:** Test manuel des requêtes Alexandria avec affichage debug
- **Output:**
  - Contexte retrieval Layer 1 (conventions trouvées)
  - Contexte retrieval Layer 2 (docs liées via technologies)
  - Contexte reformulé Layer 3 (guide fusionné)
  - Métriques de pertinence et temps de réponse
- **Usage:** Validation efficacité des conventions/docs uploadées

**2. `/alexandria-validate <code_snippet>` (Validation Explicite)**

- **Objectif:** Validation explicite du code contre conventions (post-review)
- **Input:** Code snippet ou fichier
- **Output:**
  - Liste violations de conventions détectées
  - Suggestions de correction
  - Score de conformité
- **Usage:** Phase review manuelle ou debugging

**3. `/alexandria-config` (Menu Interactif CRUD)**

- **Objectif:** Point d'entrée interactif pour toutes les opérations CRUD
- **Menu Interactif:**
  1. Upload convention/documentation (Create)
  2. List conventions/documentations (List)
  3. View convention/documentation (Read)
  4. Delete convention/documentation (Delete)
  5. Setup nouveau projet avec technologies
- **Usage:** Gestion complète knowledge base via interface conversationnelle

**4. `/alexandria-list [type] [technology]` (Liste Rapide)**

- **Objectif:** Liste rapide des documents sans menu interactif
- **Paramètres optionnels:**
  - `type`: convention | documentation | all (défaut: all)
  - `technology`: filter par technologie spécifique
- **Output:** Liste documents avec ID, nom, type, technologies, date
- **Usage:** Consultation rapide de l'inventaire

**5. `/alexandria-read <document_id>` (Lecture Document)**

- **Objectif:** Afficher le contenu complet d'un document spécifique
- **Input:** `document_id`
- **Output:**
  - Contenu markdown complet
  - Métadonnées (type, technologies, projet, date création)
- **Usage:** Consulter une convention/doc avant modification ou référence

**6. `/alexandria-delete <document_id>` (Suppression Document)**

- **Objectif:** Supprimer un document de la knowledge base
- **Input:** `document_id`
- **Confirmation:** Demande confirmation avant suppression définitive
- **Process:** Supprime document + embeddings + liens `convention_technologies`
- **Output:** Confirmation suppression avec détails du document supprimé
- **Usage:** Nettoyage knowledge base, suppression docs obsolètes

**Sub-Agent Alexandria**

**`alexandria-reformulation` (Agent Haiku - Layer 3 Spécialisé)**

- **Modèle:** Claude Haiku 3.5 (optimisation coût vs Sonnet)
- **Invocation:** Via système de sub-agents Claude Code (pas d'API directe depuis Alexandria)
- **Rôle:** Fusion conventions + docs en guide cohérent mono-approche
- **Responsabilités:**
  1. Reçoit conventions (Layer 1) + documentation (Layer 2) brutes
  2. Analyse contradictions entre sources
  3. Élimine syntaxe obsolète vs actuelle
  4. Fusionne en guide d'implémentation contraint
  5. Valide structure et pertinence
  6. Retourne contexte optimisé à Claude Code
- **Autonomie:** Fonctionnement automatique, pas d'intervention utilisateur
- **Intégration:** Alexandria invoque le sub-agent via `ClaudeCodeSubAgentAdapter` (port hexagonal)

**MCP Server Architecture**

**`alexandria-mcp-server`**

Serveur MCP exposant les tools Alexandria pour intégration Claude Code.

**MCP Tools:**

**1. `alexandria_retrieve_context`**

- **Fonction:** Retrieval RAG Layer 1+2 (pgvector + JOIN technologies)
- **Input:** Query utilisateur (text)
- **Process:**
  - Recherche vectorielle conventions pertinentes (Layer 1)
  - Récupère `technology_id` des conventions via table pivot
  - JOIN SQL pour ramener docs liées aux mêmes technologies (Layer 2)
- **Output:** Conventions + Documentation brutes (avant reformulation)

**2. `alexandria_validate_code`**

- **Fonction:** Validation code contre conventions projet
- **Input:** Code snippet + project_id
- **Process:**
  - Récupère conventions projet via project_id
  - Compare code contre règles via LLM
  - Détecte violations et non-conformités
- **Output:** Rapport de conformité avec violations détectées

**3. `alexandria_upload_convention`**

- **Fonction:** Upload nouvelle convention/doc dans knowledge base
- **Input:**
  - Fichier markdown (convention ou documentation)
  - Type: `convention` | `documentation`
  - Technologies: `["python", "fastapi"]` (pour conventions)
  - Technology_id: `"fastapi"` (pour documentation)
  - Project_id: identifiant projet
- **Process:**
  - Parse fichier markdown
  - Génère embeddings vectoriels (pgvector)
  - Insère dans PostgreSQL avec métadonnées
  - Crée liens via `convention_technologies` si convention
- **Output:** Confirmation upload avec ID généré

**4. `alexandria_list_projects`**

- **Fonction:** Liste projets configurés avec leurs conventions
- **Input:** (optionnel) filter par technologies
- **Output:**
  - Liste projets avec métadonnées
  - Nombre conventions par projet
  - Nombre documentations par technologie
  - Technologies configurées

**5. `alexandria_list_documents`**

- **Fonction:** Liste détaillée des documents (conventions et documentations)
- **Input:**
  - `project_id` (optionnel) - Filter par projet
  - `type` (optionnel) - convention | documentation | all (défaut: all)
  - `technology` (optionnel) - Filter par technologie spécifique
- **Output:**
  - Liste documents avec métadonnées complètes:
    - `document_id`, `name`, `type`, `technologies`, `project_id`
    - `created_date`, `size` (nombre caractères/tokens)
    - `embedding_count` (nombre chunks vectorisés)

**6. `alexandria_read_document`**

- **Fonction:** Récupère le contenu complet d'un document spécifique
- **Input:** `document_id`
- **Output:**
  - Contenu markdown complet
  - Métadonnées:
    - Type (convention/documentation)
    - Technologies associées
    - Project_id
    - Date création et dernière modification
    - Informations embeddings (nombre, dimensions)

**7. `alexandria_delete_document`**

- **Fonction:** Supprime un document et toutes ses données associées
- **Input:** `document_id`
- **Process:**
  - Supprime document de la table principale
  - Supprime tous les embeddings vectoriels associés
  - Supprime tous les liens dans `convention_technologies`
  - Cascade suppression complète
- **Output:**
  - Confirmation suppression
  - Détails du document supprimé (nom, type, technologies)
  - Nombre d'embeddings supprimés
  - Nombre de liens supprimés

## API Surface & Integration Points

**Pas d'API REST Externe:**

Alexandria n'expose **pas d'API REST** publique. Toute interaction se fait via:
- Skills (invoqués par Claude Code)
- Slash Commands (tapés par utilisateur)
- MCP Tools (appelés par skills/sub-agents)

**Protocole MCP Uniquement:**

- Communication Claude Code ↔ Alexandria via protocole MCP
- Sécurité et authentification gérées par Claude Code
- Pas besoin de gérer tokens/auth custom

## Documentation Requirements

**Documentation via GitHub Pages:**

**1. Guide d'Installation**

- Prérequis (Docker, Claude Code)
- Installation via Marketplace
- Configuration PostgreSQL + pgvector
- Variables d'environnement
- Vérification installation

**2. Descriptif des Composants**

- **Skills:** Fonctionnement `alexandria-context` et `alexandria-validate`
- **Slash Commands:** Usage de tous les commands (query, validate, config, list, read, delete)
- **Sub-Agent:** Rôle `alexandria-reformulation` et architecture Layer 3
- **MCP Server:** Description server et 7 tools disponibles (retrieve, validate, upload, list_projects, list_documents, read, delete)

**3. Exemples d'Utilisation**

- Onboarding nouveau projet (upload conventions + docs via `/alexandria-config`)
- Configuration technologies et linking
- Consultation knowledge base (`/alexandria-list`, `/alexandria-read`)
- Maintenance (suppression docs obsolètes via `/alexandria-delete`)
- Test requêtes avec `/alexandria-query`
- Workflow quotidien avec auto-invocation `alexandria-context`
- Validation post-code avec `alexandria-validate`

**Pas de Code Examples/Migration Guides dans MVP:**

- ❌ Pas d'exemples de code programmatique
- ❌ Pas de guides de migration
- ✅ Focus sur usage pratique via Claude Code

## Implementation Considerations

**Workflow Développeur Type:**

```
1. Installation (une fois)
   - Install skills via Marketplace
   - Configure PostgreSQL + MCP server
   - Setup variables d'environnement

2. Onboarding Projet (par projet)
   - `/alexandria-config` pour upload conventions
   - `/alexandria-config` pour upload docs techniques
   - Déclare technologies pour linking automatique

3. Validation Setup (test)
   - `/alexandria-query "comment créer API endpoint FastAPI?"`
   - Vérifie contexte retourné (conventions + docs fusionnées)
   - Ajuste si nécessaire

4. Usage Quotidien (automatique)
   - Claude Code invoque automatiquement `alexandria-context`
   - Code généré conforme dès première itération
   - `/alexandria-validate` en post-review si besoin

5. Maintenance Knowledge Base (périodique)
   - `/alexandria-list` pour voir inventaire complet
   - `/alexandria-read <doc_id>` pour consulter une convention/doc
   - `/alexandria-delete <doc_id>` pour supprimer docs obsolètes
   - `/alexandria-config` menu interactif pour gestion CRUD complète
```

**Séparation des Responsabilités:**

- **Skills:** Orchestration et invocation MCP tools
- **Sub-Agent:** Intelligence Layer 3 (reformulation)
- **MCP Server:** Logique métier RAG + PostgreSQL
- **PostgreSQL + pgvector:** Stockage et recherche vectorielle

**Pas d'Interface Web:**

- ❌ Pas de frontend à développer dans MVP
- ❌ Pas de serveur web séparé
- ✅ Interaction 100% via Claude Code

**Scalabilité Architecture:**

- PostgreSQL handle multi-projets via `project_id`
- pgvector optimisé pour millions d'embeddings
- MCP server stateless, peut scaler horizontalement (futur)
- Sub-agent Haiku économique en coûts
