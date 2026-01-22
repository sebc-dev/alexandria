# Architecture: Test Quality Tools Integration

**Domain:** Java test quality tools (JaCoCo, PIT) integration with Maven/GitHub Actions
**Researched:** 2026-01-22
**Confidence:** HIGH (verified with official documentation)

## Overview

This document details how JaCoCo and PIT integrate with the existing Alexandria build system:
- Maven 3.x with Surefire (unit tests) and Failsafe (integration tests)
- GitHub Actions CI (ci.yml, sonarqube.yml)
- Existing argLine configuration for Mockito agent on Java 21+

## Maven Lifecycle Integration

### Default Lifecycle Phases (Relevant Subset)

```
+---------------------------------------------------------------------+
|                    MAVEN DEFAULT LIFECYCLE                           |
+---------------------------------------------------------------------+
|  initialize        <-- JaCoCo prepare-agent (sets argLine)          |
|       |                                                              |
|       v                                                              |
|  compile                                                             |
|       |                                                              |
|       v                                                              |
|  test-compile                                                        |
|       |                                                              |
|       v                                                              |
|  test              <-- Surefire runs unit tests (*Test.java)        |
|       |              JaCoCo agent records to jacoco.exec             |
|       v                                                              |
|  package                                                             |
|       |                                                              |
|       v                                                              |
|  pre-integration-test <-- JaCoCo prepare-agent-integration          |
|       |                                                              |
|       v                                                              |
|  integration-test  <-- Failsafe runs ITs (*IT.java)                 |
|       |              JaCoCo agent records to jacoco-it.exec          |
|       v                                                              |
|  post-integration-test <-- JaCoCo report-integration                |
|       |                                                              |
|       v                                                              |
|  verify            <-- JaCoCo report (unit test coverage)           |
|       |              Failsafe verify goal                            |
|       |              PIT mutation testing (if profile active)        |
|       v                                                              |
|  install                                                             |
+---------------------------------------------------------------------+
```

### JaCoCo Plugin Execution Order

JaCoCo requires **four executions** to cover both unit and integration tests:

| Execution ID | Goal | Phase | Purpose |
|--------------|------|-------|---------|
| `pre-unit-test` | `prepare-agent` | `initialize` | Sets argLine for Surefire |
| `post-unit-test` | `report` | `verify` | Generates unit test report |
| `pre-integration-test` | `prepare-agent-integration` | `pre-integration-test` | Sets argLine for Failsafe |
| `post-integration-test` | `report-integration` | `post-integration-test` | Generates IT report |

### PIT Plugin Execution

PIT runs **manually via profile** or Maven goal, not bound to lifecycle by default:

| Goal | Invocation | Purpose |
|------|------------|---------|
| `mutationCoverage` | `mvn -Ppitest test` | Full mutation analysis |
| `scmMutationCoverage` | `mvn -Ppitest -DanalyseLastCommit test` | Incremental (local dev) |

## Recommended Maven Configuration

### JaCoCo Plugin (pom.xml)

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <!-- Unit test coverage -->
        <execution>
            <id>pre-unit-test</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
            <configuration>
                <destFile>${project.build.directory}/jacoco.exec</destFile>
                <propertyName>jacocoArgLine</propertyName>
            </configuration>
        </execution>
        <execution>
            <id>post-unit-test</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
            <configuration>
                <dataFile>${project.build.directory}/jacoco.exec</dataFile>
                <outputDirectory>${project.reporting.outputDirectory}/jacoco</outputDirectory>
            </configuration>
        </execution>

        <!-- Integration test coverage -->
        <execution>
            <id>pre-integration-test</id>
            <phase>pre-integration-test</phase>
            <goals>
                <goal>prepare-agent-integration</goal>
            </goals>
            <configuration>
                <destFile>${project.build.directory}/jacoco-it.exec</destFile>
                <propertyName>jacocoItArgLine</propertyName>
            </configuration>
        </execution>
        <execution>
            <id>post-integration-test</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>report-integration</goal>
            </goals>
            <configuration>
                <dataFile>${project.build.directory}/jacoco-it.exec</dataFile>
                <outputDirectory>${project.reporting.outputDirectory}/jacoco-it</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### argLine Merging (CRITICAL)

The existing Surefire configuration uses `-XX:+EnableDynamicAgentLoading` for Mockito on Java 21+. This MUST be merged with JaCoCo's argLine using late property evaluation:

**Current Surefire (problematic):**
```xml
<argLine>-XX:+EnableDynamicAgentLoading</argLine>
```

**Updated Surefire (merged):**
```xml
<argLine>@{jacocoArgLine} -XX:+EnableDynamicAgentLoading</argLine>
```

**Updated Failsafe (merged):**
```xml
<argLine>@{jacocoItArgLine} -XX:+EnableDynamicAgentLoading</argLine>
```

**Why `@{...}` syntax:** This is Maven's late property evaluation. It allows JaCoCo to set the property during `initialize` phase, and Surefire/Failsafe to read it during `test`/`integration-test` phases. Using `${...}` would fail because the property doesn't exist when Maven parses the POM.

**Empty property fallback:** Define empty properties to avoid errors when running without JaCoCo:

```xml
<properties>
    <jacocoArgLine></jacocoArgLine>
    <jacocoItArgLine></jacocoItArgLine>
</properties>
```

### PIT Plugin (Profile-Based)

```xml
<profiles>
    <profile>
        <id>pitest</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-maven</artifactId>
                    <version>1.19.1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.pitest</groupId>
                            <artifactId>pitest-junit5-plugin</artifactId>
                            <version>1.2.3</version>
                        </dependency>
                    </dependencies>
                    <configuration>
                        <targetClasses>
                            <param>fr.kalifazzia.alexandria.core.*</param>
                            <param>fr.kalifazzia.alexandria.infra.*</param>
                        </targetClasses>
                        <targetTests>
                            <param>fr.kalifazzia.alexandria.*Test</param>
                        </targetTests>
                        <excludedClasses>
                            <param>fr.kalifazzia.alexandria.api.*</param>
                        </excludedClasses>
                        <threads>4</threads>
                        <timestampedReports>false</timestampedReports>
                        <withHistory>true</withHistory>
                        <outputFormats>
                            <format>HTML</format>
                            <format>XML</format>
                        </outputFormats>
                        <failWhenNoMutations>false</failWhenNoMutations>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**Key configuration choices:**
- `withHistory>true`: Uses temp directory for history file (incremental local analysis)
- `timestampedReports>false`: Overwrites previous report (cleaner for local dev)
- `threads>4`: Parallel execution (adjust based on CPU cores)
- `excludedClasses>api.*`: Skip MCP handlers (thin adapters, tested via integration)

## GitHub Actions Workflow Modifications

### ci.yml - Add JaCoCo Report Upload

```yaml
name: CI

on:
  push:
    branches: [main, master, develop]
  pull_request:
    types: [opened, synchronize, reopened]

concurrency:
  group: ci-${{ github.head_ref || github.ref }}
  cancel-in-progress: true

env:
  JAVA_VERSION: '21'

jobs:
  build:
    name: Build & Unit Tests
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: ${{ env.JAVA_VERSION }}
          cache: 'maven'

      - name: Build and run unit tests
        run: mvn -B -T 1C verify -DskipITs

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: target/surefire-reports/
          retention-days: 7

      # NEW: JaCoCo coverage report artifact
      - name: Upload coverage report
        uses: actions/upload-artifact@v4
        if: success()
        with:
          name: coverage-report
          path: target/site/jacoco/
          retention-days: 14

  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: build

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: ${{ env.JAVA_VERSION }}
          cache: 'maven'

      - name: Run integration tests
        run: mvn -B verify -DskipTests

      - name: Upload IT coverage report
        uses: actions/upload-artifact@v4
        if: success()
        with:
          name: coverage-report-it
          path: target/site/jacoco-it/
          retention-days: 14
```

### sonarqube.yml - Add JaCoCo Report Path

```yaml
- name: Build and analyze
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
  run: |
    mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.0.0.4389:sonar \
      -DskipITs \
      -Dsonar.projectKey=sebc-dev_alexandria \
      -Dsonar.organization=sebc-dev \
      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

**Note:** SonarQube automatically detects `target/site/jacoco/jacoco.xml` but explicit configuration ensures reliability.

## Workflow Diagram: CI with Coverage

```
+---------------------------------------------------------------------+
|                    GITHUB ACTIONS CI FLOW                            |
+---------------------------------------------------------------------+
|                                                                      |
|  +------------------+                                                |
|  |    Checkout      |                                                |
|  +--------+---------+                                                |
|           |                                                          |
|           v                                                          |
|  +------------------+                                                |
|  |  Setup JDK 21    |                                                |
|  +--------+---------+                                                |
|           |                                                          |
|           v                                                          |
|  +----------------------------------------------------------+       |
|  |  mvn verify -DskipITs                                     |       |
|  |                                                           |       |
|  |  initialize:    JaCoCo prepare-agent -> sets jacocoArgLine|       |
|  |  compile:       javac                                     |       |
|  |  test-compile:  javac (tests)                             |       |
|  |  test:          Surefire + JaCoCo agent -> jacoco.exec    |       |
|  |  verify:        JaCoCo report -> target/site/jacoco/      |       |
|  +--------+-------------------------------------------------+       |
|           |                                                          |
|           v                                                          |
|  +------------------+    +------------------+                        |
|  | Upload Surefire  |    | Upload JaCoCo    |                        |
|  | Reports          |    | HTML Report      |                        |
|  +------------------+    +------------------+                        |
|                                                                      |
+---------------------------------------------------------------------+
```

## Report Output Locations

| Report | Location | Format | When Generated |
|--------|----------|--------|----------------|
| Unit test coverage | `target/site/jacoco/` | HTML, XML, CSV | `mvn verify -DskipITs` |
| IT coverage | `target/site/jacoco-it/` | HTML, XML, CSV | `mvn verify -DskipTests` |
| PIT mutation | `target/pit-reports/` | HTML, XML | `mvn -Ppitest test` |
| SonarQube | (sent to server) | - | `mvn sonar:sonar` |

## Suggested Phase Structure

Based on dependency analysis, the recommended implementation order:

### Phase 1: JaCoCo Foundation (Local + CI)

**Rationale:** JaCoCo has no dependencies, generates reports immediately.

1. Add JaCoCo plugin to pom.xml (4 executions)
2. Update Surefire/Failsafe argLine (merge with Mockito agent)
3. Verify local report generation: `mvn verify && open target/site/jacoco/index.html`
4. Update ci.yml with coverage artifact upload
5. Update sonarqube.yml with explicit report path

**Verification:** Coverage report visible in GitHub Actions artifacts.

### Phase 2: Integration Tests in CI

**Rationale:** Depends on JaCoCo being set up correctly.

1. Add integration-tests job to ci.yml
2. Configure Docker-in-Docker or services for Testcontainers
3. Upload IT coverage artifact

**Verification:** IT coverage report visible in GitHub Actions artifacts.

### Phase 3: PIT Mutation Testing (Local Only)

**Rationale:** PIT is independent but benefits from established test infrastructure.

1. Add PIT plugin in pitest profile
2. Configure targetClasses/targetTests
3. Enable withHistory for incremental analysis
4. Create local script: `scripts/mutation-test.sh`

**Verification:** `mvn -Ppitest test` produces HTML report.

## Integration Points Summary

| Tool | Integrates With | Configuration Key |
|------|-----------------|-------------------|
| JaCoCo | Surefire | `<argLine>@{jacocoArgLine} ...</argLine>` |
| JaCoCo | Failsafe | `<argLine>@{jacocoItArgLine} ...</argLine>` |
| JaCoCo | SonarQube | `sonar.coverage.jacoco.xmlReportPaths` |
| JaCoCo | GitHub Actions | `actions/upload-artifact` with `target/site/jacoco/` |
| PIT | JUnit 5 | `pitest-junit5-plugin` dependency |
| PIT | Maven | Profile activation: `-Ppitest` |

## Sources

- [JaCoCo Maven Plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html) - Official documentation
- [JaCoCo prepare-agent goal](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html) - Phase binding and argLine handling
- [PIT Quickstart for Maven](https://pitest.org/quickstart/maven/) - Official Maven configuration
- [SonarQube Java Test Coverage](https://docs.sonarsource.com/sonarqube-server/2025.4/analyzing-source-code/test-coverage/java-test-coverage) - JaCoCo integration
- [Maven Build Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html) - Phase ordering
- [Creating Coverage Reports with JaCoCo](https://www.petrikainulainen.net/programming/maven/creating-code-coverage-reports-for-unit-and-integration-tests-with-the-jacoco-maven-plugin/) - Unit + IT configuration
- [PIT PR Setup Guide](https://blog.pitest.org/pitest-pr-setup/) - PIT CI integration
- [JaCoCo Reporter GitHub Action](https://github.com/marketplace/actions/jacoco-reporter) - PR coverage comments

---
*Architecture research for: Test quality tools integration (v0.3 milestone)*
*Researched: 2026-01-22*
