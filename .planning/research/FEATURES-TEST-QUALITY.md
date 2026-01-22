# Feature Landscape: Java Test Quality Tools (JaCoCo + PIT)

**Domain:** Test quality metrics and mutation testing for Java projects
**Researched:** 2026-01-22
**Confidence:** HIGH (verified via official docs and authoritative sources)

## Table Stakes

Features users expect from any quality gate setup. Missing these means the setup feels incomplete.

### JaCoCo Coverage Features

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Line coverage metrics | Basic expectation - shows which lines executed | Low | Default counter type |
| Branch coverage metrics | Shows conditional path coverage (if/else/switch) | Low | Essential for understanding logic coverage |
| HTML report generation | Visual inspection of coverage with color-coded highlighting | Low | Green/yellow/red diamonds and line colors |
| XML report export | CI artifact storage, tool integration (SonarQube) | Low | Required for most CI pipelines |
| Maven lifecycle integration | `prepare-agent` + `report` goals bound to test/verify phases | Low | Standard setup pattern |
| Exclusion patterns | Exclude generated code, DTOs, config classes | Low | Use `excludes` config element |
| Lombok @Generated exclusion | Auto-ignore Lombok-generated code | Low | Requires JaCoCo 0.8.0+, Lombok 1.16.14+, and `lombok.config` |

### PIT Mutation Features

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Default mutators | CONDITIONALS_BOUNDARY, INCREMENTS, INVERT_NEGS, MATH, NEGATE_CONDITIONALS, VOID_METHOD_CALLS, return mutators | Low | Enabled by default, well-balanced |
| HTML mutation report | Shows survived/killed mutations with source context | Low | Default output format |
| JUnit 5 support | Modern test framework compatibility | Low | Add `pitest-junit5-plugin` dependency |
| Target class filtering | Limit which classes get mutated | Low | `targetClasses` configuration |
| Test class filtering | Limit which tests run against mutants | Low | `targetTests` configuration |
| Logging call exclusion | Avoid mutating log statements | Low | `avoidCallsTo` defaults to common logging packages |

### Quality Gate Features

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Coverage threshold check | Fail build if coverage drops | Low | JaCoCo `check` goal with `COVEREDRATIO` limits |
| Multiple counter types | LINE, BRANCH, INSTRUCTION, COMPLEXITY, METHOD, CLASS | Low | Choose what matters for your project |
| Element-level rules | BUNDLE, PACKAGE, CLASS, SOURCEFILE, METHOD granularity | Medium | Allows different thresholds per scope |

## Differentiators

Features that improve developer experience. Not expected, but valuable for serious quality efforts.

### JaCoCo Advanced Features

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Integration test coverage | Separate `prepare-agent-integration` + `report-integration` goals | Medium | Requires failsafe plugin coordination |
| Report aggregation | Multi-module unified coverage view | Medium | Requires dedicated aggregation module |
| Merge goal | Combine exec files from multiple sources | Medium | Useful for distributed test runs |
| Offline instrumentation | Pre-instrument classes (rare use case) | High | `instrument` + `restore-instrumented-classes` goals |
| Package-level exclusions | Fine-grained coverage rules per package | Medium | Multiple `<rule>` elements with different `<element>` |

### PIT Advanced Features

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Incremental analysis (`withHistory`) | Dramatic speed improvement - 7min to 17s on unchanged code | Low | Just set `withHistory=true` |
| History file persistence | Share analysis between CI runs | Medium | Persist `target/pit-history.xml` as artifact |
| `scmMutationCoverage` goal | Only mutate changed files - perfect for PR builds | Medium | 10-50x faster for PRs; requires maven-scm-plugin |
| Multi-threading | Parallel mutation analysis | Low | Set `threads` > 1 |
| STRONGER mutator group | More aggressive mutations for critical code | Medium | Enable with `mutators=STRONGER` |
| Dry run mode | Generate mutants without analysis (debug) | Low | `pit.dryRun=true` |
| XML output format | Machine-readable for tooling integration | Low | Add XML to `outputFormats` |
| Timestamped reports | Keep historical reports in separate directories | Low | Default behavior, disable with `timestampedReports=false` |

### Developer Workflow Features

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| IDE integration (EclEmma) | Live coverage visualization in Eclipse/IntelliJ | Low | Uses JaCoCo agent |
| Local fast feedback | Run mutation tests on changed files only | Medium | `scmMutationCoverage` with `-DanalyseLastCommit` |
| Custom mutators | Domain-specific mutation operators | High | Plugin system available |

## Anti-Features

Features to deliberately NOT configure. Common mistakes that cause frustration.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Hard 80% coverage gates from day one | Encourages "tests for coverage" - tests that execute code but don't assert behavior. Creates technical debt theater. | Start with metrics as information. Add gates only when team is mature. Focus on coverage trends, not absolute numbers. |
| PACKAGE-level coverage rules | Forces testing POJOs and utility classes with no business logic. Different packages have different testing needs. | Use BUNDLE-level for overall project, CLASS-level only for critical components. |
| Full mutation analysis on every CI build | Mutation testing is slow (minutes to hours). Blocks PR merges, frustrates developers. | Use `scmMutationCoverage` for PRs (changed files only). Full analysis on nightly/weekly schedule. |
| Mutation score gates without baseline | Teams don't know what's achievable. Arbitrary thresholds demotivate. | Run mutation analysis first, establish baseline, then set achievable improvement targets. |
| Testing MapStruct mappers for coverage | Generated code - no value in testing generated implementations. | Exclude with pattern `**/*MapperImpl.class` or use custom @Generated annotation |
| Ignoring branch coverage | Line coverage can be 100% while missing half the conditional paths. | Always track BRANCH coverage alongside LINE. Branch coverage reveals untested edge cases. |
| Running mutation tests in forkCount=0 mode | Breaks JaCoCo agent attachment. Zero coverage recorded. | Always use forkCount >= 1 (surefire/failsafe default) |
| Mutation testing entire codebase including libraries | Wasted compute time on code you don't own. | Use `targetClasses` to scope to your application packages only |

## Feature Dependencies

```
JaCoCo Basic Flow:
  prepare-agent --> test execution --> report

JaCoCo with Check:
  prepare-agent --> test execution --> report --> check

JaCoCo Multi-Module:
  [module-a: prepare-agent --> test]
  [module-b: prepare-agent --> test]
  [aggregate: report-aggregate]

PIT Basic Flow:
  compile --> mutationCoverage (generates mutants, runs tests, reports)

PIT Incremental (CI):
  compile --> scmMutationCoverage (with history file)

Combined Quality Gate:
  compile --> test (with JaCoCo agent) --> jacoco:report --> pitest:mutationCoverage --> jacoco:check
```

## Feature Maturity

| Feature | Stability | Version Requirement |
|---------|-----------|---------------------|
| JaCoCo basic coverage | Stable | 0.8.0+ |
| JaCoCo @Generated exclusion | Stable | 0.8.0+ |
| JaCoCo report-aggregate | Stable | 0.7.7+ |
| PIT default mutators | Stable | 1.0.0+ |
| PIT JUnit 5 | Stable | 1.4.0+ with pitest-junit5-plugin |
| PIT incremental analysis | Experimental | 0.29+ (improved in 1.20.0+) |
| PIT scmMutationCoverage | Stable | 1.19.0+ recommended |

## Recommended Configuration for Alexandria

Based on project context (Java 21, Spring Boot 3.4, Maven, local + CI usage, "tools for reflection not hard thresholds"):

### MVP Quality Setup

**Local (fast iteration):**
- JaCoCo: Basic `prepare-agent` + `report` only (no thresholds)
- PIT: Manual invocation with `mvn pitest:mutationCoverage` when desired
- Use `withHistory=true` for incremental local runs

**CI (full reports):**
- JaCoCo: Generate HTML/XML reports, store as artifacts
- PIT: Use `scmMutationCoverage` for PRs (changed files only)
- Full mutation analysis on nightly/weekly schedule
- No hard gates - metrics for reflection

### Recommended Thresholds (When Ready)

For "reflection not enforcement" philosophy:

| Metric | Information Level | Warning Level | Gate Level |
|--------|------------------|---------------|------------|
| Line Coverage | Track always | < 60% | Never enforce initially |
| Branch Coverage | Track always | < 50% | Never enforce initially |
| Mutation Score | Track always | < 70% | Never enforce initially |

**Note:** These are for information only. The philosophy is to observe and improve, not to block.

### Future Enhancement Path

1. **Phase 1:** Establish baselines (run both tools, record numbers)
2. **Phase 2:** Add trend tracking (compare to previous build)
3. **Phase 3:** Add PIT incremental analysis for faster local runs
4. **Phase 4:** Consider soft gates only after team has calibrated expectations

## Detailed Feature Reference

### JaCoCo Goals

| Goal | Phase | Purpose |
|------|-------|---------|
| `prepare-agent` | initialize | Set up JaCoCo agent for unit tests |
| `prepare-agent-integration` | pre-integration-test | Set up agent for integration tests |
| `report` | test | Generate coverage report from unit test execution |
| `report-integration` | post-integration-test | Generate report from IT execution |
| `report-aggregate` | verify | Combine reports from multiple modules |
| `check` | verify | Validate coverage against thresholds |
| `merge` | generate-resources | Combine multiple exec files |
| `dump` | n/a | Extract exec data from running JVM |
| `instrument` | process-classes | Offline instrumentation |
| `restore-instrumented-classes` | prepare-package | Restore original classes |

### JaCoCo Counter Types

| Counter | What It Measures | When to Use |
|---------|------------------|-------------|
| INSTRUCTION | Java bytecode instructions | Most granular, rarely used directly |
| LINE | Source code lines | Standard metric, easy to understand |
| BRANCH | Decision points (if/else/switch) | Essential for logic coverage |
| COMPLEXITY | Cyclomatic complexity coverage | Shows path coverage |
| METHOD | Method coverage | Coarse-grained overview |
| CLASS | Class coverage | Very coarse, limited value |

### PIT Mutator Groups

| Group | Mutators | Use Case |
|-------|----------|----------|
| DEFAULTS | 11 mutators (boundaries, negation, math, returns, void calls) | Standard analysis, good balance |
| STRONGER | DEFAULTS + REMOVE_CONDITIONALS + math variants | Critical code requiring thorough analysis |
| ALL | All available mutators | Research/analysis, not recommended for CI |
| OLD_DEFAULTS | Legacy mutator set | Backward compatibility only |

### PIT Default Mutators (v1.19+)

1. **CONDITIONALS_BOUNDARY** - Changes `<` to `<=`, `>` to `>=`, etc.
2. **INCREMENTS** - Swaps `++` with `--` and vice versa
3. **INVERT_NEGS** - Negates numeric values
4. **MATH** - Replaces arithmetic operators (+, -, *, /, %)
5. **NEGATE_CONDITIONALS** - Changes `==` to `!=`, etc.
6. **VOID_METHOD_CALLS** - Removes void method calls
7. **EMPTY_RETURNS** - Returns empty values ("", Collections.emptyList())
8. **FALSE_RETURNS** - Returns false from boolean methods
9. **TRUE_RETURNS** - Returns true from boolean methods
10. **NULL_RETURNS** - Returns null from object methods
11. **PRIMITIVE_RETURNS** - Returns 0 from numeric methods

## Sources

### Official Documentation (HIGH confidence)
- [JaCoCo Maven Plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html)
- [JaCoCo Check Goal](https://www.eclemma.org/jacoco/trunk/doc/check-mojo.html)
- [PIT Maven Quickstart](https://pitest.org/quickstart/maven/)
- [PIT Mutators](https://pitest.org/quickstart/mutators/)
- [PIT Incremental Analysis](https://pitest.org/quickstart/incremental_analysis/)

### Authoritative Tutorials (MEDIUM confidence)
- [Baeldung: JaCoCo Multi-Module](https://www.baeldung.com/maven-jacoco-multi-module-project)
- [Baeldung: Mutation Testing with PITest](https://www.baeldung.com/java-mutation-testing-with-pitest)
- [Baeldung: JaCoCo Report Exclusions](https://www.baeldung.com/jacoco-report-exclude)

### Community Best Practices (MEDIUM confidence)
- [DEV.to: Exclude Lombok from JaCoCo](https://dev.to/derlin/exclude-lombok-generated-code-from-test-coverage-jacocosonarqube-4nh1)
- [Medium: PIT on CI/CD Pipeline](https://medium.com/trendyol-tech/pit-mutation-testing-on-ci-cd-pipeline-1298f355bae5)
- [Nicolas Frankel: Faster Mutation Testing](https://blog.frankel.ch/faster-mutation-testing/)

### Current Version Information
- JaCoCo: 0.8.11+ (latest stable)
- PIT: 1.19.1 (as of April 2025)
- pitest-junit5-plugin: Required for JUnit 5 support
