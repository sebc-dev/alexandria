# Roadmap: Documentation RAG

## Overview

Ce roadmap guide la construction d'un systeme RAG personnel pour indexer et rechercher de la documentation technique. On commence par l'infrastructure (PostgreSQL, pgvector, AGE), puis le pipeline d'ingestion markdown avec chunking hierarchique, ensuite les relations graph, la recherche semantique avec filtres, la recherche avancee (hybride + graph traversal), l'exposition MCP pour Claude Code, et enfin les outils CLI pour la maintenance.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 1: Infrastructure** - PostgreSQL 17 avec pgvector et Apache AGE pret au dev
- [ ] **Phase 2: Ingestion Core** - Pipeline d'indexation markdown avec embeddings locaux
- [ ] **Phase 3: Graph Relations** - Relations parent-child et references croisees dans AGE
- [ ] **Phase 4: Recherche Base** - Recherche semantique avec filtres categorie/tags
- [ ] **Phase 5: Recherche Avancee** - Hybride vector+fulltext et traversee graph
- [ ] **Phase 6: MCP Server** - Tools exposes pour Claude Code
- [ ] **Phase 7: CLI** - Commandes pour indexation et maintenance

## Phase Details

### Phase 1: Infrastructure
**Goal**: L'environnement de developpement est pret avec PostgreSQL, pgvector et AGE configures
**Depends on**: Nothing (first phase)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04
**Success Criteria** (what must be TRUE):
  1. Docker Compose lance PostgreSQL 17 avec pgvector et AGE fonctionnels
  2. Le schema de base (tables documents, chunks, embeddings) est cree par un script
  3. Le projet Maven compile avec toutes les dependances LangChain4j
  4. Un test de connexion valide que pgvector et AGE repondent correctement
**Plans**: 2 plans

Plans:
- [x] 01-01-PLAN.md — Docker et schema de base (Dockerfile, docker-compose, Liquibase migrations)
- [x] 01-02-PLAN.md — Projet Maven et dependances (pom.xml, project structure, integration tests)

### Phase 2: Ingestion Core
**Goal**: Les fichiers markdown sont indexes avec embeddings et metadata stockes
**Depends on**: Phase 1
**Requirements**: ING-01, ING-02, ING-03, ING-04, ING-05
**Success Criteria** (what must be TRUE):
  1. Un repertoire de fichiers markdown est parse et les documents sont crees en base
  2. Chaque document est chunke en parent (1000 tokens) et children (200 tokens)
  3. Les embeddings all-MiniLM-L6-v2 sont generes pour chaque chunk
  4. Les metadonnees (titre, tags, categorie) sont extraites du frontmatter YAML
  5. Les embeddings sont stockes dans pgvector avec index HNSW
**Plans**: TBD

Plans:
- [ ] 02-01: Markdown parser et extraction frontmatter
- [ ] 02-02: Chunking hierarchique
- [ ] 02-03: Embeddings et stockage pgvector

### Phase 3: Graph Relations
**Goal**: Les relations hierarchiques et references entre documents sont stockees dans AGE
**Depends on**: Phase 2
**Requirements**: ING-06, ING-07
**Success Criteria** (what must be TRUE):
  1. Les relations parent-child entre chunks sont stockees comme edges dans AGE
  2. Les references croisees entre documents (liens markdown) sont detectees et stockees
  3. Une requete Cypher peut traverser les relations pour trouver documents lies
**Plans**: TBD

Plans:
- [ ] 03-01: Relations parent-child dans AGE
- [ ] 03-02: Detection et stockage des references croisees

### Phase 4: Recherche Base
**Goal**: L'utilisateur peut chercher semantiquement avec filtres
**Depends on**: Phase 2
**Requirements**: SRCH-01, SRCH-02, SRCH-03, SRCH-04
**Success Criteria** (what must be TRUE):
  1. Une recherche par texte retourne les chunks les plus similaires (cosine similarity)
  2. Les resultats incluent le chunk enfant et son contexte parent
  3. Les resultats peuvent etre filtres par categorie
  4. Les resultats peuvent etre filtres par tags
**Plans**: TBD

Plans:
- [ ] 04-01: Recherche semantique pgvector
- [ ] 04-02: Contexte parent et filtres

### Phase 5: Recherche Avancee
**Goal**: Recherche hybride et exploration du graph de documents
**Depends on**: Phase 3, Phase 4
**Requirements**: SRCH-05, SRCH-06
**Success Criteria** (what must be TRUE):
  1. La recherche combine vector similarity et full-text PostgreSQL (tsvector)
  2. Les documents lies via le graph AGE sont retournes en complement
  3. La traversee graph permet de decouvrir documents a 1-2 hops de distance
**Plans**: TBD

Plans:
- [ ] 05-01: Recherche hybride vector + fulltext
- [ ] 05-02: Traversee graph pour documents lies

### Phase 6: MCP Server
**Goal**: Claude Code peut acceder a la documentation via tools MCP
**Depends on**: Phase 5
**Requirements**: MCP-01, MCP-02, MCP-03, MCP-04
**Success Criteria** (what must be TRUE):
  1. Le tool `search_docs` retourne des resultats de recherche avec filtres optionnels
  2. Le tool `index_docs` declenche l'indexation d'un repertoire
  3. Le tool `list_categories` retourne les categories disponibles
  4. Le tool `get_doc` retourne un document complet par son ID
  5. Claude Code peut invoquer ces tools via le protocole MCP
**Plans**: TBD

Plans:
- [ ] 06-01: MCP Server setup avec Java SDK
- [ ] 06-02: Implementation des 4 tools

### Phase 7: CLI
**Goal**: L'utilisateur peut gerer l'indexation via ligne de commande
**Depends on**: Phase 5
**Requirements**: CLI-01, CLI-02, CLI-03, CLI-04
**Success Criteria** (what must be TRUE):
  1. La commande `index <path>` indexe un repertoire de markdown
  2. La commande `search <query>` retourne des resultats de recherche
  3. La commande `status` affiche le nombre de documents et la derniere indexation
  4. La commande `clear` vide la base pour permettre une reindexation complete
**Plans**: TBD

Plans:
- [ ] 07-01: CLI framework et commandes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Infrastructure | 2/2 | ✓ Complete | 2026-01-19 |
| 2. Ingestion Core | 0/3 | Not started | - |
| 3. Graph Relations | 0/2 | Not started | - |
| 4. Recherche Base | 0/2 | Not started | - |
| 5. Recherche Avancee | 0/2 | Not started | - |
| 6. MCP Server | 0/2 | Not started | - |
| 7. CLI | 0/1 | Not started | - |

---
*Roadmap created: 2026-01-19*
*Total requirements: 25 | Phases: 7 | Depth: standard*
