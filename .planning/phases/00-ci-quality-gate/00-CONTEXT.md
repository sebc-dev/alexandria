# Phase 0: CI & Quality Gate - Context

**Gathered:** 2026-02-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Set up a CI pipeline (local + GitHub Actions) that runs quality gates on the codebase: unit tests, integration tests, mutation testing, dead code/bug detection, code coverage analysis, and architecture tests. This phase creates the quality infrastructure that all subsequent phases build under. No application code is written here — only build tooling, CI configuration, and quality scripts.

</domain>

<decisions>
## Implementation Decisions

### Outillage par quality gate
- **Unit & integration tests**: JUnit 5 (standard Spring Boot)
- **Code coverage**: JaCoCo (generates reports consumed by SonarCloud)
- **Mutation testing**: PIT (pitest) — standard Java mutation testing
- **Bug/dead code detection**: SpotBugs — analyse bytecode, mode warn (ne bloque pas le build)
- **Architecture tests**: ArchUnit — tests de contraintes de dépendances entre packages
- **Dashboard qualité**: SonarCloud (SaaS gratuit open-source) — consomme les rapports JaCoCo, centralise métriques

### Seuils et politique d'échec
- **Philosophie**: Les outils de qualité sont des instruments d'analyse, PAS des contraintes bloquantes
- **JaCoCo coverage**: Aucun seuil bloquant — la couverture est un outil d'analyse, pas une contrainte de merge
- **SpotBugs**: Mode avertissement (warn) — reporte les bugs sans bloquer le build
- **PIT mutation score**: Pas de seuil bloquant — le score est reporté pour analyse, pas pour bloquer
- **SonarCloud Quality Gate**: Custom (pas "Sonar way" par défaut) — adaptée à la philosophie non-bloquante
- **Ce qui bloque le merge**: Uniquement les tests unitaires et d'intégration qui échouent

### Workflow GitHub Actions
- **Structure**: Jobs parallèles — chaque quality gate dans son propre job (tests, SpotBugs, PIT, SonarCloud)
- **Triggers**: Push sur main + PR ciblant main
- **Tests d'intégration**: Testcontainers (PostgreSQL+pgvector démarré automatiquement pendant les tests)
- **Caching**: gradle-build-action pour le cache automatique des dépendances et du build Gradle

### Reporting et visibilité
- **Dashboard central**: SonarCloud avec commentaires automatiques sur les PR (résumé couverture, bugs, code smells)
- **Artefacts GitHub Actions**: Rapports HTML publiés (JaCoCo, PIT, SpotBugs) en plus de SonarCloud
- **Rapport PIT**: Publié comme artefact GitHub téléchargeable

### Scripts locaux pour Claude Code
- **Un script unique `quality.sh`** avec sous-commandes: `test`, `mutation`, `spotbugs`, `arch`, `coverage`, `all`
- **Sortie texte console résumée** — concise, lisible directement par Claude Code pour économiser les tokens
- **Ciblage par package/fichier** supporté — ex: `./quality.sh test --package com.alexandria.search`
- **Usage principal**: Permettre à Claude Code de lancer rapidement une partie ciblée de la quality gate et obtenir un rapport concis

### Claude's Discretion
- Configuration exacte de la custom Quality Gate SonarCloud
- Structure interne du script quality.sh (parsing d'arguments, formatage de sortie)
- Configuration SpotBugs (quels détecteurs activer/désactiver)
- Règles ArchUnit initiales (conventions de nommage, dépendances entre couches)
- Séparation tests unitaires vs intégration (convention de nommage ou source sets)

</decisions>

<specifics>
## Specific Ideas

- Claude Code doit pouvoir lancer `./quality.sh test` ou `./quality.sh mutation --package com.alexandria.search` et obtenir un résumé textuel concis sans avoir à parser des rapports HTML volumineux
- SonarCloud choisi plutôt que SonarQube self-hosted pour zéro infra supplémentaire
- La CI est un filet de sécurité analytique, pas un mur bloquant — seuls les tests cassés bloquent

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 00-ci-quality-gate*
*Context gathered: 2026-02-14*
