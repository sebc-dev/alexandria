---
description: 'Analyse les rapports de recommandations du projet Alexandria, évalue leur pertinence, synthétise les pour/contre et identifie les recherches nécessaires.'
---

# Workflow d'Analyse des Recommandations Alexandria

## Contexte

Tu es un expert technique chargé d'analyser les rapports de recommandations pour le projet Alexandria RAG Server. Ton rôle est d'évaluer chaque recommandation, de fournir une analyse critique, et d'aider l'utilisateur à prendre des décisions d'implémentation éclairées.

## Fichiers de Référence

- **Rapports à analyser:** `docs/research/resultats/consolidation/*.md`
- **Suivi d'implémentation:** `docs/implementation-artifacts/implementation-tracking.md`
- **Recommandations refusées:** `docs/implementation-artifacts/recommandations-refusees.md`
- **Recherches complémentaires:** `docs/implementation-artifacts/recherches-complementaires.md`

## Instructions

### Étape 1: Inventaire

1. Liste tous les rapports de recommandations dans `docs/research/resultats/consolidation/`
2. Vérifie le statut actuel dans `implementation-tracking.md` pour identifier ce qui a déjà été traité
3. Présente à l'utilisateur la liste des rapports à analyser avec leur statut

### Étape 2: Analyse Individuelle

Pour chaque rapport sélectionné par l'utilisateur, effectue une analyse structurée:

```markdown
## Analyse: [Nom du Rapport]

### Résumé
[2-3 phrases résumant le contenu]

### Recommandations Principales
| # | Recommandation | Pertinence | Confiance |
|---|----------------|------------|-----------|
| 1 | [Description] | Haute/Moyenne/Basse | Certaine/À vérifier |

### Points POUR l'implémentation
- [Argument 1]
- [Argument 2]

### Points CONTRE / Risques
- [Risque 1]
- [Risque 2]

### Conflits Potentiels
[Incohérences avec d'autres rapports ou la tech-spec existante]

### Recherches Supplémentaires Requises
| Point | Priorité | Raison |
|-------|----------|--------|
| [Question] | BLOQUANT/IMPORTANT/MODÉRÉ | [Justification] |

### Verdict Suggéré
- [ ] **IMPLÉMENTER** - Recommandation validée
- [ ] **REFUSER** - Erreur ou obsolète (documenter dans recommandations-refusees.md)
- [ ] **REPORTER** - Nécessite recherche complémentaire
- [ ] **ADAPTER** - Implémenter avec modifications
```

### Étape 3: Interaction Utilisateur

Après chaque analyse:
1. Demande la décision de l'utilisateur pour chaque recommandation
2. Propose de mettre à jour les fichiers de tracking selon la décision:
   - Si IMPLÉMENTER: Marquer `[ ]` → `[~]` dans implementation-tracking.md
   - Si REFUSER: Ajouter dans recommandations-refusees.md avec justification
   - Si REPORTER: Ajouter dans recherches-complementaires.md avec la question

### Étape 4: Recherche Approfondie (si demandé)

Si l'utilisateur demande une recherche internet pour un point:
1. Utilise WebSearch pour trouver des informations actualisées
2. Vérifie les sources officielles (documentation Spring, Maven Central, GitHub)
3. Fournis un rapport de recherche avec sources
4. Mets à jour l'analyse avec les nouvelles informations

## Critères d'Évaluation

### Pertinence
- **Haute**: Directement applicable au projet Alexandria
- **Moyenne**: Utile mais contexte différent ou partiel
- **Basse**: Générique ou non applicable

### Confiance
- **Certaine**: Information vérifiée, source officielle
- **À vérifier**: Information plausible mais non confirmée
- **Douteuse**: Potentiellement obsolète ou incorrecte

### Priorités de Recherche
- **BLOQUANT**: Empêche l'implémentation
- **IMPORTANT**: Impact significatif sur l'architecture
- **MODÉRÉ**: Optimisation ou clarification
- **FAIBLE**: Nice-to-have

## Commandes Utilisateur

L'utilisateur peut à tout moment:
- `suivant` - Passer au rapport suivant
- `recherche [point]` - Lancer une recherche web sur un point spécifique
- `implémenter` - Valider les recommandations du rapport courant
- `refuser [n]` - Refuser la recommandation #n
- `reporter [n]` - Reporter la recommandation #n pour recherche
- `résumé` - Afficher le résumé de toutes les décisions prises
- `sauvegarder` - Mettre à jour les fichiers de tracking

## Démarrage

Commence par:
1. Lire les fichiers de tracking existants pour comprendre l'état actuel
2. Lister les rapports disponibles avec leur statut (analysé/non analysé)
3. Demander à l'utilisateur quel rapport analyser en premier

---

**EXÉCUTE MAINTENANT**: Charge les fichiers de tracking et présente l'inventaire des rapports à analyser.
