# Requirements: Alexandria v0.3

**Defined:** 2026-01-22
**Core Value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## v0.3 Requirements

Requirements for Better DX and Quality Gate milestone. Each maps to roadmap phases.

### Coverage (JaCoCo)

- [x] **COV-01**: Rapport HTML local genere dans `target/site/jacoco/`
- [x] **COV-02**: Rapport XML pour CI et SonarCloud
- [x] **COV-03**: Rapports separes pour tests unitaires vs integration
- [x] **COV-04**: Rapport agrege merged combinant unit + integration

### Mutation (PIT)

- [x] **MUT-01**: Rapport HTML local genere dans `target/pit-reports/`
- [x] **MUT-02**: Mode incremental active (withHistory=true) pour iteration rapide
- [x] **MUT-03**: Execution multi-threaded (threads=4) pour performance

### CI

- [x] **CI-01**: Tests d'integration Testcontainers sur chaque PR + push master
- [x] **CI-02**: Rapports JaCoCo uploades comme artifacts GitHub Actions
- [ ] **CI-03**: Badge de couverture dynamique dans README

### Developer Experience

- [x] **DX-01**: Script `./coverage` lance JaCoCo et affiche resume
- [x] **DX-02**: Script `./mutation` lance PIT incremental sur fichiers modifies
- [ ] **DX-03**: Script `./quality` lance tout et affiche resume consolide

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Seuils de couverture bloquants | Philosophie: outils de reflexion, pas de barrieres |
| PIT en CI | Trop lent (~minutes), garde en local seulement |
| Mutation sur tests d'integration | Exclus pour performance (Testcontainers startup) |
| Coverage enforcement rules | Pas de `jacoco:check` avec seuils |
| GraalVM native-image testing | Complexite ONNX, hors scope |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| COV-01 | Phase 11 | Complete |
| COV-02 | Phase 11 | Complete |
| COV-03 | Phase 12 | Complete |
| COV-04 | Phase 12 | Complete |
| MUT-01 | Phase 13 | Complete |
| MUT-02 | Phase 13 | Complete |
| MUT-03 | Phase 13 | Complete |
| CI-01 | Phase 12 | Complete |
| CI-02 | Phase 12 | Complete |
| CI-03 | Phase 14 | Pending |
| DX-01 | Phase 11 | Complete |
| DX-02 | Phase 13 | Complete |
| DX-03 | Phase 14 | Pending |

**Coverage:**
- v0.3 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0

---
*Requirements defined: 2026-01-22*
*Last updated: 2026-01-23 after Phase 13 completion*
