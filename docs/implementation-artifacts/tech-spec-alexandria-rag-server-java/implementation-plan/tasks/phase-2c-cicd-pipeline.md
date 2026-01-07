# Phase 2C: CI/CD Pipeline

> **Note**: Cette phase n'utilise pas de TDD. Les fichiers de configuration CI sont validés par l'exécution du pipeline.

## Objectif

Mettre en place le pipeline CI/CD GitHub Actions pour :
- Valider chaque commit avec build + tests
- Détecter les vulnérabilités de sécurité tôt
- Maintenir la qualité du code via SonarCloud
- Automatiser les mises à jour de dépendances

## Prérequis

- Phase 1 complétée (structure Maven fonctionnelle)
- Phase 2A complétée (dev tools locaux)
- Phase 2B complétée (plugins qualité dans pom.xml)
- Secrets GitHub configurés (voir Task 3)

---

- [ ] **Task 1: Create GitHub Actions CI Workflow**
  - File: `.github/workflows/ci.yml`
  - Action: Workflow unifié avec jobs parallèles
    ```yaml
    name: CI Alexandria - Build, Test & Security

    on:
      # Triggers principaux pour status checks automatiques
      push:
        branches: [main]
      pull_request:
        branches: [main]
      # Trigger manuel (workaround pour re-runs)
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
      # Job 1: Initialisation du status check
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

      # Job 2: Build + Tests unitaires
      build-and-unit-tests:
        name: Build & Unit Tests
        runs-on: ubuntu-latest
        needs: init-check
        outputs:
          build_status: ${{ job.status }}
        steps:
          - name: Checkout code
            uses: actions/checkout@v6
            with:
              fetch-depth: 0

          - name: Set up Java 25 LTS
            uses: actions/setup-java@v5
            with:
              distribution: ${{ env.JAVA_DISTRIBUTION }}
              java-version: ${{ env.JAVA_VERSION }}
              cache: maven
              # check-latest: false (défaut) pour builds plus rapides
              # Java 25 n'est pas pré-caché sur runners GitHub

          - name: Compile project
            run: mvn -B compile -DskipTests

          - name: Run unit tests
            run: mvn -B test -Dtest='!**/*IT.java'

          - name: Upload test results
            if: always()
            uses: actions/upload-artifact@v6
            with:
              name: unit-test-results
              path: |
                **/target/surefire-reports/
                **/target/jacoco.exec
              retention-days: 7

      # Job 3: Tests d'intégration Testcontainers
      # ⚠️ Testcontainers 2.0.2+ requis (1.x incompatible Docker Engine 29.0.0+)
      integration-tests:
        name: Integration Tests (Testcontainers + pgvector)
        runs-on: ubuntu-latest
        needs: init-check
        outputs:
          test_status: ${{ job.status }}
        steps:
          - name: Checkout code
            uses: actions/checkout@v6

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
            uses: actions/upload-artifact@v6
            with:
              name: integration-test-results
              path: |
                **/target/failsafe-reports/
              retention-days: 7

      # Job 4: Analyse qualité SonarCloud
      sonarcloud:
        name: SonarCloud Analysis
        runs-on: ubuntu-latest
        needs: init-check
        outputs:
          sonar_status: ${{ job.status }}
        steps:
          - name: Checkout code
            uses: actions/checkout@v6
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
              mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.3.0.6276:sonar \
                -Dsonar.projectKey=alexandria-mcp \
                -Dsonar.organization=${{ github.repository_owner }} \
                -Dsonar.host.url=https://sonarcloud.io \
                -Dsonar.java.source=25 \
                -Dsonar.scanner.jreProvisioning=disabled

      # Job 5: Sécurité SAST - CodeQL
      codeql:
        name: CodeQL SAST Analysis
        runs-on: ubuntu-latest
        needs: init-check
        outputs:
          codeql_status: ${{ job.status }}
        steps:
          - name: Checkout code
            uses: actions/checkout@v6

          - name: Initialize CodeQL
            uses: github/codeql-action/init@v4
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
            uses: github/codeql-action/analyze@v4
            with:
              category: "/language:java"

      # Job 6: Sécurité dépendances - OWASP + Trivy
      dependency-security:
        name: Dependency Security Scan
        runs-on: ubuntu-latest
        needs: init-check
        outputs:
          security_status: ${{ job.status }}
        steps:
          - name: Checkout code
            uses: actions/checkout@v6

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
            uses: aquasecurity/trivy-action@0.33.1
            with:
              scan-type: 'fs'
              scan-ref: '.'
              format: 'sarif'
              output: 'trivy-results.sarif'
              severity: 'CRITICAL,HIGH'
              limit-severities-for-sarif: true
              ignore-unfixed: true

          - name: Upload Trivy results to GitHub Security
            uses: github/codeql-action/upload-sarif@v4
            with:
              sarif_file: 'trivy-results.sarif'
              category: 'trivy-dependency-scan'

          - name: Upload OWASP report
            uses: actions/upload-artifact@v6
            if: always()
            with:
              name: owasp-dependency-check-report
              path: target/dependency-check-report.*
              retention-days: 14

      # Job 7: Build Docker (validation sans push)
      docker-build:
        name: Docker Build Validation
        runs-on: ubuntu-latest
        needs: [build-and-unit-tests]
        outputs:
          docker_status: ${{ job.status }}
        steps:
          - name: Checkout code
            uses: actions/checkout@v6

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
            uses: aquasecurity/trivy-action@0.33.1
            with:
              image-ref: 'alexandria-mcp:${{ github.sha }}'
              format: 'sarif'
              output: 'trivy-docker.sarif'
              severity: 'CRITICAL,HIGH'

          - name: Upload Docker scan results
            uses: github/codeql-action/upload-sarif@v4
            with:
              sarif_file: 'trivy-docker.sarif'
              category: 'trivy-docker-scan'

      # Job 8: Finalisation du status check
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

  - Notes:
    - Jobs 2-6 s'exécutent en parallèle après init-check
    - Docker build attend le succès des tests unitaires
    - Temps total estimé: ~12-15 min (contrôlé par CodeQL)
    - **Pas de mutation testing** (PIT) pour réduire le temps d'exécution
    - **Versions mises à jour janvier 2026** :

      | Action | Version | Notes |
      |--------|---------|-------|
      | actions/checkout | v6.0.1 | Breaking change: credentials sous `$RUNNER_TEMP` |
      | actions/upload-artifact | v6.0.0 | Node.js 24, max 500 artifacts/job |
      | actions/setup-java | v5 (v5.1.0) | Ajout `.sdkmanrc`, Microsoft OpenJDK 25 |
      | github/codeql-action | v4.31.7 | Java 25 support via CodeQL 2.23.1 |
      | aquasecurity/trivy-action | 0.33.1 | Fix fuite inputs (v0.31.0) |
      | docker/build-push-action | v6.18.0 | Cache API v1 deprecated, utiliser `type=gha` |
    - **Note runner** : actions/checkout v6 requiert Actions Runner v2.329.0+ pour Docker. upload-artifact v6 requiert runner v2.327.1+.
    - **Java 25** : Non pré-caché sur runners GitHub (seulement 8, 11, 17, 21). setup-java le télécharge à chaque run (~15-30s supplémentaires).
    - **SonarCloud Java 25** : Support officiel prévu mi-2026 (bloqué par ECJ). Workaround: `jreProvisioning=disabled` + `sonar.java.source=25`. Parsing errors possibles si syntaxe Java 25 utilisée.

---

- [ ] **Task 2: Create Dependabot Configuration**
  - File: `.github/dependabot.yml`
  - Action: Configuration avec groupes de dépendances
    ```yaml
    version: 2
    updates:
      - package-ecosystem: "maven"
        directory: "/"
        schedule:
          interval: "weekly"
          day: "monday"
        open-pull-requests-limit: 10
        # Cooldown: attendre avant de proposer des mises à jour (évite les versions buggy)
        cooldown:
          default-days: 3
          semver-major-days: 7  # Attendre plus longtemps pour les majors
        groups:
          spring-boot:
            patterns:
              - "org.springframework.boot*"
              - "org.springframework*"
            update-types: ["minor", "patch"]
          testing:
            patterns:
              - "org.junit*"
              - "org.mockito*"
              - "org.testcontainers*"
            update-types: ["minor", "patch"]
          langchain4j:
            patterns:
              - "dev.langchain4j*"
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
          interval: "monthly"
        groups:
          actions-minor:
            update-types: ["minor", "patch"]
        labels:
          - "dependencies"
          - "ci"
    ```
  - Notes:
    - Groupes pour réduire le bruit des PRs (les groupes ne s'appliquent qu'aux version updates, pas aux security updates)
    - **Cooldown** : Nouvelle fonctionnalité 2025-2026 - retarde les mises à jour pour éviter les versions buggy récentes
    - **Caveat groupes** : Une dépendance matchant plusieurs groupes rejoint seulement le premier match
    - Updates Maven hebdomadaires le lundi, GitHub Actions mensuelles

---

- [ ] **Task 3: Configure SonarCloud Project**
  - File: `sonar-project.properties` (optionnel, config via Maven)
  - Action: Configuration dans pom.xml ou fichier dédié
    ```properties
    sonar.projectKey=alexandria-mcp
    sonar.organization=${GITHUB_REPOSITORY_OWNER}
    sonar.host.url=https://sonarcloud.io
    sonar.java.source=25
    sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
    sonar.exclusions=**/generated/**,**/test/**
    ```
  - Action manuelle:
    1. Aller sur sonarcloud.io
    2. Importer le projet depuis GitHub
    3. Générer SONAR_TOKEN
    4. Ajouter aux secrets GitHub
  - Notes:
    - Gratuit pour projets open source
    - **⚠️ Java 25 NON SUPPORTÉ** : SonarCloud ne supporte pas officiellement Java 25 (janvier 2026). Bloqué par ECJ (Eclipse Compiler for Java). Support prévu **mi-2026**, première LTA probablement **SonarQube 2027.1**.
    - **Workarounds** :
      1. `-Dsonar.scanner.jreProvisioning=disabled` (évite conflit JRE 17 provisionné vs JDK 25)
      2. Si code n'utilise pas de syntaxe Java 25, analyse fonctionnera avec warnings
      3. Définir `sonar.java.source=24` si compatible avec code Java 24

---

- [ ] **Task 4: Configure GitHub Secrets**
  - Location: Settings → Secrets and variables → Actions
  - Action: Ajouter les secrets requis
    | Secret | Source | Obligatoire |
    |--------|--------|-------------|
    | `SONAR_TOKEN` | sonarcloud.io → My Account → Security | Oui |
    | `NVD_API_KEY` | nvd.nist.gov (gratuit, accélère OWASP) | Recommandé |
  - Notes:
    - GITHUB_TOKEN est automatique
    - **NVD_API_KEY fortement recommandé** : Sans clé, téléchargement initial de la base NVD prend des heures ; avec clé ~20 minutes. Demander sur nvd.nist.gov/developers/request-an-api-key
    - **OSS Index** : Depuis septembre 2025, requiert authentification. Sans credentials, l'analyseur se désactive automatiquement (voir Phase 2B)

---

- [ ] **Task 5: Configure Branch Protection Rules**
  - Location: Settings → Branches → Add branch protection rule
  - Action: Configurer pour `main`
    ```
    Branch name pattern: main

    ☑ Require a pull request before merging
      ☑ Require approvals: 1

    ☑ Require status checks to pass before merging
      ☐ Require branches to be up to date before merging (optionnel pour dev solo)
      Status checks: "Alexandria CI Pipeline"

    ☑ Require conversation resolution before merging

    ☑ Do not allow bypassing the above settings
    ```text

  - Notes:
    - Exécuter le workflow une fois sur `main` avant d'ajouter le status check
    - Le check "Alexandria CI Pipeline" apparaîtra après la première exécution
    - **Développeur solo** : "Require branches to be up to date" peut être désactivé pour éviter les "merge trains" qui ajoutent de l'overhead sans bénéfice significatif. Désactiver "Include administrators" permet de merger après CI sans review (vous ne pouvez pas approuver vos propres PRs)
    - **Limitation workflow_dispatch** : Les runs déclenchés par `workflow_dispatch` ne reportent pas automatiquement les status checks aux PRs (les checks sont associés aux commits, pas aux PRs). Le pattern `push` + `pull_request` est préférable. Pour workflow_dispatch, utiliser LouisBrunner/checks-action pour créer manuellement les check runs

---

## Estimation temps d'exécution CI

| Job | Temps estimé |
|-----|-------------|
| Build + Unit Tests | ~3-4 min |
| Integration Tests (Testcontainers) | ~5-7 min |
| SonarCloud | ~4-5 min |
| CodeQL | ~8-12 min |
| OWASP + Trivy | ~5-8 min |
| Docker Build + Scan | ~3-5 min |
| **Total (parallélisé)** | **~12-15 min** |

Jobs parallèles après init: build, integration, sonar, codeql, security.
Docker build séquentiel après tests unitaires.

## Critères de validation

- [ ] Workflow exécuté avec succès sur une PR de test
- [ ] Status check bloque le merge si tests échouent
- [ ] Rapports SonarCloud visibles sur sonarcloud.io
- [ ] Alertes sécurité visibles dans GitHub Security tab
- [ ] Dependabot crée des PRs de mise à jour
