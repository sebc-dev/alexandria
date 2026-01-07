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
│  5. Propose actions groupées                                   │
│  6. [sync] Synchronise avec GitHub (nouveaux + résolutions)    │
│  7. [REJECT] Propose réponse GitHub avec justification         │
│  8. [applied] Vérifie la résolution par CodeRabbit             │
└─────────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   SUB-AGENT:     │  │   SYNC avec      │  │   REPLY sur      │
│   analyzer       │  │   GitHub API     │  │   GitHub         │
├──────────────────┤  ├──────────────────┤  ├──────────────────┤
│ Analyse 1 comment│  │ Nouveaux comments│  │ Auto-reply REJECT│
│ Retourne JSON    │  │ Vérif résolutions│  │ Demande vérif    │
│ ACCEPT/REJECT/.. │  │ Détecte réponses │  │ Discussion CB    │
└──────────────────┘  └──────────────────┘  └──────────────────┘
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

```bash
# Récupérer tous les review comments
gh api repos/{owner}/{repo}/pulls/$PR_NUMBER/comments --jq '.[] | select(.user.login | contains("coderabbit")) | {id: .id, path: .path, line: .line, body: .body, created_at: .created_at}'

# Récupérer les commentaires généraux de la PR
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
  subagent_type: "coderabbit-comment-analyzer",
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
- `répondre` - Générer réponses GitHub
- `terminer` - Marquer review comme terminée
- `sync` - Synchroniser l'état des commentaires avec GitHub (nouveaux + vérification résolutions)
- `vérifier résolutions` - Vérifier si les commentaires corrigés sont résolus par CodeRabbit

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

Afficher un rapport:

```markdown
## Rapport de Synchronisation PR #$PR_NUMBER

### Nouveaux Commentaires
- 2 nouveaux commentaires détectés → ajoutés au tracking

### État des Résolutions
| Commentaire | Fichier | Statut | CodeRabbit |
|-------------|---------|--------|------------|
| #123 | file.java:42 | ✅ Résolu | - |
| #456 | other.java:100 | ⚠️ Non résolu | Réponse en attente |
| #789 | test.java:50 | ❓ Nouveau feedback | [Voir réponse] |

### Actions Requises
- Commentaire #456: CodeRabbit n'a pas confirmé la résolution
  → Options: [Résoudre manuellement] [Répondre à CodeRabbit]
```

## Étape 9: Auto-Reply sur REJECT

Quand un commentaire est marqué REJECT (initial ou override), proposer d'ajouter un commentaire sur GitHub:

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

### 9.3 Proposer à l'Utilisateur

Avant de poster, TOUJOURS demander confirmation:

```markdown
## Répondre au commentaire CodeRabbit

**Fichier**: path/to/file.java:42
**Commentaire original**: {extrait}
**Raison du rejet**: {rationale}

**Réponse proposée**:
> Merci pour la suggestion @coderabbitai.
> Après analyse, nous avons décidé de ne pas appliquer cette modification:
> **Raison**: {rationale}

Voulez-vous poster cette réponse sur GitHub?
- `oui` - Poster la réponse
- `modifier` - Modifier la réponse avant de poster
- `non` - Ne pas répondre
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
