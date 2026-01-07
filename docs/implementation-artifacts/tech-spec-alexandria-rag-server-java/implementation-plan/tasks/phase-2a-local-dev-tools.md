# Phase 2A: Local Dev Tools

> **Note**: Cette phase n'utilise pas de TDD. Les fichiers de configuration sont validés par leur fonctionnement.

## Objectif

Configurer l'environnement de développement local pour un feedback instantané pendant l'itération avec Claude Code.

## Prérequis

- Phase 1 complétée (structure Maven fonctionnelle)
- VS Code installé

---

## ℹ️ Compatibilité Java 25 - Janvier 2026

### Spotless corrigé pour Java 25 (Issue #2468 RÉSOLUE)

**L'issue Spotless #2468 est FERMÉE.** Le problème `NoSuchMethodError` causé par Java 25 changeant `DeferredDiagnosticHandler::getDiagnostics` est corrigé via **google-java-format 1.28.0+**, que Spotless 3.1.0 bundle par défaut.

**Configuration recommandée:** Définir explicitement google-java-format 1.33.0 dans la configuration Spotless pour une compatibilité maximale.

**Breaking changes Spotless 3.x** (depuis 2.43.0):
- Java minimum passé à **17** (depuis 11)
- `removeWildcardImports` renommé en `forbidWildcardImports`
- Version google-java-format par défaut est maintenant 1.28.0+

### Versions validées Janvier 2026

| Outil | Version | Java 25 | Notes |
|-------|---------|---------|-------|
| Spotless Maven Plugin | 3.1.0 | ✅ | Fixé via GJF 1.28.0+ |
| google-java-format | 1.33.0 | ✅ | Depuis 1.28.0. ⚠️ Requiert JDK 21+ depuis 1.29.0 |
| Checkstyle | **13.0.0** | ✅ Complet | ⚠️ Requiert JDK 21 minimum |
| PMD | 7.20.0 | ✅ Complet | Support Java 25 depuis 7.16.0 |
| SpotBugs | 4.9.8 | ✅ | Via ASM 9.9, BCEL 6.11.0 |
| FindSecBugs | 1.14.0 | ✅ | Testé avec SpotBugs 4.9.x |
| Error Prone | 2.45.0 | ✅ | ⚠️ Requiert JDK 21 minimum |
| Pitest | 1.22.0 | ✅ | Via ASM 9.9 |
| pitest-junit5-plugin | 1.2.3 | ✅ | Pour JUnit 5 |

### ⚠️ Limitations connues

| Composant | Statut Java 25 | Impact |
|-----------|----------------|--------|
| SonarQube for IDE | ❌ Java 8-24 seulement | Analyse locale limitée, support prévu mi-2026 |
| SonarCloud | ❌ Java 8-24 seulement | Utiliser `sonar.java.source=24` si code compatible |
| Checkstyle for Java (VS Code) | ⚠️ Extension non maintenue depuis 2023 | Configurer version via `java.checkstyle.version` |

---

## Philosophie du workflow

```text
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

---

- [ ] **Task 1: Configure VS Code Settings**
  - File: `.vscode/settings.json`
  - Action: Créer la configuration projet
    ```json
    {
        // ════════════════════════════════════════════════════════════════
        // Java Settings
        // ⚠️ OBSOLÈTE: L'URL eclipse-java-google-style.xml est marquée obsolète
        // (GitHub Issue #687, non mise à jour depuis 4+ ans).
        // Google recommande d'utiliser google-java-format directement.
        // Conservée ici pour compatibilité VS Code, mais Spotless fait foi.
        // ════════════════════════════════════════════════════════════════

        // Formatage Google Java Style (fallback VS Code)
        "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
        "java.format.settings.profile": "GoogleStyle",

        // Format on save (cohérent avec Spotless)
        "editor.formatOnSave": true,
        "editor.formatOnPaste": false,

        // Analyse null automatique
        "java.compile.nullAnalysis.mode": "automatic",

        // Mise à jour auto de la config build
        "java.configuration.updateBuildConfiguration": "automatic",

        // ════════════════════════════════════════════════════════════════
        // Checkstyle
        // ⚠️ Note: "/google_checks.xml" n'est PAS un chemin valide.
        // Utiliser l'URL officielle ou le preset "Google's Check" via commande.
        // ════════════════════════════════════════════════════════════════
        "java.checkstyle.configuration": "https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/google_checks.xml",
        "java.checkstyle.version": "13.0.0",

        // ════════════════════════════════════════════════════════════════
        // SonarLint
        // ════════════════════════════════════════════════════════════════
        "sonarlint.rules": {
            // Désactiver TODO warnings (trop de bruit)
            "java:S1135": { "level": "off" },
            // Désactiver "add serialVersionUID" (pas de serialization)
            "java:S2057": { "level": "off" }
        },
        "sonarlint.output.showVerboseLogs": false,

        // ════════════════════════════════════════════════════════════════
        // Files
        // ════════════════════════════════════════════════════════════════
        "files.exclude": {
            "**/target": true,
            "**/.git": true,
            "**/.idea": true,
            "**/*.iml": true
        },

        "files.watcherExclude": {
            "**/target/**": true
        },

        // ════════════════════════════════════════════════════════════════
        // Editor
        // ════════════════════════════════════════════════════════════════
        "editor.rulers": [100],
        "editor.tabSize": 2,
        "editor.insertSpaces": true,
        "editor.detectIndentation": false,

        // ════════════════════════════════════════════════════════════════
        // Error Lens (affichage inline des erreurs)
        // ════════════════════════════════════════════════════════════════
        "errorLens.enabled": true,
        "errorLens.enabledDiagnosticLevels": ["error", "warning"],
        "errorLens.messageMaxChars": 100,

        // ════════════════════════════════════════════════════════════════
        // Terminal
        // ════════════════════════════════════════════════════════════════
        "terminal.integrated.defaultProfile.linux": "bash",
        "terminal.integrated.scrollback": 10000,

        // ════════════════════════════════════════════════════════════════
        // Search
        // ════════════════════════════════════════════════════════════════
        "search.exclude": {
            "**/target": true,
            "**/node_modules": true,
            "**/.git": true
        }
    }
    ```
  - Notes: Le formatage VS Code doit être cohérent avec Spotless (Google Java Format)

---

- [ ] **Task 2: Document Required VS Code Extensions**
  - File: `.vscode/extensions.json`
  - Action: Créer les recommandations d'extensions
    ```json
    {
        "recommendations": [
            "vscjava.vscode-java-pack",
            "oracle.oracle-java",
            "sonarsource.sonarlint-vscode",
            "shengchen.vscode-checkstyle",
            "usernamehw.errorlens"
        ]
    }
    ```
  - Extensions détaillées:
    | Extension | ID | Version | Rôle | Java 25 |
    |-----------|-----|---------|------|---------|
    | **Oracle Java Extension** | `oracle.oracle-java` | 25.0.0 | Support Java 25 complet (preview inclus) | ✅ **Recommandé** |
    | Extension Pack for Java | `vscjava.vscode-java-pack` | 0.30.x | Support Java complet | ⚠️ Dépend de Red Hat |
    | SonarQube for IDE | `sonarsource.sonarlint-vscode` | 4.39.0 | Analyse temps réel | ❌ Java 8-24 seulement |
    | Checkstyle for Java | `shengchen.vscode-checkstyle` | 1.4.2 | Vérification style | ⚠️ Dernière release mars 2023 |
    | Error Lens | `usernamehw.errorlens` | 3.26.0 | Erreurs inline | ✅ Language-agnostic |
  - Notes:
    - **Oracle Java Extension** : Basé sur Apache NetBeans 27 (8 déc. 2025). Supporte toutes les fonctionnalités Java 25 y compris preview. **Recommandé pour Java 25.**
    - **SonarQube for IDE** : Renommé depuis SonarLint, requiert JRE 17+. **Ne supporte PAS Java 25** (analyzer limité à Java 8-24, support prévu mi-2026)
    - **Checkstyle for Java** : Dernière release en mars 2023. Configurer `java.checkstyle.version: "13.0.0"` manuellement. 40 issues ouvertes.

---

- [ ] **Task 3: Configure JVM for Error Prone**
  - File: `.mvn/jvm.config`
  - Action: Ajouter les exports JVM requis pour Error Prone et google-java-format sur Java 25

    ```bash
    --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
    --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
    --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
    ```
  - Notes:
    - Requis pour Error Prone 2.43.0+ sur JDK 16+
    - Error Prone 2.43.0 a augmenté le runtime JDK minimum de 17 à 21
    - Sans ces exports, Error Prone échoue silencieusement ou avec des erreurs cryptiques

---

- [ ] **Task 4: Create Makefile**
  - File: `Makefile`
  - Action: Simplifier les commandes Maven fréquentes
    ```makefile
    # Makefile pour Alexandria
    # Simplifie les commandes Maven fréquentes

    .PHONY: help dev compile test test-it analyse pit full-check format clean install-hooks

    # Couleurs
    CYAN := \033[0;36m
    GREEN := \033[0;32m
    YELLOW := \033[1;33m
    NC := \033[0m

    help: ## Affiche cette aide
    	@echo ""
    	@echo "$(CYAN)Alexandria - Commandes disponibles$(NC)"
    	@echo ""
    	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-15s$(NC) %s\n", $$1, $$2}'
    	@echo ""

    # ============================================================
    # Développement quotidien
    # ============================================================

    dev: ## Compilation rapide (profil dev)
    	@echo "$(YELLOW)Compiling...$(NC)"
    	@mvn compile -q
    	@echo "$(GREEN)✓ Done$(NC)"

    compile: ## Compilation avec Error Prone
    	@echo "$(YELLOW)Compiling with Error Prone...$(NC)"
    	@mvn compile -Perror-prone
    	@echo "$(GREEN)✓ Done$(NC)"

    test: ## Tests unitaires uniquement
    	@echo "$(YELLOW)Running unit tests...$(NC)"
    	@mvn test -Dtest='!**/*IT.java'

    test-it: ## Tests d'intégration (Testcontainers)
    	@echo "$(YELLOW)Running integration tests...$(NC)"
    	@mvn verify -DskipUnitTests

    test-all: ## Tous les tests
    	@echo "$(YELLOW)Running all tests...$(NC)"
    	@mvn verify

    # ============================================================
    # Analyse de code
    # ============================================================

    analyse: ## Analyse statique (PMD + SpotBugs + Checkstyle)
    	@echo "$(YELLOW)Running static analysis...$(NC)"
    	@mvn verify -Pstatic-analysis

    pmd: ## PMD uniquement
    	@mvn pmd:check pmd:cpd-check

    spotbugs: ## SpotBugs uniquement
    	@mvn compile spotbugs:check

    spotbugs-gui: ## SpotBugs avec interface graphique
    	@mvn compile spotbugs:gui

    checkstyle: ## Checkstyle uniquement
    	@mvn checkstyle:check

    pit: ## Mutation testing (PIT) - ~10-15 minutes
    	@echo "$(YELLOW)Running mutation testing (this takes 8-15 minutes)...$(NC)"
    	@mvn test-compile org.pitest:pitest-maven:mutationCoverage -DwithHistory

    # ============================================================
    # Validation complète
    # ============================================================

    full-check: ## Analyse complète avant PR (inclut PIT)
    	@./scripts/full-analysis.sh

    quick-check: ## Analyse rapide (sans PIT)
    	@./scripts/full-analysis.sh --skip-pit

    # ============================================================
    # Formatage
    # ============================================================

    format: ## Applique le formatage Spotless
    	@echo "$(YELLOW)Formatting code...$(NC)"
    	@mvn spotless:apply -q
    	@echo "$(GREEN)✓ Code formatted$(NC)"

    format-check: ## Vérifie le formatage sans modifier
    	@mvn spotless:check

    # ============================================================
    # Sécurité
    # ============================================================

    security: ## Scan OWASP des dépendances
    	@echo "$(YELLOW)Running OWASP Dependency-Check...$(NC)"
    	@mvn org.owasp:dependency-check-maven:check -Dformat=HTML
    	@echo "$(GREEN)Report: target/dependency-check-report.html$(NC)"

    # ============================================================
    # Utilitaires
    # ============================================================

    clean: ## Nettoie les fichiers générés
    	@mvn clean -q
    	@echo "$(GREEN)✓ Cleaned$(NC)"

    install-hooks: ## Installe les Git hooks
    	@echo "$(YELLOW)Installing Git hooks...$(NC)"
    	@mkdir -p .git/hooks
    	@cp hooks/pre-commit .git/hooks/pre-commit
    	@chmod +x .git/hooks/pre-commit
    	@echo "$(GREEN)✓ Hooks installed$(NC)"

    deps: ## Affiche l'arbre des dépendances
    	@mvn dependency:tree

    deps-updates: ## Vérifie les mises à jour disponibles
    	@mvn versions:display-dependency-updates

    # ============================================================
    # CI local
    # ============================================================

    ci-local: ## Simule le build CI en local
    	@echo "$(YELLOW)Running CI build locally...$(NC)"
    	@mvn clean verify -Pci

    # ============================================================
    # Rapports
    # ============================================================

    reports: analyse ## Génère tous les rapports d'analyse
    	@echo ""
    	@echo "$(CYAN)Reports generated:$(NC)"
    	@echo "  • PMD:        target/pmd.html"
    	@echo "  • CPD:        target/cpd.html"
    	@echo "  • SpotBugs:   target/spotbugsXml.xml"
    	@echo "  • Checkstyle: target/checkstyle-result.xml"

    open-pmd: ## Ouvre le rapport PMD dans le navigateur
    	@xdg-open target/pmd.html 2>/dev/null || open target/pmd.html 2>/dev/null || echo "Open target/pmd.html manually"

    open-pit: ## Ouvre le rapport PIT dans le navigateur
    	@xdg-open target/pit-reports/index.html 2>/dev/null || open target/pit-reports/index.html 2>/dev/null || echo "Open target/pit-reports/index.html manually"
    ```
  - Notes: `make help` affiche toutes les commandes disponibles

---

- [ ] **Task 5: Create Pre-commit Hook**
  - File: `hooks/pre-commit`
  - Action: Hook Git léger pour validation avant commit
    ```bash
    #!/bin/bash
    # hooks/pre-commit
    # Hook Git léger pour validation rapide avant commit
    # Installé automatiquement par le plugin Cosium ou manuellement

    set -e

    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    NC='\033[0m'

    echo -e "${YELLOW}🔍 Pre-commit checks...${NC}"

    # Vérifier qu'on est dans un projet Maven
    if [ ! -f "pom.xml" ]; then
        echo -e "${RED}❌ Not a Maven project (pom.xml not found)${NC}"
        exit 1
    fi

    # 1. Formatage des fichiers staged
    echo "  → Formatting staged Java files..."

    # Récupérer les fichiers Java staged
    STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep '\.java$' || true)

    if [ -n "$STAGED_JAVA" ]; then
        # Appliquer Spotless
        if ! mvn spotless:apply -q; then
            echo -e "${RED}❌ Spotless formatting failed${NC}"
            exit 1
        fi

        # Re-stage les fichiers modifiés par Spotless
        echo "$STAGED_JAVA" | xargs git add

        echo -e "  ${GREEN}✓ Formatted${NC}"
    else
        echo "  → No Java files staged, skipping format"
    fi

    # 2. Checkstyle rapide (uniquement si des fichiers Java sont staged)
    if [ -n "$STAGED_JAVA" ]; then
        echo "  → Running Checkstyle..."
        if ! mvn checkstyle:check -q; then
            echo -e "${RED}❌ Checkstyle violations found${NC}"
            echo ""
            echo "Run 'mvn checkstyle:check' for details"
            exit 1
        fi
        echo -e "  ${GREEN}✓ Checkstyle passed${NC}"
    fi

    echo ""
    echo -e "${GREEN}✅ Pre-commit passed!${NC}"
    ```
  - Notes: S'installe automatiquement via plugin Cosium ou `make install-hooks`
  - Bonnes pratiques de sécurité pour les hooks:
    | Pratique | Recommandation |
    |----------|----------------|
    | Gestion des chemins | Utiliser des chemins absolus; éviter les chemins relatifs qui permettent des attaques de traversée |
    | Validation des entrées | Assainir toutes les entrées externes; ne jamais exécuter directement des données fournies par l'utilisateur |
    | Permissions fichiers | Définir les hooks à 700 ou 755; restreindre l'accès en modification |
    | Secrets | Ne jamais coder en dur les credentials; utiliser des variables d'environnement |
    | Contrôle de version | Stocker les hooks dans `hooks/` avec configuration `core.hooksPath` |
    | Appels réseau | Valider et restreindre tout accès réseau externe |

---

- [ ] **Task 6: Create Full Analysis Script**
  - File: `scripts/full-analysis.sh`
  - Action: Script d'analyse complète pre-push
    ```bash
    #!/bin/bash
    # scripts/full-analysis.sh
    # Analyse complète locale incluant mutation testing (PIT)
    # Exécuter avant de créer/mettre à jour une PR

    # Bash strict mode - Best practice recommandée
    set -euo pipefail

    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    NC='\033[0m'

    # Cleanup trap pour nettoyage en cas d'erreur
    cleanup() {
        # Ajouter ici le nettoyage si nécessaire
        :
    }
    trap cleanup EXIT ERR

    # Parse arguments
    SKIP_PIT=false
    VERBOSE=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-pit)
                SKIP_PIT=true
                shift
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --skip-pit    Skip mutation testing (saves 8-15 minutes)"
                echo "  --verbose     Show detailed output"
                echo "  --help        Show this help"
                exit 0
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    MVN_QUIET=""
    if [ "$VERBOSE" = false ]; then
        MVN_QUIET="-q"
    fi

    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}         ALEXANDRIA - Full Local Analysis Pipeline              ${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""

    START_TIME=$(date +%s)

    # Compteur d'étapes
    if [ "$SKIP_PIT" = true ]; then
        TOTAL_STEPS=5
    else
        TOTAL_STEPS=6
    fi
    CURRENT_STEP=0

    next_step() {
        CURRENT_STEP=$((CURRENT_STEP + 1))
        echo -e "${YELLOW}[$CURRENT_STEP/$TOTAL_STEPS] $1${NC}"
    }

    # ============================================================
    # Étape 1: Compilation avec Error Prone
    # ============================================================
    next_step "Compiling with Error Prone..."
    if mvn clean compile -Perror-prone $MVN_QUIET; then
        echo -e "${GREEN}  ✓ Compilation OK${NC}"
    else
        echo -e "${RED}❌ Compilation failed${NC}"
        echo ""
        echo "Fix compilation errors before proceeding."
        exit 1
    fi

    # ============================================================
    # Étape 2: Tests unitaires
    # ============================================================
    next_step "Running unit tests..."
    if mvn test -Dtest='!**/*IT.java' $MVN_QUIET; then
        echo -e "${GREEN}  ✓ Unit tests OK${NC}"
    else
        echo -e "${RED}❌ Unit tests failed${NC}"
        echo ""
        echo "Check test failures in target/surefire-reports/"
        exit 1
    fi

    # ============================================================
    # Étape 3: PMD + CPD (Copy-Paste Detection)
    # ============================================================
    next_step "Running PMD analysis..."
    if mvn pmd:check pmd:cpd-check $MVN_QUIET; then
        echo -e "${GREEN}  ✓ PMD OK${NC}"
    else
        echo -e "${RED}❌ PMD violations found${NC}"
        echo ""
        echo "Run 'mvn pmd:pmd' for HTML report at target/pmd.html"
        exit 1
    fi

    # ============================================================
    # Étape 4: SpotBugs
    # ============================================================
    next_step "Running SpotBugs..."
    if mvn spotbugs:check $MVN_QUIET; then
        echo -e "${GREEN}  ✓ SpotBugs OK${NC}"
    else
        echo -e "${RED}❌ SpotBugs issues found${NC}"
        echo ""
        echo "Run 'mvn spotbugs:gui' for interactive review"
        exit 1
    fi

    # ============================================================
    # Étape 5: Checkstyle
    # ============================================================
    next_step "Running Checkstyle..."
    if mvn checkstyle:check $MVN_QUIET; then
        echo -e "${GREEN}  ✓ Checkstyle OK${NC}"
    else
        echo -e "${RED}❌ Checkstyle violations found${NC}"
        echo ""
        echo "Check target/checkstyle-result.xml for details"
        exit 1
    fi

    # ============================================================
    # Étape 6: Mutation Testing (PIT) - Optionnel
    # ============================================================
    if [ "$SKIP_PIT" = false ]; then
        next_step "Running PIT Mutation Testing..."
        echo -e "  ${CYAN}(This may take 8-15 minutes - use --skip-pit to skip)${NC}"

        PIT_START=$(date +%s)

        if mvn test-compile org.pitest:pitest-maven:mutationCoverage \
            -DwithHistory=true \
            -Dthreads=4 \
            -DtimestampedReports=false $MVN_QUIET; then

            PIT_END=$(date +%s)
            PIT_DURATION=$((PIT_END - PIT_START))
            echo -e "${GREEN}  ✓ Mutation testing OK (${PIT_DURATION}s)${NC}"
        else
            echo -e "${RED}❌ Mutation testing failed threshold${NC}"
            echo ""
            echo "Report: target/pit-reports/index.html"
            echo ""
            echo "Low mutation score indicates tests don't catch all bugs."
            echo "Consider adding more assertions or edge case tests."
            exit 1
        fi
    fi

    # ============================================================
    # Résumé final
    # ============================================================
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    MINUTES=$((DURATION / 60))
    SECONDS=$((DURATION % 60))

    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✅ All local analyses passed!${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "Duration: ${CYAN}${MINUTES}m ${SECONDS}s${NC}"
    echo ""
    echo "Reports generated:"
    echo "  • PMD:           target/pmd.html"
    echo "  • CPD:           target/cpd.html"
    echo "  • SpotBugs:      target/spotbugsXml.xml"
    echo "  • Checkstyle:    target/checkstyle-result.xml"
    if [ "$SKIP_PIT" = false ]; then
        echo "  • PIT Mutations: target/pit-reports/index.html"
    fi
    echo ""
    echo -e "${GREEN}Ready to create/update your PR!${NC}"
    echo ""
    ```
  - Notes: Utiliser `--skip-pit` pour une analyse rapide sans mutation testing
  - Bash strict mode expliqué:
    | Flag | Objectif |
    |------|----------|
    | `-e` (errexit) | Quitte immédiatement sur retour non-zero |
    | `-u` (nounset) | Traite les variables non définies comme erreurs |
    | `-o pipefail` | Le pipeline retourne le code d'erreur du premier échec |
  - Caveat: L'expansion arithmétique `((counter++))` retourne 1 quand le résultat est 0, déclenchant errexit. Utiliser `((counter++)) || true` pour les compteurs commençant à zéro.

---

- [ ] **Task 7: Update .gitignore**
  - File: `.gitignore`
  - Action: Ajouter les entrées pour les caches d'analyse
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
  - Notes: `.vscode/` dans .gitignore car settings.json est spécifique au projet (à enlever si on veut partager)

---

## Résumé des temps d'exécution

| Commande | Description | Durée |
|----------|-------------|-------|
| `make dev` | Compilation rapide | ~5s |
| `make test` | Tests unitaires | ~30s |
| `make format` | Formatage Spotless | ~2s |
| Pre-commit hook | Spotless + Checkstyle | ~5s |
| `make quick-check` | Analyse sans PIT | ~3-5 min |
| `make full-check` | Analyse complète + PIT | ~15-20 min |

---

## Checklist développeur

### Pendant le développement avec Claude Code
- [ ] SonarQube for IDE actif dans VS Code (erreurs inline, limité Java 8-24)
- [ ] Compilation rapide avec `make dev`
- [ ] Format on save activé
- [ ] Oracle Java Extension installée pour support Java 25 complet (recommandé)

### Avant chaque commit
- [ ] Pre-commit hook s'exécute automatiquement
- [ ] Code formaté par Spotless

### Avant de créer/mettre à jour une PR
- [ ] `make full-check` passé (ou `make quick-check` pour itérations rapides)
- [ ] Mutation testing vérifié si code critique

---

## Critères de validation

- [ ] VS Code formate automatiquement on save
- [ ] SonarQube for IDE détecte les problèmes en temps réel (Java 8-24 seulement)
- [ ] `.mvn/jvm.config` présent avec les exports Error Prone
- [ ] Pre-commit hook bloque les commits mal formatés
- [ ] `make help` affiche toutes les commandes
- [ ] `make full-check` passe sans erreur
- [ ] `make compile` utilise Error Prone sans erreur JVM
