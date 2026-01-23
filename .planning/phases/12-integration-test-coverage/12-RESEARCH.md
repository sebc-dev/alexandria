# Phase 12: Integration Test Coverage - Research

**Researched:** 2026-01-22
**Domain:** JaCoCo integration test coverage, Maven Failsafe, GitHub Actions CI
**Confidence:** HIGH

## Summary

This phase extends the JaCoCo configuration from Phase 11 to include integration test coverage with separate data files and a merged report. The key additions are:

1. **JaCoCo integration test goals**: `prepare-agent-integration` and `report-integration` goals work identically to their unit test counterparts but with defaults optimized for `maven-failsafe-plugin`.

2. **Merged coverage report**: The `merge` goal combines `jacoco.exec` (unit) and `jacoco-it.exec` (integration) into a single `jacoco-merged.exec`, from which a combined report is generated.

3. **GitHub Actions with Testcontainers**: Ubuntu runners have Docker pre-installed, so Testcontainers works out of the box. No Docker-in-Docker or special configuration is needed.

**Primary recommendation:** Add `prepare-agent-integration`, `report-integration`, `merge`, and a merged report execution to pom.xml. Create a new GitHub Actions job for integration tests that runs `mvn verify` and uploads JaCoCo reports as artifacts.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| jacoco-maven-plugin | 0.8.14 | Coverage for unit and integration tests | Already configured in Phase 11 |
| maven-failsafe-plugin | (managed by spring-boot-parent) | Integration test execution | Already configured, runs *IT.java tests |
| Testcontainers | (BOM version) | PostgreSQL container for ITs | Already in project dependencies |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| actions/upload-artifact | v4 | Upload reports to GitHub | CI artifact retention |
| actions/setup-java | v4 | Java environment in CI | Maven builds |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Separate reports + merge | report-aggregate | aggregate is for multi-module projects only |
| Native Docker on runner | Testcontainers Cloud | Cloud requires paid subscription, native is sufficient |
| One CI job for all | Separate jobs for unit/IT | Separate allows faster unit test feedback, parallel execution |

**Additional pom.xml executions:**
```xml
<!-- Add to existing jacoco-maven-plugin -->
<execution>
    <id>prepare-agent-integration</id>
    <goals>
        <goal>prepare-agent-integration</goal>
    </goals>
</execution>
<execution>
    <id>report-integration</id>
    <phase>post-integration-test</phase>
    <goals>
        <goal>report-integration</goal>
    </goals>
</execution>
<execution>
    <id>merge-results</id>
    <phase>post-integration-test</phase>
    <goals>
        <goal>merge</goal>
    </goals>
    <configuration>
        <fileSets>
            <fileSet>
                <directory>${project.build.directory}</directory>
                <includes>
                    <include>jacoco.exec</include>
                    <include>jacoco-it.exec</include>
                </includes>
            </fileSet>
        </fileSets>
        <destFile>${project.build.directory}/jacoco-merged.exec</destFile>
    </configuration>
</execution>
<execution>
    <id>report-merged</id>
    <phase>verify</phase>
    <goals>
        <goal>report</goal>
    </goals>
    <configuration>
        <dataFile>${project.build.directory}/jacoco-merged.exec</dataFile>
        <outputDirectory>${project.reporting.outputDirectory}/jacoco-merged</outputDirectory>
    </configuration>
</execution>
```

## Architecture Patterns

### Report Output Structure
```
target/
├── jacoco.exec              # Unit test coverage (from prepare-agent)
├── jacoco-it.exec           # Integration test coverage (from prepare-agent-integration)
├── jacoco-merged.exec       # Combined coverage (from merge goal)
└── site/
    ├── jacoco/              # Unit test report (from report goal)
    │   ├── index.html
    │   ├── jacoco.xml
    │   └── jacoco.csv
    ├── jacoco-it/           # Integration test report (from report-integration goal)
    │   ├── index.html
    │   ├── jacoco.xml
    │   └── jacoco.csv
    └── jacoco-merged/       # Merged report (from report goal with dataFile override)
        ├── index.html
        ├── jacoco.xml
        └── jacoco.csv
```

### Pattern 1: Separate argLine Properties (NOT Recommended)
**What:** Configure separate property names for unit and integration test agents
**When to use:** Only when you need different agent configurations
**Why NOT here:** The default `argLine` property works for both plugins. Both Surefire and Failsafe automatically use `argLine` when `@{argLine}` is in their configuration.

### Pattern 2: Shared argLine Property (Recommended)
**What:** Both `prepare-agent` and `prepare-agent-integration` set the same `argLine` property
**When to use:** Single-module projects with standard test separation
**Example:**
```xml
<!-- Source: https://blog.soebes.io/posts/2023/10/2023-10-26-maven-jacoco-configuration/ -->
<!-- Surefire uses @{argLine} -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
    </configuration>
</plugin>

<!-- Failsafe uses same @{argLine} pattern -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
    </configuration>
</plugin>
```

### Pattern 3: Merge Goal Phase Binding
**What:** Bind merge goal to `post-integration-test` or `verify` phase
**When to use:** Always when merging unit and integration .exec files
**Why:** Default merge phase is `generate-resources` (before any tests run)
**Example:**
```xml
<!-- Source: https://github.com/jacoco/jacoco/issues/844 -->
<execution>
    <id>merge-results</id>
    <phase>post-integration-test</phase>
    <goals>
        <goal>merge</goal>
    </goals>
</execution>
```

### Anti-Patterns to Avoid
- **Using report-aggregate for single-module project:** `report-aggregate` is designed for multi-module projects only. Use `merge` + `report` instead.
- **Default merge phase binding:** Merge goal defaults to `generate-resources` phase (before tests). Always explicitly bind to `post-integration-test` or `verify`.
- **Missing fileSets in merge:** The `merge` goal requires explicit `fileSets` configuration.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Integration test agent | Manual javaagent config | prepare-agent-integration | Automatic destFile to jacoco-it.exec |
| IT coverage report | Manual report config | report-integration | Default paths for IT reports |
| Merging .exec files | Binary file concatenation | merge goal | .exec format is not simple concat |
| Combined coverage report | Manual calculation | merge + report | JaCoCo handles class/session merging |

**Key insight:** JaCoCo's `.exec` binary format contains session information and class ID mappings. The `merge` goal properly combines these; manual file concatenation would corrupt the data.

## Common Pitfalls

### Pitfall 1: Failsafe Missing argLine Configuration
**What goes wrong:** Integration tests run but coverage shows 0%
**Why it happens:** Failsafe doesn't have `@{argLine}` in its configuration
**How to avoid:** Add `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>` to Failsafe configuration
**Warning signs:** `jacoco-it.exec` doesn't exist or is empty after `mvn verify`

### Pitfall 2: Merge Goal Runs Before Tests
**What goes wrong:** Merged report is empty or only contains partial data
**Why it happens:** Default merge phase is `generate-resources` (before test phase)
**How to avoid:** Explicitly bind merge to `post-integration-test` phase
**Warning signs:** `jacoco-merged.exec` exists but merged report shows less coverage than individual reports

### Pitfall 3: Report-Integration Uses Wrong Phase
**What goes wrong:** Integration test report not generated
**Why it happens:** Default report-integration phase is `verify`, but should be `post-integration-test` for proper ordering
**How to avoid:** Explicitly bind to `post-integration-test` phase
**Warning signs:** Report directory exists but is empty or stale

### Pitfall 4: Testcontainers Timeout in CI
**What goes wrong:** Integration tests fail with container startup timeout
**Why it happens:** GitHub Actions runners may be slower than local Docker
**How to avoid:** Use default Testcontainers timeouts (generous), ensure adequate memory
**Warning signs:** `ContainerLaunchException` with timeout messages

### Pitfall 5: Artifact Upload Path Mismatch
**What goes wrong:** GitHub Actions shows 0 files uploaded
**Why it happens:** Incorrect path pattern in upload-artifact action
**How to avoid:** Use specific paths like `target/site/jacoco*/` or explicit directory list
**Warning signs:** "With the provided path, there will be 0 files uploaded"

## Code Examples

Verified patterns from official sources:

### Complete JaCoCo Configuration for Unit + Integration + Merged Reports
```xml
<!-- Source: https://www.eclemma.org/jacoco/trunk/doc/maven.html -->
<!-- Source: https://natritmeyer.com/howto/reporting-aggregated-unit-and-integration-test-coverage-with-jacoco/ -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <!-- Unit test coverage -->
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
            <!-- Default destFile: ${project.build.directory}/jacoco.exec -->
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
            <!-- Default outputDirectory: ${project.reporting.outputDirectory}/jacoco -->
        </execution>

        <!-- Integration test coverage -->
        <execution>
            <id>prepare-agent-integration</id>
            <goals>
                <goal>prepare-agent-integration</goal>
            </goals>
            <!-- Default destFile: ${project.build.directory}/jacoco-it.exec -->
            <!-- Default phase: pre-integration-test -->
        </execution>
        <execution>
            <id>report-integration</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>report-integration</goal>
            </goals>
            <!-- Default outputDirectory: ${project.reporting.outputDirectory}/jacoco-it -->
        </execution>

        <!-- Merged coverage -->
        <execution>
            <id>merge-results</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>merge</goal>
            </goals>
            <configuration>
                <fileSets>
                    <fileSet>
                        <directory>${project.build.directory}</directory>
                        <includes>
                            <include>jacoco.exec</include>
                            <include>jacoco-it.exec</include>
                        </includes>
                    </fileSet>
                </fileSets>
                <destFile>${project.build.directory}/jacoco-merged.exec</destFile>
            </configuration>
        </execution>
        <execution>
            <id>report-merged</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
            <configuration>
                <dataFile>${project.build.directory}/jacoco-merged.exec</dataFile>
                <outputDirectory>${project.reporting.outputDirectory}/jacoco-merged</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Failsafe Configuration with argLine
```xml
<!-- Source: https://blog.soebes.io/posts/2023/10/2023-10-26-maven-jacoco-configuration/ -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*IT.java</include>
        </includes>
        <!-- @{argLine} = JaCoCo agent, then Mockito agent for Java 21+ -->
        <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### GitHub Actions Integration Test Job
```yaml
# Source: https://www.docker.com/blog/running-testcontainers-tests-using-github-actions/
# Source: https://github.com/actions/upload-artifact
integration-test:
  name: Integration Tests
  runs-on: ubuntu-latest
  # Optional: run after unit tests pass
  needs: build

  steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '21'
        cache: 'maven'

    # Docker is pre-installed on ubuntu-latest, no setup needed

    - name: Run integration tests
      run: mvn -B verify -DskipUTs
      # -DskipUTs skips unit tests (custom property, needs surefire config)
      # Alternative: run full verify to get merged report

    - name: Upload JaCoCo reports
      uses: actions/upload-artifact@v4
      if: always()
      with:
        name: jacoco-reports
        path: |
          target/site/jacoco/
          target/site/jacoco-it/
          target/site/jacoco-merged/
        retention-days: 7
```

### Alternative: Full Build in Single Job (Simpler)
```yaml
# Source: Derived from existing ci.yml pattern
build:
  name: Build & All Tests
  runs-on: ubuntu-latest

  steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '21'
        cache: 'maven'

    - name: Build and run all tests
      run: mvn -B verify

    - name: Upload test results
      uses: actions/upload-artifact@v4
      if: always()
      with:
        name: test-results
        path: |
          target/surefire-reports/
          target/failsafe-reports/
        retention-days: 7

    - name: Upload JaCoCo reports
      uses: actions/upload-artifact@v4
      if: always()
      with:
        name: jacoco-reports
        path: |
          target/site/jacoco/
          target/site/jacoco-it/
          target/site/jacoco-merged/
        retention-days: 7
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Separate argLine properties | Shared argLine with @{} | JaCoCo 0.7+ | Simpler configuration |
| Manual phase binding | prepare-agent-integration defaults | JaCoCo 0.6.4+ | Less configuration needed |
| report-aggregate for single module | merge + report | Always | aggregate is multi-module only |
| DinD for Testcontainers in CI | Native Docker on ubuntu-latest | GitHub Actions default | No special setup needed |

**Deprecated/outdated:**
- **Separate `propertyName` for unit/IT:** Not needed unless you have conflicting argLine requirements
- **Docker-in-Docker for Testcontainers:** Ubuntu runners have Docker pre-installed

## GitHub Actions Testcontainers Notes

### Docker Support on ubuntu-latest
- **Docker is pre-installed**: No `docker-compose up` or Docker setup action needed
- **Ryuk container**: Testcontainers automatically cleans up containers via Ryuk
- **No privileged mode**: Standard Docker socket access is sufficient

### Resource Considerations
- **Memory**: Ubuntu runners have 7GB RAM, sufficient for typical Testcontainers tests
- **Timeout**: Default Testcontainers startup timeout (60s) is usually adequate
- **Parallelism**: Consider separating unit and IT jobs for faster feedback

### Artifact Upload Best Practices
- **Use v4**: Latest version with better performance
- **Multiple paths**: Use pipe syntax for multiple directories
- **retention-days**: 7 days is sufficient for CI debugging
- **if: always()**: Upload even if tests fail (for debugging)

## Open Questions

Things that couldn't be fully resolved:

1. **Skip property for unit tests only**
   - What we know: `-DskipITs` skips integration tests (Failsafe built-in)
   - What's unclear: No built-in `-DskipUTs` for Surefire
   - Recommendation: Use `-Dtest=skip -DfailIfNoTests=false` or configure custom property

2. **Optimal CI job structure**
   - What we know: Single job is simpler, separate jobs allow parallel execution
   - What's unclear: Which is better for this project size
   - Recommendation: Start with single job (simpler), split later if needed

## Sources

### Primary (HIGH confidence)
- [JaCoCo prepare-agent-integration Mojo](https://www.eclemma.org/jacoco/trunk/doc/prepare-agent-integration-mojo.html) - Default destFile: jacoco-it.exec, phase: pre-integration-test
- [JaCoCo report-integration Mojo](https://www.eclemma.org/jacoco/trunk/doc/report-integration-mojo.html) - Default outputDirectory: jacoco-it, phase: verify
- [JaCoCo merge Mojo](https://www.eclemma.org/jacoco/trunk/doc/merge-mojo.html) - fileSets config, destFile parameter
- [JaCoCo Maven Plugin Documentation](https://www.eclemma.org/jacoco/trunk/doc/maven.html) - Example POM with unit+IT coverage
- [actions/upload-artifact v4](https://github.com/actions/upload-artifact) - Multiple paths, retention-days

### Secondary (MEDIUM confidence)
- [Apache Maven JaCoCo Configuration - SoEBeS Blog](https://blog.soebes.io/posts/2023/10/2023-10-26-maven-jacoco-configuration/) - argLine with Failsafe, @{argLine} pattern
- [Aggregated Coverage with JaCoCo - NatRitmeyer](https://natritmeyer.com/howto/reporting-aggregated-unit-and-integration-test-coverage-with-jacoco/) - merge + report pattern
- [Running Testcontainers on GitHub Actions - Docker Blog](https://www.docker.com/blog/running-testcontainers-tests-using-github-actions/) - Ubuntu Docker support

### Tertiary (LOW confidence)
- [JaCoCo GitHub Issue #844](https://github.com/jacoco/jacoco/issues/844) - Merge goal phase binding discussion

## Metadata

**Confidence breakdown:**
- JaCoCo integration goals: HIGH - Official documentation, tested patterns
- Merge configuration: HIGH - Official documentation with examples
- argLine for Failsafe: HIGH - Multiple authoritative sources agree
- GitHub Actions setup: HIGH - Official documentation, standard pattern
- Artifact upload: HIGH - Official action documentation

**Research date:** 2026-01-22
**Valid until:** 2026-04-22 (JaCoCo stable, GitHub Actions v4 current)
