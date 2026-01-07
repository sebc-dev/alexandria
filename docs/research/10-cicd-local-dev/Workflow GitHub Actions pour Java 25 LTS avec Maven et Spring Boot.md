# Workflow GitHub Actions pour Java 25 LTS avec Maven et Spring Boot

Le pattern "déclenchement manuel mais obligatoire pour merger" nécessite l'API Checks de GitHub car `workflow_dispatch` ne peut pas directement être un status check requis. La solution recommandée combine un workflow principal avec reporting vers le commit SHA de la PR, permettant ainsi de bloquer les merges tant que le workflow n'a pas été exécuté manuellement.

## Configuration branch protection + workflow_dispatch obligatoire

Le défi technique réside dans le fait que **GitHub Actions ne permet pas de lier directement un `workflow_dispatch` aux status checks**. Les status checks sont associés à un SHA de commit, alors que `workflow_dispatch` s'exécute sur une branche. La solution consiste à utiliser l'API Checks pour reporter le statut sur le commit de la PR.

### Workflow YAML complet et fonctionnel

```yaml
# .github/workflows/ci-unified.yml
name: CI Alexandria - Build, Test & Security

on:
  workflow_dispatch:
    inputs:
      pr_number:
        description: 'Numéro de la PR (laisser vide si exécuté manuellement)'
        required: false
        type: string
      commit_sha:
        description: 'SHA du commit pour le status check'
        required: false
        type: string

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
  # JOB 1: Initialisation du status check
  # ============================================================
  init-check:
    name: Initialize Status Check
    runs-on: ubuntu-latest
    outputs:
      check_run_id: ${{ steps.create-check.outputs.check_id }}
      effective_sha: ${{ steps.determine-sha.outputs.sha }}
    steps:
      - name: Determine effective SHA
        id: determine-sha
        run: |
          if [ -n "${{ inputs.commit_sha }}" ]; then
            echo "sha=${{ inputs.commit_sha }}" >> $GITHUB_OUTPUT
          else
            echo "sha=${{ github.sha }}" >> $GITHUB_OUTPUT
          fi

      - name: Create check run
        id: create-check
        uses: LouisBrunner/checks-action@v2.0.0
        with:
          sha: ${{ steps.determine-sha.outputs.sha }}
          token: ${{ secrets.GITHUB_TOKEN }}
          name: Alexandria CI Pipeline
          status: in_progress
          output: |
            {"summary":"Pipeline en cours d'exécution..."}

  # ============================================================
  # JOB 2: Build + Tests unitaires (parallèle)
  # ============================================================
  build-and-unit-tests:
    name: Build & Unit Tests
    runs-on: ubuntu-latest
    needs: init-check
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

      - name: Cache Maven dependencies
        uses: actions/cache@v5
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-maven-

      - name: Compile project
        run: mvn -B compile -DskipTests

      - name: Run unit tests
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
  # JOB 3: Tests d'intégration Testcontainers (parallèle)
  # ============================================================
  integration-tests:
    name: Integration Tests (Testcontainers + pgvector)
    runs-on: ubuntu-latest
    needs: init-check
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
          path: |
            **/target/failsafe-reports/
          retention-days: 7

  # ============================================================
  # JOB 4: Analyse qualité SonarCloud (parallèle)
  # ============================================================
  sonarcloud:
    name: SonarCloud Analysis
    runs-on: ubuntu-latest
    needs: init-check
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
            -Dsonar.organization=your-org \
            -Dsonar.host.url=https://sonarcloud.io \
            -Dsonar.java.source=25

  # ============================================================
  # JOB 5: Sécurité SAST - CodeQL (parallèle)
  # ============================================================
  codeql:
    name: CodeQL SAST Analysis
    runs-on: ubuntu-latest
    needs: init-check
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
  # JOB 6: Sécurité dépendances - OWASP + Trivy (parallèle)
  # ============================================================
  dependency-security:
    name: Dependency Security Scan
    runs-on: ubuntu-latest
    needs: init-check
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

      - name: Upload Trivy results to GitHub Security
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: 'trivy-results.sarif'
          category: 'trivy-dependency-scan'

      - name: Upload OWASP report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: owasp-dependency-check-report
          path: target/dependency-check-report.*
          retention-days: 14

  # ============================================================
  # JOB 7: Mutation Testing - PIT (séquentiel après build)
  # ============================================================
  mutation-testing:
    name: PIT Mutation Testing
    runs-on: ubuntu-latest
    needs: [build-and-unit-tests]
    outputs:
      pit_status: ${{ job.status }}
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

      - name: Cache PIT history
        uses: actions/cache@v5
        with:
          path: .pitest-history
          key: pitest-${{ runner.os }}-${{ github.ref_name }}
          restore-keys: |
            pitest-${{ runner.os }}-main
            pitest-${{ runner.os }}-

      - name: Run PIT mutation testing
        run: |
          mvn -B test-compile org.pitest:pitest-maven:mutationCoverage \
            -DwithHistory=true \
            -Dthreads=4 \
            -DtimestampedReports=false \
            -DmutationThreshold=70

      - name: Publish PIT Report
        uses: Bonajo/pitest-report-action@v0.5
        if: always()
        with:
          file: '**/pit-reports/mutations.xml'
          summary: true
          annotation-types: SURVIVED
          max-annotations: 20

      - name: Upload PIT report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: pit-mutation-report
          path: target/pit-reports/
          retention-days: 14

  # ============================================================
  # JOB 8: Build Docker (validation sans push)
  # ============================================================
  docker-build:
    name: Docker Build Validation
    runs-on: ubuntu-latest
    needs: [build-and-unit-tests]
    outputs:
      docker_status: ${{ job.status }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v5

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Set up Java 25 LTS
        uses: actions/setup-java@v5
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Build JAR for Docker
        run: mvn -B package -DskipTests

      - name: Build Docker image (no push)
        uses: docker/build-push-action@v6
        with:
          context: .
          push: false
          tags: alexandria-mcp:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          load: true

      - name: Scan Docker image with Trivy
        uses: aquasecurity/trivy-action@0.28.0
        with:
          image-ref: 'alexandria-mcp:${{ github.sha }}'
          format: 'sarif'
          output: 'trivy-docker.sarif'
          severity: 'CRITICAL,HIGH'

      - name: Upload Docker scan results
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: 'trivy-docker.sarif'
          category: 'trivy-docker-scan'

  # ============================================================
  # JOB 9: Finalisation du status check
  # ============================================================
  finalize:
    name: Finalize Status Check
    runs-on: ubuntu-latest
    needs:
      - init-check
      - build-and-unit-tests
      - integration-tests
      - sonarcloud
      - codeql
      - dependency-security
      - mutation-testing
      - docker-build
    if: always()
    steps:
      - name: Determine overall status
        id: status
        run: |
          if [[ "${{ needs.build-and-unit-tests.outputs.build_status }}" == "success" && \
                "${{ needs.integration-tests.outputs.test_status }}" == "success" && \
                "${{ needs.sonarcloud.outputs.sonar_status }}" == "success" && \
                "${{ needs.codeql.outputs.codeql_status }}" == "success" && \
                "${{ needs.docker-build.outputs.docker_status }}" == "success" ]]; then
            echo "conclusion=success" >> $GITHUB_OUTPUT
          else
            echo "conclusion=failure" >> $GITHUB_OUTPUT
          fi

      - name: Update check run
        uses: LouisBrunner/checks-action@v2.0.0
        with:
          sha: ${{ needs.init-check.outputs.effective_sha }}
          token: ${{ secrets.GITHUB_TOKEN }}
          name: Alexandria CI Pipeline
          conclusion: ${{ steps.status.outputs.conclusion }}
          output: |
            {"summary":"Pipeline terminé avec statut: ${{ steps.status.outputs.conclusion }}","title":"Résultat CI Alexandria"}
```

### Configuration Branch Protection Rules

Accédez à **Settings → Branches → Add branch protection rule** et configurez:

```
Branch name pattern: main

☑ Require a pull request before merging
  ☑ Require approvals: 1
  
☑ Require status checks to pass before merging
  ☑ Require branches to be up to date before merging
  Status checks: "Alexandria CI Pipeline"  ← Tapez ce nom exact
  
☑ Require conversation resolution before merging

☑ Do not allow bypassing the above settings
```

La clé est d'ajouter **"Alexandria CI Pipeline"** comme status check requis après avoir exécuté le workflow au moins une fois sur la branche `main`.

## Configuration Maven pour PIT (pom.xml)

```xml
<properties>
    <pitest.version>1.19.1</pitest.version>
    <pitest-junit5.version>1.2.2</pitest-junit5.version>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-maven</artifactId>
            <version>${pitest.version}</version>
            <configuration>
                <targetClasses>
                    <param>com.alexandria.*</param>
                </targetClasses>
                <targetTests>
                    <param>com.alexandria.*Test</param>
                    <param>com.alexandria.**.*Test</param>
                </targetTests>
                <excludedClasses>
                    <param>com.alexandria.config.*</param>
                    <param>com.alexandria.*Application</param>
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
                <features>
                    <feature>+auto_threads</feature>
                </features>
            </configuration>
            <dependencies>
                <dependency>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-junit5-plugin</artifactId>
                    <version>${pitest-junit5.version}</version>
                </dependency>
            </dependencies>
        </plugin>
    </plugins>
</build>
```

## Configuration Testcontainers pour pgvector

```java
// src/test/java/com/alexandria/test/PgVectorContainerConfig.java
package com.alexandria.test;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class PgVectorContainerConfig {
    
    private static final DockerImageName PGVECTOR_IMAGE = 
        DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
            .asCompatibleSubstituteFor("postgres");

    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> createPgVectorContainer() {
        return new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("alexandria_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-pgvector.sql");
    }
}
```

## Recommandations sur les outils

### Comparaison des outils de qualité et sécurité

| Catégorie | Outil recommandé | Raison | Alternatives |
|-----------|-----------------|--------|--------------|
| **Qualité code** | SonarCloud | Gratuit illimité OSS, meilleure intégration PR, règles Java matures | Qodana (bon IDE integration) |
| **SAST profond** | CodeQL | Gratuit repos publics, analyse sémantique excellente pour Java | - |
| **SAST rapide** | Semgrep | Feedback rapide, règles custom faciles | Inclus dans SonarCloud |
| **Dépendances** | Dependabot + OWASP Dep-Check | PRs auto + couverture NVD complète | Snyk (200 tests/mois gratuits) |
| **Containers** | Trivy | 100% gratuit, SARIF pour GitHub Security | Grype |
| **Supply chain** | Socket.dev | Détection comportementale unique | - |

### Stack recommandé pour Alexandria (100% gratuit)

La combinaison optimale pour un projet open source Java couvre toutes les dimensions de qualité et sécurité sans coût:

1. **SonarCloud** pour la qualité du code, bugs, code smells, security hotspots
2. **GitHub CodeQL** pour l'analyse SAST sémantique profonde
3. **Dependabot** activé pour les mises à jour automatiques avec PRs
4. **OWASP Dependency-Check** pour une couverture NVD complète
5. **Trivy** pour le scan des dépendances et images Docker
6. **Socket.dev** pour la détection d'attaques supply chain

## Évaluation SocketDev: garder ou supprimer?

**Recommandation: GARDER SocketDev**

Socket.dev apporte une valeur unique que Dependabot + Trivy ne peuvent pas fournir. Alors que Dependabot et Trivy sont **réactifs** (ils détectent les CVE connues), Socket utilise une **analyse comportementale proactive** qui identifie les packages malveillants avant qu'un CVE soit publié. Socket détecte plus de **70 signaux de risque** incluant l'accès réseau, l'exécution de shell, le code obfusqué, et les changements de mainteneurs suspects.

Le support Java/Maven est maintenant complet avec analyse des fichiers `pom.xml` et des dépendances MavenCentral. Pour un projet open source, la tier gratuite offre une protection illimitée, ce qui représente un excellent rapport qualité/prix (gratuit).

Le seul cas où supprimer SocketDev ferait sens serait si vous avez plusieurs dépôts privés et ne souhaitez pas payer pour les plans Team/Enterprise.

## Configuration CodeRabbit Pro optimale

```yaml
# .coderabbit.yaml
language: "fr-FR"

tone_instructions: |
  Expert Java, Spring Boot 3.5, et Maven.
  Focus sur sécurité, bugs, patterns Spring, et best practices Java 25.
  Sois concis et actionnable.

reviews:
  profile: assertive
  request_changes_workflow: false
  high_level_summary: true
  
  auto_review:
    enabled: true
    drafts: false
    ignore_title_keywords:
      - "WIP"
      - "Draft"
      - "[skip review]"
    
    path_filters:
      - "src/**/*.java"
      - "pom.xml"
      - "*.properties"
      - "*.yaml"
      - "*.yml"
      - "!**/target/**"
      - "!**/*.generated.java"

chat:
  auto_reply: true

tools:
  enabled: true
```

CodeRabbit **complète** SonarCloud sans conflit: SonarCloud applique des règles statiques tandis que CodeRabbit fournit une revue contextuelle par IA. Ne l'utilisez pas comme status check bloquant.

## Estimation du temps d'exécution

| Job | Temps estimé | Minutes GitHub Actions |
|-----|-------------|----------------------|
| Build + Unit Tests | ~3-4 min | 4 min |
| Integration Tests (Testcontainers) | ~5-7 min | 7 min |
| SonarCloud | ~4-5 min | 5 min |
| CodeQL | ~8-15 min | 12 min |
| OWASP + Trivy | ~5-8 min | 7 min |
| PIT Mutation Testing | ~8-12 min | 10 min |
| Docker Build + Scan | ~3-5 min | 5 min |
| **Total (séquentiel)** | ~36-56 min | - |
| **Total (parallélisé)** | **~15-20 min** | **~50 min** |

Avec la structure parallèle du workflow, les jobs 2-6 s'exécutent simultanément après l'initialisation, puis PIT et Docker en parallèle après le build. Le temps total est contrôlé par le job le plus long (CodeQL ~12 min) plus les jobs séquentiels (~8 min).

**Consommation mensuelle estimée** (100 PRs/mois): **~5,000 minutes** — largement dans la limite gratuite de GitHub Actions pour les repos publics (illimité).

## Secrets à configurer

Ajoutez ces secrets dans **Settings → Secrets and variables → Actions**:

| Secret | Source | Obligatoire |
|--------|--------|-------------|
| `SONAR_TOKEN` | sonarcloud.io → My Account → Security | Oui |
| `NVD_API_KEY` | nvd.nist.gov (gratuit, recommandé) | Recommandé |
| `GITHUB_TOKEN` | Automatique | Auto |

## Fichier Dependabot recommandé

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 10
    groups:
      spring-boot:
        patterns:
          - "org.springframework.boot*"
          - "org.springframework*"
      testing:
        patterns:
          - "org.junit*"
          - "org.mockito*"
          - "org.testcontainers*"
    labels:
      - "dependencies"
      - "java"

  - package-ecosystem: "docker"
    directory: "/"
    schedule:
      interval: "weekly"
    labels:
      - "dependencies"
      - "docker"

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    labels:
      - "dependencies"
      - "ci"
```

Cette configuration regroupe les dépendances Spring et testing pour réduire le bruit des PRs tout en maintenant les mises à jour automatiques.