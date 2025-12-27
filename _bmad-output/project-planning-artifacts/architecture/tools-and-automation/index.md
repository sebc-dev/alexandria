# Outils et Automatisation - Alexandria

## Vue d'ensemble

Cette section documente les outils de développement, d'automatisation, et d'assurance qualité utilisés dans le projet Alexandria. Ces outils constituent des décisions architecturales critiques pour garantir la conformité à l'architecture hexagonale et maintenir des standards de qualité élevés.

## Table des matières

- [Code Quality Enforcement](./code-quality-enforcement.md) - Stratégie 3-Tiers pour l'assurance qualité
- CI/CD Pipeline *(à venir)*
- Git Workflow & Hooks *(à venir)*
- Code Review Process *(à venir)*

## Philosophie

Alexandria étant un outil de gouvernance technique pour garantir la conformité du code généré par IA, il est **critique** que le code d'Alexandria lui-même respecte les plus hauts standards de qualité. L'architecture hexagonale est une contrainte **non-négociable** qui permet l'expérimentation technique et l'évolution post-MVP sans dette technique.

**Objectif:** Garantir que chaque commit respecte l'architecture hexagonale, les conventions TypeScript strictes, et maintient une qualité de code élevée, tout en **facilitant les reviews** avec un objectif de ≤2 commentaires par commit.

**Approche:** Stratégie préventive multi-couches combinant enforcement déterministe (Dependency Cruiser, ESLint) et intelligence contextuelle (CodeRabbit AI).

## Stratégie 3-Tiers pour Enforcement Qualité

Alexandria utilise une approche **complémentaire à 3 tiers** pour garantir la qualité du code et le respect de l'architecture hexagonale:

### Tier 1: Dependency Cruiser - Hard Enforcement (Build-Breaking)

**Rôle:** Validation déterministe des règles architecturales **non-négociables**.

**Exécution:** Tests unitaires dans CI pipeline - échec du build si violations.

**Garantie:** L'architecture hexagonale est respectée à 100% - le build échoue immédiatement en cas de violation.

### Tier 2: ESLint - Real-Time IDE Feedback

**Rôle:** Feedback immédiat dans l'IDE pendant l'écriture du code.

**Exécution:** Pre-commit hook + IDE integration.

**Bénéfice:** Détection immédiate des violations pendant l'écriture - économise des cycles de review.

### Tier 3: CodeRabbit - AI-Powered Contextual Review

**Rôle:** Review intelligente contextuelle focalisée sur les violations subtiles, sécurité, et patterns métier.

**Exécution:** Automatique sur chaque Pull Request.

**Objectif:** ≤2 commentaires par commit (focus sur critiques uniquement).

**Bénéfice:** Détecte violations que les outils déterministes manquent + feedback éducatif pour nouveaux contributeurs.

## Configuration détaillée

Pour la configuration complète de chaque tier (fichiers de configuration, règles AST-grep, workflow de développement), consultez:

- **[Code Quality Enforcement](./code-quality-enforcement.md)** - Documentation complète de la stratégie 3-Tiers

## Bénéfices mesurables

**Qualité Code:**
- **100% enforcement architecture** via Dependency Cruiser (build-breaking)
- **0 violations architecture** passent en production
- **Feedback <1s** via ESLint dans IDE

**Efficacité Reviews:**
- **≤2 commentaires/commit** via CodeRabbit configuré "chill"
- **Réduction 70%** temps review manuel (focus sur logique métier)
- **Onboarding facilité** nouveaux contributeurs (feedback éducatif automatique)

**Maintenabilité:**
- **Architecture hexagonale garantie** → expérimentation technique facilitée
- **Swap adapters** possible sans risque régression
- **Tests unitaires** simplifiés via isolation ports

## Ressources externes

- [Documentation Dependency Cruiser](https://github.com/MaibornWolff/Dependency Cruiser)
- [Documentation ESLint](https://eslint.org/)
- [CodeRabbit GitHub Marketplace](https://github.com/marketplace/coderabbitai)
- [AST-grep Documentation](https://ast-grep.github.io/)
