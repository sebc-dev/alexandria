# Roadmap: Alexandria v0.3

## Overview

Alexandria v0.3 adds test quality visibility through JaCoCo code coverage and PIT mutation testing. The milestone delivers local HTML reports for developer reflection, CI artifacts for visibility, and convenience scripts for quick quality analysis. No enforcement gates or thresholds - tools for reflection, not barriers.

## Milestones

- v0.1 MVP (Phases 1-7) - SHIPPED 2026-01-21
- v0.2 Full Docker (Phases 8-10) - SHIPPED 2026-01-22
- v0.3 Better DX and Quality Gate (Phases 11-14) - IN PROGRESS

## Phases

- [ ] **Phase 11: JaCoCo Unit Coverage** - Code coverage foundation with local reports and CI integration
- [ ] **Phase 12: Integration Test Coverage** - Failsafe coverage and merged reports
- [ ] **Phase 13: PIT Mutation Testing** - Mutation testing with incremental analysis
- [ ] **Phase 14: Quality Dashboard** - Coverage badge and consolidated quality scripts

## Phase Details

### Phase 11: JaCoCo Unit Coverage
**Goal**: Developer can see unit test coverage via local HTML report and CI artifacts
**Depends on**: Nothing (first phase of v0.3)
**Requirements**: COV-01, COV-02, DX-01
**Success Criteria** (what must be TRUE):
  1. `mvn test` generates HTML coverage report in `target/site/jacoco/`
  2. `mvn test` generates XML report for CI/SonarCloud integration
  3. `./coverage` script runs JaCoCo and displays coverage summary in terminal
  4. Existing `-XX:+EnableDynamicAgentLoading` flag coexists with JaCoCo agent (no argLine conflict)
**Plans**: TBD

Plans:
- [ ] 11-01: [TBD during planning]

### Phase 12: Integration Test Coverage
**Goal**: Developer can see coverage for integration tests, with merged overall report
**Depends on**: Phase 11
**Requirements**: COV-03, COV-04, CI-01, CI-02
**Success Criteria** (what must be TRUE):
  1. `mvn verify` generates separate `jacoco.exec` (unit) and `jacoco-it.exec` (integration) files
  2. `mvn verify` generates merged coverage report combining unit + integration tests
  3. GitHub Actions runs integration tests (Testcontainers) on PR and push to master
  4. GitHub Actions uploads JaCoCo reports as downloadable artifacts
**Plans**: TBD

Plans:
- [ ] 12-01: [TBD during planning]

### Phase 13: PIT Mutation Testing
**Goal**: Developer can run mutation testing locally with fast incremental analysis
**Depends on**: Nothing (can run in parallel with Phase 12 technically)
**Requirements**: MUT-01, MUT-02, MUT-03, DX-02
**Success Criteria** (what must be TRUE):
  1. `mvn test -Ppitest` generates mutation testing report in `target/pit-reports/`
  2. Second PIT run is significantly faster due to incremental history file
  3. PIT runs with 4 threads for performance
  4. `./mutation` script runs PIT incremental and displays mutation score summary
  5. PIT excludes integration tests (no Testcontainers startup during mutation)
**Plans**: TBD

Plans:
- [ ] 13-01: [TBD during planning]

### Phase 14: Quality Dashboard
**Goal**: Developer has single command for full quality analysis and visible coverage badge
**Depends on**: Phase 11, Phase 12, Phase 13
**Requirements**: CI-03, DX-03
**Success Criteria** (what must be TRUE):
  1. README.md displays dynamic coverage badge updated on each CI run
  2. `./quality` script runs JaCoCo + PIT and displays consolidated quality summary
**Plans**: TBD

Plans:
- [ ] 14-01: [TBD during planning]

## Progress

**Execution Order:** Phases execute in numeric order: 11 -> 12 -> 13 -> 14

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 11. JaCoCo Unit Coverage | 0/TBD | Not started | - |
| 12. Integration Test Coverage | 0/TBD | Not started | - |
| 13. PIT Mutation Testing | 0/TBD | Not started | - |
| 14. Quality Dashboard | 0/TBD | Not started | - |

---
*Roadmap created: 2026-01-22*
*Last updated: 2026-01-22*
