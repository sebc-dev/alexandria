# Pitfalls Research: JaCoCo and PIT Mutation Testing

**Domain:** Java Test Quality Tools (JaCoCo Code Coverage + PIT Mutation Testing)
**Project Context:** Java 21, Spring Boot 3.4.7, Maven, Testcontainers, Hexagonal Architecture
**Researched:** 2026-01-22
**Confidence:** HIGH (verified with official docs and multiple sources)

## Critical Pitfalls

### Pitfall 1: JaCoCo argLine Overwritten by Surefire/Failsafe Configuration

**What goes wrong:**
JaCoCo's `prepare-agent` goal sets the `argLine` property to attach the JaCoCo agent. If your pom.xml already defines `<argLine>` in surefire-plugin configuration (which Alexandria does for `-XX:+EnableDynamicAgentLoading`), the JaCoCo agent is silently ignored. Result: 0% coverage with no error message.

**Why it happens:**
Maven's property resolution happens at different phases. When surefire runs, it uses its explicit `<argLine>` configuration rather than the property set by JaCoCo. The current pom.xml has:
```xml
<argLine>-XX:+EnableDynamicAgentLoading</argLine>
```
This overwrites whatever JaCoCo sets.

**Warning signs:**
- JaCoCo exec file is very small (< 1KB) or empty
- Coverage report shows 0% despite tests passing
- No errors in build output (silent failure)

**Prevention strategy:**
Use late property evaluation with `@{argLine}` syntax AND define an empty default:
```xml
<properties>
    <argLine></argLine>  <!-- Prevent @{argLine} error when JaCoCo not active -->
</properties>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- @{argLine} is replaced by JaCoCo's prepare-agent -->
        <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
    </configuration>
</plugin>
```

**Detection (before shipping):**
- Run `mvn test jacoco:report` and check HTML report immediately
- Verify exec file size: `ls -la target/jacoco.exec` should be > 10KB for real projects

**Phase to address:** Phase 1 - Initial JaCoCo setup (first task)

---

### Pitfall 2: PIT Runs Integration Tests (Testcontainers), Massive Slowdown

**What goes wrong:**
PIT examines the entire classpath and runs ALL tests it finds, including `*IT.java` integration tests that use Testcontainers. Each mutation causes PIT to spin up PostgreSQL containers. A 2-minute mutation run becomes 2+ hours.

**Why it happens:**
PIT doesn't parse surefire's `<excludes>` by default. Even if surefire excludes `**/*IT.java`, PIT sees them on the classpath and runs them. The project has 3 integration tests (`IngestionIT`, `SearchIT`, `DatabaseConnectionIT`) that each start PostgreSQL containers with ONNX model loading (~30s startup per container).

**Warning signs:**
- PIT "running tests" phase takes > 5 minutes before mutations start
- Docker containers being created during `mvn pitest:mutationCoverage`
- Console shows "Starting PostgreSQLContainer" during mutation run
- PIT reports tests failing without mutations (Testcontainers isolation issues)

**Prevention strategy:**
Explicitly exclude integration tests in PIT configuration:
```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <configuration>
        <excludedTestClasses>
            <param>**.*IT</param>
            <param>**.*IntegrationTest</param>
        </excludedTestClasses>
        <!-- Also exclude by tag if using JUnit 5 tags -->
        <excludedGroups>
            <excludedGroup>integration</excludedGroup>
        </excludedGroups>
    </configuration>
</plugin>
```

**Detection (before shipping):**
- First PIT run: watch for container startup messages
- Time the initial "running tests" phase - should be < 30s for unit tests only

**Phase to address:** Phase 2 - PIT setup (critical first-run configuration)

---

### Pitfall 3: JaCoCo Integration Test Coverage Not Merged with Unit Test Coverage

**What goes wrong:**
Running `mvn verify` produces two separate exec files: `target/jacoco.exec` (unit tests) and `target/jacoco-it.exec` (integration tests). JaCoCo reports only show one or the other, not combined coverage. SonarQube/local reports undercount actual coverage.

**Why it happens:**
JaCoCo's `prepare-agent` runs twice (once for surefire, once for failsafe) with different default output files. Without explicit merge configuration, reports use only one exec file.

**Warning signs:**
- Coverage numbers seem low despite comprehensive integration tests
- Classes tested only in ITs show 0% coverage
- Two separate coverage reports instead of one combined

**Prevention strategy:**
Configure explicit merge of execution data:
```xml
<execution>
    <id>merge-results</id>
    <phase>post-integration-test</phase>
    <goals>
        <goal>merge</goal>
    </goals>
    <configuration>
        <destFile>${project.build.directory}/jacoco-merged.exec</destFile>
        <fileSets>
            <fileSet>
                <directory>${project.build.directory}</directory>
                <includes>
                    <include>jacoco.exec</include>
                    <include>jacoco-it.exec</include>
                </includes>
            </fileSet>
        </fileSets>
    </configuration>
</execution>
```

**Detection (before shipping):**
- Check for both exec files after `mvn verify`
- Compare coverage numbers in unit-only report vs merged report

**Phase to address:** Phase 1 - JaCoCo configuration (after basic setup works)

---

### Pitfall 4: PIT JUnit 5 Plugin Version Mismatch

**What goes wrong:**
PIT fails with cryptic errors like `NoSuchMethodError` or `ClassNotFoundException` on JUnit Platform classes. Tests that pass normally fail under PIT.

**Why it happens:**
The `pitest-junit5-plugin` must match both the pitest version AND the JUnit Platform version. Spring Boot 3.4.7 uses JUnit 5.10.x (Platform 1.10.x). Using an old pitest-junit5-plugin causes binary incompatibility.

**Warning signs:**
- `NoSuchMethodError: org.junit.platform.launcher...`
- Tests pass with `mvn test` but fail with `mvn pitest:mutationCoverage`
- "No tests found" despite tests existing

**Prevention strategy:**
Use compatible versions (as of 2025):
```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.19.1</version>  <!-- Latest stable -->
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.2</version>  <!-- Requires pitest 1.19.4+ -->
        </dependency>
    </dependencies>
</plugin>
```

**Detection (before shipping):**
- Run `mvn pitest:mutationCoverage` on a single class first
- Check for JUnit-related exceptions in output

**Phase to address:** Phase 2 - Initial PIT configuration

---

### Pitfall 5: Coverage Thresholds Block All PRs During Initial Adoption

**What goes wrong:**
Adding JaCoCo with `<rule>` enforcement (e.g., 80% line coverage) immediately fails CI for all PRs. Existing untested code makes threshold impossible to meet. Team blocks all development until coverage catches up.

**Why it happens:**
Enforcement is configured before baseline is established. Teams copy "best practices" configs that include strict thresholds without considering current state.

**Warning signs:**
- CI immediately starts failing after JaCoCo PR merges
- "Coverage ratio 45% is lower than 80%" errors
- Team debates lowering threshold vs writing tests, blocking progress

**Prevention strategy:**
1. Add JaCoCo WITHOUT thresholds first (report-only)
2. Measure baseline coverage
3. Set threshold slightly below current baseline (ratchet approach)
4. Increment threshold gradually over sprints

For Alexandria (goal: no blocking thresholds):
```xml
<!-- Report only, no enforcement -->
<execution>
    <id>report</id>
    <phase>test</phase>
    <goals>
        <goal>report</goal>
    </goals>
</execution>
<!-- NO jacoco:check goal binding -->
```

**Detection (before shipping):**
- Verify CI passes with no code changes after adding JaCoCo
- Check pom.xml has no `<haltOnFailure>true</haltOnFailure>` with rules

**Phase to address:** Phase 1 - JaCoCo setup (design decision upfront)

---

### Pitfall 6: PIT Single-Threaded by Default, Extremely Slow

**What goes wrong:**
PIT mutation run takes 30+ minutes for a small codebase. Developers stop running it locally. Mutation testing becomes "CI only" which defeats fast feedback purpose.

**Why it happens:**
PIT defaults to single-threaded execution. For a project with ~50 classes and ~20 test classes, single-threaded execution may require hours.

**Warning signs:**
- First mutation run takes > 10 minutes
- No parallelism visible in output
- CPU usage shows only one core active

**Prevention strategy:**
Configure parallel execution:
```xml
<configuration>
    <threads>4</threads>  <!-- Or use auto-detection -->
    <!-- Enable history for incremental runs -->
    <withHistory>true</withHistory>
</configuration>
```

For CI with constrained resources:
```xml
<threads>2</threads>  <!-- Match CI runner core count -->
```

**Detection (before shipping):**
- Time the first run, should be < 5 minutes for ~50 classes
- Check output shows multiple mutation threads

**Phase to address:** Phase 2 - PIT performance tuning

---

## Moderate Pitfalls

### Pitfall 7: JaCoCo Reports Don't Exclude Spring Configuration Classes

**What goes wrong:**
Coverage reports show red/uncovered code for Spring `@Configuration` classes, `@SpringBootApplication` main class, and generated proxies. Misleading coverage numbers and visual noise.

**Why it happens:**
These classes are loaded but not "tested" in the traditional sense. Spring's AOT and CGLIB generate additional classes that appear in reports.

**Prevention:**
```xml
<configuration>
    <excludes>
        <exclude>**/AlexandriaApplication.class</exclude>
        <exclude>**/*Config.class</exclude>
        <exclude>**/*Configuration.class</exclude>
        <exclude>**/*$$SpringCGLIB$$*</exclude>
        <exclude>**/*__*</exclude>
    </excludes>
</configuration>
```

**Phase to address:** Phase 1 - JaCoCo configuration refinement

---

### Pitfall 8: PIT History File Incompatibility After Upgrade

**What goes wrong:**
After upgrading pitest version, incremental runs fail or produce wrong results. History file from old version is incompatible.

**Why it happens:**
PIT's history file format changes between versions. Old history files are silently used but produce incorrect incremental analysis.

**Warning signs:**
- "No mutations found" after upgrade despite code changes
- Inconsistent mutation scores between local and CI
- Incremental run takes as long as full run

**Prevention:**
- Delete history file after PIT version upgrades
- Store PIT version in history file name: `pitest-history-${pitest.version}.xml`
- Document version upgrade procedure

**Phase to address:** Phase 2 - PIT maintenance documentation

---

### Pitfall 9: Failsafe Not Passing JaCoCo Agent to Integration Tests

**What goes wrong:**
Integration test coverage is not collected. The `jacoco-it.exec` file is missing or empty.

**Why it happens:**
Failsafe plugin also needs the `@{argLine}` configuration, same as Surefire. If only Surefire is configured, integration tests run without the agent.

**Prevention:**
Apply same `argLine` pattern to both plugins:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <argLine>@{argLine}</argLine>
    </configuration>
</plugin>
```

**Phase to address:** Phase 1 - JaCoCo integration test coverage

---

### Pitfall 10: PIT `parseSurefireConfig` Causes Unexpected Behavior

**What goes wrong:**
PIT parses Surefire configuration and applies its excludes, but this can cause tests to be unexpectedly included or excluded.

**Why it happens:**
By default, PIT tries to be helpful by reading Surefire config. But this can conflict with explicit PIT configuration.

**Prevention:**
Explicitly control behavior:
```xml
<configuration>
    <parseSurefireConfig>false</parseSurefireConfig>
    <!-- Then explicitly configure all excludes -->
    <excludedTestClasses>
        <param>**.*IT</param>
    </excludedTestClasses>
</configuration>
```

**Phase to address:** Phase 2 - PIT configuration

---

## Minor Pitfalls

### Pitfall 11: JaCoCo HTML Report Not Generated

**What goes wrong:**
After `mvn test`, no HTML report exists in `target/site/jacoco/`.

**Why it happens:**
The `report` goal must be explicitly bound to a phase or run manually.

**Prevention:**
```xml
<execution>
    <id>report</id>
    <phase>test</phase>
    <goals>
        <goal>report</goal>
    </goals>
</execution>
```

**Phase to address:** Phase 1 - Basic JaCoCo setup

---

### Pitfall 12: PIT `scmMutationCoverage` Requires SCM Plugin

**What goes wrong:**
Running `mvn pitest:scmMutationCoverage` fails with "No SCM configured" error.

**Why it happens:**
PIT delegates change detection to Maven SCM plugin, which requires `<scm>` section in pom.xml.

**Prevention:**
Ensure pom.xml has SCM configuration:
```xml
<scm>
    <connection>scm:git:git://github.com/sebc-dev/alexandria.git</connection>
    <developerConnection>scm:git:ssh://github.com/sebc-dev/alexandria.git</developerConnection>
</scm>
```

**Phase to address:** Phase 2 - PIT incremental mode

---

### Pitfall 13: CI Artifacts Missing JaCoCo Reports

**What goes wrong:**
JaCoCo runs successfully but reports aren't available in CI artifacts.

**Why it happens:**
Reports are generated in `target/site/jacoco/` which may not be in artifact upload path.

**Prevention:**
In GitHub Actions, explicitly include JaCoCo output:
```yaml
- uses: actions/upload-artifact@v4
  with:
    name: coverage-report
    path: target/site/jacoco/
```

**Phase to address:** Phase 3 - CI integration

---

## Phase-Specific Warnings

| Phase | Topic | Likely Pitfall | Mitigation |
|-------|-------|----------------|------------|
| Phase 1 | JaCoCo basic setup | argLine overwritten (Pitfall 1) | Use @{argLine} syntax from start |
| Phase 1 | JaCoCo integration tests | Failsafe not instrumented (Pitfall 9) | Configure both plugins identically |
| Phase 1 | JaCoCo thresholds | Blocking CI (Pitfall 5) | Report-only mode initially |
| Phase 2 | PIT first run | Integration tests run (Pitfall 2) | Exclude *IT pattern immediately |
| Phase 2 | PIT JUnit 5 | Version mismatch (Pitfall 4) | Verify compatible versions |
| Phase 2 | PIT performance | Single-threaded (Pitfall 6) | Configure threads=4 |
| Phase 3 | CI artifacts | Reports missing (Pitfall 13) | Explicit artifact paths |

## "Looks Done But Isn't" Checklist

Before considering JaCoCo/PIT setup complete:

- [ ] **argLine preserved:** Verify `@{argLine}` syntax in both surefire and failsafe
- [ ] **Coverage non-zero:** Check HTML report shows actual coverage > 0%
- [ ] **Exec files exist:** Both `jacoco.exec` and `jacoco-it.exec` present after `mvn verify`
- [ ] **No thresholds blocking:** CI passes with zero code changes after adding tools
- [ ] **ITs excluded from PIT:** Verify no container startup during mutation run
- [ ] **PIT multi-threaded:** Confirm `threads` configured
- [ ] **pitest-junit5-plugin present:** JUnit 5 tests run under PIT
- [ ] **History enabled:** `withHistory=true` for incremental local runs
- [ ] **CI artifacts include reports:** Download and verify HTML reports exist

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| argLine overwritten | LOW | Add @{argLine}, rebuild, regenerate report |
| ITs run by PIT | LOW | Add excludedTestClasses, rerun |
| Coverage not merged | LOW | Add merge execution, regenerate |
| JUnit 5 version mismatch | LOW | Update plugin versions, rerun |
| Threshold blocking CI | MEDIUM | Remove rules, establish baseline first |
| Single-threaded PIT | LOW | Add threads config, rerun |
| History incompatibility | LOW | Delete history file, run full analysis |

## Sources

### Official Documentation
- [JaCoCo prepare-agent goal](https://www.eclemma.org/jacoco/trunk/doc/prepare-agent-mojo.html)
- [JaCoCo check goal](https://www.eclemma.org/jacoco/trunk/doc/check-mojo.html)
- [PIT Maven Quickstart](https://pitest.org/quickstart/maven/)
- [PIT Incremental Analysis](https://pitest.org/quickstart/incremental_analysis/)
- [PIT FAQ](https://pitest.org/faq/)
- [pitest-junit5-plugin GitHub](https://github.com/pitest/pitest-junit5-plugin)

### Community Resources
- [Baeldung: Exclusions from JaCoCo Report](https://www.baeldung.com/jacoco-report-exclude)
- [Baeldung: Maven JaCoCo Configuration](https://www.baeldung.com/jacoco)
- [Mutation Testing in Spring Boot 3 Projects](https://en.paradigmadigital.com/dev/mutation-testing-spring-boot-3-projects-beat-slowness-supercharge-tests/)
- [Faster Mutation Testing](https://blog.frankel.ch/faster-mutation-testing/)
- [Integration Testing with Failsafe and Merging Reports with JaCoCo](https://medium.com/@aleaandre/integration-testing-with-failsafe-and-merging-reports-with-jacoco-in-a-spring-boot-project-9e313a38ae26)
- [Apache Maven JaCoCo Configuration](https://dev.to/khmarbaise/apache-maven-jacoco-configuration-i71)
- [JaCoCo Maven plugin and test plugins](https://groups.google.com/g/jacoco/c/FuzoshJb2KU)
- [PIT excludedGroups issue #699](https://github.com/hcoles/pitest/issues/699)
- [withHistory disables explicit file paths](https://github.com/hcoles/pitest/issues/293)

---
*Pitfalls research for: JaCoCo and PIT Mutation Testing with Java 21 Spring Boot*
*Project: Alexandria v0.3*
*Researched: 2026-01-22*
