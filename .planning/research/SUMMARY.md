# Project Research Summary

**Project:** Alexandria v0.3 - Better DX and Quality Gate
**Domain:** Java Test Quality Tools (JaCoCo Code Coverage + PIT Mutation Testing)
**Researched:** 2026-01-22
**Confidence:** HIGH

## Executive Summary

Alexandria v0.3 focuses on adding test quality visibility without enforcement gates. The research confirms that JaCoCo 0.8.14 and PIT 1.22.0 are the correct tool choices for Java 21 with Spring Boot 3.4. Both tools integrate cleanly with Maven's lifecycle, but require careful argLine configuration to coexist with the existing Mockito dynamic agent loading flag. The core philosophy of "tools for reflection, not hard thresholds" aligns with best practices that discourage premature coverage gates.

The recommended approach is phased integration: JaCoCo first (simpler, immediate value), then PIT (slower, optional). JaCoCo provides immediate feedback via HTML reports and CI artifacts, while PIT's incremental analysis mode makes local mutation testing practical. Both tools should be profile-activated to avoid impacting normal build times. SonarQube integration comes essentially free once JaCoCo generates XML reports.

The primary risk is silent failure where coverage shows 0% due to misconfigured argLine merging. This is a well-documented pitfall with a clear prevention strategy (`@{argLine}` syntax). Secondary risks include PIT accidentally running integration tests (causing massive slowdowns) and version mismatches with the JUnit 5 plugin. All identified pitfalls have straightforward mitigations and low recovery costs.

## Key Findings

### Recommended Stack

JaCoCo and PIT are the standard tools for Java code coverage and mutation testing. Both have mature Maven integrations and explicit Java 21 support.

**Core technologies:**
- **JaCoCo 0.8.14**: Code coverage measurement - latest stable with Java 21-25 support via ASM 9.8
- **PIT 1.22.0**: Mutation testing engine - latest stable with incremental analysis and history support
- **pitest-junit5-plugin 1.2.3**: JUnit 5 support for PIT - auto-detects JUnit Platform 1.5-1.10+
- **cicirello/jacoco-badge-generator v2**: GitHub Action for coverage badges - no external API calls, PR-aware

**Critical version requirements:**
- JaCoCo must be 0.8.11+ for Java 21 support (0.8.14 recommended)
- PIT must be 1.9.0+ for pitest-junit5-plugin 1.0+ compatibility
- pitest-junit5-plugin must be 1.2.0+ to avoid manual launcher configuration

### Expected Features

**Must have (table stakes):**
- Line and branch coverage metrics with HTML/XML reports
- Maven lifecycle integration (prepare-agent, report goals)
- Exclusion patterns for generated/config code
- Default mutators covering boundaries, negation, math, returns
- JUnit 5 test framework support
- CI artifact upload for coverage reports

**Should have (differentiators):**
- Integration test coverage (separate jacoco-it.exec)
- PIT incremental analysis with history file (7min to 17s improvement)
- `scmMutationCoverage` goal for PR-only mutation analysis
- Multi-threaded PIT execution
- SonarQube integration via XML report path

**Defer (v2+):**
- Coverage threshold enforcement (add only after baseline established)
- Multi-module report aggregation (Alexandria is single-module)
- Custom mutators (domain-specific, high complexity)
- Offline instrumentation (rare use case)

### Architecture Approach

Both tools integrate at the Maven lifecycle level. JaCoCo attaches as a Java agent during test execution (prepare-agent goal), while PIT runs as a separate analysis phase. The key integration point is argLine merging with Surefire/Failsafe plugins.

**Major components:**
1. **JaCoCo Maven Plugin** - 4 executions: prepare-agent (unit), report (unit), prepare-agent-integration (IT), report-integration (IT)
2. **PIT Maven Plugin** - Profile-activated, targets core package only, excludes API/infra layers
3. **GitHub Actions Integration** - Coverage artifact upload, optional badge generation
4. **SonarQube Integration** - Automatic via `sonar.coverage.jacoco.xmlReportPaths` property

**Data flow:**
```
tests execute --> JaCoCo agent records --> jacoco.exec --> report goal --> HTML/XML/CSV
                                                        --> SonarQube scanner reads XML
```

### Critical Pitfalls

1. **argLine overwritten by Surefire** - JaCoCo agent silently ignored, 0% coverage. **Prevention:** Use `@{argLine}` late evaluation syntax and define empty `<argLine></argLine>` property default.

2. **PIT runs integration tests** - Testcontainers spin up for each mutation, 2min becomes 2+ hours. **Prevention:** Explicit `<excludedTestClasses>**.*IT</excludedTestClasses>` configuration.

3. **Coverage thresholds block all PRs** - Adding enforcement before baseline breaks CI immediately. **Prevention:** Report-only mode for v0.3, no `jacoco:check` goal binding.

4. **PIT JUnit 5 plugin version mismatch** - Cryptic NoSuchMethodError failures. **Prevention:** Use compatible versions (PIT 1.22.0 + plugin 1.2.3).

5. **PIT single-threaded default** - 30+ minute runs discourage local usage. **Prevention:** Configure `<threads>4</threads>` and `<withHistory>true</withHistory>`.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: JaCoCo Foundation

**Rationale:** JaCoCo has no dependencies, provides immediate value, and must be configured correctly before PIT. The argLine integration is the critical first step.

**Delivers:**
- Code coverage reports (HTML/XML) for unit tests
- CI artifact upload for coverage visibility
- SonarQube coverage integration

**Addresses features:**
- Line/branch coverage metrics
- HTML report generation
- XML report export
- Maven lifecycle integration
- Exclusion patterns for config classes

**Avoids pitfalls:**
- Pitfall 1: argLine overwritten (use @{argLine} from start)
- Pitfall 5: Coverage thresholds blocking CI (report-only mode)
- Pitfall 7: Config classes in reports (exclusion patterns)

### Phase 2: Integration Test Coverage

**Rationale:** Depends on Phase 1 JaCoCo setup being correct. Adds coverage for IT tests which exercise the full stack.

**Delivers:**
- Separate jacoco-it.exec and report
- Optional merged coverage report
- IT coverage artifact in CI

**Addresses features:**
- Integration test coverage
- Report separation (unit vs IT)

**Avoids pitfalls:**
- Pitfall 3: Coverage not merged (explicit merge execution if desired)
- Pitfall 9: Failsafe not instrumented (same argLine pattern)

### Phase 3: PIT Mutation Testing

**Rationale:** PIT is independent but benefits from established test infrastructure. Should be optional/profile-based to avoid impacting normal builds.

**Delivers:**
- Mutation testing capability (local only initially)
- HTML mutation report
- Incremental analysis for fast local feedback

**Addresses features:**
- Default mutators
- Target class filtering (core package only)
- Multi-threaded execution
- History file for incremental runs

**Avoids pitfalls:**
- Pitfall 2: ITs run by PIT (excludedTestClasses pattern)
- Pitfall 4: JUnit 5 version mismatch (verified compatible versions)
- Pitfall 6: Single-threaded slowness (threads=4)
- Pitfall 10: parseSurefireConfig confusion (set to false)

### Phase Ordering Rationale

- **Phase 1 before 2:** JaCoCo base configuration must work before IT coverage extension
- **Phase 3 last:** PIT is optional tooling, not blocking for milestone completion
- **No thresholds in any phase:** Aligns with "reflection, not enforcement" philosophy

Dependencies:
```
Phase 1 (JaCoCo unit) --> Phase 2 (JaCoCo IT)
                     \--> Phase 3 (PIT) [independent but logically after]
```

### Research Flags

Phases with standard patterns (skip research-phase):
- **Phase 1:** Well-documented, official JaCoCo Maven plugin docs sufficient
- **Phase 2:** Extension of Phase 1, same patterns apply
- **Phase 3:** Official PIT Maven quickstart covers all needs

No phases require additional research. All patterns are well-documented with high confidence.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Official release notes and changelogs verified, versions confirmed for Java 21 |
| Features | HIGH | Official documentation reviewed, feature maturity documented |
| Architecture | HIGH | Maven lifecycle integration is standard, verified with multiple sources |
| Pitfalls | HIGH | Official docs + community sources confirm, all have documented solutions |

**Overall confidence:** HIGH

All research was validated against official documentation (JaCoCo Maven Plugin, PIT Quickstart, Maven Build Lifecycle). Community sources corroborated findings. No areas require validation during implementation.

### Gaps to Address

- **Actual baseline coverage:** Unknown until JaCoCo runs. First run will establish baseline for future comparison.
- **PIT performance on Alexandria codebase:** Estimated based on class count, actual timing will vary.

Neither gap affects implementation approach. Both are resolved by running the tools.

## Sources

### Primary (HIGH confidence)
- [JaCoCo Maven Plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html) - Plugin configuration, goal binding
- [JaCoCo Change History](https://www.jacoco.org/jacoco/trunk/doc/changes.html) - Version history, Java 21 support
- [PIT Quickstart for Maven](https://pitest.org/quickstart/maven/) - Maven plugin configuration
- [PIT Incremental Analysis](https://pitest.org/quickstart/incremental_analysis/) - History file usage
- [pitest-junit5-plugin GitHub](https://github.com/pitest/pitest-junit5-plugin) - Version compatibility

### Secondary (MEDIUM confidence)
- [Baeldung: Mutation Testing with PITest](https://www.baeldung.com/java-mutation-testing-with-pitest) - Tutorial patterns
- [Baeldung: JaCoCo Report Exclusions](https://www.baeldung.com/jacoco-report-exclude) - Exclusion patterns
- [Nicolas Frankel: Faster Mutation Testing](https://blog.frankel.ch/faster-mutation-testing/) - Performance optimization

### Tertiary (LOW confidence)
- [GitHub cicirello/jacoco-badge-generator](https://github.com/cicirello/jacoco-badge-generator) - CI badge generation (optional feature)

---
*Research completed: 2026-01-22*
*Ready for roadmap: yes*
