---
description: 'Review les commentaires CodeRabbit d''une PR avec analyse de pertinence par sub-agent. Économise le contexte et garantit un suivi complet.'
allowed-tools: Read, Write, Edit, Grep, Glob, Bash(gh:*), Bash(git:*), Bash(cat:*), Bash(mkdir:*), Task
argument-hint: [PR_NUMBER ou URL]
---

# Workflow Review Commentaires CodeRabbit

Tu es un orchestrateur de review de PR qui délègue l'analyse de pertinence à un sub-agent spécialisé pour économiser le contexte.

## Arguments

- `$ARGUMENTS` : Numéro de PR ou URL GitHub (ex: `123` ou `https://github.com/owner/repo/pull/123`)

## Architecture du Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│                    WORKFLOW PRINCIPAL                           │
│  (Ce prompt - gère l'orchestration et le tracking)             │
├─────────────────────────────────────────────────────────────────┤
│  1. Récupère les commentaires PR via gh CLI                    │
│  2. Crée/charge le fichier de tracking                         │
│  3. Pour chaque commentaire non traité:                        │
│     └── Lance sub-agent coderabbit-comment-analyzer            │
│  4. Compile les résultats                                      │
│  5. Pour chaque DEFER:                                         │
│     └── Lance sub-agent defer-backlog-analyzer                 │
│  6. Propose actions groupées                                   │
│  7. [sync] Synchronise avec GitHub + tableau résolutions       │
│  8. [REJECT] OBLIGATOIRE: propose réponse GitHub (insistant)   │
│  9. [applied] Vérifie la résolution par CodeRabbit             │
└─────────────────────────────────────────────────────────────────┘
                              │
    ┌─────────────────┬───────┴───────┬─────────────────┐
    ▼                 ▼               ▼                 ▼
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
│ SUB-AGENT: │  │ SUB-AGENT: │  │   SYNC     │  │   REPLY    │
│ analyzer   │  │ defer-     │  │  GitHub    │  │  GitHub    │
├────────────┤  │ backlog    │  ├────────────┤  ├────────────┤
│ Analyse 1  │  ├────────────┤  │ Nouveaux   │  │ REJECT:    │
│ comment    │  │ Vérifie    │  │ comments   │  │ insistant  │
│ ACCEPT/    │  │ beads/docs │  │ Tableau    │  │ + rappels  │
│ REJECT/..  │  │ Crée issue │  │ résolutions│  │            │
└────────────┘  └────────────┘  └────────────┘  └────────────┘
```

## Étape 1: Initialisation

1. Extraire le numéro de PR de `$ARGUMENTS`
2. Vérifier que gh est authentifié
3. Récupérer les infos de la PR

```bash
# Extraire numéro PR
PR_NUMBER=$(echo "$ARGUMENTS" | grep -oE '[0-9]+' | head -1)

# Récupérer les détails de la PR
gh pr view $PR_NUMBER --json title,body,state,headRefName,baseRefName
```

## Étape 2: Récupérer les Commentaires CodeRabbit

### 2.1 Commentaires inline (review comments API)

```bash
# Récupérer tous les review comments inline
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/comments --jq '.[] | select(.user.login | contains("coderabbit")) | {id: .id, path: .path, line: .line, body: .body, created_at: .created_at}'
```

### 2.2 Commentaires dans les review bodies (IMPORTANT!)

Certains commentaires CodeRabbit sont "outside the diff" et apparaissent **uniquement dans le corps des reviews**, pas dans l'API comments. Ces commentaires sont formatés dans une section `<details>` avec `🤖 Fix all issues with AI agents`.

```bash
# Récupérer tous les review bodies de CodeRabbit
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/reviews --jq '
  .[] | select(.user.login | contains("coderabbit")) |
  select(.body != null and .body != "") |
  {review_id: .id, state: .state, body: .body, submitted_at: .submitted_at}
'
```

**Extraction des commentaires depuis le review body:**

Le body contient des blocs formatés comme:
```
In @path/to/file.ext:
- Around line X: Description du problème...
- Line Y: Autre problème...
```

Parser avec regex ou manuellement pour extraire:
- `path`: le chemin du fichier après `In @`
- `line`: le numéro de ligne (Around line X, Line Y, lines X-Y)
- `body`: la description du problème

### 2.3 Récupérer les commentaires généraux de la PR

```bash
gh pr view $PR_NUMBER --comments --json comments
```

## Étape 3: Charger/Créer le Fichier de Tracking

Chemin: `.claude/pr-reviews/pr-{PR_NUMBER}-tracking.yaml`

Si le fichier existe, le charger. Sinon, créer avec ce template:

```yaml
# Tracking Review PR #$PR_NUMBER
pr_number: $PR_NUMBER
pr_title: "$PR_TITLE"
branch: "$HEAD_REF"
created_at: "$TIMESTAMP"
last_updated: "$TIMESTAMP"
status: in_progress  # in_progress | completed | abandoned

summary:
  total_comments: 0
  analyzed: 0
  accepted: 0
  rejected: 0
  discussed: 0
  deferred: 0
  applied: 0

comments: []
# Structure de chaque comment:
# - id: "comment_id"
#   source: "inline | review_body"  # D'où vient le commentaire
#   review_id: null                  # ID de la review si source=review_body
#   file: "path/to/file.java"
#   line: 42
#   type: "bug | security | performance | style | best-practice"
#   original_comment: "Texte du commentaire CodeRabbit"
#   analysis:
#     decision: "ACCEPT | REJECT | DISCUSS | DEFER"
#     confidence: 0.85
#     criticality: "BLOCKING | IMPORTANT | MINOR | COSMETIC"
#     effort: "TRIVIAL | LOW | MEDIUM | HIGH"
#     rationale: "Justification"
#     code_suggestion: "Code si applicable"
#   status: "pending | analyzed | applied | skipped"
#   applied_at: null
#   resolution_status: null | pending_verification | verified | needs_attention | manually_resolved
#   coderabbit_replies: []  # Réponses de CodeRabbit
#   our_replies: []         # Nos réponses postées
```

## Étape 4: Préparation du Tracking

Avant de lancer les analyses, créer les entrées pour chaque commentaire dans le fichier de tracking:

```yaml
comments:
  - id: "COMMENT_ID"
    file: "path/to/file.java"
    line: 42
    original_comment: |
      Texte du commentaire CodeRabbit...
    status: "pending"
    # Les champs analysis, resolution_status, etc. seront ajoutés par le sub-agent
```

## Étape 5: Analyse par Sub-Agent

Pour chaque commentaire avec `status: pending`, utiliser le Task tool.

**Le sub-agent modifie DIRECTEMENT le fichier de tracking** et retourne un statut minimal.

```
Task(
  subagent_type: "pr-comment-analyzer",
  description: "Analyse commentaire #{COMMENT_ID}",
  prompt: """
  Analyse ce commentaire CodeRabbit et mets à jour le fichier de tracking.

  TRACKING_FILE: .claude/pr-reviews/pr-{PR_NUMBER}-tracking.yaml
  COMMENT_ID: {comment_id}
  FILE_PATH: {file_path}
  LINE: {line_number}
  COMMENT_BODY: |
    {comment_body}

  Instructions:
  1. Lis le fichier source pour comprendre le contexte
  2. Analyse la pertinence du commentaire
  3. Modifie le fichier de tracking pour ajouter le bloc 'analysis' au commentaire
  4. Retourne: OK|COMMENT_ID|DECISION|CRITICALITY|TYPE
  """,
  model: "sonnet"
)
```

**Réponse attendue du sub-agent:**
```
OK|2667219223|ACCEPT|IMPORTANT|best-practice
```

**En cas d'erreur:**
```
ERROR|2667219223|Fichier non trouvé
```

**IMPORTANT**: Lancer les analyses en PARALLÈLE (plusieurs Task dans un seul message) pour les commentaires indépendants (fichiers différents).

## Étape 6: Compilation des Résultats

Après toutes les analyses:
1. **Relire le fichier de tracking** pour récupérer les analyses complètes
2. Mettre à jour les compteurs `summary`
3. Afficher le résumé:

```markdown
## Résumé Review PR #$PR_NUMBER

### Statistiques
| Décision | Nombre | % |
|----------|--------|---|
| ACCEPT   | X      | X%|
| REJECT   | X      | X%|
| DISCUSS  | X      | X%|
| DEFER    | X      | X%|

### Commentaires à Appliquer (ACCEPT)

#### [BLOCKING] Fichier: path/to/file.java:42
- **Type**: bug
- **Suggestion**: Description
- **Code**:
```java
// Code suggéré
```

#### [IMPORTANT] Fichier: path/to/autre.java:100
...

### Commentaires Rejetés (REJECT)
- `file.java:50` - Faux positif: [raison]
- `file.java:75` - Incompatible avec standards: [raison]

### À Discuter (DISCUSS)
- `file.java:80` - Trade-off à évaluer: [question]

### Reportés (DEFER)
- `file.java:90` - Hors scope: [raison]
```

## Étape 6.5: Analyse Approfondie des DEFER

Pour chaque commentaire marqué DEFER, lancer le sub-agent `defer-backlog-analyzer` pour vérifier si la suggestion est déjà couverte par le backlog.

### Lancement du Sub-Agent

```
Task(
  subagent_type: "pr-defer-analyzer",
  description: "Analyse backlog DEFER #{COMMENT_ID}",
  prompt: """
  Analyse ce commentaire DEFER pour vérifier s'il est déjà planifié.

  TRACKING_FILE: .claude/pr-reviews/pr-{PR_NUMBER}-tracking.yaml
  DEFERRED_FILE: .claude/pr-reviews/deferred-comments.yaml
  COMMENT_ID: {comment_id}
  TITLE: {title from analysis}
  SUMMARY: {summary from analysis}
  FILE_PATH: {file_path}
  ACTION_REQUIRED: {action_required if any}

  Instructions:
  1. Cherche dans les issues beads (bd list)
  2. Cherche dans docs/project/phases/
  3. Cherche dans les issues GitHub (gh issue list)
  4. Met à jour deferred-comments.yaml avec le résultat
  5. Retourne: OK|COMMENT_ID|STATUS|REFERENCE
  """,
  model: "haiku"
)
```

### Rapport des DEFER

Après analyse, afficher:

```markdown
## Analyse Backlog des DEFER

| ID | Fichier | Titre | Statut | Référence |
|----|---------|-------|--------|-----------|
| #123 | pom.xml:50 | Update deps | already_planned | phase-2c-cicd.md:249 |
| #456 | file.java:100 | Add validation | tracked_github | Issue #9 |
| #789 | config.yaml:20 | Add retry | needs_issue | - |

### Actions Requises

**Issues à créer:**
- [ ] #789: "Add retry to config" → `bd create --title "Add retry config" --priority low --label "coderabbit-defer"`

Voulez-vous créer ces issues maintenant ?
- `créer tout` - Créer toutes les issues proposées
- `créer [id]` - Créer une issue spécifique
- `ignorer` - Ne pas créer d'issues
```

## Étape 7: Actions Interactives

Proposer à l'utilisateur:

1. **Appliquer tous les ACCEPT** - Applique automatiquement les corrections acceptées
2. **Appliquer sélectivement** - Choisir quels ACCEPT appliquer
3. **Discuter un commentaire** - Lancer une discussion sur un DISCUSS
4. **Rechercher** - Lancer une WebSearch pour un commentaire DISCUSS
5. **Marquer comme terminé** - Finaliser le tracking
6. **Répondre sur GitHub** - Générer des réponses pour les commentaires

## Commandes Utilisateur

L'utilisateur peut dire:
- `appliquer tout` - Applique tous les ACCEPT
- `appliquer [id]` - Applique un commentaire spécifique
- `rejeter [id]` - Override: rejeter un ACCEPT (avec réponse GitHub automatique)
- `accepter [id]` - Override: accepter un REJECT
- `discuter [id]` - Ouvrir discussion sur un commentaire
- `rechercher [id]` - WebSearch sur le sujet du commentaire
- `résumé` - Réafficher le résumé
- `répondre` - Générer réponses GitHub pour les REJECT
- `répondre tout` - Poster toutes les réponses REJECT en attente
- `terminer` - Marquer review comme terminée
- `sync` - Synchroniser l'état des commentaires avec GitHub (nouveaux + vérification résolutions)
- `vérifier résolutions` - Vérifier si les commentaires corrigés sont résolus par CodeRabbit
- `créer tout` - Créer toutes les issues beads pour les DEFER non couverts
- `créer [id]` - Créer une issue beads pour un DEFER spécifique
- `résoudre [id]` - Marquer un commentaire comme résolu manuellement

## Étape 8: Synchronisation et Suivi (Commande `sync`)

La commande `sync` permet de mettre à jour l'état du tracking avec GitHub:

### 8.1 Récupérer les Nouveaux Commentaires

```bash
# Récupérer tous les review comments actuels
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/comments \
  --jq '.[] | select(.user.login | contains("coderabbit")) | {id: .id, path: .path, line: .line, body: .body, created_at: .created_at, in_reply_to_id: .in_reply_to_id}'
```

**Actions:**
1. Comparer les IDs avec ceux déjà dans le tracking
2. Ajouter les nouveaux commentaires avec `status: pending`
3. Identifier les réponses de CodeRabbit (champ `in_reply_to_id`)

### 8.2 Vérifier l'État de Résolution

Pour chaque commentaire marqué `applied` dans le tracking:

```bash
# Vérifier si le commentaire est résolu sur GitHub
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/comments/$COMMENT_ID \
  --jq '{id: .id, path: .path, position: .position, resolved: (.position == null)}'
```

**Note**: Un commentaire est considéré "résolu" si:
- Il n'apparaît plus dans la liste des commentaires actifs
- Ou CodeRabbit a répondu avec une confirmation de résolution

### 8.3 Détecter les Réponses de CodeRabbit

Rechercher les réponses de CodeRabbit à nos corrections:

```bash
# Récupérer les commentaires qui sont des réponses
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/comments \
  --jq '.[] | select(.user.login | contains("coderabbit")) | select(.in_reply_to_id != null) | {id: .id, in_reply_to_id: .in_reply_to_id, body: .body}'
```

### 8.4 Rapport de Synchronisation

Afficher un rapport détaillé avec tableau de résolutions:

```markdown
## Rapport de Synchronisation PR #$PR_NUMBER

**Dernière sync**: {timestamp}

### Statistiques de Résolution

| Statut | Nombre | Description |
|--------|--------|-------------|
| ✅ Vérifié | X | CodeRabbit a confirmé la correction |
| ⏳ En attente | X | Correction appliquée, en attente de vérification |
| ⚠️ Attention | X | CodeRabbit a émis un nouveau feedback |
| 🔄 Manuellement résolu | X | Résolu manuellement sur GitHub |

### Nouveaux Commentaires

| ID | Fichier | Type | Priorité |
|----|---------|------|----------|
| #901 | new-file.java:15 | style | MINOR |
| #902 | config.yaml:30 | best-practice | COSMETIC |

→ **{N} nouveau(x) commentaire(s)** ajouté(s) au tracking avec `status: pending`

### Tableau des Résolutions (Commentaires Corrigés)

| ID | Fichier | Décision | Appliqué | Résolution | CodeRabbit |
|----|---------|----------|----------|------------|------------|
| #123 | file.java:42 | ACCEPT | ✅ Oui | ✅ Vérifié | "LGTM" |
| #456 | other.java:100 | ACCEPT | ✅ Oui | ⏳ En attente | - |
| #789 | test.java:50 | ACCEPT | ✅ Oui | ⚠️ Attention | "Still seeing issue..." |
| #321 | util.java:80 | REJECT | - | ✅ Répondu | Notre justification postée |

### Détail des Commentaires Nécessitant Attention

#### ⚠️ #789 - test.java:50
**Notre correction**: Ajout de la validation null
**Feedback CodeRabbit**:
> Still seeing the issue in some edge cases. Consider also handling empty strings.

**Options**:
- `appliquer #789` - Appliquer la suggestion additionnelle
- `répondre #789` - Répondre à CodeRabbit
- `résoudre #789` - Marquer comme résolu manuellement

### Résumé des Actions

| Action | Nombre |
|--------|--------|
| Nouveaux à analyser | X |
| En attente de vérification | X |
| Nécessitant attention | X |
| REJECT sans réponse GitHub | X |
| DEFER sans issue | X |

**Prochaines étapes suggérées**:
1. Analyser les {X} nouveaux commentaires
2. Traiter les {X} commentaires nécessitant attention
3. Répondre aux {X} REJECT en attente
```

## Étape 9: Auto-Reply sur REJECT (OBLIGATOIRE)

⚠️ **IMPORTANT**: Répondre aux commentaires REJECT sur GitHub est ESSENTIEL pour:
- Informer CodeRabbit de notre décision et éviter qu'il répète la même suggestion
- Documenter notre raisonnement pour référence future
- Maintenir une communication claire avec l'outil de review

**TOUJOURS proposer de poster une réponse pour chaque REJECT. Ne jamais sauter cette étape.**

Quand un commentaire est marqué REJECT (initial ou override):

### 9.1 Générer la Réponse

Format de réponse standard:

```markdown
Merci pour la suggestion @coderabbitai.

Après analyse, nous avons décidé de ne pas appliquer cette modification:

**Raison**: {rationale du REJECT}

{Détails supplémentaires si pertinents}
```

### 9.2 Poster le Commentaire

```bash
# Répondre à un review comment CodeRabbit
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/comments \
  -X POST \
  -f body="$REPLY_BODY" \
  -f in_reply_to=$COMMENT_ID
```

### 9.3 Demander Confirmation (avec insistance)

**OBLIGATOIRE**: Après chaque analyse REJECT, présenter cette demande:

```markdown
## 📢 Réponse GitHub Requise pour REJECT

⚠️ **Il est fortement recommandé de répondre à CodeRabbit** pour documenter notre décision.

**Fichier**: path/to/file.java:42
**Commentaire original**: {extrait}
**Raison du rejet**: {rationale}

**Réponse proposée**:
> Merci pour la suggestion @coderabbitai.
> Après analyse, nous avons décidé de ne pas appliquer cette modification:
> **Raison**: {rationale}

**Poster cette réponse sur GitHub ?** (Recommandé)
- `oui` - ✅ Poster la réponse (recommandé)
- `modifier` - Modifier la réponse avant de poster
- `non` - Ne pas répondre (non recommandé)
```

### 9.4 Après Compilation des Résultats

À la fin de l'analyse, si des commentaires REJECT n'ont pas encore de réponse GitHub:

```markdown
## ⚠️ Rappel: Réponses GitHub en attente

Il reste **{N} commentaire(s) REJECT** sans réponse sur GitHub.

| ID | Fichier | Raison |
|----|---------|--------|
| #123 | file.java:42 | {raison courte} |

**Voulez-vous répondre à ces commentaires maintenant ?**
- `répondre tout` - Poster toutes les réponses en une fois
- `répondre [id]` - Répondre à un commentaire spécifique
- `plus tard` - Reporter (non recommandé)
```

## Étape 10: Gestion des Non-Résolutions

Quand un commentaire est marqué `applied` mais CodeRabbit a répondu avec un nouveau feedback:

### 10.1 Détecter le Nouveau Feedback

Après sync, vérifier si CodeRabbit a répondu à nos corrections:

```bash
# Vérifier les réponses sur un commentaire spécifique
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/comments \
  --jq '.[] | select(.in_reply_to_id == $COMMENT_ID) | select(.user.login | contains("coderabbit"))'
```

### 10.2 Analyser la Réponse

Si CodeRabbit a répondu:
- **Confirmation** (contient "LGTM", "looks good", "resolved"): Marquer comme `verified`
- **Nouveau problème**: Marquer comme `needs_attention` et proposer actions

### 10.3 Proposer des Actions

```markdown
## ⚠️ Commentaire Non Résolu

**Fichier**: path/to/file.java:42
**Notre correction**: {description de ce qu'on a fait}
**Réponse CodeRabbit**:
> {extrait de la réponse}

**Options**:
1. **Résoudre le commentaire manuellement** - Marquer comme résolu sur GitHub
2. **Répondre à CodeRabbit** - Demander une vérification
3. **Appliquer nouvelle suggestion** - Si CodeRabbit propose un fix alternatif
4. **Ignorer** - Marquer comme traité localement

Votre choix?
```

### 10.4 Résoudre Manuellement

Si l'utilisateur choisit de résoudre:

```bash
# Résoudre un thread de commentaire (via GraphQL car pas supporté en REST)
gh api graphql -f query='
  mutation {
    resolveReviewThread(input: {threadId: "$THREAD_ID"}) {
      thread { isResolved }
    }
  }
'
```

**Alternative**: Demander à l'utilisateur de résoudre manuellement via l'interface GitHub.

### 10.5 Générer Réponse pour Vérification

Si l'utilisateur veut répondre à CodeRabbit:

```markdown
@coderabbitai Peux-tu vérifier si le problème a été résolu ?

J'ai appliqué la correction suivante:
- {description du changement}

Le code modifié:
\`\`\`java
{extrait du nouveau code}
\`\`\`
```

## Nouveaux Statuts dans le Tracking

Le fichier YAML utilise maintenant ces statuts:

```yaml
# Statuts de base
status: pending | analyzed | applied | skipped

# Nouveaux statuts pour le suivi des résolutions
resolution_status: null | pending_verification | verified | needs_attention | manually_resolved

# Champs additionnels
coderabbit_replies:
  - id: "reply_comment_id"
    body: "Réponse de CodeRabbit"
    received_at: "timestamp"
    type: confirmation | new_feedback | question

our_replies:
  - id: "our_reply_id"
    body: "Notre réponse"
    posted_at: "timestamp"
```

## Règles Importantes

1. **Économie de contexte**: TOUJOURS utiliser le sub-agent pour l'analyse
2. **Persistance**: TOUJOURS sauvegarder dans le fichier tracking après chaque action
3. **Idempotence**: Ne pas ré-analyser les commentaires déjà traités
4. **Parallélisation**: Lancer les analyses en parallèle quand possible
5. **Traçabilité**: Logger toutes les décisions avec rationale

## Démarrage

EXÉCUTE MAINTENANT:

1. Parse `$ARGUMENTS` pour extraire le numéro de PR
2. Si pas de numéro, demande à l'utilisateur
3. Récupère les commentaires CodeRabbit
4. Charge ou crée le fichier de tracking
5. Lance l'analyse des commentaires non traités
6. Affiche le résumé et propose les actions

---

**Note**: Ce workflow peut être relancé à tout moment. Il reprendra où il en était grâce au fichier de tracking.
