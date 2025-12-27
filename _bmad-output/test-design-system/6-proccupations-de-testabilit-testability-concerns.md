# 6. Préoccupations de Testabilité (Testability Concerns)

## 6.1 Concerns Identifiés

| ID | Concern | Severity | Impact on Testing | Mitigation |
|----|---------|----------|-------------------|------------|
| **TC-001** | **MCP stdio E2E Testing** | ⚠️ CONCERNS | Nécessite Claude Code runtime complet → Slow E2E setup | Use integration tests with mock stdio + limited E2E smoke tests (10% coverage) |
| **TC-002** | **HNSW Performance Baseline Missing** | ⚠️ CONCERNS | Impossible valider NFR2 sans baseline | Sprint 0: k6 benchmarks avec 100/1K/10K embeddings |
| **TC-003** | **Sub-Agent External Dependency** | ⚠️ CONCERNS | Layer 3 hors contrôle Alexandria → Flaky tests possibles | Mock sub-agent pour integration, test fallback explicitement |
| **TC-004** | **OpenAI API Rate Limiting** | ⚠️ CONCERNS | Embedding generation peut échouer (429) sans retry | Add exponential backoff + circuit breaker (Epic 1) |
| **TC-005** | **Multi-Project Isolation Validation** | ⚠️ CONCERNS | Application-level filtering facile à oublier → Security risk | ts-arch custom rule + integration tests multi-projets obligatoires |
| **TC-006** | **No Performance Test Infrastructure** | ✅ **RESOLVED** | k6 pas installé, pas de load test scripts | ~~Epic 7: Add k6 scripts + CI job pour performance validation~~ **RESOLVED 2025-12-27** - See section 6.3 |

## 6.2 Gate Decision Impact

**BLOCKER pour Implementation Readiness Gate:**

- **TC-006** : Must resolve AVANT Phase 4 implementation
  - **Action Required:** Epic 7 (Observability) doit inclure k6 setup + baseline benchmarks
  - **Owner:** Tech Lead
  - **Deadline:** Sprint 0 (avant premier epic implementation)

**CONCERNS (pas de blocage):**

- **TC-001 à TC-005** : Mitigations définies, implémentables pendant Phase 4
  - Trackés dans test-design-epic-X.md pour chaque epic

## 6.3 TC-006 Resolution (2025-12-27)

**BLOCKER RESOLVED** ✅

### Infrastructure Created

Complete k6 performance testing infrastructure has been implemented in `tests/performance/`:

**Scripts Created:**
1. **`layer1-vector-search.k6.js`** - Validates NFR2 (Layer 1 ≤1s p95)
   - Tests HNSW vector search performance
   - Configurable for 100/1K/10K embeddings
   - Automated threshold validation

2. **`layer2-sql-joins.k6.js`** - Validates NFR3 (Layer 2 ≤500ms p95)
   - Tests SQL JOIN performance via pivot tables
   - Technology linking validation
   - Database index verification

3. **`end-to-end-retrieval.k6.js`** - Validates NFR1 (p50 ≤3s, p95 ≤5s, p99 ≤10s)
   - Complete pipeline testing (Layer 1 + 2 + 3)
   - Per-layer latency breakdown
   - Context quality validation

**Helper Scripts:**
- **`run-benchmarks.sh`** - Automated benchmark execution
  - Commands: `all`, `layer1`, `layer2`, `e2e`, `baseline`, `smoke`
  - Environment variable support
  - Result archiving with timestamps

**Documentation:**
- **`README.md`** - Complete usage guide
  - Installation instructions for k6
  - SLO targets and pass criteria
  - Troubleshooting guide
  - CI/CD integration example

### Baseline Benchmarking Process

**Sprint 0 Actions Required:**
1. Install k6 (see README.md for platform-specific instructions)
2. Run baseline suite: `./run-benchmarks.sh baseline`
3. Document results in `docs/performance-baselines.md`
4. Verify all NFRs pass (NFR1-NFR4)
5. Add CI job for weekly performance regression testing

**Expected Outcomes:**
- ✅ NFR2 validation: Layer 1 p95 <1s
- ✅ NFR3 validation: Layer 2 p95 <500ms
- ✅ NFR1 validation: End-to-end p50 <3s, p95 <5s, p99 <10s
- ✅ Performance baseline documented for future comparison

### Gate Impact

**Status Change:** ❌ BLOCKER → ✅ RESOLVED

**Gate Decision Update:**
- Previous: ⚠️ CONCERNS with 1 BLOCKER
- Current: ⚠️ CONCERNS (TC-001 to TC-005 remain, non-blocking)
- **Implementation Readiness Gate:** Can proceed after Sprint 0 baseline benchmarking

**Remaining Actions:**
1. Execute baseline benchmarks in Sprint 0
2. Document baseline in `docs/performance-baselines.md`
3. Run `/bmad:bmm:workflows:check-implementation-readiness` to validate gate

---
