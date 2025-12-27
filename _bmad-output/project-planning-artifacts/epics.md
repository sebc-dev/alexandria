---
stepsCompleted: [1, 2]
inputDocuments:
  - '/home/negus/dev/alexandria/_bmad-output/prd.md'
  - '/home/negus/dev/alexandria/_bmad-output/architecture.md'
workflowDecisions:
  - 'CI/CD repositionné en Epic 2 (immédiatement après Infrastructure) pour validation continue'
  - '7 épopées organisées par valeur utilisateur'
  - 'Couverture complète: 151 requirements (106 FRs + 33 NFRs + 12 Architecture)'
epicStructureApproved: true
totalEpics: 7
---

# alexandria - Épopées et Stories

## Aperçu

Ce document fournit la décomposition complète des épopées et stories pour **alexandria**, en transformant les exigences du PRD et de l'Architecture en stories implémentables.

## Ordre d'Exécution des Epics

### Séquentiel (Complétion Obligatoire dans l'Ordre)

1. **Epic 1: Local Development Environment Ready** → Doit être complété en premier
2. **Epic 2: Automated Quality Gates Enforce Architecture Compliance** → Requiert Epic 1 (infrastructure)

### Après Completion Epic 1+2

3. **Epic 3: Knowledge Base Management** → Indépendant, peut démarrer immédiatement

### Après Completion Epic 3

- **Epic 4: Intelligent Context Fusion Prevents Code Drift** → Requiert documents d'Epic 3
- **Epic 6: Code Validation & Conformity** → Requiert conventions d'Epic 3 (indépendant d'Epic 4)

### Après Completion Epic 4

- **Epic 5: Claude Code Integration** → Requiert pipeline RAG d'Epic 4
- **Epic 7: Observability & Debugging** → Observe pipeline (peut démarrer plus tôt pour logging infrastructure)

### Graphe de Dépendances

```
Epic 1 (Foundation)
  └─> Epic 2 (Quality Gates)
       └─> Epic 3 (Knowledge Base)
            ├─> Epic 4 (RAG Filter)
            │    ├─> Epic 5 (Claude Code Integration)
            │    └─> Epic 7 (Observability)
            └─> Epic 6 (Code Validation - indépendant de 4-5)
```

## Inventaire des Exigences

### Exigences Fonctionnelles

**Convention & Documentation Management (FR1-FR15):**

- **FR1**: Les utilisateurs peuvent uploader des documents de conventions en format markdown dans la base de connaissances
- **FR2**: Les utilisateurs peuvent uploader de la documentation technique en format markdown dans la base de connaissances
- **FR3**: Les utilisateurs peuvent spécifier manuellement le type de document (convention vs documentation) lors de l'upload
- **FR4**: Les utilisateurs peuvent lister toutes les conventions et documentations avec options de filtrage
- **FR5**: Les utilisateurs peuvent filtrer les documents par type (convention, documentation, ou tous)
- **FR6**: Les utilisateurs peuvent filtrer les documents par technologie associée
- **FR7**: Les utilisateurs peuvent filtrer les documents par identifiant de projet
- **FR8**: Les utilisateurs peuvent visualiser le contenu complet de tout document stocké
- **FR9**: Les utilisateurs peuvent visualiser les métadonnées des documents (type, technologies, projet, date de création)
- **FR10**: Les utilisateurs peuvent supprimer des conventions ou documentations de la base de connaissances
- **FR11**: Les utilisateurs peuvent accéder à un menu CRUD interactif pour toutes les opérations sur les documents
- **FR12**: Le système peut confirmer les opérations de suppression avant la suppression définitive
- **FR13**: Le système peut supprimer en cascade les embeddings associés lorsqu'un document est supprimé
- **FR14**: Le système peut supprimer en cascade les liens de technologies lorsqu'un document est supprimé
- **FR15**: Le système peut parser les fichiers markdown et extraire le contenu pour le stockage

**Active Compliance Filter - Architecture RAG 3 Layers (FR16-FR28):**

- **FR16**: Le système peut effectuer une recherche vectorielle sémantique sur les conventions (Layer 1)
- **FR17**: Le système peut récupérer les conventions basées sur la similarité de requête en utilisant pgvector
- **FR18**: Le système peut présenter les conventions comme des "lois non-négociables" à l'agent IA
- **FR19**: Le système peut lier automatiquement la documentation technique aux conventions via les identifiants de technologie (Layer 2)
- **FR20**: Le système peut récupérer la documentation associée aux technologies des conventions en utilisant SQL JOIN
- **FR21**: Le système peut contextualiser la documentation basée sur les règles de conventions Layer 1
- **FR22**: Le système peut invoquer l'agent de reformulation LLM pour fusionner conventions et documentation (Layer 3)
- **FR23**: Le système peut éliminer les patterns contradictoires entre conventions et documentation
- **FR24**: Le système peut éliminer la syntaxe obsolète en faveur des meilleures pratiques actuelles
- **FR25**: Le système peut générer un guide d'implémentation unifié à partir du contexte multi-source
- **FR26**: Le système peut valider la cohérence structurelle du contexte fusionné
- **FR27**: Le système peut optimiser le contexte pour la consommation de l'agent IA
- **FR28**: Le système peut s'assurer que la sortie Layer 3 est mono-approche (un seul chemin recommandé)

**Context Retrieval & Delivery (FR29-FR38):**

- **FR29**: Le système peut recevoir des requêtes contextuelles des agents Claude Code
- **FR30**: Le système peut générer des embeddings vectoriels pour les requêtes de recherche
- **FR31**: Le système peut récupérer les conventions pertinentes basées sur la similarité sémantique
- **FR32**: Le système peut identifier les identifiants de technologie des conventions récupérées
- **FR33**: Le système peut récupérer toute la documentation liée aux technologies identifiées
- **FR34**: Le système peut agréger conventions et documentation avant la reformulation
- **FR35**: Le système peut livrer le contexte fusionné aux agents Claude Code
- **FR36**: Le système peut livrer le contexte optimisé pour les tâches de génération de code
- **FR37**: Le système peut livrer le contexte optimisé pour les tâches de génération de documentation
- **FR38**: Le système peut maintenir la pertinence du contexte tout au long du pipeline de récupération

**Code Validation & Conformity (FR39-FR49):**

- **FR39**: Le système peut valider le code généré contre les conventions du projet
- **FR40**: Le système peut détecter les violations de conventions dans les extraits de code
- **FR41**: Le système peut détecter les non-conformités dans la structure du code
- **FR42**: Le système peut générer des rapports de conformité avec les violations détectées
- **FR43**: Le système peut fournir des suggestions de correction pour les violations
- **FR44**: Le système peut calculer des scores de conformité pour les soumissions de code
- **FR45**: Le système peut comparer les patterns de code contre les règles de conventions
- **FR46**: Le système peut identifier les patterns requis manquants des conventions
- **FR47**: Les utilisateurs peuvent demander une validation explicite des extraits de code
- **FR48**: Les utilisateurs peuvent valider le code au niveau du fichier
- **FR49**: Le système peut rapporter les résultats de validation avec des descriptions détaillées des violations

**Claude Code Integration (FR50-FR74):**

- **FR50**: Les Skills peuvent être auto-invoqués par Claude Code pendant la génération de code
- **FR51**: Les Skills peuvent récupérer le contexte de manière transparente sans intervention utilisateur
- **FR52**: Les Skills peuvent invoquer les outils MCP pour la récupération de contexte
- **FR53**: Les Skills peuvent déléguer au sub-agent de reformulation
- **FR54**: Les Skills peuvent retourner le contexte fusionné à Claude Code
- **FR55**: Les utilisateurs peuvent invoquer manuellement les skills de validation via des commandes slash
- **FR56**: Les utilisateurs peuvent interroger le contexte Alexandria en utilisant des commandes slash
- **FR57**: Les utilisateurs peuvent configurer Alexandria en utilisant des commandes slash interactives
- **FR58**: Les utilisateurs peuvent lister les documents en utilisant des commandes slash avec paramètres optionnels
- **FR59**: Les utilisateurs peuvent lire le contenu des documents en utilisant des commandes slash
- **FR60**: Les utilisateurs peuvent supprimer des documents en utilisant des commandes slash
- **FR61**: Les Sub-agents peuvent recevoir conventions et documentation en entrée
- **FR62**: Les Sub-agents peuvent analyser les contradictions entre plusieurs sources
- **FR63**: Les Sub-agents peuvent produire des guides unifiés cohérents
- **FR64**: Les Sub-agents peuvent opérer de manière autonome sans intervention utilisateur
- **FR65**: Les Sub-agents peuvent utiliser des modèles LLM économiques (Haiku 4.5) pour l'optimisation des coûts
- **FR66**: Le serveur MCP peut exposer des outils via le Model Context Protocol
- **FR67**: Le serveur MCP peut gérer les requêtes de récupération de contexte
- **FR68**: Le serveur MCP peut gérer les requêtes de validation de code
- **FR69**: Le serveur MCP peut gérer les requêtes d'upload de conventions
- **FR70**: Le serveur MCP peut gérer les requêtes de liste de projets
- **FR71**: Le serveur MCP peut gérer les requêtes de liste de documents
- **FR72**: Le serveur MCP peut gérer les requêtes de lecture de documents
- **FR73**: Le serveur MCP peut gérer les requêtes de suppression de documents
- **FR74**: Le serveur MCP peut communiquer avec Claude Code en utilisant le protocole MCP

**Project & Technology Configuration (FR75-FR106):**

- **FR75**: Les utilisateurs peuvent configurer plusieurs projets indépendants
- **FR76**: Les utilisateurs peuvent assigner des identifiants uniques aux projets
- **FR77**: Les utilisateurs peuvent déclarer plusieurs technologies par convention
- **FR78**: Les utilisateurs peuvent associer plusieurs technologies avec la documentation
- **FR79**: Le système peut créer des associations technologie-convention via une table pivot
- **FR80**: Le système peut maintenir les relations technologie-documentation
- **FR81**: Le système peut récupérer les conventions pour des identifiants de projet spécifiques
- **FR82**: Le système peut récupérer toutes les conventions associées à des technologies spécifiques
- **FR83**: Le système peut récupérer toute la documentation pour des technologies spécifiques
- **FR84**: Les utilisateurs peuvent lister tous les projets configurés avec métadonnées
- **FR85**: Les utilisateurs peuvent visualiser la configuration de technologie par projet
- **FR86**: Les utilisateurs peuvent visualiser le nombre de conventions par projet
- **FR87**: Les utilisateurs peuvent visualiser le nombre de documentations par technologie
- **FR88**: Le système peut stocker et gérer des conventions pour tout langage de programmation utilisé par les projets utilisateur
- **FR89**: Le système peut stocker et gérer de la documentation pour tout framework utilisé par les projets utilisateur
- **FR90**: Les utilisateurs peuvent onboarder de nouveaux projets avec configuration de technologie
- **FR91**: Le système peut maintenir les associations document-projet pour le partage multi-projet
- **FR104**: Les utilisateurs peuvent associer des documents (conventions ou documentation) avec plusieurs projets
- **FR105**: Le système peut récupérer les documents partagés entre plusieurs projets
- **FR106**: Le système peut maintenir les associations projet-document via une table pivot

**Testing, Debugging & Observability (FR92-FR103):**

- **FR92**: Les utilisateurs peuvent tester les requêtes de récupération avec sortie debug
- **FR93**: Le système peut afficher les résultats de récupération Layer 1 (conventions trouvées)
- **FR94**: Le système peut afficher les résultats de récupération Layer 2 (documentation liée)
- **FR95**: Le système peut afficher la sortie de reformulation Layer 3 (guide fusionné)
- **FR96**: Le système peut afficher les métriques de pertinence pour le contenu récupéré
- **FR97**: Le système peut afficher les métriques de temps de réponse
- **FR98**: Le système peut logger toutes les requêtes de récupération avec timestamps
- **FR99**: Le système peut logger toutes les requêtes de validation avec résultats
- **FR100**: Le système peut logger les opérations d'upload avec métadonnées des documents
- **FR101**: Les utilisateurs peuvent suivre manuellement les métriques de succès (interventions, commits, reviews)
- **FR102**: Le système peut fournir une visibilité sur le pipeline de récupération pour le debugging
- **FR103**: Le système peut valider l'efficacité de la configuration avant l'utilisation en production

### Exigences Non-Fonctionnelles

**Performance (NFR1-NFR6):**

- **NFR1: Temps de Réponse Retrieval Queries** - p50 (médiane): ≤3 secondes pour `/alexandria-query` end-to-end; p95 (95e percentile): ≤5 secondes pour requêtes complexes; p99 (99e percentile): ≤10 secondes dans worst-case scenarios; Timeout rate: 0%
- **NFR2: Performance Layer 1 (Vector Search)** - Recherche vectorielle sur pgvector: ≤1 seconde pour 95% des requêtes; Support de >10,000 embeddings sans dégradation significative
- **NFR3: Performance Layer 2 (SQL Joins)** - Récupération documentation via JOIN `convention_technologies`: ≤500ms; Indexation optimale sur `technology_id` et `project_id`
- **NFR4: Performance Layer 3 (LLM Reformulation)** - Reformulation via Haiku 4.5: ≤2 secondes pour contexte standard (<5000 tokens); Pas de retry automatique pour éviter latence cumulée
- **NFR5: Performance Upload/Indexation** - Upload convention/doc markdown: Acceptation immédiate (<200ms); Génération embeddings: Asynchrone, pas de blocage utilisateur; Notification completion indexation disponible
- **NFR6: Concurrent Request Handling** - Support minimum 5 requêtes simultanées sans dégradation; Isolation transactions PostgreSQL pour éviter conflicts

**Security (NFR7-NFR10):**

- **NFR7: Credentials Management** - Support stockage via variables d'environnement système; Support lecture fichier `.env` pour configuration locale; Validation présence credentials au démarrage (fail fast si manquants)
- **NFR8: API Keys Protection** - Credentials PostgreSQL (`ALEXANDRIA_DB_URL`) protégées en mémoire; `OPENAI_API_KEY` jamais loggée ni exposée dans outputs; Pas de stockage credentials en clair dans code source ou repos
- **NFR9: Knowledge Base Access** - Pas d'authentification requise pour MCP server (usage local uniquement); Accès PostgreSQL restreint via credentials (pas d'accès public)
- **NFR10: Data Privacy** - Conventions et documentation projet stockées localement uniquement (PostgreSQL local); Reformulation Layer 3 via sub-agent Claude Code; Embeddings via OpenAI API (chunks de texte envoyés pour vectorisation uniquement); Logs ne doivent pas contenir code snippets complets

**Integration (NFR11-NFR15):**

- **NFR11: MCP Protocol Compliance** - Serveur MCP 100% conforme au protocole Model Context Protocol; Support toutes opérations MCP tools définies; Réponses format JSON valide selon spécification MCP
- **NFR12: Claude Code Skills Integration** - Skills auto-invocables par Claude Code sans erreur; Skills peuvent appeler MCP tools de manière fiable; Retour contexte formaté compatible avec ingestion Claude Code
- **NFR13: Slash Commands Reliability** - Tous slash commands fonctionnels depuis Claude Code CLI; Paramètres optionnels gérés correctement (avec defaults appropriés); Messages d'erreur clairs si paramètres invalides
- **NFR14: Sub-Agent Communication** - Sub-agent `alexandria-reformulation` peut être invoqué via le système de sub-agents de Claude Code; Input/output format stable et documenté; Pas de breaking changes dans interface sub-agent
- **NFR15: PostgreSQL + pgvector Dependency** - Détection automatique disponibilité PostgreSQL au démarrage; Vérification extension pgvector installée et fonctionnelle; Message d'erreur clair si infrastructure manquante

**Reliability (NFR16-NFR20):**

- **NFR16: Fail Fast Behavior** - Si PostgreSQL inaccessible: Erreur immédiate avec message explicite; Si sub-agent reformulation inaccessible: Erreur Layer 3 avec fallback possible; Si MCP server crash: Logging stack trace complet
- **NFR17: Error Messages Quality** - Tous messages d'erreur incluent contexte actionnable; Pas de stack traces techniques exposés à l'utilisateur final; Codes d'erreur distincts pour chaque failure mode
- **NFR18: Data Integrity** - Transactions PostgreSQL pour opérations multi-étapes; Rollback automatique si échec partiel; Validation schéma avant insertion
- **NFR19: Graceful Degradation** - Si Layer 3 reformulation échoue: Retour Layer 1+2 context brut avec warning
- **NFR20: Uptime Requirements** - MCP server disponible tant que Claude Code est actif; Pas de memory leaks causant crash après usage prolongé; Recovery automatique après restart

**Maintainability (NFR21-NFR25):**

- **NFR21: Code Documentation Complète** - JSDoc/TSDoc pour toutes fonctions publiques et classes TypeScript; Explications logique complexe RAG via commentaires inline; Documentation architecture dans README.md; Types TypeScript stricts (strict mode activé)
- **NFR22: Code Organization** - Séparation claire responsabilités (skills / sub-agent / MCP server / database layer); Modules découplés pour faciliter tests unitaires; Conventions de nommage cohérentes
- **NFR23: Tests Coverage** - Tests unitaires pour logique métier critique; Tests intégration pour pipeline complet Layer 1→2→3; Tests MCP tools pour validation protocole
- **NFR24: Configuration Management** - Tous paramètres configurables externalisés (pas de hardcoding); Fichier config exemple fourni (`.env.example`); Documentation complète variables d'environnement
- **NFR25: Dependency Management** - Liste explicite dépendances (package.json avec bun.lockb); Versions pinned pour reproductibilité; Dockerfile fourni pour installation simplifiée

**Observability (NFR26-NFR33):**

- **NFR26: Logging Verbose avec Debug Mode** - Mode verbose activable via variable d'environnement `ALEXANDRIA_LOG_LEVEL=DEBUG`; Logs structurés (JSON format); Rotation logs automatique
- **NFR27: Métriques Techniques Essentielles** - Logging automatique: Timestamp requête, Type d'opération, Query text, Project ID et technologies, Résultat (success/error), Message d'erreur détaillé si échec
- **NFR28: Performance Metrics par Layer** - Logging temps d'exécution: Temps Layer 1, Temps Layer 2, Temps Layer 3, Temps total end-to-end, Nombre conventions récupérées, Nombre documentations récupérées
- **NFR29: Métriques de Pertinence** - Logging qualité retrieval: Similarity scores, Nombre résultats avant/après filtrage, Technologies matchées vs demandées, Nombre total documents scannés
- **NFR30: Pipeline Visibility** - Debugging mode expose: Layer 1 output (IDs conventions + scores), Layer 2 output (IDs docs + relations), Layer 3 input/output (taille contexte tokens), Tokens LLM consommés
- **NFR31: Opérations CRUD Tracking** - Logging modifications knowledge base: Upload (type, taille, technologies, project ID), Delete (ID, type, raison), Read (ID, timestamp), List (filters, résultats)
- **NFR32: Validation Requests Logging** - Logging validation code: Code snippet hash (privacy), Nombre violations, Types violations, Conformity score (0-100%)
- **NFR33: Metrics Export** - Logs exportables pour analyse externe (fichiers texte/JSON); Pas de dashboard intégré dans MVP

### Exigences Additionnelles

**Exigences Architecturales (de architecture.md):**

**1. Stack Technique Obligatoire:**
- Runtime: Bun 1.3.5 (JavaScript runtime ultra-rapide, acquis par Anthropic)
- Framework Web: Hono 4.11.1 (TypeScript, framework web minimaliste ~12KB)
- Langage: TypeScript 5.9.7 (strict mode obligatoire, dernière branche 5.x stable)
- ORM: Drizzle ORM 0.36.4 (type-safe, support natif pgvector)
- Validation: Zod 4.2.1 (bundle 57% plus petit, 3x plus rapide que 3.x)
- Base de données: PostgreSQL 17.7 avec extension pgvector 0.8.1
- Embeddings: OpenAI Embeddings API (text-embedding-3-small ou text-embedding-3-large)
- LLM Reformulation: Claude Haiku 4.5 via sub-agent Claude Code

**2. Architecture Hexagonale (Contrainte NON-NÉGOCIABLE):**
- Séparation stricte Domain/Ports/Adapters
- Domain layer ne dépend JAMAIS d'Adapters (vérifiable par ts-arch)
- Domain layer ne peut importer Zod, Drizzle, Hono
- Tous les use-cases injectent Ports (pas implémentations concrètes)
- Structure dossiers: `src/domain/`, `src/ports/`, `src/adapters/`
- Validation via ts-arch rules (tests automatisés)

**3. Configuration Vector Search (HNSW):**
- Index Type: HNSW (Hierarchical Navigable Small World)
- HNSW Parameters: m=16, ef_construction=64
- Distance Metric: Cosine similarity (opérateur `<=>` de pgvector)
- Embedding Dimensions: 1536 (text-embedding-3-small) ou 3072 (text-embedding-3-large)
- Top-k retrieval strategy à déterminer lors implémentation

**4. Multi-Project Isolation:**
- Pattern: Application-level filtering via Drizzle WHERE clauses
- Tous use-cases reçoivent ProjectId value object obligatoire
- Tous repository adapters filtrent systématiquement par `project_id`
- Index PostgreSQL: `CREATE INDEX idx_conventions_project_id ON conventions(project_id)`
- Tests d'isolation via fixtures multi-projets

**5. Layer 3 Orchestration (Architecture Critique):**
- Alexandria MCP Server n'invoque PAS directement le sub-agent
- MCP Tool `retrieve_raw_context` retourne uniquement Layer 1+2 brut
- Sub-Agent Definition: `.claude/agents/alexandria-reformulation.md` (fichier externe, model: haiku)
- Skill Alexandria orchestre: MCP tool → demande invocation sub-agent → reformulation
- Fallback strategy: Retour Layer 1+2 brut si sub-agent échoue (graceful degradation)
- Pas de cache pour MVP (simplicité prioritaire, quota Plan Max suffisant)

**6. Dual Logging Strategy:**
- Console Logging: JSON structuré via Bun natif (stdout)
- File Logging: Format .jsonl, location `./logs/alexandria-YYYY-MM-DD.jsonl`
- Rotation: Quotidienne (nouveau fichier chaque jour)
- Retention: Configurable via `LOG_RETENTION_DAYS` env var (défaut: 30 jours)
- Log Levels: DEBUG, INFO, WARN, ERROR (standard)
- Analysis: `grep`/`jq` sur fichiers JSON (suffisant pour MVP)

**7. Naming Patterns (Conventions Strictes):**
- Database: camelCase (Drizzle schemas avec columns en camelCase)
- TypeScript Files: PascalCase (ex: `Convention.ts`, `SearchConventions.ts`)
- Variables/Functions: camelCase
- Constants: UPPER_SNAKE_CASE (ex: `DEFAULT_TOP_K`, `EMBEDDING_DIMENSIONS`)
- Ports: Suffix "Port" obligatoire (ex: `ConventionRepositoryPort`)
- MCP Tools: snake_case (ex: `retrieve_raw_context`)
- JSON Fields: camelCase (cohérence avec Database + TypeScript)

**8. Validation Boundaries (Zod):**
- Zod uniquement aux boundaries (Adapters Primaires, Adapters Secondaires, Config)
- MCP tools inputs/outputs: Zod validation systématique
- .env startup: Zod schema fail-fast si invalide
- OpenAI responses: Zod validation responses
- Domain layer: JAMAIS de Zod (viole hexagonal)

**9. Immutability Patterns:**
- Entities: readonly properties partout
- Value Objects: readonly + private constructor + static factory
- Pas de setters (méthodes retournent nouvelles instances)
- Pattern: `withEmbedding()` retourne nouvelle Convention au lieu de muter

**10. Structure Projet Custom (Pas de Starter Template):**
- Arborescence complète définie: 150+ fichiers/répertoires
- Organisation hexagonale stricte: domain/, ports/, adapters/, config/, shared/
- Tests structure miroir: tests/unit/domain/, tests/integration/
- Claude Code Assets: `.claude/agents/`, `.claude/skills/`
- Scripts: `scripts/setup-db.ts`, `scripts/seed-data.ts`, `scripts/rotate-logs.sh`

**11. Dependency Injection:**
- Constructor injection manuelle (pas d'IoC container)
- Bootstrap centralisé dans `src/index.ts`
- Ordre création: Adapters secondaires → Use-cases → Adapters primaires
- Tous use-cases injectent Ports via constructor

**12. CI/CD Pipeline (GitHub Actions):**
- Jobs: Lint & Type Check, Architecture Compliance (ts-arch), Tests Unitaires, Tests Intégration
- PostgreSQL service container: `pgvector/pgvector:pg17` image
- Secrets GitHub: `OPENAI_API_KEY` pour tests intégration

### Carte de Couverture des Exigences

**Convention & Documentation Management:**
- FR1-FR15 → Epic 3: Knowledge Base Management

**Active Compliance Filter - RAG 3 Layers:**
- FR16-FR28 → Epic 4: Active Compliance Filter

**Context Retrieval & Delivery:**
- FR29-FR38 → Epic 4: Active Compliance Filter

**Code Validation & Conformity:**
- FR39-FR49 → Epic 6: Code Validation & Conformity

**Claude Code Integration:**
- FR50-FR74 → Epic 5: Claude Code Integration

**Project & Technology Configuration:**
- FR75-FR91 → Epic 3: Knowledge Base Management
- FR92-FR103 → Epic 7: Observability & Debugging
- FR104-FR106 → Epic 3: Knowledge Base Management

**Non-Functional Requirements:**
- NFR1-NFR6 (Performance) → Epic 4: Active Compliance Filter
- NFR7-NFR10 (Security) → Epic 1: Infrastructure & Setup
- NFR11-NFR15 (Integration) → Epic 5: Claude Code Integration
- NFR16-NFR20 (Reliability) → Epic 1: Infrastructure & Setup
- NFR21-NFR25 (Maintainability) → Epic 1: Infrastructure & Setup + Epic 2: CI/CD
- NFR26-NFR33 (Observability) → Epic 7: Observability & Debugging

**Exigences Architecturales:**
- Architecture #1 (Stack Technique) → Epic 1: Infrastructure & Setup
- Architecture #2 (Hexagonal) → Epic 1: Infrastructure & Setup + Epic 2: CI/CD
- Architecture #3 (HNSW) → Epic 1: Infrastructure & Setup + Epic 4: Active Compliance Filter
- Architecture #4 (Multi-Project) → Epic 3: Knowledge Base Management
- Architecture #5 (Layer 3 Orchestration) → Epic 4: Active Compliance Filter + Epic 5: Claude Code Integration
- Architecture #6 (Dual Logging) → Epic 7: Observability & Debugging
- Architecture #7 (Naming Patterns) → Epic 1: Infrastructure & Setup
- Architecture #8 (Validation Boundaries) → Epic 1: Infrastructure & Setup
- Architecture #9 (Immutability) → Epic 1: Infrastructure & Setup
- Architecture #10 (Structure Projet) → Epic 1: Infrastructure & Setup
- Architecture #11 (DI) → Epic 1: Infrastructure & Setup
- Architecture #12 (CI/CD) → Epic 2: CI/CD & Quality Assurance

**✅ Coverage: 106 FRs + 33 NFRs + 12 Architecture Requirements = 151 Total Requirements Mapped**

## Liste des Épopées

### Epic 1: Local Development Environment Ready for Alexandria

**Résultat utilisateur:** Les développeurs peuvent installer et configurer Alexandria localement avec PostgreSQL + pgvector, validant que tous les composants sont fonctionnels

**FRs couvertes:** Architecture #1 (Stack Technique), Architecture #2 (Hexagonal), Architecture #3 (HNSW), Architecture #7 (Naming), Architecture #8 (Validation Boundaries), Architecture #9 (Immutability), Architecture #10 (Structure Projet), Architecture #11 (DI), NFR7-NFR10 (Security), NFR15 (PostgreSQL + pgvector), NFR16-NFR20 (Reliability), NFR21-NFR25 (Maintainability)

**Notes d'implémentation:**
- Setup Bun 1.3.5, PostgreSQL 17.7 + pgvector 0.8.1, structure dossiers hexagonale
- Configuration .env avec validation Zod fail-fast
- Scripts d'initialisation et migrations Drizzle
- Validation de l'architecture via ts-arch tests
- Immutability patterns: readonly properties, static factories
- Naming conventions strictes: camelCase DB, PascalCase files, suffix "Port"
- Dependency injection manuelle via constructor

#### Story 1.1: Project Structure & Configuration Setup

**En tant que** développeur,
**Je veux** avoir la structure de projet hexagonale et la configuration de base,
**Afin de** pouvoir commencer le développement avec les bonnes fondations architecturales.

**Critères d'acceptation:**

1. Structure dossiers hexagonale créée: `src/domain/`, `src/ports/`, `src/adapters/`, `src/config/`, `src/shared/`
2. Bun 1.3.5 installé et configuré (package.json, bun.lockb)
3. TypeScript 5.9.7 configuré en strict mode (tsconfig.json)
4. Configuration .env avec variables requises (ALEXANDRIA_DB_URL, OPENAI_API_KEY, LOG_LEVEL)
5. Validation Zod de la configuration au démarrage (fail-fast si invalide)
6. Naming conventions documentées et appliquées (camelCase DB, PascalCase files, suffix "Port")
7. README.md avec instructions d'installation

**Exigences couvertes:** Architecture #1, #7, #8, #10, NFR7, NFR21, NFR24

---

#### Story 1.2: PostgreSQL & pgvector Infrastructure

**En tant que** développeur,
**Je veux** avoir PostgreSQL et pgvector configurés et opérationnels,
**Afin de** pouvoir stocker les conventions et générer des embeddings vectoriels.

**Critères d'acceptation:**

1. PostgreSQL 17.7 installé localement
2. Extension pgvector 0.8.1 installée et activée
3. Script `scripts/setup-db.ts` créé pour initialisation DB
4. Script `scripts/seed-data.ts` créé pour données de test
5. Connexion DB validée au démarrage (fail-fast si inaccessible)
6. Credentials sécurisés via ALEXANDRIA_DB_URL (.env)
7. Documentation pour setup PostgreSQL local

**Exigences couvertes:** Architecture #1, #3, NFR7, NFR8, NFR15, NFR16

---

#### Story 1.3: Domain Layer Foundation

**En tant que** développeur,
**Je veux** avoir les entités domain et ports définis,
**Afin de** respecter l'architecture hexagonale et l'isolation du domaine.

**Critères d'acceptation:**

1. Entité Convention créée (domain/entities/Convention.ts) avec readonly properties
2. Entité Documentation créée (domain/entities/Documentation.ts) avec immutability
3. Value Objects créés: ProjectId, TechnologyId, EmbeddingVector, ConformityScore
4. Pattern immutability: static factories, méthodes retournent nouvelles instances
5. Ports interfaces définis: ConventionRepositoryPort, DocumentationRepositoryPort, EmbeddingServicePort
6. Domain layer ne dépend d'AUCUNE librairie externe (pas de Zod, Drizzle, Hono)
7. Tests unitaires domain entities (pure logic, pas de mocks)

**Exigences couvertes:** Architecture #2, #9, NFR21, NFR23

---

#### Story 1.4: Database Schema & Migrations

**En tant que** développeur,
**Je veux** avoir le schéma de base de données complet avec migrations,
**Afin de** pouvoir persister les conventions, documentation et embeddings.

**Critères d'acceptation:**

1. Drizzle ORM 0.36.4 configuré avec support pgvector
2. Schéma `conventions` avec colonne embedding (vector type)
3. Schéma `documentation` avec métadonnées
4. Schéma `technologies` et `projects`
5. Tables pivot: `convention_technologies`, `document_projects`
6. Index HNSW créé: m=16, ef_construction=64, cosine similarity
7. Index PostgreSQL: `idx_conventions_project_id`, `idx_documentation_technology_id`
8. Migration initiale exécutable via Drizzle
9. Script de rollback disponible

**Exigences couvertes:** Architecture #1, #3, #4, NFR18, NFR25

---

#### Story 1.5: Dependency Injection & Bootstrap

**En tant que** développeur,
**Je veux** avoir un système de DI manuel et un bootstrap centralisé,
**Afin de** construire les dépendances dans le bon ordre et injecter les ports.

**Critères d'acceptation:**

1. Bootstrap centralisé dans `src/index.ts`
2. Constructor injection pour tous les use-cases (injectent Ports)
3. Ordre création respecté: Adapters secondaires → Use-cases → Adapters primaires
4. Pas d'IoC container (DI manuel uniquement)
5. Error handling fail-fast au démarrage
6. Validation présence credentials au démarrage (NFR7)
7. Logging démarrage avec composants initialisés

**Exigences couvertes:** Architecture #11, NFR16, NFR17, NFR26

---

#### Story 1.6: Architecture Compliance Validation

**En tant que** développeur,
**Je veux** avoir des tests automatisés validant l'architecture hexagonale,
**Afin de** garantir que le domain reste isolé et que les conventions sont respectées.

**Critères d'acceptation:**

1. ts-arch installé et configuré
2. Tests architecture: Domain ne dépend pas de Adapters/Infrastructure
3. Tests architecture: Domain ne dépend pas de Zod/Drizzle/Hono
4. Tests architecture: Ports ne dépendent pas de Adapters
5. Tests naming: Ports ont suffix "Port"
6. Tests naming: Files en PascalCase, variables en camelCase
7. Tests exécutables via `bun test:arch`
8. CI pipeline configured pour exécuter tests architecture (build-breaking)

**Exigences couvertes:** Architecture #2, #7, NFR21, NFR23

---

### Epic 2: Automated Quality Gates Enforce Architecture Compliance

**Résultat utilisateur:** L'équipe de développement bénéficie d'un pipeline CI/CD automatisé dès le début avec stratégie 3-Tiers de quality enforcement, garantissant la qualité du code via tests unitaires, intégration, validation d'architecture hexagonale à chaque commit, et review AI contextuelle

**FRs couvertes:** Architecture #12 (CI/CD Pipeline), NFR23 (Tests Coverage), Architecture #2 (Architecture Hexagonale validation)

**Notes d'implémentation:**

**Stratégie 3-Tiers Quality Enforcement:**

**Tier 1: ts-arch - Hard Enforcement (Build-Breaking)**
- Tests architecture hexagonale via ts-arch dans CI pipeline
- Règles: Domain ne dépend pas de Adapters/Infrastructure/Zod/Drizzle/Hono
- Règles: Ports ne dépendent pas de Adapters/Infrastructure
- Build échoue immédiatement si violations détectées
- Garantie: 100% enforcement architecture, 0 violations en production

**Tier 2: ESLint - Real-Time IDE Feedback**
- Configuration ESLint avec import/no-restricted-paths pour architecture hexagonale
- Pre-commit hooks (Husky ou lefthook) lançant ESLint avant commit
- TypeScript strict rules: no-explicit-any, explicit-function-return-type, no-unused-vars
- Feedback <1s dans IDE pendant écriture code
- Détection immédiate violations pendant développement

**Tier 3: CodeRabbit - AI-Powered Contextual Review**
- Review automatique sur chaque Pull Request via CodeRabbit AI
- Configuration .coderabbit.yaml avec profile "chill" pour reviews ciblées
- path_instructions spécifiques par couche (domain/, ports/, adapters/, application/)
- AST-grep custom rules dans .coderabbit/rules/: no-then-chains, no-bare-throws, no-domain-adapter-import, no-zod-in-domain, no-drizzle-in-domain, no-hono-in-domain
- Knowledge base: CLAUDE.md avec documentation architecture hexagonale
- Learnings activés pour amélioration continue
- Gitleaks integration pour détection secrets
- Focus: violations subtiles, sécurité, patterns métier (ce que ts-arch/ESLint manquent)

**GitHub Actions Pipeline:**
- Jobs: Lint & Type Check, Architecture Compliance (ts-arch), Tests Unitaires, Tests Intégration
- PostgreSQL service container (pgvector/pgvector:pg17)
- Tests unitaires domain core sans mocks
- Tests intégration avec PostgreSQL + OpenAI réels
- Secrets GitHub: OPENAI_API_KEY

**Fichiers de configuration à créer:**
- .coderabbit.yaml (configuration CodeRabbit complète)
- .coderabbit/rules/ (6 règles AST-grep custom)
- CLAUDE.md (documentation architecture pour CodeRabbit knowledge base)
- .eslintrc.js (règles ESLint + import restrictions)
- tests/architecture.spec.ts (tests ts-arch)
- .github/workflows/ci.yml (GitHub Actions pipeline)
- Pre-commit hooks config (Husky ou lefthook)

**Objectifs mesurables:**
- 100% enforcement architecture hexagonale (ts-arch build-breaking)
- Réduction 70% temps review manuel
- Feedback <1s via ESLint dans IDE

---

### Epic 3: Knowledge Base Management

**Résultat utilisateur:** Les développeurs peuvent uploader, lister, lire, et supprimer des conventions et documentations pour leurs projets, gérant leur knowledge base de manière complète

**FRs couvertes:** FR1-FR15 (Convention & Documentation Management), FR75-FR91 (Project & Technology Configuration), FR104-FR106 (Multi-project associations), Architecture #4 (Multi-Project Isolation)

**Notes d'implémentation:**
- CRUD complet conventions et documentation via MCP tools
- Distinction manuelle Convention vs Documentation lors upload
- Parsing markdown et extraction contenu
- Cascade deletes (embeddings + technology links)
- Multi-project isolation via ProjectId value object
- Application-level filtering: Drizzle WHERE clauses sur project_id
- Table pivot pour associations projet-document et technology-document
- Index PostgreSQL: idx_conventions_project_id, idx_documentation_technology_id

---

### Epic 4: Intelligent Context Fusion Prevents Code Drift

**Résultat utilisateur:** Les développeurs reçoivent un contexte intelligent, sans contradictions, qui élimine les patterns obsolètes et garantit la conformité du code dès la première itération

**FRs couvertes:** FR16-FR28 (Active Compliance Filter 3-Layer RAG), FR29-FR38 (Context Retrieval & Delivery), Architecture #3 (HNSW Config), Architecture #5 (Layer 3 Orchestration), NFR1-NFR6 (Performance), NFR30 (Pipeline Visibility)

**Notes d'implémentation:**
- Layer 1: Vector search HNSW (m=16, ef_construction=64) avec cosine similarity
- Layer 2: Technology linking via SQL JOIN pivot table
- Layer 3: Orchestration via Skill Alexandria → Sub-agent Haiku 4.5 (externe à Alexandria MCP)
- OpenAI Embeddings API (text-embedding-3-small ou text-embedding-3-large)
- Fallback graceful si Layer 3 échoue (retour Layer 1+2 brut)
- Performance targets: p50 ≤3s, Layer 1 ≤1s, Layer 2 ≤500ms, Layer 3 ≤2s
- Embedding dimensions: 1536 (small) ou 3072 (large)
- MCP Tool: retrieve_raw_context (retourne Layer 1+2 uniquement)

---

### Epic 5: Claude Code Integration

**Résultat utilisateur:** Les développeurs peuvent utiliser Alexandria de manière transparente via Skills auto-invoqués, slash commands interactifs, et sub-agents pour générer du code conforme dès la première itération

**FRs couvertes:** FR50-FR74 (Claude Code Integration), NFR11-NFR14 (Integration), Architecture #5 (Layer 3 Orchestration)

**Notes d'implémentation:**
- MCP Server Hono exposant 7 tools via stdio transport
- MCP Tools: retrieve_raw_context, validate_code, upload_convention, list_conventions, read_convention, delete_convention, list_projects
- Skills: alexandria-context (auto-invoqué), alexandria-validate
- Slash commands: /alexandria-query, /alexandria-config, /alexandria-list, /alexandria-read, /alexandria-delete
- Sub-agent: .claude/agents/alexandria-reformulation.md (Haiku 4.5)
- Skill orchestration: MCP tool → invocation sub-agent → reformulation → contexte fusionné
- MCP protocol 100% compliant (NFR11)
- Zod validation systématique sur tous inputs/outputs MCP tools

---

### Epic 6: Code Validation & Conformity

**Résultat utilisateur:** Les développeurs peuvent valider explicitement leur code généré contre les conventions du projet, recevant des rapports de conformité avec violations détectées et suggestions de correction

**FRs couvertes:** FR39-FR49 (Code Validation & Conformity)

**Notes d'implémentation:**
- Use-case ValidateCodeConformity
- Détection violations de conventions dans code snippets
- Calcul conformity score (0-100%) via ConformityScore value object
- Rapports détaillés avec violations et suggestions corrections
- MCP tool validate_code + slash command /alexandria-validate
- Logging validation requests (NFR32) avec privacy (code hash, pas snippets complets)
- Comparison patterns code vs règles conventions

---

### Epic 7: Observability & Debugging

**Résultat utilisateur:** Les développeurs peuvent observer et débugger le pipeline RAG complet avec visibilité sur chaque layer, métriques de performance, et logs structurés pour optimiser leur configuration

**FRs couvertes:** FR92-FR103 (Testing, Debugging & Observability), NFR26-NFR33 (Observability), Architecture #6 (Dual Logging)

**Notes d'implémentation:**
- Dual logging: Console JSON structuré (Bun stdout) + fichiers .jsonl rotatifs quotidiens
- Location logs: ./logs/alexandria-YYYY-MM-DD.jsonl
- Retention configurable: LOG_RETENTION_DAYS env var (défaut 30 jours)
- Pipeline visibility: Layer 1/2/3 outputs avec similarity scores, latency, tokens consommés
- Slash command /alexandria-query avec debug output complet
- Métriques automatiques: timestamp, operation, layer, latency, matches, errors, project_id
- Log levels: DEBUG, INFO, WARN, ERROR
- Analysis: grep/jq sur fichiers JSON (suffisant pour MVP)
- Script rotation: scripts/rotate-logs.sh
