# Non-Functional Requirements

## Performance

**NFR1: Temps de Réponse Retrieval Queries**
- p50 (médiane): ≤3 secondes pour `/alexandria-query` end-to-end
- p95 (95e percentile): ≤5 secondes pour requêtes complexes
- p99 (99e percentile): ≤10 secondes dans worst-case scenarios
- Timeout rate: 0% (aucune requête ne doit timeout)

**NFR2: Performance Layer 1 (Vector Search)**
- Recherche vectorielle sur pgvector: ≤1 seconde pour 95% des requêtes
- Support de >10,000 embeddings sans dégradation significative

**NFR3: Performance Layer 2 (SQL Joins)**
- Récupération documentation via JOIN `convention_technologies`: ≤500ms
- Indexation optimale sur `technology_id` et `project_id`

**NFR4: Performance Layer 3 (LLM Reformulation)**
- Reformulation via Haiku 3.5: ≤2 secondes pour contexte standard (<5000 tokens)
- Pas de retry automatique pour éviter latence cumulée

**NFR5: Performance Upload/Indexation**
- Upload convention/doc markdown: Acceptation immédiate (<200ms)
- Génération embeddings: Asynchrone, pas de blocage utilisateur
- Notification completion indexation disponible

**NFR6: Concurrent Request Handling**
- Support minimum 5 requêtes simultanées sans dégradation
- Isolation transactions PostgreSQL pour éviter conflicts

## Security

**NFR7: Credentials Management**
- Support stockage via variables d'environnement système
- Support lecture fichier `.env` pour configuration locale
- Validation présence credentials au démarrage (fail fast si manquants)

**NFR8: API Keys Protection**
- Credentials PostgreSQL (`ALEXANDRIA_DB_URL`) protégées en mémoire
- `OPENAI_API_KEY` jamais loggée ni exposée dans outputs
- Pas de stockage credentials en clair dans code source ou repos (utiliser `.env` exclu du git via `.gitignore`)
- Pas besoin de `CLAUDE_API_KEY` dans Alexandria (reformulation via sub-agent Claude Code)

**NFR9: Knowledge Base Access**
- Pas d'authentification requise pour MCP server (usage local uniquement)
- Accès PostgreSQL restreint via credentials (pas d'accès public)

**NFR10: Data Privacy**
- Conventions et documentation projet stockées localement uniquement (PostgreSQL local)
- Reformulation Layer 3 via sub-agent Claude Code (géré par Claude Code lui-même, pas d'API directe)
- Embeddings via OpenAI API (chunks de texte envoyés pour vectorisation uniquement, pas de stockage OpenAI)
- Logs ne doivent pas contenir code snippets complets (privacy)
- `.env` avec credentials doit être exclu du git (.gitignore)

## Integration

**NFR11: MCP Protocol Compliance**
- Serveur MCP 100% conforme au protocole Model Context Protocol
- Support toutes opérations MCP tools définies (retrieve, validate, upload, list, read, delete)
- Réponses format JSON valide selon spécification MCP

**NFR12: Claude Code Skills Integration**
- Skills auto-invocables par Claude Code sans erreur
- Skills peuvent appeler MCP tools de manière fiable
- Retour contexte formaté compatible avec ingestion Claude Code

**NFR13: Slash Commands Reliability**
- Tous slash commands fonctionnels depuis Claude Code CLI
- Paramètres optionnels gérés correctement (avec defaults appropriés)
- Messages d'erreur clairs si paramètres invalides

**NFR14: Sub-Agent Communication**
- Sub-agent `alexandria-reformulation` peut être invoqué via le système de sub-agents de Claude Code
- Input/output format stable et documenté
- Pas de breaking changes dans interface sub-agent
- Communication via protocole interne Claude Code (pas d'appel API direct depuis Alexandria)

**NFR15: PostgreSQL + pgvector Dependency**
- Détection automatique disponibilité PostgreSQL au démarrage
- Vérification extension pgvector installée et fonctionnelle
- Message d'erreur clair si infrastructure manquante

## Reliability

**NFR16: Fail Fast Behavior**
- Si PostgreSQL inaccessible: Erreur immédiate avec message explicite (pas de retry silencieux)
- Si sub-agent reformulation inaccessible: Erreur Layer 3 avec fallback possible (retour Layer 1+2 brut)
- Si MCP server crash: Logging stack trace complet pour debugging

**NFR17: Error Messages Quality**
- Tous messages d'erreur incluent contexte actionnable (ex: "PostgreSQL unreachable at DB_URL, verify connection")
- Pas de stack traces techniques exposés à l'utilisateur final (loggés seulement)
- Codes d'erreur distincts pour chaque failure mode

**NFR18: Data Integrity**
- Transactions PostgreSQL pour opérations multi-étapes (upload + embedding generation)
- Rollback automatique si échec partiel (pas de données orphelines)
- Validation schéma avant insertion (reject invalid documents)

**NFR19: Graceful Degradation**
- Si Layer 3 reformulation échoue: Retour Layer 1+2 context brut avec warning

**NFR20: Uptime Requirements**
- MCP server disponible tant que Claude Code est actif
- Pas de memory leaks causant crash après usage prolongé
- Recovery automatique après restart (pas de corruption état)

## Maintainability

**NFR21: Code Documentation Complète**
- JSDoc/TSDoc pour toutes fonctions publiques et classes TypeScript
- Explications logique complexe RAG (Layers 1-3) via commentaires inline
- Documentation architecture dans README.md
- Types TypeScript stricts (strict mode activé dans tsconfig.json)

**NFR22: Code Organization**
- Séparation claire responsabilités (skills / sub-agent / MCP server / database layer)
- Modules découplés pour faciliter tests unitaires
- Conventions de nommage cohérentes (TypeScript/JavaScript standards, linting via ESLint/Biome)

**NFR23: Tests Coverage**
- Tests unitaires pour logique métier critique (retrieval, reformulation, CRUD)
- Tests intégration pour pipeline complet Layer 1→2→3
- Tests MCP tools pour validation protocole

**NFR24: Configuration Management**
- Tous paramètres configurables externalisés (pas de hardcoding)
- Fichier config exemple fourni (`.env.example`)
- Documentation complète variables d'environnement requises

**NFR25: Dependency Management**
- Liste explicite dépendances (package.json avec bun.lockb pour lock exact)
- Versions pinned pour reproductibilité (éviter breaking changes)
- Dockerfile fourni pour installation simplifiée (image Bun officielle)
- Support Bun native (pas besoin de Node.js, runtime ultra-rapide)

## Observability

**NFR26: Logging Verbose avec Debug Mode**
- Mode verbose activable via variable d'environnement `ALEXANDRIA_LOG_LEVEL=DEBUG`
- Logs structurés (JSON format recommandé pour parsing)
- Rotation logs automatique pour éviter saturation disque

**NFR27: Métriques Techniques Essentielles (MUST-HAVE)**

Logging automatique pour chaque requête:
- ✅ Timestamp requête (ISO 8601 format)
- ✅ Type d'opération (query, validate, upload, delete, read, list)
- ✅ Query text (pour debugging retrieval)
- ✅ Project ID et technologies concernées
- ✅ Résultat (success/error)
- ✅ Message d'erreur détaillé si échec

**NFR28: Performance Metrics par Layer**

Logging temps d'exécution:
- ✅ Temps Layer 1 (vector search pgvector)
- ✅ Temps Layer 2 (SQL joins technologies)
- ✅ Temps Layer 3 (LLM reformulation Haiku)
- ✅ Temps total end-to-end
- ✅ Nombre conventions récupérées (Layer 1)
- ✅ Nombre documentations récupérées (Layer 2)

**NFR29: Métriques de Pertinence**

Logging qualité retrieval:
- ✅ Similarity scores des chunks vectoriels récupérés
- ✅ Nombre résultats avant/après filtrage
- ✅ Technologies matchées vs technologies demandées
- ✅ Nombre total documents scannés

**NFR30: Pipeline Visibility (SHOULD-HAVE)**

Debugging mode expose:
- ✅ Layer 1 output: IDs conventions + similarity scores
- ✅ Layer 2 output: IDs docs liés + relations technologies
- ✅ Layer 3 input: Taille contexte avant reformulation (tokens)
- ✅ Layer 3 output: Taille contexte après reformulation (tokens)
- ✅ Tokens LLM consommés (Haiku 3.5 API usage)

**NFR31: Opérations CRUD Tracking**

Logging modifications knowledge base:
- ✅ Upload: doc type, taille fichier, technologies déclarées, project ID
- ✅ Delete: doc ID, type, raison suppression (si fournie par utilisateur)
- ✅ Read: doc ID accédé, timestamp
- ✅ List: filters appliqués, nombre résultats retournés

**NFR32: Validation Requests Logging**

Logging validation code:
- ✅ Code snippet hash (pour privacy - pas code complet)
- ✅ Nombre violations détectées
- ✅ Types violations (par catégorie de convention)
- ✅ Conformity score calculé (0-100%)

**NFR33: Metrics Export**
- Logs exportables pour analyse externe (fichiers texte/JSON)
- Pas de dashboard intégré dans MVP (tracking manuel acceptable)
