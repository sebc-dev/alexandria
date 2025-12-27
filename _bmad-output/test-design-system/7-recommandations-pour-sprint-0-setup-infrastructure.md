# 7. Recommandations pour Sprint 0 (Setup Infrastructure)

## 7.1 Avant Premier Epic Implementation

### Epic 1: Infrastructure & Setup

1. **Add Exponential Backoff + Circuit Breaker** (TC-004)
   - OpenAI API client wrapper avec retry logic
   - Circuit breaker après 5 échecs consécutifs
   - Test: Mock 429 responses, assert retry behavior

2. **Add Dependency Cruiser Custom Rule pour Multi-Project Isolation** (TC-005)
   - Règle: Tous repository methods doivent filtrer par `project_id`
   - Build-breaking si WHERE clause manquante
   - Test: Dependency Cruiser test suite

### Epic 2: CI/CD & Quality Assurance

3. **Setup GitHub Actions Pipeline**
   - PostgreSQL service container (pgvector/pgvector:pg17)
   - Jobs: Unit Tests, Integration Tests, Architecture Compliance, Coverage Report
   - OPENAI_API_KEY secret configuré

### Epic 7: Observability & Debugging

4. **Setup k6 Performance Test Infrastructure** (TC-006 - BLOCKER)
   - Install k6 CLI
   - Create baseline benchmarks scripts:
     - `tests/performance/layer1-vector-search.k6.js` (100/1K/10K embeddings)
     - `tests/performance/layer2-sql-joins.k6.js`
     - `tests/performance/end-to-end-retrieval.k6.js`
   - Document baseline results (p50/p95/p99) pour comparaison future
   - Add CI job: `performance-tests` (run on schedule, not every commit)

5. **Dual Logging Validation**
   - Integration test: Verify console JSON + file .jsonl outputs
   - Rotation script: `scripts/rotate-logs.sh` avec tests

## 7.2 Per-Epic Test Planning

- **Epic 3 (Knowledge Base):** Focus multi-project isolation integration tests (TC-005)
- **Epic 4 (RAG Pipeline):** Focus HNSW performance validation contre baseline (TC-002)
- **Epic 5 (Claude Code Integration):** Focus MCP stdio mock strategy (TC-001)

---
