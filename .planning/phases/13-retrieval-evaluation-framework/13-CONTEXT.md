# Phase 13: Retrieval Evaluation Framework - Context

**Gathered:** 2026-02-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Rendre la qualité de recherche mesurable et traçable avec un golden set et des métriques IR standard, AVANT tout changement de pipeline. Ce framework servira de baseline pour les phases 14-18.

</domain>

<decisions>
## Implementation Decisions

### Construction du golden set
- 100 requêtes annotées, générées par IA (Claude Code + Gemini) à partir de la documentation Spring Boot
- Jugements de pertinence gradués 0-2 (0=non pertinent, 1=partiellement pertinent, 2=très pertinent)
- Annotations de pertinence générées par IA également (pas de review humaine)
- Golden set stocké en JSON (structuré avec queries, documents attendus et scores)
- Spring Boot comme documentation de référence unique — sert à la fois pour le golden set et comme source de test pour tous les tests du projet

### Format des résultats
- Deux CSV par run : un agrégé (métriques globales) + un détaillé (par requête)
- Le CSV détaillé inclut les chunks retournés : query + chunk_id + score + rang + jugement de pertinence
- Nommage : timestamp ISO + label descriptif (ex: `eval-results-2026-02-21T14-30-00-baseline.csv`)
- Stockage hors projet (pas dans git) — répertoire configurable (ex: `~/.alexandria/eval/`)

### Comportement des tests
- Seuils pass/fail configurables dans application properties (pas en dur dans le code)
- Test paramétré à la demande uniquement (@Tag("eval")), pas dans le build CI normal
- En cas d'échec : le test fail ET génère le CSV détaillé avec les requêtes problématiques
- Métriques calculées à plusieurs profondeurs : k=5, k=10, k=20

### Couverture des types de requêtes
- 4 types : factual, conceptual, code lookup, troubleshooting
- Répartition pondérée usage réel : ~30 code, ~30 factual, ~25 conceptual, ~15 troubleshooting
- Métriques ventilées par type + score global (détecte les trade-offs cachés entre phases)

### Claude's Discretion
- Choix des pages Spring Boot à crawler pour la source de test
- Structure interne de la classe RetrievalMetrics
- Répertoire par défaut pour les CSV hors projet
- Algorithme de génération des requêtes par IA

</decisions>

<specifics>
## Specific Ideas

- La doc Spring Boot sert de source unique pour le golden set ET pour tous les tests d'intégration du projet
- Les métriques par type permettent de vérifier que les changements de pipeline (phases 14-15) n'améliorent pas un type au détriment d'un autre
- Le label dans le nom de fichier CSV permet de tagger les runs (baseline, post-chunking, post-fusion, etc.) pour comparaison directe

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 13-retrieval-evaluation-framework*
*Context gathered: 2026-02-21*
