# Requirements: Alexandria v0.3

**Defined:** 2026-01-22
**Core Value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## v0.3 Requirements

Requirements for Better DX and Quality Gate milestone. Each maps to roadmap phases.

### Coverage (JaCoCo)

- [ ] **COV-01**: Rapport HTML local genere dans `target/site/jacoco/`
- [ ] **COV-02**: Rapport XML pour CI et SonarCloud
- [ ] **COV-03**: Rapports separes pour tests unitaires vs integration
- [ ] **COV-04**: Rapport agrege merged combinant unit + integration

### Mutation (PIT)

- [ ] **MUT-01**: Rapport HTML local genere dans `target/pit-reports/`
- [ ] **MUT-02**: Mode incremental active (withHistory=true) pour iteration rapide
- [ ] **MUT-03**: Execution multi-threaded (threads=4) pour performance

### CI

- [ ] **CI-01**: Tests d'integration Testcontainers sur chaque PR + push master
- [ ] **CI-02**: Rapports JaCoCo uploades comme artifacts GitHub Actions
- [ ] **CI-03**: Badge de couverture dynamique dans README

### Developer Experience

- [ ] **DX-01**: Script `./coverage` lance JaCoCo et affiche resume
- [ ] **DX-02**: Script `./mutation` lance PIT incremental sur fichiers modifies
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
| COV-01 | TBD | Pending |
| COV-02 | TBD | Pending |
| COV-03 | TBD | Pending |
| COV-04 | TBD | Pending |
| MUT-01 | TBD | Pending |
| MUT-02 | TBD | Pending |
| MUT-03 | TBD | Pending |
| CI-01 | TBD | Pending |
| CI-02 | TBD | Pending |
| CI-03 | TBD | Pending |
| DX-01 | TBD | Pending |
| DX-02 | TBD | Pending |
| DX-03 | TBD | Pending |

**Coverage:**
- v0.3 requirements: 13 total
- Mapped to phases: 0
- Unmapped: 13

---
*Requirements defined: 2026-01-22*
*Last updated: 2026-01-22 after initial definition*
