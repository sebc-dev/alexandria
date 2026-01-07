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
