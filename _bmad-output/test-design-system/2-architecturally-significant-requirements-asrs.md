# 2. Architecturally Significant Requirements (ASRs)

## 2.1 ASRs avec Risk Scoring (Probability × Impact)

| ASR ID | Description | Category | Probability | Impact | Score | Justification Architecture |
|--------|-------------|----------|-------------|--------|-------|---------------------------|
| **ASR-001** | NFR1: Retrieval p50 ≤3s end-to-end | PERF | 2 | 3 | **6 HIGH** | Drive HNSW index config + Layer 3 cache skip decision |
| **ASR-002** | NFR2: Vector search ≤1s (95%) | PERF | 2 | 3 | **6 HIGH** | Drive pgvector HNSW parameters (m=16, ef_construction=64) |
| **ASR-003** | NFR4: Layer 3 reformulation ≤2s | PERF | 3 | 2 | **6 HIGH** | Drive Haiku 4.5 model selection + no auto-retry policy |
| **ASR-004** | NFR15: PostgreSQL + pgvector detection | OPS | 1 | 3 | **3 MEDIUM** | Drive fail-fast startup validation + clear error messages |
| **ASR-005** | NFR16: Fail-fast behavior | OPS | 2 | 2 | **4 MEDIUM** | Drive graceful degradation strategy (Layer 3 fallback) |
| **ASR-006** | NFR18: Data integrity (transactions) | DATA | 1 | 3 | **3 MEDIUM** | Drive PostgreSQL transaction usage + rollback on failures |
| **ASR-007** | Architecture #2: Hexagonal isolation | TECH | 1 | 3 | **3 MEDIUM** | Drive ts-arch validation + import restriction rules |
| **ASR-008** | Architecture #4: Multi-project isolation | SEC | 2 | 3 | **6 HIGH** | Drive application-level filtering + ProjectId value object |

## 2.2 Résumé ASRs

- **Total ASRs:** 8
- **HIGH (score ≥6):** 4 (ASR-001, ASR-002, ASR-003, ASR-008)
- **MEDIUM (score 3-5):** 4 (ASR-004, ASR-005, ASR-006, ASR-007)
- **Catégories:** PERF (3), SEC (1), OPS (2), DATA (1), TECH (1)

**Critical Insight:** 4 HIGH risks liés performance/security nécessitent test infrastructure spécialisée (k6 load testing + security tests multi-projets).

---
