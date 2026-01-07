# Workflow de développement et CI/CD pour Alexandria

Ce guide définit une stratégie en trois phases pour maximiser la qualité du code généré par Claude Code tout en préservant la vélocité de développement.

## Philosophie du workflow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    FEEDBACK LOOP OPTIMIZATION                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   DEV LOCAL  │───▶│  PRE-COMMIT  │───▶│   CI/CD      │              │
│  │   (continu)  │    │   (rapide)   │    │  (complet)   │              │
│  └──────────────┘    └──────────────┘    └──────────────┘              │
│        │                    │                    │                      │
│        ▼                    ▼                    ▼                      │
│   SonarLint            Spotless            Tous analyseurs             │
│   temps réel           Checkstyle          Tests intégration           │
│   Compilation          ~5 secondes         SonarCloud                  │
│   instantanée                              OWASP + Trivy               │
│                                                                         │
│  ┌──────────────┐                                                       │
│  │  PRE-PUSH    │  ◀── Mutation testing (PIT)                          │
│  │  (manuel)    │      PMD + SpotBugs + Error Prone                    │
│  └──────────────┘      ~10-15 minutes                                  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Phase 1 : Développement local avec Claude Code

### Objectif
Feedback instantané sans friction pendant l'itération avec Claude Code.

### Configuration VS Code (settings.json)

```json
{
    "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
    "java.format.settings.profile": "GoogleStyle",
    "editor.formatOnSave": true,
    "editor.formatOnPaste": false,
    "java.compile.nullAnalysis.mode": "automatic",
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.checkstyle.configuration": "/google_checks.xml",
    "java.checkstyle.version": "12.3.1",
    "sonarlint.rules": {
        "java:S1135": { "level": "off" }
    },
    "files.exclude": {
        "**/target": true
    }
}
```

### Extensions VS Code requises

| Extension | ID | Rôle |
|-----------|-----|------|
| Extension Pack for Java | `vscjava.vscode-java-pack` | Support Java complet |
| SonarQube for VS Code | `sonarsource.sonarlint-vscode` | Analyse temps réel |
| Checkstyle for Java | `shengchen.vscode-checkstyle` | Linting temps réel |
| Error Lens | `usernamehw.errorlens` | Erreurs inline |

### Commandes pendant le développement

```bash
# Compilation rapide (profil dev par défaut)
mvn compile

# Tests unitaires rapides
mvn test -Dtest='!**/*IT.java'

# Formatage manuel si nécessaire
mvn spotless:apply
```

## Phase 2 : Pre-commit hooks (validation légère)

### Objectif
Garantir un formatage consistant et détecter les violations de style évidentes avant commit.

### Installation automatique via Maven

Le hook s'installe automatiquement lors du premier `mvn compile` grâce au plugin Cosium.

### Script pre-commit personnalisé

Fichier `hooks/pre-commit` :

```bash
#!/bin/bash
set -e

# Couleurs pour output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}🔍 Pre-commit checks...${NC}"

# 1. Formatage des fichiers staged uniquement
echo "  → Formatting staged files..."
mvn spotless:apply -q 2>/dev/null || {
    echo -e "${RED}❌ Spotless formatting failed${NC}"
    exit 1
}

# Re-stage les fichiers modifiés par Spotless
git diff --name-only | xargs -r git add

# 2. Checkstyle rapide sur fichiers modifiés
echo "  → Running Checkstyle..."
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep '\.java$' || true)
if [ -n "$STAGED_JAVA" ]; then
    mvn checkstyle:check -q 2>/dev/null || {
        echo -e "${RED}❌ Checkstyle violations found${NC}"
        echo "Run 'mvn checkstyle:check' for details"
        exit 1
    }
fi

echo -e "${GREEN}✅ Pre-commit passed!${NC}"
```

## Phase 3 : Analyse approfondie locale (pre-push manuel)

### Objectif
Exécuter les analyses longues (mutation testing, analyse statique complète) avant de pousser du code significatif.

### Script d'analyse complète

Fichier `scripts/full-analysis.sh` :

```bash
#!/bin/bash
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}       ALEXANDRIA - Full Local Analysis Pipeline            ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""

START_TIME=$(date +%s)

# 1. Compilation avec Error Prone
echo -e "${YELLOW}[1/6] Compiling with Error Prone...${NC}"
mvn clean compile -Perror-prone -q || {
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
}
echo -e "${GREEN}  ✓ Compilation OK${NC}"

# 2. Tests unitaires
echo -e "${YELLOW}[2/6] Running unit tests...${NC}"
mvn test -Dtest='!**/*IT.java' -q || {
    echo -e "${RED}❌ Unit tests failed${NC}"
    exit 1
}
echo -e "${GREEN}  ✓ Unit tests OK${NC}"

# 3. PMD + CPD
echo -e "${YELLOW}[3/6] Running PMD analysis...${NC}"
mvn pmd:check pmd:cpd-check -q || {
    echo -e "${RED}❌ PMD violations found${NC}"
    echo "Run 'mvn pmd:pmd' for HTML report"
    exit 1
}
echo -e "${GREEN}  ✓ PMD OK${NC}"

# 4. SpotBugs
echo -e "${YELLOW}[4/6] Running SpotBugs...${NC}"
mvn spotbugs:check -q || {
    echo -e "${RED}❌ SpotBugs issues found${NC}"
    echo "Run 'mvn spotbugs:gui' for interactive review"
    exit 1
}
echo -e "${GREEN}  ✓ SpotBugs OK${NC}"

# 5. Checkstyle complet
echo -e "${YELLOW}[5/6] Running Checkstyle...${NC}"
mvn checkstyle:check -q || {
    echo -e "${RED}❌ Checkstyle violations found${NC}"
    exit 1
}
echo -e "${GREEN}  ✓ Checkstyle OK${NC}"

# 6. Mutation Testing (PIT) - Le plus long
echo -e "${YELLOW}[6/6] Running PIT Mutation Testing...${NC}"
echo -e "  ${BLUE}(This may take 5-15 minutes)${NC}"
mvn test-compile org.pitest:pitest-maven:mutationCoverage \
    -DwithHistory=true \
    -Dthreads=4 \
    -DtimestampedReports=false || {
    echo -e "${RED}❌ Mutation testing failed threshold${NC}"
    echo "Report: target/pit-reports/index.html"
    exit 1
}
echo -e "${GREEN}  ✓ Mutation testing OK${NC}"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ All local analyses passed! (${DURATION}s)${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo "Reports generated:"
echo "  • PMD:       target/pmd.html"
echo "  • SpotBugs:  target/spotbugsXml.xml"
echo "  • PIT:       target/pit-reports/index.html"
```

### Quand exécuter l'analyse complète

| Situation | Commande |
|-----------|----------|
| Avant de créer une PR | `./scripts/full-analysis.sh` |
| Après refactoring majeur | `./scripts/full-analysis.sh` |
| Vérification qualité périodique | `./scripts/full-analysis.sh` |
| Juste mutation testing | `mvn pitest:mutationCoverage -DwithHistory` |
| Juste analyse statique | `mvn verify -Pstatic-analysis` |

## Phase 4 : CI/CD GitHub Actions

### Stratégie de déclenchement

Pour économiser les minutes GitHub Actions, le workflow se déclenche uniquement :

1. **Manuellement** via `workflow_dispatch` (bouton "Run workflow")
2. **Sur label** `ready-for-ci` ajouté à la PR
3. **Sur `ready_for_review`** quand la PR sort de draft

Le workflow est **obligatoire pour merger** via les branch protection rules.

### Workflow GitHub Actions complet

Fichier `.github/workflows/ci.yml` :

```yaml
name: Alexandria CI Pipeline

on:
  # Déclenchement manuel avec option de spécifier le SHA
  workflow_dispatch:
    inputs:
      commit_sha:
        description: 'SHA du commit pour le status check (auto-détecté si vide)'
        required: false
        type: string

  # Déclenchement sur label "ready-for-ci"
  pull_request:
    types: [labeled, ready_for_review]

permissions:
  contents: read
  checks: write
  security-events: write
  pull-requests: write

env:
  JAVA_VERSION: '25'
  JAVA_DISTRIBUTION: 'temurin'
  MAVEN_OPTS: '-Xmx4096m'

jobs:
  # ============================================================
  # Gate: Vérifier si le workflow doit s'exécuter
  # ============================================================
  should-run:
    name: Check Trigger Conditions
    runs-on: ubuntu-latest
    outputs:
      should_run: ${{ steps.check.outputs.should_run }}
      effective_sha: ${{ steps.check.outputs.effective_sha }}
    steps:
      - name: Check if should run
        id: check
        run: |
          SHOULD_RUN="false"
          
          # Cas 1: workflow_dispatch (toujours exécuter)
          if [ "${{ github.event_name }}" == "workflow_dispatch" ]; then
            SHOULD_RUN="true"
            SHA="${{ inputs.commit_sha || github.sha }}"
          fi
          
          # Cas 2: Label "ready-for-ci" ajouté
          if [ "${{ github.event.action }}" == "labeled" ] && \
             [ "${{ github.event.label.name }}" == "ready-for-ci" ]; then
            SHOULD_RUN="true"
            SHA="${{ github.event.pull_request.head.sha }}"
          fi
          
          # Cas 3: PR passée de draft à ready
          if [ "${{ github.event.action }}" == "ready_for_review" ]; then
            SHOULD_RUN="true"
            SHA="${{ github.event.pull_request.head.sha }}"
          fi
          
          echo "should_run=$SHOULD_RUN" >> $GITHUB_OUTPUT
          echo "effective_sha=${SHA:-${{ github.sha }}}" >> $GITHUB_OUTPUT
          
          echo "Decision: should_run=$SHOULD_RUN, sha=${SHA:-${{ github.sha }}}"

  # ============================================================
  # Initialisation du status check
  # ============================================================
  init-check:
    name: Initialize Status Check
    runs-on: ubuntu-latest
    needs: should-run
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      check_run_id: ${{ steps.create-check.outputs.check_id }}
    steps:
      - name: Create check run
        id: create-check
        uses: LouisBrunner/checks-action@v2.0.0
        with:
          sha: ${{ needs.should-run.outputs.effective_sha }}
          token: ${{ secrets.GITHUB_TOKEN }}
          name: Alexandria CI Pipeline
          status: in_progress
          output: |
            {"summary":"Pipeline en cours d'exécution..."}

  # ============================================================
  # Build + Tests unitaires
  # ============================================================
  build-and-test:
    name: Build & Unit Tests
    runs-on: ubuntu-latest
    needs: [should-run, init-check]
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      build_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5
        with:
          fetch-depth: 0

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven
          check-latest: true

      - name: Compile with Error Prone
        run: mvn -B compile -Perror-prone

      - name: Run unit tests with coverage
        run: mvn -B test -Dtest='!**/*IT.java'

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: |
            **/target/surefire-reports/
            **/target/jacoco.exec
          retention-days: 7

  # ============================================================
  # Analyse statique (PMD + SpotBugs + Checkstyle)
  # ============================================================
  static-analysis:
    name: Static Analysis
    runs-on: ubuntu-latest
    needs: [should-run, init-check]
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      analysis_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Run PMD
        run: mvn -B pmd:check pmd:cpd-check

      - name: Run SpotBugs
        run: mvn -B compile spotbugs:check

      - name: Run Checkstyle
        run: mvn -B checkstyle:check

      - name: Upload analysis reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: static-analysis-reports
          path: |
            **/target/pmd.xml
            **/target/cpd.xml
            **/target/spotbugsXml.xml
            **/target/checkstyle-result.xml
          retention-days: 7

  # ============================================================
  # Tests d'intégration (Testcontainers)
  # ============================================================
  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: [should-run, build-and-test]
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      test_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Pre-pull pgvector image
        run: docker pull pgvector/pgvector:0.8.1-pg18

      - name: Run integration tests
        run: |
          mvn -B verify \
            -DskipUnitTests=true \
            -Dtest='**/*IT.java' \
            -Dfailsafe.timeout=600

      - name: Upload integration test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: integration-test-results
          path: **/target/failsafe-reports/
          retention-days: 7

  # ============================================================
  # SonarCloud Analysis
  # ============================================================
  sonarcloud:
    name: SonarCloud Analysis
    runs-on: ubuntu-latest
    needs: [should-run, build-and-test]
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      sonar_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5
        with:
          fetch-depth: 0

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Build and analyze
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: |
          mvn -B verify sonar:sonar \
            -Dsonar.projectKey=alexandria-mcp \
            -Dsonar.organization=${{ github.repository_owner }} \
            -Dsonar.host.url=https://sonarcloud.io \
            -Dsonar.java.source=25

  # ============================================================
  # Sécurité: CodeQL SAST
  # ============================================================
  codeql:
    name: CodeQL Analysis
    runs-on: ubuntu-latest
    needs: [should-run, init-check]
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      codeql_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5

      - name: Initialize CodeQL
        uses: github/codeql-action/init@v3
        with:
          languages: java
          queries: security-and-quality

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Build for CodeQL
        run: mvn -B compile -DskipTests

      - name: Perform CodeQL Analysis
        uses: github/codeql-action/analyze@v3
        with:
          category: "/language:java"

  # ============================================================
  # Sécurité: Dépendances (OWASP + Trivy)
  # ============================================================
  dependency-security:
    name: Dependency Security
    runs-on: ubuntu-latest
    needs: [should-run, init-check]
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      security_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: OWASP Dependency-Check
        run: |
          mvn -B org.owasp:dependency-check-maven:check \
            -DfailBuildOnCVSS=8 \
            -Dformat=ALL \
            -DnvdApiKey=${{ secrets.NVD_API_KEY }}
        continue-on-error: true

      - name: Trivy filesystem scan
        uses: aquasecurity/trivy-action@0.28.0
        with:
          scan-type: 'fs'
          scan-ref: '.'
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'
          ignore-unfixed: true

      - name: Upload Trivy results
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: 'trivy-results.sarif'
          category: 'trivy-dependency-scan'

      - name: Upload OWASP report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: owasp-report
          path: target/dependency-check-report.*
          retention-days: 14

  # ============================================================
  # Validation format (Spotless)
  # ============================================================
  format-check:
    name: Format Validation
    runs-on: ubuntu-latest
    needs: [should-run, init-check]
    if: needs.should-run.outputs.should_run == 'true'
    outputs:
      format_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Check formatting
        run: mvn -B spotless:check

  # ============================================================
  # Finalisation du status check
  # ============================================================
  finalize:
    name: Finalize Status
    runs-on: ubuntu-latest
    needs:
      - should-run
      - init-check
      - build-and-test
      - static-analysis
      - integration-tests
      - sonarcloud
      - codeql
      - dependency-security
      - format-check
    if: always() && needs.should-run.outputs.should_run == 'true'
    steps:
      - name: Determine overall status
        id: status
        run: |
          # Jobs critiques (doivent réussir)
          CRITICAL_JOBS="${{ needs.build-and-test.outputs.build_status }},${{ needs.integration-tests.outputs.test_status }},${{ needs.format-check.outputs.format_status }}"
          
          # Jobs importants (warning si échec)
          IMPORTANT_JOBS="${{ needs.static-analysis.outputs.analysis_status }},${{ needs.sonarcloud.outputs.sonar_status }}"
          
          # Jobs sécurité (peuvent continuer en erreur)
          SECURITY_JOBS="${{ needs.codeql.outputs.codeql_status }},${{ needs.dependency-security.outputs.security_status }}"
          
          # Vérifier les jobs critiques
          if echo "$CRITICAL_JOBS" | grep -q "failure"; then
            echo "conclusion=failure" >> $GITHUB_OUTPUT
            echo "summary=❌ Critical jobs failed: build, tests, or formatting" >> $GITHUB_OUTPUT
          elif echo "$IMPORTANT_JOBS" | grep -q "failure"; then
            echo "conclusion=failure" >> $GITHUB_OUTPUT
            echo "summary=⚠️ Analysis jobs failed: static analysis or SonarCloud" >> $GITHUB_OUTPUT
          else
            echo "conclusion=success" >> $GITHUB_OUTPUT
            echo "summary=✅ All checks passed" >> $GITHUB_OUTPUT
          fi

      - name: Update check run
        uses: LouisBrunner/checks-action@v2.0.0
        with:
          sha: ${{ needs.should-run.outputs.effective_sha }}
          token: ${{ secrets.GITHUB_TOKEN }}
          name: Alexandria CI Pipeline
          conclusion: ${{ steps.status.outputs.conclusion }}
          output: |
            {"summary":"${{ steps.status.outputs.summary }}","title":"Alexandria CI Results"}

      - name: Remove ready-for-ci label on success
        if: steps.status.outputs.conclusion == 'success' && github.event.action == 'labeled'
        uses: actions/github-script@v7
        with:
          script: |
            try {
              await github.rest.issues.removeLabel({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: context.issue.number,
                name: 'ready-for-ci'
              });
            } catch (e) {
              console.log('Label already removed or not found');
            }
```

## Configuration Maven complète (pom.xml)

### Properties

```xml
<properties>
    <java.version>25</java.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    
    <!-- Versions des plugins de qualité -->
    <spotless.version>2.43.0</spotless.version>
    <google-java-format.version>1.25.2</google-java-format.version>
    <checkstyle.version>12.3.1</checkstyle.version>
    <pmd.version>7.20.0</pmd.version>
    <spotbugs.version>4.9.8</spotbugs.version>
    <error-prone.version>2.45.0</error-prone.version>
    <owasp-dc.version>12.1.9</owasp-dc.version>
    <sonar.version>5.5.0.6356</sonar.version>
    <pitest.version>1.19.1</pitest.version>
    <enforcer.version>3.6.2</enforcer.version>
    <git-code-format.version>5.4</git-code-format.version>
    
    <!-- Skips par défaut (profil dev) -->
    <skipITs>true</skipITs>
    <spotless.check.skip>true</spotless.check.skip>
    <checkstyle.skip>true</checkstyle.skip>
    <pmd.skip>true</pmd.skip>
    <spotbugs.skip>true</spotbugs.skip>
    <dependency-check.skip>true</dependency-check.skip>
    
    <!-- SonarCloud -->
    <sonar.organization>${env.GITHUB_REPOSITORY_OWNER}</sonar.organization>
    <sonar.host.url>https://sonarcloud.io</sonar.host.url>
</properties>
```

### Plugins de qualité

```xml
<build>
    <plugins>
        <!-- Spotless - Formatage -->
        <plugin>
            <groupId>com.diffplug.spotless</groupId>
            <artifactId>spotless-maven-plugin</artifactId>
            <version>${spotless.version}</version>
            <configuration>
                <java>
                    <googleJavaFormat>
                        <version>${google-java-format.version}</version>
                        <style>GOOGLE</style>
                    </googleJavaFormat>
                    <removeUnusedImports/>
                    <importOrder>
                        <order>java,javax,org,com,dev.alexandria</order>
                    </importOrder>
                </java>
                <upToDateChecking>
                    <enabled>true</enabled>
                </upToDateChecking>
            </configuration>
            <executions>
                <execution>
                    <goals><goal>check</goal></goals>
                    <phase>compile</phase>
                </execution>
            </executions>
        </plugin>

        <!-- Git hooks - Cosium -->
        <plugin>
            <groupId>com.cosium.code</groupId>
            <artifactId>git-code-format-maven-plugin</artifactId>
            <version>${git-code-format.version}</version>
            <executions>
                <execution>
                    <id>install-formatter-hook</id>
                    <goals><goal>install-hooks</goal></goals>
                </execution>
            </executions>
            <dependencies>
                <dependency>
                    <groupId>com.cosium.code</groupId>
                    <artifactId>google-java-format</artifactId>
                    <version>${git-code-format.version}</version>
                </dependency>
            </dependencies>
        </plugin>

        <!-- Checkstyle -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-checkstyle-plugin</artifactId>
            <version>3.6.0</version>
            <dependencies>
                <dependency>
                    <groupId>com.puppycrawl.tools</groupId>
                    <artifactId>checkstyle</artifactId>
                    <version>${checkstyle.version}</version>
                </dependency>
            </dependencies>
            <configuration>
                <configLocation>google_checks.xml</configLocation>
                <consoleOutput>true</consoleOutput>
                <failsOnError>true</failsOnError>
                <violationSeverity>warning</violationSeverity>
                <includeTestSourceDirectory>true</includeTestSourceDirectory>
            </configuration>
            <executions>
                <execution>
                    <phase>validate</phase>
                    <goals><goal>check</goal></goals>
                </execution>
            </executions>
        </plugin>

        <!-- PMD -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-pmd-plugin</artifactId>
            <version>3.28.0</version>
            <configuration>
                <targetJdk>25</targetJdk>
                <rulesets>
                    <ruleset>/category/java/bestpractices.xml</ruleset>
                    <ruleset>/category/java/errorprone.xml</ruleset>
                    <ruleset>/category/java/codestyle.xml</ruleset>
                </rulesets>
                <analysisCache>true</analysisCache>
                <analysisCacheLocation>${project.build.directory}/pmd.cache</analysisCacheLocation>
                <printFailingErrors>true</printFailingErrors>
            </configuration>
            <executions>
                <execution>
                    <phase>verify</phase>
                    <goals>
                        <goal>check</goal>
                        <goal>cpd-check</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <!-- SpotBugs -->
        <plugin>
            <groupId>com.github.spotbugs</groupId>
            <artifactId>spotbugs-maven-plugin</artifactId>
            <version>4.9.8.2</version>
            <dependencies>
                <dependency>
                    <groupId>com.github.spotbugs</groupId>
                    <artifactId>spotbugs</artifactId>
                    <version>${spotbugs.version}</version>
                </dependency>
            </dependencies>
            <configuration>
                <effort>Max</effort>
                <threshold>Medium</threshold>
                <xmlOutput>true</xmlOutput>
                <plugins>
                    <plugin>
                        <groupId>com.h3xstream.findsecbugs</groupId>
                        <artifactId>findsecbugs-plugin</artifactId>
                        <version>1.12.0</version>
                    </plugin>
                </plugins>
            </configuration>
            <executions>
                <execution>
                    <phase>verify</phase>
                    <goals><goal>check</goal></goals>
                </execution>
            </executions>
        </plugin>

        <!-- OWASP Dependency-Check -->
        <plugin>
            <groupId>org.owasp</groupId>
            <artifactId>dependency-check-maven</artifactId>
            <version>${owasp-dc.version}</version>
            <configuration>
                <nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>
                <failBuildOnCVSS>7</failBuildOnCVSS>
                <formats>
                    <format>HTML</format>
                    <format>JSON</format>
                </formats>
                <suppressionFiles>
                    <suppressionFile>${project.basedir}/owasp-suppressions.xml</suppressionFile>
                </suppressionFiles>
                <assemblyAnalyzerEnabled>false</assemblyAnalyzerEnabled>
                <nodeAuditAnalyzerEnabled>false</nodeAuditAnalyzerEnabled>
                <retireJsAnalyzerEnabled>false</retireJsAnalyzerEnabled>
            </configuration>
        </plugin>

        <!-- SonarCloud -->
        <plugin>
            <groupId>org.sonarsource.scanner.maven</groupId>
            <artifactId>sonar-maven-plugin</artifactId>
            <version>${sonar.version}</version>
        </plugin>

        <!-- PIT Mutation Testing -->
        <plugin>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-maven</artifactId>
            <version>${pitest.version}</version>
            <configuration>
                <targetClasses>
                    <param>dev.alexandria.*</param>
                </targetClasses>
                <targetTests>
                    <param>dev.alexandria.*Test</param>
                    <param>dev.alexandria.**.*Test</param>
                </targetTests>
                <excludedClasses>
                    <param>dev.alexandria.config.*</param>
                    <param>dev.alexandria.*Application</param>
                </excludedClasses>
                <threads>4</threads>
                <timeoutConstant>10000</timeoutConstant>
                <timeoutFactor>1.5</timeoutFactor>
                <outputFormats>
                    <param>HTML</param>
                    <param>XML</param>
                </outputFormats>
                <timestampedReports>false</timestampedReports>
                <mutationThreshold>70</mutationThreshold>
                <coverageThreshold>70</coverageThreshold>
                <withHistory>true</withHistory>
                <historyInputFile>.pitest-history</historyInputFile>
                <historyOutputFile>.pitest-history</historyOutputFile>
            </configuration>
            <dependencies>
                <dependency>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-junit5-plugin</artifactId>
                    <version>1.2.2</version>
                </dependency>
            </dependencies>
        </plugin>

        <!-- Maven Enforcer -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-enforcer-plugin</artifactId>
            <version>${enforcer.version}</version>
            <executions>
                <execution>
                    <id>enforce</id>
                    <goals><goal>enforce</goal></goals>
                    <configuration>
                        <rules>
                            <requireJavaVersion>
                                <version>[25,)</version>
                                <message>Java 25+ requis!</message>
                            </requireJavaVersion>
                            <requireMavenVersion>
                                <version>[3.8.1,)</version>
                            </requireMavenVersion>
                            <dependencyConvergence/>
                            <banDuplicatePomDependencyVersions/>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Profils Maven

```xml
<profiles>
    <!-- Profil par défaut: développement rapide -->
    <profile>
        <id>dev</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <!-- Utilise les skips définis dans properties -->
    </profile>

    <!-- Profil CI: toutes les vérifications -->
    <profile>
        <id>ci</id>
        <activation>
            <property>
                <name>env.CI</name>
                <value>true</value>
            </property>
        </activation>
        <properties>
            <skipITs>false</skipITs>
            <spotless.check.skip>false</spotless.check.skip>
            <checkstyle.skip>false</checkstyle.skip>
            <pmd.skip>false</pmd.skip>
            <spotbugs.skip>false</spotbugs.skip>
            <dependency-check.skip>false</dependency-check.skip>
        </properties>
    </profile>

    <!-- Profil analyse statique locale -->
    <profile>
        <id>static-analysis</id>
        <properties>
            <skipITs>true</skipITs>
            <spotless.check.skip>false</spotless.check.skip>
            <checkstyle.skip>false</checkstyle.skip>
            <pmd.skip>false</pmd.skip>
            <spotbugs.skip>false</spotbugs.skip>
            <dependency-check.skip>true</dependency-check.skip>
        </properties>
    </profile>

    <!-- Profil Error Prone (compilation avec détection bugs) -->
    <profile>
        <id>error-prone</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <source>25</source>
                        <target>25</target>
                        <compilerArgs>
                            <arg>-XDcompilePolicy=simple</arg>
                            <arg>--should-stop=ifError=FLOW</arg>
                            <arg>-Xplugin:ErrorProne</arg>
                        </compilerArgs>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>com.google.errorprone</groupId>
                                <artifactId>error_prone_core</artifactId>
                                <version>${error-prone.version}</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

## Configuration Branch Protection

Dans **Settings → Branches → Add rule** pour `main` :

```
Branch name pattern: main

☑ Require a pull request before merging
  ☑ Require approvals: 1

☑ Require status checks to pass before merging
  ☑ Require branches to be up to date before merging
  Status checks: "Alexandria CI Pipeline"

☑ Require conversation resolution before merging

☐ Do not allow bypassing the above settings
```

## Secrets GitHub requis

| Secret | Source | Obligatoire |
|--------|--------|-------------|
| `SONAR_TOKEN` | sonarcloud.io → My Account → Security | Oui |
| `NVD_API_KEY` | nvd.nist.gov (gratuit) | Recommandé |
| `GITHUB_TOKEN` | Automatique | Auto |

## Fichier .gitignore additionnel

```gitignore
# Analysis caches
.pitest-history
target/pmd.cache

# IDE
.vscode/
.idea/
*.iml

# Build
target/
```

## Makefile pour simplifier les commandes

```makefile
.PHONY: dev test analyse full-check format pit

# Développement rapide
dev:
	mvn compile

# Tests unitaires
test:
	mvn test -Dtest='!**/*IT.java'

# Tests intégration
test-it:
	mvn verify -DskipUnitTests

# Analyse statique complète
analyse:
	mvn verify -Pstatic-analysis

# Mutation testing (long)
pit:
	mvn test-compile org.pitest:pitest-maven:mutationCoverage -DwithHistory

# Vérification complète avant push
full-check:
	./scripts/full-analysis.sh

# Formatage
format:
	mvn spotless:apply

# Nettoyage
clean:
	mvn clean
```

## Résumé des temps d'exécution estimés

| Phase | Commande | Durée |
|-------|----------|-------|
| **Dev local** | `mvn compile` | ~5s |
| **Pre-commit** | Spotless + Checkstyle | ~5s |
| **Tests unitaires** | `mvn test` | ~30s |
| **Analyse statique** | `mvn verify -Pstatic-analysis` | ~2-3min |
| **Mutation testing** | `mvn pitest:mutationCoverage` | ~8-15min |
| **Full local** | `./scripts/full-analysis.sh` | ~15-20min |
| **CI/CD complet** | Workflow parallélisé | ~12-15min |

## Checklist développeur

### Pendant le développement avec Claude Code
- [ ] SonarLint actif dans VS Code
- [ ] Compilation rapide avec `mvn compile`
- [ ] Format on save activé

### Avant chaque commit
- [ ] Pre-commit hook s'exécute automatiquement
- [ ] Code formaté par Spotless

### Avant de créer/mettre à jour une PR
- [ ] `./scripts/full-analysis.sh` passé
- [ ] Mutation testing vérifié si code critique

### Pour déclencher la CI
- [ ] Option 1: Ajouter le label `ready-for-ci`
- [ ] Option 2: Passer la PR de draft à ready
- [ ] Option 3: Workflow dispatch manuel
