# 4. Approche de Test NFR

## 4.1 Security (NFR7-NFR10) - ✅ PASS

**Outils:** Bun test (integration) + npm audit (CI) + Gitleaks (CI via CodeRabbit)

| NFR | Test Approach | Tools | Priority | Pass Criteria |
|-----|---------------|-------|----------|---------------|
| **NFR7: Credentials Management** | Validation Zod fail-fast au startup | Bun test + `.env.test` | P0 | Startup fails avec message clair si credentials manquants |
| **NFR8: API Keys Protection** | Logs inspection (pas de secrets exposés) | Grep logs pour `OPENAI_API_KEY` pattern | P0 | 0 occurrences de secrets dans logs |
| **NFR9: Knowledge Base Access** | MCP stdio transport (pas HTTP) | Integration test MCP server binding | P1 | MCP server écoute uniquement stdio (pas TCP port) |
| **NFR10: Data Privacy** | Code hash validation (pas snippets complets) | Unit test ConformityScore value object | P1 | Logs contiennent hash, pas code snippets >100 chars |

**Verdict Security:** ✅ **PASS** - Security validable via integration tests + CI automation

---

## 4.2 Performance (NFR1-NFR6) - ⚠️ CONCERNS

**Outils:** **k6** (load testing) + Bun profiling + PostgreSQL EXPLAIN ANALYZE

| NFR | Test Approach | Tools | Target | Priority | Pass Criteria |
|-----|---------------|-------|--------|----------|---------------|
| **NFR1: p50 ≤3s, p95 ≤5s, p99 ≤10s** | k6 load test end-to-end | k6 stages (50→100 VUs) | SLO enforcement | P0 | p50 <3s, p95 <5s, p99 <10s dans rapport k6 |
| **NFR2: Layer 1 ≤1s (95%)** | k6 + PostgreSQL profiling | k6 + EXPLAIN ANALYZE | >10K embeddings | P0 | p95 latency <1s avec 10K embeddings indexés |
| **NFR3: Layer 2 ≤500ms** | k6 API calls | k6 HTTP requests | SQL JOIN performance | P1 | p95 latency <500ms pour JOIN pivot table |
| **NFR4: Layer 3 ≤2s** | Integration test with real Haiku | Bun test + latency tracking | Token size variation | P0 | p95 latency <2s avec contexte <5000 tokens |
| **NFR5: Upload <200ms acceptance** | k6 upload test | k6 POST /api/conventions | Async embed generation | P1 | Acceptation <200ms, génération async validée |
| **NFR6: 5 concurrent requests** | k6 parallel requests | k6 VU ramping | Transaction isolation | P1 | 5 VUs simultanés sans dégradation >10% |

**⚠️ CRITICAL CONCERN (TC-006 - BLOCKER):**

- **Pas de baseline performance actuel** → Impossible valider NFR2 (HNSW tuning) sans benchmarking
- **Pas d'infrastructure k6** → Scripts load testing manquants
- **Mitigation REQUIRED:** Epic 7 (Observability) + Sprint 0 performance benchmarking AVANT implementation

**Verdict Performance:** ⚠️ **CONCERNS** - Infrastructure test performance manquante (BLOCKER pour gate)

---

## 4.3 Reliability (NFR16-NFR20) - ✅ PASS

**Outils:** Bun test (integration) + Chaos engineering (optionnel)

| NFR | Test Approach | Tools | Priority | Pass Criteria |
|-----|---------------|-------|----------|---------------|
| **NFR16: Fail Fast** | Mock PostgreSQL unavailable, assert error message | Bun test with connection mock | P0 | Error message contient "PostgreSQL unavailable" + connection string (sans credentials) |
| **NFR17: Error Messages Quality** | Validation format messages (context actionnable) | Unit tests error handlers | P1 | Tous messages incluent: what failed, why, what to do |
| **NFR18: Data Integrity** | Rollback transaction test (partial failure) | Integration test with PostgreSQL | P0 | Transaction rollback automatique si échec partiel |
| **NFR19: Graceful Degradation** | Layer 3 timeout → fallback Layer 1+2 | Integration test sub-agent mock | P0 | Fallback Layer 1+2 brut avec warning loggé |
| **NFR20: Uptime** | Memory leak detection (long-running test) | Bun test with 1000+ iterations | P2 | Memory stable après 1000 requêtes (<10% variance) |

**Verdict Reliability:** ✅ **PASS** - Reliability validable via integration tests avec error injection

---

## 4.4 Maintainability (NFR21-NFR25) - ✅ PASS

**Outils:** CI automation (GitHub Actions) + ts-arch + ESLint + CodeRabbit

| NFR | Test Approach | Tools | Target | Priority | Pass Criteria |
|-----|---------------|-------|--------|----------|---------------|
| **NFR21: Code Documentation** | TSDoc coverage validation | ESLint plugin-jsdoc | 100% public APIs | P1 | 0 ESLint errors pour missing JSDoc |
| **NFR22: Code Organization** | Architecture compliance | ts-arch build-breaking tests | 0 violations | P0 | Build fails si Domain imports Adapters |
| **NFR23: Tests Coverage** | Coverage threshold enforcement | Bun test --coverage | ≥80% | P0 | Coverage report ≥80% lines/branches |
| **NFR24: Configuration Management** | `.env.example` sync check | CI script validation | All vars documented | P1 | Tous vars `.env` présents dans `.env.example` |
| **NFR25: Dependency Management** | Vulnerability scan | npm audit (CI) | 0 critical/high | P0 | npm audit returns 0 critical/high vulnerabilities |

**Epic 2 Integration (Tier 3 Strategy):**

- **Tier 1:** ts-arch (build-breaking) → 100% architecture enforcement
- **Tier 2:** ESLint + pre-commit hooks → Real-time IDE feedback
- **Tier 3:** CodeRabbit AI → Contextual PR reviews avec AST-grep custom rules

**Verdict Maintainability:** ✅ **PASS** - Maintainability garantie via Epic 2 CI/CD pipeline

---
