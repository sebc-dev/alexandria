# Phase 13: PIT Mutation Testing - Research

**Researched:** 2026-01-23
**Domain:** Mutation testing with PITest Maven plugin
**Confidence:** HIGH

## Summary

PITest (PIT) is the de facto standard mutation testing framework for the JVM. The pitest-maven plugin version 1.19.4+ (stable as of 2025) provides mutation testing integrated with Maven, requiring a separate JUnit 5 plugin dependency for JUnit 5 support. PITest works by injecting mutations (small code changes) into compiled bytecode and running tests to see if they detect the changes.

The key technical challenges for this phase are:
1. **JUnit 5 integration:** Requires `pitest-junit5-plugin` dependency (version 1.2.2+)
2. **Excluding integration tests:** PITest must only run fast unit tests, not Testcontainers-based integration tests
3. **Incremental analysis:** The `withHistory` flag enables caching for faster subsequent runs
4. **Multi-threading:** The `threads` parameter controls parallel mutation analysis

**Primary recommendation:** Configure PITest with `pitest-maven` plugin version 1.19.4, add `pitest-junit5-plugin` 1.2.2, set `threads=4`, enable `withHistory=true`, and exclude `*IT` test classes. Create a `./mutation` script that runs incremental analysis and displays the mutation score.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| pitest-maven | 1.19.4 | Mutation testing Maven plugin | Latest stable with performance improvements |
| pitest-junit5-plugin | 1.2.2 | JUnit 5 test framework support | Required for JUnit Jupiter tests |

### Supporting
| Tool | Purpose | When to Use |
|------|---------|-------------|
| awk | Parse CSV mutation reports | Script for terminal summary display |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| PITest | Stryker | Stryker is for JS/TS, PITest is JVM standard |
| Default mutators | Descartes engine | Reduces mutants by ~75%, faster but less thorough |
| withHistory | explicit history files | withHistory is simpler for local development |
| CSV parsing with awk | HTML parsing | CSV is machine-readable, consistent with coverage script |

**Installation:**
```xml
<!-- Add to pom.xml build/plugins section in a profile -->
<profile>
    <id>pitest</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>1.19.4</version>
                <dependencies>
                    <dependency>
                        <groupId>org.pitest</groupId>
                        <artifactId>pitest-junit5-plugin</artifactId>
                        <version>1.2.2</version>
                    </dependency>
                </dependencies>
                <configuration>
                    <targetClasses>
                        <param>fr.kalifazzia.alexandria.*</param>
                    </targetClasses>
                    <targetTests>
                        <param>fr.kalifazzia.alexandria.*Test</param>
                    </targetTests>
                    <excludedTestClasses>
                        <param>**.*IT</param>
                    </excludedTestClasses>
                    <threads>4</threads>
                    <withHistory>true</withHistory>
                    <timestampedReports>false</timestampedReports>
                    <outputFormats>
                        <format>HTML</format>
                        <format>CSV</format>
                    </outputFormats>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

## Architecture Patterns

### Report Output Structure
```
target/
└── pit-reports/
    ├── index.html           # HTML report entry point
    ├── mutations.csv        # CSV report for parsing
    └── [package dirs]/      # Per-package HTML reports
```

Note: When `timestampedReports=false`, reports go directly to `pit-reports/`. When true, they go to `pit-reports/YYYYMMDDHHMI/`.

### Pattern 1: Maven Profile for Optional Execution
**What:** Configure PITest in a Maven profile so it only runs when explicitly requested
**When to use:** Always - mutation testing is expensive and should be opt-in
**Example:**
```xml
<!-- Source: https://pitest.org/quickstart/maven/ -->
<profiles>
    <profile>
        <id>pitest</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-maven</artifactId>
                    <!-- configuration here -->
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```
**Run with:** `mvn test -Ppitest` or `mvn org.pitest:pitest-maven:mutationCoverage -Ppitest`

### Pattern 2: Excluding Integration Tests
**What:** Use `excludedTestClasses` to prevent IT tests from running during mutation testing
**When to use:** Always when integration tests exist (Testcontainers, database tests)
**Example:**
```xml
<!-- Source: https://pitest.org/quickstart/maven/ -->
<configuration>
    <excludedTestClasses>
        <param>**.*IT</param>
        <param>**.*IntegrationTest</param>
    </excludedTestClasses>
</configuration>
```
**Why critical:** Integration tests with Testcontainers would cause container startup for every mutant, making analysis impractically slow.

### Pattern 3: Incremental Analysis with withHistory
**What:** Enable history tracking to skip re-analyzing unchanged code
**When to use:** Local development for fast iteration
**Example:**
```xml
<!-- Source: https://pitest.org/quickstart/incremental_analysis/ -->
<configuration>
    <withHistory>true</withHistory>
</configuration>
```
**How it works:** PITest stores results in `java.io.tmpdir` (e.g., `/tmp/`) with project-specific filename. Unchanged classes/tests reuse previous results.

### Pattern 4: Targeting Specific Packages
**What:** Use `targetClasses` and `targetTests` to scope mutation analysis
**When to use:** Always - prevents analyzing third-party code
**Example:**
```xml
<configuration>
    <targetClasses>
        <param>fr.kalifazzia.alexandria.*</param>
    </targetClasses>
    <targetTests>
        <param>fr.kalifazzia.alexandria.*Test</param>
    </targetTests>
</configuration>
```

### Anti-Patterns to Avoid
- **Running against integration tests:** Don't include `*IT` classes - Testcontainers startup per mutant is catastrophic
- **timestampedReports=true for local dev:** Creates new directory per run, harder to access reports
- **threads > CPU count:** No benefit, may cause contention
- **Mutating Application class:** Spring Boot entry points don't benefit from mutation testing

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Mutation injection | Bytecode manipulation | PITest prepare-agent | Complex, version-specific, needs deep bytecode knowledge |
| Test coverage for mutations | Custom test runner | PITest test plugin | Integrates with JUnit 5 lifecycle properly |
| Incremental analysis | File hash comparison script | withHistory flag | PITest tracks bytecode changes, not just files |
| Report generation | Manual CSV creation | outputFormats config | PITest generates consistent, parseable formats |

**Key insight:** Mutation testing involves injecting thousands of mutations and running tests against each. The combinatorial complexity makes hand-rolling impractical.

## Common Pitfalls

### Pitfall 1: Missing JUnit 5 Plugin
**What goes wrong:** PITest runs but no tests execute, reports 100% mutation survival
**Why it happens:** PITest defaults to JUnit 4; JUnit 5 requires explicit plugin
**How to avoid:** Always add `pitest-junit5-plugin` as a dependency of the plugin
**Warning signs:** "No mutations found" or "0 tests run"

### Pitfall 2: Integration Tests Running During Mutation
**What goes wrong:** Extremely slow execution, Testcontainers starting repeatedly
**Why it happens:** PITest scans classpath and runs all tests unless excluded
**How to avoid:** Configure `excludedTestClasses` with `**.*IT` pattern
**Warning signs:** Docker container startup messages in PITest output, multi-hour runs

### Pitfall 3: Static Initializers and Enum Mutations
**What goes wrong:** Mutations in static initializers or enum constructors show as surviving
**Why it happens:** These execute once per JVM before PITest can insert mutants
**How to avoid:** Understand this is a known limitation, not a test quality issue
**Warning signs:** 100% survival rate on enum classes or static fields

### Pitfall 4: withHistory Not Working in CI
**What goes wrong:** Every CI run takes full time, no incremental benefit
**Why it happens:** `withHistory` uses `java.io.tmpdir` which is cleared between CI jobs
**How to avoid:** For CI, use explicit `historyInputFile`/`historyOutputFile` with cached paths
**Warning signs:** CI runs always take same time as fresh local run

### Pitfall 5: Low Mutation Score Due to External Dependencies
**What goes wrong:** Mutations in database calls, HTTP clients survive because tests use mocks
**Why it happens:** Mocked dependencies don't exercise real code paths
**How to avoid:** This reveals actual test gaps; write more thorough unit tests or accept the limitation
**Warning signs:** Repository/adapter classes show low mutation scores

### Pitfall 6: Timeout on Slow Tests
**What goes wrong:** Tests that were passing now timeout during mutation analysis
**Why it happens:** PITest applies `timeoutFactor` (default 1.25x) to normal test time
**How to avoid:** Increase `timeoutConstant` or `timeoutFactor` if legitimate slow tests exist
**Warning signs:** "Timeout" status on mutations, especially infinite loop detection

## Code Examples

Verified patterns from official sources:

### Complete pom.xml Profile Configuration
```xml
<!-- Source: https://pitest.org/quickstart/maven/ -->
<profiles>
    <profile>
        <id>pitest</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-maven</artifactId>
                    <version>1.19.4</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.pitest</groupId>
                            <artifactId>pitest-junit5-plugin</artifactId>
                            <version>1.2.2</version>
                        </dependency>
                    </dependencies>
                    <configuration>
                        <targetClasses>
                            <param>fr.kalifazzia.alexandria.*</param>
                        </targetClasses>
                        <targetTests>
                            <param>fr.kalifazzia.alexandria.*Test</param>
                        </targetTests>
                        <excludedTestClasses>
                            <param>**.*IT</param>
                        </excludedTestClasses>
                        <excludedClasses>
                            <param>fr.kalifazzia.alexandria.AlexandriaApplication</param>
                        </excludedClasses>
                        <threads>4</threads>
                        <withHistory>true</withHistory>
                        <timestampedReports>false</timestampedReports>
                        <outputFormats>
                            <format>HTML</format>
                            <format>CSV</format>
                        </outputFormats>
                    </configuration>
                    <executions>
                        <execution>
                            <id>pit-report</id>
                            <phase>test</phase>
                            <goals>
                                <goal>mutationCoverage</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### Mutation Script - Parsing mutations.csv with awk
```bash
#!/usr/bin/env bash
# Mutation testing script - runs PIT incremental and displays summary
# Parses PIT CSV report using awk (portable, matches coverage script pattern)

set -euo pipefail

REPORT_CSV="target/pit-reports/mutations.csv"

# Run mutation testing with incremental history
echo "Running mutation testing (incremental)..."
mvn test -Ppitest -q

if [[ ! -f "$REPORT_CSV" ]]; then
    echo "Error: Mutation report not found at $REPORT_CSV"
    exit 1
fi

echo ""
echo "Mutation Testing Summary"
echo "========================"

# CSV columns: filename,class,mutator,method,line,status,killingTest
# Status values: KILLED, SURVIVED, NO_COVERAGE, TIMED_OUT, MEMORY_ERROR
awk -F',' '
NR > 1 {
    total++
    if ($6 == "KILLED") killed++
    else if ($6 == "SURVIVED") survived++
    else if ($6 == "NO_COVERAGE") no_coverage++
    else if ($6 == "TIMED_OUT") timed_out++
}
END {
    if (total > 0) {
        score = int(killed * 100 / total)
        printf "Mutation Score : %3d%% (%d/%d mutants killed)\n", score, killed, total
        printf "Survived       : %3d\n", survived + 0
        printf "No Coverage    : %3d\n", no_coverage + 0
        printf "Timed Out      : %3d\n", timed_out + 0
    } else {
        print "No mutations generated"
    }
}
' "$REPORT_CSV"

echo ""
echo "Full report: target/pit-reports/index.html"
```

### Running PITest Directly (Alternative to Profile)
```bash
# Run mutation testing without profile
mvn org.pitest:pitest-maven:mutationCoverage \
    -Dthreads=4 \
    -DwithHistory=true \
    -DtargetClasses="fr.kalifazzia.alexandria.*" \
    -DtargetTests="fr.kalifazzia.alexandria.*Test" \
    -DexcludedTestClasses="**.*IT"
```

### CSV Report Format Reference
```csv
# Source: PITest CSV output format
filename,className,mutatedClass,mutatedMethod,methodDescription,lineNumber,mutator,indexes,killingTests,blocks,status
SomeClass.java,fr.kalifazzia.SomeClass,fr.kalifazzia.SomeClass,someMethod,()V,42,CONDITIONALS_BOUNDARY,1,fr.kalifazzia.SomeClassTest.testSomething,1,KILLED
```

Relevant columns for summary:
- Column 6 (`status`): KILLED, SURVIVED, NO_COVERAGE, TIMED_OUT, MEMORY_ERROR
- Column 1-4: Location information for detailed analysis

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual JUnit 4 discovery | Auto JUnit 5 discovery via plugin | pitest-junit5-plugin 1.2.0 | No need to match launcher versions |
| Separate history file management | `withHistory=true` flag | PITest 1.4.0+ | Simpler local dev configuration |
| External argline management | Auto-inherit from Surefire | PITest 1.18+ | Surefire config automatically used |
| Default single-thread | Configurable `threads` | Always available | Significant speedup on multi-core |

**Deprecated/outdated:**
- **Manual JUnit Platform version matching:** No longer needed with pitest-junit5-plugin 1.2.0+
- **`ALWAYS` mutation threshold in CI:** Use incremental `scmMutationCoverage` goal for PRs instead

## Open Questions

Things that couldn't be fully resolved:

1. **Optimal thread count for this project**
   - What we know: 4 threads is a reasonable default, maximum benefit at CPU count
   - What's unclear: Actual speedup depends on test suite characteristics
   - Recommendation: Start with 4, measure and adjust if needed

2. **Mutation score threshold**
   - What we know: Can configure `mutationThreshold` to fail build below target
   - What's unclear: Appropriate threshold for this codebase
   - Recommendation: Start without threshold, measure baseline, add later if desired

3. **CI integration approach**
   - What we know: `withHistory` doesn't persist between CI runs
   - What's unclear: Whether to cache history file or use `scmMutationCoverage`
   - Recommendation: For Phase 13, focus on local development; CI can be added later

## Sources

### Primary (HIGH confidence)
- [PITest Maven Quickstart](https://pitest.org/quickstart/maven/) - Plugin configuration, goals, parameters
- [PITest Incremental Analysis](https://pitest.org/quickstart/incremental_analysis/) - History mechanism, withHistory usage
- [PITest FAQ](https://pitest.org/faq/) - Common problems, performance tips
- [pitest-junit5-plugin GitHub](https://github.com/pitest/pitest-junit5-plugin/releases) - Version compatibility, release notes
- [PITest GitHub Releases](https://github.com/hcoles/pitest) - Version 1.22.0 latest, 1.19.4 recommended stable

### Secondary (MEDIUM confidence)
- [Mutation Testing in Spring Boot 3 - Paradigma Digital](https://en.paradigmadigital.com/dev/mutation-testing-spring-boot-3-projects-beat-slowness-supercharge-tests/) - Spring Boot optimization tips
- [Baeldung PITest Guide](https://www.baeldung.com/java-mutation-testing-with-pitest) - Configuration examples (verified with official docs)

### Tertiary (LOW confidence)
- WebSearch results for excludedTestClasses patterns - Validated against official documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official documentation, stable releases, version verified
- Architecture: HIGH - Default paths and CSV format documented
- Configuration patterns: HIGH - Official quickstart examples
- Script implementation: MEDIUM - CSV parsing approach consistent with existing coverage script
- Pitfalls: HIGH - Documented in official FAQ and issue trackers

**Research date:** 2026-01-23
**Valid until:** 2026-04-23 (PITest is stable, major releases ~quarterly)
