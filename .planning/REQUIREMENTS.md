# Requirements: Documentation RAG

**Defined:** 2026-01-19
**Core Value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## v1 Requirements

### Ingestion

- [x] **ING-01**: Parser les fichiers markdown depuis un repertoire
- [x] **ING-02**: Chunking hierarchique - parent (1000 tokens), child (200 tokens)
- [x] **ING-03**: Generer embeddings avec all-MiniLM-L6-v2 (ONNX local)
- [x] **ING-04**: Stocker embeddings dans PostgreSQL/pgvector avec index HNSW
- [x] **ING-05**: Extraire metadonnees depuis frontmatter YAML (titre, tags, categorie)
- [x] **ING-06**: Stocker relations parent-child dans Apache AGE
- [x] **ING-07**: Stocker relations entre documents (references croisees) dans AGE

### Recherche

- [ ] **SRCH-01**: Recherche semantique par similarite cosine sur embeddings
- [ ] **SRCH-02**: Retourner chunks enfants avec contexte parent
- [ ] **SRCH-03**: Filtrer resultats par categorie
- [ ] **SRCH-04**: Filtrer resultats par tags
- [ ] **SRCH-05**: Recherche hybride vector + full-text (tsvector PostgreSQL)
- [ ] **SRCH-06**: Traversee graph pour trouver documents lies via AGE

### MCP Server

- [ ] **MCP-01**: Tool `search_docs` - recherche semantique avec filtres optionnels
- [ ] **MCP-02**: Tool `index_docs` - declencher indexation d'un repertoire
- [ ] **MCP-03**: Tool `list_categories` - lister categories disponibles
- [ ] **MCP-04**: Tool `get_doc` - recuperer document complet par ID

### CLI

- [ ] **CLI-01**: Commande `index <path>` - indexer repertoire de markdown
- [ ] **CLI-02**: Commande `search <query>` - tester une recherche
- [ ] **CLI-03**: Commande `status` - afficher etat de la base (nb docs, derniere indexation)
- [ ] **CLI-04**: Commande `clear` - vider la base pour reindexation complete

### Infrastructure

- [x] **INFRA-01**: Configuration PostgreSQL 17 avec pgvector et Apache AGE
- [x] **INFRA-02**: Schema de base de donnees (tables, index, graph)
- [x] **INFRA-03**: Configuration Maven/Gradle avec dependances LangChain4j
- [x] **INFRA-04**: Docker Compose pour PostgreSQL (dev environment)

## v2 Requirements

### Recherche avancee

- **SRCH-07**: Scoring pondere configurable (vector vs keyword)
- **SRCH-08**: Reranking des resultats
- **SRCH-09**: Query expansion (synonymes)

### Ingestion avancee

- **ING-08**: Support fichiers RST, AsciiDoc
- **ING-09**: Extraction d'entites NLP (Apache OpenNLP)
- **ING-10**: Detection automatique de references entre documents

### MCP avance

- **MCP-05**: Tool `similar_docs` - trouver documents similaires a un document donne
- **MCP-06**: Streaming des resultats pour grandes reponses

## Out of Scope

| Feature | Reason |
|---------|--------|
| Watch mode / indexation auto | Complexite inutile, commande manuelle suffit |
| Interface web | Usage via Claude Code uniquement |
| Multi-utilisateur | Usage personnel |
| Knowledge graph complet (GraphRAG) | Overkill pour quelques milliers de docs |
| API REST | MCP suffit pour l'integration Claude Code |
| Embeddings via API externe | Doit fonctionner offline |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 1 | Complete |
| INFRA-02 | Phase 1 | Complete |
| INFRA-03 | Phase 1 | Complete |
| INFRA-04 | Phase 1 | Complete |
| ING-01 | Phase 2 | Complete |
| ING-02 | Phase 2 | Complete |
| ING-03 | Phase 2 | Complete |
| ING-04 | Phase 2 | Complete |
| ING-05 | Phase 2 | Complete |
| ING-06 | Phase 3 | Complete |
| ING-07 | Phase 3 | Complete |
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
- v1 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0

---
*Requirements defined: 2026-01-19*
*Last updated: 2026-01-20 - Phase 3 complete (ING-06, ING-07)*
