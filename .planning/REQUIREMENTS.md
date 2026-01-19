# Requirements: Documentation RAG

**Defined:** 2026-01-19
**Core Value:** Claude Code peut accéder à ma documentation technique personnelle pendant l'implémentation pour respecter mes conventions et bonnes pratiques.

## v1 Requirements

### Ingestion

- [ ] **ING-01**: Parser les fichiers markdown depuis un répertoire
- [ ] **ING-02**: Chunking hiérarchique — parent (1000 tokens), child (200 tokens)
- [ ] **ING-03**: Générer embeddings avec all-MiniLM-L6-v2 (ONNX local)
- [ ] **ING-04**: Stocker embeddings dans PostgreSQL/pgvector avec index HNSW
- [ ] **ING-05**: Extraire métadonnées depuis frontmatter YAML (titre, tags, catégorie)
- [ ] **ING-06**: Stocker relations parent-child dans Apache AGE
- [ ] **ING-07**: Stocker relations entre documents (références croisées) dans AGE

### Recherche

- [ ] **SRCH-01**: Recherche sémantique par similarité cosine sur embeddings
- [ ] **SRCH-02**: Retourner chunks enfants avec contexte parent
- [ ] **SRCH-03**: Filtrer résultats par catégorie
- [ ] **SRCH-04**: Filtrer résultats par tags
- [ ] **SRCH-05**: Recherche hybride vector + full-text (tsvector PostgreSQL)
- [ ] **SRCH-06**: Traversée graph pour trouver documents liés via AGE

### MCP Server

- [ ] **MCP-01**: Tool `search_docs` — recherche sémantique avec filtres optionnels
- [ ] **MCP-02**: Tool `index_docs` — déclencher indexation d'un répertoire
- [ ] **MCP-03**: Tool `list_categories` — lister catégories disponibles
- [ ] **MCP-04**: Tool `get_doc` — récupérer document complet par ID

### CLI

- [ ] **CLI-01**: Commande `index <path>` — indexer répertoire de markdown
- [ ] **CLI-02**: Commande `search <query>` — tester une recherche
- [ ] **CLI-03**: Commande `status` — afficher état de la base (nb docs, dernière indexation)
- [ ] **CLI-04**: Commande `clear` — vider la base pour réindexation complète

### Infrastructure

- [ ] **INFRA-01**: Configuration PostgreSQL 17 avec pgvector et Apache AGE
- [ ] **INFRA-02**: Schema de base de données (tables, index, graph)
- [ ] **INFRA-03**: Configuration Maven/Gradle avec dépendances LangChain4j
- [ ] **INFRA-04**: Docker Compose pour PostgreSQL (dev environment)

## v2 Requirements

### Recherche avancée

- **SRCH-07**: Scoring pondéré configurable (vector vs keyword)
- **SRCH-08**: Reranking des résultats
- **SRCH-09**: Query expansion (synonymes)

### Ingestion avancée

- **ING-08**: Support fichiers RST, AsciiDoc
- **ING-09**: Extraction d'entités NLP (Apache OpenNLP)
- **ING-10**: Détection automatique de références entre documents

### MCP avancé

- **MCP-05**: Tool `similar_docs` — trouver documents similaires à un document donné
- **MCP-06**: Streaming des résultats pour grandes réponses

## Out of Scope

| Feature | Reason |
|---------|--------|
| Watch mode / indexation auto | Complexité inutile, commande manuelle suffit |
| Interface web | Usage via Claude Code uniquement |
| Multi-utilisateur | Usage personnel |
| Knowledge graph complet (GraphRAG) | Overkill pour quelques milliers de docs |
| API REST | MCP suffit pour l'intégration Claude Code |
| Embeddings via API externe | Doit fonctionner offline |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 1 | Pending |
| INFRA-02 | Phase 1 | Pending |
| INFRA-03 | Phase 1 | Pending |
| INFRA-04 | Phase 1 | Pending |
| ING-01 | Phase 2 | Pending |
| ING-02 | Phase 2 | Pending |
| ING-03 | Phase 2 | Pending |
| ING-04 | Phase 2 | Pending |
| ING-05 | Phase 2 | Pending |
| ING-06 | Phase 3 | Pending |
| ING-07 | Phase 3 | Pending |
| SRCH-01 | Phase 4 | Pending |
| SRCH-02 | Phase 4 | Pending |
| SRCH-03 | Phase 4 | Pending |
| SRCH-04 | Phase 4 | Pending |
| SRCH-05 | Phase 5 | Pending |
| SRCH-06 | Phase 5 | Pending |
| MCP-01 | Phase 6 | Pending |
| MCP-02 | Phase 6 | Pending |
| MCP-03 | Phase 6 | Pending |
| MCP-04 | Phase 6 | Pending |
| CLI-01 | Phase 7 | Pending |
| CLI-02 | Phase 7 | Pending |
| CLI-03 | Phase 7 | Pending |
| CLI-04 | Phase 7 | Pending |

**Coverage:**
- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0 ✓

---
*Requirements defined: 2026-01-19*
*Last updated: 2026-01-19 after initial definition*
