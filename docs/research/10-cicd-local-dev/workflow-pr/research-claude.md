# Workflow de Gestion des Commentaires PR CodeRabbit

## Vue d'Ensemble

Ce workflow permet de gérer efficacement les commentaires de review générés par CodeRabbit sur les Pull Requests du projet Alexandria. Il utilise une architecture à deux niveaux pour optimiser l'utilisation du contexte et garantir un suivi complet.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CONVERSATION PRINCIPALE                             │
│                    /review-coderabbit-pr [PR_NUMBER]                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  Responsabilités:                                                           │
│  - Orchestration du workflow                                                │
│  - Récupération des commentaires via gh CLI                                 │
│  - Gestion du fichier de tracking (persistance)                             │
│  - Compilation des résultats                                                │
│  - Proposition d'actions groupées                                           │
│  - Application des corrections                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Task tool (parallel)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SUB-AGENT: coderabbit-comment-analyzer                   │
│                         (Contexte isolé - model: haiku)                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  Responsabilités:                                                           │
│  - Analyser UN SEUL commentaire                                             │
│  - Lire le fichier concerné                                                 │
│  - Évaluer la pertinence selon les standards Alexandria                     │
│  - Retourner une décision JSON structurée                                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     FICHIER DE TRACKING (persistance)                       │
│                .claude/pr-reviews/pr-{NUMBER}-tracking.yaml                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  Contenu:                                                                   │
│  - Métadonnées PR                                                           │
│  - Statistiques (accepted, rejected, discussed, deferred)                   │
│  - Liste des commentaires avec analyse                                      │
│  - Historique des actions                                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Avantages de cette Architecture

### 1. Économie de Contexte
- Chaque commentaire est analysé dans un contexte isolé (sub-agent)
- Le workflow principal ne charge que les résultats JSON
- Permet de traiter des PRs avec 50+ commentaires sans saturer le contexte

### 2. Parallélisation
- Les analyses de commentaires sur fichiers différents sont lancées en parallèle
- Réduit significativement le temps total de traitement

### 3. Persistance et Reprise
- Le fichier YAML tracking permet de reprendre une review interrompue
- Idempotence: les commentaires déjà analysés ne sont pas re-traités
- Audit trail complet des décisions

### 4. Coût Optimisé
- Sub-agent utilise le modèle `haiku` (moins cher)
- Seul le workflow principal utilise le modèle de la conversation

## Composants

### 1. Agent: `coderabbit-comment-analyzer`

**Emplacement**: `.claude/agents/coderabbit-comment-analyzer.md`

**Rôle**: Analyser la pertinence d'un commentaire CodeRabbit individuel.

**Input**:
- Chemin du fichier concerné
- Numéro de ligne
- Texte du commentaire CodeRabbit

**Output** (JSON):
```json
{
  "decision": "ACCEPT | REJECT | DISCUSS | DEFER",
  "confidence": 0.0-1.0,
  "type": "bug | security | performance | style | best-practice | documentation",
  "criticality": "BLOCKING | IMPORTANT | MINOR | COSMETIC",
  "effort": "TRIVIAL | LOW | MEDIUM | HIGH",
  "regression_risk": true | false,
  "summary": "Résumé en une phrase",
  "rationale": "Justification de la décision",
  "action": {
    "type": "fix | ignore | research | discuss",
    "details": "Description de l'action"
  },
  "code_suggestion": "Code corrigé ou null",
  "research_needed": "Question de recherche ou null"
}
```

**Décisions possibles**:
- **ACCEPT**: Suggestion pertinente, à appliquer
- **REJECT**: Faux positif ou incompatible avec nos standards
- **DISCUSS**: Besoin d'avis humain ou contexte insuffisant
- **DEFER**: Valide mais hors scope actuel

### 2. Commande: `/review-coderabbit-pr`

**Emplacement**: `.claude/commands/custom/review-coderabbit-pr.md`

**Usage**:
```bash
# Avec numéro de PR
/review-coderabbit-pr 123

# Avec URL
/review-coderabbit-pr https://github.com/owner/repo/pull/123
```

**Fonctionnalités**:
- Récupère les commentaires CodeRabbit via `gh api`
- Crée/charge le fichier de tracking
- Lance l'analyse parallélisée
- Affiche un résumé structuré
- Permet d'appliquer les corrections

**Commandes interactives**:
- `appliquer tout` - Applique tous les ACCEPT
- `appliquer [id]` - Applique un commentaire spécifique
- `rejeter [id]` - Override: rejeter un ACCEPT
- `accepter [id]` - Override: accepter un REJECT
- `discuter [id]` - Ouvrir discussion
- `rechercher [id]` - WebSearch sur le sujet
- `résumé` - Réafficher le résumé
- `répondre` - Générer réponses GitHub
- `terminer` - Marquer review terminée

### 3. Fichier de Tracking

**Emplacement**: `.claude/pr-reviews/pr-{NUMBER}-tracking.yaml`

**Structure**:
```yaml
pr_number: 123
pr_title: "Feature X"
status: in_progress  # in_progress | completed | abandoned

summary:
  total_comments: 15
  analyzed: 15
  accepted: 8
  rejected: 4
  discussed: 2
  deferred: 1
  applied: 6

comments:
  - id: "1234567890"
    file: "src/main/java/Service.java"
    line: 42
    original_comment: "Consider using Optional..."
    analysis:
      decision: "ACCEPT"
      confidence: 0.85
      criticality: "MINOR"
      rationale: "Pattern recommandé dans nos standards"
    status: "applied"

history:
  - timestamp: "2024-01-15T10:30:00Z"
    action: "analysis_started"
```

## Flux de Travail Typique

### 1. Démarrage
```
Utilisateur: /review-coderabbit-pr 42

Claude: Chargement de la PR #42...
- Titre: "Add retry logic to embedding service"
- 12 commentaires CodeRabbit détectés
- Lancement de l'analyse...
```

### 2. Analyse (parallèle)
```
[Task 1] Analyse src/adapters/InfinityClient.java:45
[Task 2] Analyse src/adapters/InfinityClient.java:78
[Task 3] Analyse src/core/EmbeddingService.java:23
[Task 4] Analyse src/config/ResilienceConfig.java:15
...
```

### 3. Résumé
```
## Résumé Review PR #42

| Décision | Nombre |
|----------|--------|
| ACCEPT   | 6      |
| REJECT   | 3      |
| DISCUSS  | 2      |
| DEFER    | 1      |

### À Appliquer (ACCEPT)
1. [IMPORTANT] InfinityClient.java:45 - Ajouter timeout explicite
2. [MINOR] InfinityClient.java:78 - Utiliser var pour type évident
...

### Rejetés (REJECT)
1. EmbeddingService.java:23 - Faux positif: Optional déjà utilisé
...
```

### 4. Actions
```
Utilisateur: appliquer tout

Claude: Application de 6 corrections...
✓ InfinityClient.java:45 - Timeout ajouté
✓ InfinityClient.java:78 - var utilisé
...

Fichier tracking mis à jour.
```

### 5. Reprise (session ultérieure)
```
Utilisateur: /review-coderabbit-pr 42

Claude: Fichier tracking existant trouvé.
- 6/12 commentaires déjà traités
- 2 commentaires en DISCUSS en attente
- Voulez-vous continuer la review?
```

## Intégration avec le Projet

### Standards Alexandria Intégrés

L'agent d'analyse connaît les standards du projet:
- Java 25 avec Virtual Threads
- Records pour DTOs
- Optional au lieu de null
- Injection constructeur
- @Retry Resilience4j

### Détection de Faux Positifs

L'agent identifie automatiquement les faux positifs courants:
- Suggestions incompatibles avec notre version Java/Spring
- Patterns non applicables (Lombok vs records natifs)
- Over-engineering inutile
- Suggestions déjà implémentées différemment

## Limitations et Évolutions Futures

### Limitations Actuelles
- Pas de hooks automatiques (volontairement désactivés)
- Nécessite `gh` CLI authentifié
- Analyse limitée au code (pas aux tests associés)

### Évolutions Prévues
- [ ] Intégration avec GitHub Actions pour déclencher automatiquement
- [ ] Génération automatique de réponses GitHub
- [ ] Métriques de qualité des suggestions CodeRabbit
- [ ] Apprentissage des patterns de rejection pour améliorer la config CodeRabbit

## Fichiers Créés

```
.claude/
├── agents/
│   └── coderabbit-comment-analyzer.md    # Sub-agent d'analyse
├── commands/
│   └── custom/
│       └── review-coderabbit-pr.md       # Workflow principal
└── pr-reviews/
    ├── .gitkeep
    ├── TEMPLATE.yaml                      # Template de tracking
    └── pr-{NUMBER}-tracking.yaml          # Fichiers générés
```

## Utilisation

```bash
# Lancer une review
/review-coderabbit-pr 123

# Reprendre une review existante
/review-coderabbit-pr 123

# Voir le statut
cat .claude/pr-reviews/pr-123-tracking.yaml
```
