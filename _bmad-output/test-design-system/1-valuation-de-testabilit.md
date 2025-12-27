# 1. Évaluation de Testabilité

## 1.1 Controllability (Contrôlabilité) - ✅ PASS

### Peut-on contrôler l'état du système pour les tests ?

**✅ Strengths:**

- **API Seeding:** PostgreSQL accessible via Drizzle ORM
  - Factory functions TypeScript pour Convention, Documentation, Project entities
  - Test database isolation via `project_id` application-level filtering
  - Migrations Drizzle versionnées et reversibles

- **Mockability (Architecture Hexagonale):**
  - Domain layer JAMAIS dépendant des adapters → Interfaces mockables
  - Dependency Injection manuelle via constructors
  - ts-arch enforcement (build-breaking si violations)
  - Tous use-cases injectent Ports (ConventionRepositoryPort, EmbeddingServicePort, etc.)

- **External Dependencies Mockability:**
  - **OpenAI Embeddings API:** Mock via EmbeddingServicePort interface
  - **PostgreSQL:** Test containers (pgvector/pgvector:pg17 Docker image)
  - **Sub-Agent (Layer 3):** Désactivable avec fallback graceful vers Layer 1+2 brut
  - **MCP stdio transport:** Mock stdio streams pour tests unitaires MCP tools

- **Error Triggering:**
  - PostgreSQL connection failure mockable (port injection avec adapter error)
  - OpenAI API errors (429 rate limit, 503 unavailable, timeout) via mocks
  - Layer 3 sub-agent timeout/failure via orchestration skill mock

**⚠️ Limitations:**

1. **MCP stdio E2E Testing** (Concern ID: TC-001)
   - **Impact:** Nécessite Claude Code runtime complet → Slow setup E2E
   - **Mitigation:** Integration tests avec mock stdio + limited E2E smoke tests (10% coverage)

**Verdict Controllability:** ✅ **PASS** - Excellent mockability, limitations E2E gérables

---

## 1.2 Observability (Observabilité) - ✅ PASS

### Peut-on inspecter l'état du système ?

**✅ Strengths:**

- **Dual Logging Strategy (Architecture #6):**
  - **Console Logging:** JSON structuré via Bun native (stdout)
  - **File Logging:** Format .jsonl, location `./logs/alexandria-YYYY-MM-DD.jsonl`
  - **Rotation:** Quotidienne (nouveau fichier chaque jour)
  - **Retention:** Configurable via `LOG_RETENTION_DAYS` (défaut: 30 jours)
  - **Analysis:** `grep`/`jq` sur fichiers JSON (suffisant pour MVP)

- **Métriques Techniques par Layer (NFR27-NFR30):**
  - **Layer 1:** Latency, nombre conventions récupérées, similarity scores
  - **Layer 2:** Latency, nombre documentations liées
  - **Layer 3:** Latency, tokens LLM consommés, taille contexte
  - **End-to-End:** Timestamp, total latency, success/error, project_id

- **Debug Mode:**
  - `ALEXANDRIA_LOG_LEVEL=DEBUG` expose pipeline complet
  - Layer 1 output (IDs conventions + scores cosine)
  - Layer 2 output (IDs docs + relations technologies)
  - Layer 3 input/output (taille contexte tokens)

- **Deterministic Results:**
  - HNSW index cosine similarity déterministe (seed contrôlé)
  - Embedding dimensions fixe (1536 ou 3072)
  - PostgreSQL transactions ACID avec rollback automatique

**⚠️ Limitations:**

1. **Pas de Dashboard Intégré** (NFR33)
   - **Impact:** Analysis manuelle via grep/jq
   - **Acceptable pour MVP:** Logs exportables pour analyse externe (Datadog, CloudWatch future)

**Verdict Observability:** ✅ **PASS** - Observabilité complète avec structured logs + metrics per layer

---

## 1.3 Reliability (Fiabilité) - ⚠️ CONCERNS

### Les tests sont-ils isolés et reproductibles ?

**✅ Strengths:**

- **Parallel-Safe Design:**
  - Application-level filtering via `project_id` (Architecture #4)
  - Pas de shared state global
  - PostgreSQL transaction isolation

- **Cleanup Discipline:**
  - Cascade deletes (embeddings + technology_links supprimés automatiquement)
  - Factory functions avec faker pour données uniques
  - Test fixtures avec auto-cleanup pattern

- **Deterministic Waits:**
  - Pas de retry automatique Layer 3 (évite latence cumulée)
  - OpenAI API calls synchrones (pas de race conditions)

**⚠️ Concerns Identifiés:**

| ID | Concern | Probability | Impact | Score | Mitigation |
|----|---------|-------------|--------|-------|------------|
| **TC-002** | **HNSW Performance Baseline Missing** | 2 | 2 | **4 MEDIUM** | Sprint 0: k6 benchmarks avec 100/1K/10K embeddings |
| **TC-003** | **Sub-Agent External Dependency** | 2 | 2 | **4 MEDIUM** | Mock sub-agent integration, test fallback explicitement |
| **TC-004** | **OpenAI API Rate Limiting (429)** | 2 | 2 | **4 MEDIUM** | Add exponential backoff + circuit breaker (Epic 1) |
| **TC-005** | **Multi-Project Isolation Validation** | 1 | 3 | **3 LOW-MED** | ts-arch custom rule + integration tests multi-projets |

**Verdict Reliability:** ⚠️ **CONCERNS** - Testabilité acceptable MAIS 4 mitigations requises

---
