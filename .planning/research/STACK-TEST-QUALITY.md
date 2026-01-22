# Technology Stack: Test Quality Tools

**Project:** Alexandria v0.3 Milestone
**Researched:** 2026-01-22
**Confidence:** HIGH (versions verified against official releases)

## Recommended Stack

### Code Coverage: JaCoCo

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| [jacoco-maven-plugin](https://www.eclemma.org/jacoco/) | **0.8.14** | Code coverage measurement | Latest stable with Java 21-25 support, ASM 9.8 |

**Version rationale:**
- 0.8.14 released October 2024, officially supports Java 21-25
- 0.8.11 was the first version with official Java 21 support
- Current project uses Java 21, so 0.8.14 provides comfortable headroom

**Do NOT use:**
- 0.8.10 or earlier: Experimental/no Java 21 support
- 0.8.15-SNAPSHOT: Development version, unstable

### Mutation Testing: PIT (Pitest)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| [pitest-maven](https://pitest.org/) | **1.22.0** | Mutation testing engine | Latest stable (Nov 2024), Java 21 compatible |
| [pitest-junit5-plugin](https://github.com/pitest/pitest-junit5-plugin) | **1.2.3** | JUnit 5 support | Auto-detects JUnit Platform 1.5-1.10+, required for project |

**Version rationale:**
- 1.22.0 is latest stable, includes test filter extension points
- 1.2.3 of JUnit 5 plugin auto-selects compatible junit-platform-launcher
- Project uses JUnit 5 via spring-boot-starter-test

**Do NOT use:**
- pitest-maven < 1.9.0: Incompatible with pitest-junit5-plugin 1.0+
- pitest-junit5-plugin < 1.2.0: Manual launcher configuration required

---

## Maven Plugin Configuration

### JaCoCo Configuration

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <!-- Prepare agent for unit tests -->
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <!-- Generate report after unit tests -->
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <!-- Prepare agent for integration tests -->
        <execution>
            <id>prepare-agent-integration</id>
            <goals>
                <goal>prepare-agent-integration</goal>
            </goals>
        </execution>
        <!-- Generate report after integration tests -->
        <execution>
            <id>report-integration</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>report-integration</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Critical: argLine Integration with Surefire**

The project already uses `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` in Surefire. JaCoCo sets the `argLine` property automatically, but this will be OVERWRITTEN by the explicit configuration.

**Required change to maven-surefire-plugin:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/*IT.java</exclude>
        </excludes>
        <!-- Use @{argLine} for late property evaluation -->
        <!-- JaCoCo sets argLine, we append our JVM args -->
        <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
    </configuration>
</plugin>
```

The `@{argLine}` syntax (available since Surefire 2.17) enables late property evaluation, allowing JaCoCo to inject its agent while preserving custom JVM arguments.

### PIT (Pitest) Configuration

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.22.0</version>
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.3</version>
        </dependency>
    </dependencies>
    <configuration>
        <!-- Target only core business logic -->
        <targetClasses>
            <param>fr.kalifazzia.alexandria.core.*</param>
        </targetClasses>
        <!-- Run unit tests only (faster feedback) -->
        <targetTests>
            <param>fr.kalifazzia.alexandria.*Test</param>
        </targetTests>
        <!-- Exclude infrastructure and generated code -->
        <excludedClasses>
            <param>fr.kalifazzia.alexandria.infra.*</param>
            <param>fr.kalifazzia.alexandria.api.*</param>
            <param>fr.kalifazzia.alexandria.*Config</param>
            <param>fr.kalifazzia.alexandria.*Configuration</param>
        </excludedClasses>
        <!-- Performance: parallel execution -->
        <threads>4</threads>
        <!-- Output formats -->
        <outputFormats>
            <param>HTML</param>
            <param>XML</param>
        </outputFormats>
        <!-- Use STRONGER mutators (sensible default) -->
        <mutators>
            <mutator>STRONGER</mutator>
        </mutators>
        <!-- History for incremental runs -->
        <historyInputFile>${project.build.directory}/pit-history.bin</historyInputFile>
        <historyOutputFile>${project.build.directory}/pit-history.bin</historyOutputFile>
        <!-- No thresholds - reflection tool, not gate -->
        <!-- mutationThreshold and coverageThreshold intentionally omitted -->
    </configuration>
</plugin>
```

### Maven Profile Strategy

**Recommended: Separate profiles for coverage and mutation testing**

```xml
<profiles>
    <!-- Coverage profile: runs with tests -->
    <profile>
        <id>coverage</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>0.8.14</version>
                    <!-- executions as above -->
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Mutation testing profile: separate execution -->
    <profile>
        <id>pitest</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-maven</artifactId>
                    <version>1.22.0</version>
                    <!-- configuration as above -->
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**Usage:**
```bash
# Coverage with unit tests
mvn test -Pcoverage

# Coverage with all tests
mvn verify -Pcoverage

# Mutation testing (separate, after tests pass)
mvn test-compile pitest:mutationCoverage -Ppitest

# Incremental mutation testing (PR workflow)
mvn test-compile pitest:scmMutationCoverage -Ppitest
```

---

## CI/CD Integration

### GitHub Actions: Coverage Reporting

**Recommended Action:** [cicirello/jacoco-badge-generator](https://github.com/cicirello/jacoco-badge-generator)

**Why this over alternatives:**
- Generates badges locally (no external API calls)
- Supports PR coverage checks
- Multi-module aware (future-proof)
- Can fail workflow on coverage decrease

**Alternative:** [madrapps/jacoco-report](https://github.com/madrapps/jacoco-report) for PR comments with coverage details.

**Workflow addition to ci.yml:**
```yaml
- name: Build and run unit tests
  run: mvn -B verify -DskipITs -Pcoverage

- name: Generate JaCoCo Badge
  uses: cicirello/jacoco-badge-generator@v2
  with:
    jacoco-csv-file: target/site/jacoco/jacoco.csv
    badges-directory: .github/badges
    generate-branches-badge: true

- name: Upload coverage report
  uses: actions/upload-artifact@v4
  with:
    name: coverage-report
    path: target/site/jacoco/
    retention-days: 30
```

### GitHub Actions: Mutation Testing

**Do NOT run PIT on every PR** - it is slow. Options:

1. **Manual trigger** (recommended for reflection tool philosophy):
```yaml
on:
  workflow_dispatch:
    inputs:
      run_mutation:
        description: 'Run mutation testing'
        type: boolean
        default: false
```

2. **Scheduled nightly run:**
```yaml
on:
  schedule:
    - cron: '0 2 * * *'  # 2 AM daily
```

3. **Incremental on PR** (advanced, requires history persistence):
```yaml
- name: Run incremental mutation testing
  run: |
    mvn test-compile pitest:scmMutationCoverage -Ppitest \
      -DdestinationBranch=origin/master
```

---

## What NOT to Use

### Avoid These Configurations

| Configuration | Why Avoid |
|---------------|-----------|
| `<mutationThreshold>85</mutationThreshold>` | Fails builds, counterproductive for "reflection tool" philosophy |
| `<coverageThreshold>80</coverageThreshold>` | Same - use as information, not gate |
| `<forkCount>0</forkCount>` in Surefire | Prevents JaCoCo agent from attaching |
| `<argLine>...</argLine>` without `@{argLine}` | Overwrites JaCoCo agent configuration |
| Running PIT on `*IT.java` tests | Integration tests are slow, minimal mutation value |
| PIT on Spring `@Configuration` classes | Poor test detection, low value mutations |

### Avoid These Tools

| Tool | Why Avoid |
|------|-----------|
| Cobertura | Deprecated, no Java 21 support |
| Emma | Abandoned since 2005 |
| Clover | Commercial, unnecessary for this use case |
| PITest < 1.9.0 | JUnit 5 plugin compatibility issues |

---

## Exclusion Strategy for Alexandria

Given the hexagonal architecture (api -> core <- infra), mutation testing should focus on core business logic.

### Recommended Exclusions for PIT

```xml
<excludedClasses>
    <!-- Infrastructure: repositories, external integrations -->
    <param>fr.kalifazzia.alexandria.infra.*</param>

    <!-- API: MCP handlers, REST controllers, CLI -->
    <param>fr.kalifazzia.alexandria.api.*</param>

    <!-- Spring configurations -->
    <param>fr.kalifazzia.alexandria.*Config</param>
    <param>fr.kalifazzia.alexandria.*Configuration</param>

    <!-- Application entry point -->
    <param>fr.kalifazzia.alexandria.AlexandriaApplication</param>
</excludedClasses>
```

### Rationale
- **Core:** Business logic, domain models, port interfaces - HIGH mutation value
- **Infra:** Repository implementations, external adapters - tested via integration tests
- **API:** Thin layer, minimal logic - covered by integration tests

---

## Report Output Locations

| Tool | Report Location | Format |
|------|-----------------|--------|
| JaCoCo (unit) | `target/site/jacoco/index.html` | HTML + XML + CSV |
| JaCoCo (integration) | `target/site/jacoco-it/index.html` | HTML + XML + CSV |
| PIT | `target/pit-reports/[timestamp]/index.html` | HTML + XML |

**Disable timestamped PIT reports for CI:**
```xml
<timestampedReports>false</timestampedReports>
```

This outputs to `target/pit-reports/` directly, simplifying artifact upload paths.

---

## Sources

### Official Documentation (HIGH confidence)
- [JaCoCo Change History](https://www.jacoco.org/jacoco/trunk/doc/changes.html) - Version history and Java support
- [JaCoCo Maven Plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html) - Plugin configuration reference
- [PIT Quickstart for Maven](https://pitest.org/quickstart/maven/) - Maven plugin configuration
- [GitHub: JaCoCo Releases](https://github.com/jacoco/jacoco/releases) - Release notes
- [GitHub: Pitest Releases](https://github.com/hcoles/pitest/releases) - Release notes
- [GitHub: pitest-junit5-plugin](https://github.com/pitest/pitest-junit5-plugin) - JUnit 5 plugin

### Community Resources (MEDIUM confidence)
- [JaCoCo Badge Generator Action](https://github.com/cicirello/jacoco-badge-generator) - CI badge generation
- [Baeldung: Mutation Testing with PITest](https://www.baeldung.com/java-mutation-testing-with-pitest) - Tutorial reference
- [Dev.to: JaCoCo and the Surefire argLine](http://www.devll.org/blog/2020/java/jacoco-argline.html) - argLine integration pattern
