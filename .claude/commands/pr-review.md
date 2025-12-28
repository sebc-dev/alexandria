---
description: Review PR comments one by one - delegate each to a sub-agent for isolated processing
argument-hint: [optional: PR number or comment ID, or empty for interactive mode]
allowed-tools: Bash(gh:*), Bash(git status:*), Bash(git log:*), Read, Glob, Grep, Task, TodoWrite, AskUserQuestion
---

# PR Comment Review Command (Orchestrator)

Orchestrez la revue de commentaires de PR en déléguant le traitement de chaque commentaire à un sub-agent dédié. Cela permet de garder un contexte léger (uniquement l'avancement) tout en traitant chaque commentaire en profondeur.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  /pr-review (Orchestrator)                   │
│  - Gestion des modes (interactif, from-collect, direct)     │
│  - Récupération des commentaires                             │
│  - Compteurs de session (applied, rejected, skipped...)      │
│  - Affichage du résumé final                                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ Pour chaque commentaire
┌─────────────────────────────────────────────────────────────┐
│              Task(pr-comment-processor)                      │
│  - Analyse du fichier et du code                            │
│  - Évaluation de la pertinence                               │
│  - Proposition de correction                                 │
│  - Application + commit si validé                            │
│  - Retour d'un RESULT structuré                              │
└─────────────────────────────────────────────────────────────┘
```

## Modes de Fonctionnement

Ce command supporte trois modes :

1. **Mode Interactif** (sans arguments) : Vous fournissez les commentaires un par un
2. **Mode From Collect** (`/pr-review --from-collect [PR-number]`) : Traite les commentaires depuis `.scd/`
3. **Mode Direct** (`/pr-review <PR-number> <comment-id>`) : Revue d'un commentaire spécifique

## Arguments Courants

Arguments fournis : `$ARGUMENTS`

## Votre Rôle d'Orchestrateur

### 1. Initialisation

Créez un TodoWrite pour tracker l'avancement :

```
- Initialiser la session de review
- Déterminer le mode de fonctionnement
- Traiter les commentaires via sub-agents
- Afficher le résumé final
```

### 2. Déterminer le Mode

Selon `$ARGUMENTS` :

- Si vide → **Mode Interactif**
- Si `--from-collect` → **Mode From Collected Data**
- Si PR number et comment ID → **Mode Direct**

### 3. Mode Interactif - Welcome Message

Si aucun argument, afficher :

```
╔══════════════════════════════════════════════════════════════╗
║               🔍 PR Comment Review Session                    ║
╠══════════════════════════════════════════════════════════════╣
║  Je suis prêt à orchestrer l'analyse de vos commentaires.    ║
║                                                              ║
║  🏗️ Architecture:                                            ║
║     → Chaque commentaire sera traité par un sub-agent dédié  ║
║     → Je garde uniquement l'avancement en contexte           ║
║     → Résultats consolidés à la fin                          ║
║                                                              ║
║  📝 Pour chaque commentaire, fournissez :                    ║
║     - Le contenu du commentaire (copié depuis GitHub)        ║
║     - Le fichier concerné (si applicable)                    ║
║     - Le contexte si nécessaire                              ║
║                                                              ║
║  📌 Commandes disponibles :                                  ║
║     - "stop" ou "fin" → Terminer la session                  ║
║     - "status" → Voir l'avancement                           ║
║     - "skip" → Passer au commentaire suivant                 ║
╚══════════════════════════════════════════════════════════════╝
```

Puis utiliser AskUserQuestion pour obtenir le premier commentaire.

### 4. Gestion de Session

Maintenir des compteurs simples :

```
SESSION_STATS:
  total: 0
  applied: 0      # RESULT.status = APPLIED
  rejected: 0     # RESULT.status = REJECTED
  adapted: 0      # RESULT.status = ADAPTED
  already_fixed: 0 # RESULT.status = ALREADY_FIXED
  skipped: 0      # RESULT.status = SKIPPED
  errors: 0       # RESULT.status = ERROR
```

### 5. Traitement d'un Commentaire via Sub-Agent

Pour chaque commentaire reçu :

#### A. Parser les informations

Extraire du commentaire :
- **source** : Agent IA (CodeRabbit, Copilot, etc.) ou "Human"
- **file** : Chemin du fichier mentionné
- **lines** : Numéros de ligne si spécifiés
- **severity** : Critical/Major/Minor/Trivial (depuis emoji ou contexte)
- **content** : Le contenu du commentaire
- **suggestion** : La suggestion de fix si présente

#### B. Lancer le Sub-Agent

Utiliser le Task tool pour déléguer :

```
Tool: Task
subagent_type: general-purpose
description: "Traiter commentaire PR: [fichier]"
prompt: |
  Vous êtes l'agent pr-comment-processor.

  Référez-vous aux instructions dans: .claude/agents/pr-comment-processor.md

  COMMENT_DATA:
  - source: [source extraite]
  - file: [fichier extrait]
  - lines: [lignes extraites]
  - severity: [sévérité extraite]
  - content: |
      [contenu du commentaire]
  - suggestion: |
      [suggestion si présente]

  PROJECT_CONTEXT:
  - standards_file: CLAUDE.md

  Traitez ce commentaire et retournez un RESULT structuré.
```

#### C. Parser le Résultat

Le sub-agent retournera un bloc RESULT. Extraire :
- `status` : APPLIED | REJECTED | ADAPTED | ERROR | ALREADY_FIXED | SKIPPED
- `commit_hash` : Si commit effectué
- `reason` : Explication

#### D. Mettre à Jour les Compteurs

Incrémenter le compteur approprié selon `status`.

#### E. Afficher le Résumé Intermédiaire

Après chaque commentaire traité :

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 Session Progress: ✅ [applied] | ❌ [rejected] | 🔄 [already_fixed] | ⏭️ [skipped] | ⚠️ [errors]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 6. Mode From Collected Data

Si `--from-collect` :

1. Vérifier l'existence de `.scd/pr-data/`
2. Lister les commentaires disponibles :
   ```bash
   ls .scd/pr-data/
   ```
3. Si PR number spécifié, filtrer sur cette PR
4. Lire les commentaires depuis les fichiers structurés
5. Pour chaque commentaire, suivre le process du §5

### 7. Mode Direct

Si PR number et comment ID fournis :

1. Récupérer le commentaire via gh CLI :
   ```bash
   gh api repos/{owner}/{repo}/pulls/{pr}/comments/{comment_id}
   ```
2. Parser la réponse JSON
3. Traiter via sub-agent (§5)

### 8. Gestion des Commandes Utilisateur

Dans le mode interactif, surveiller :

- **"stop"** ou **"fin"** → Aller au résumé final
- **"status"** → Afficher les compteurs actuels
- **"skip"** → Marquer comme SKIPPED, passer au suivant

### 9. Résumé Final

Quand la session se termine :

```
╔══════════════════════════════════════════════════════════════╗
║              📊 Résumé de la Session de Review                ║
╠══════════════════════════════════════════════════════════════╣
║                                                              ║
║  📈 Statistiques:                                            ║
║     Total traités:        [total]                            ║
║     ✅ Corrections appliquées: [applied]                     ║
║     🔄 Adaptations:            [adapted]                     ║
║     ❌ Rejetés:                [rejected]                    ║
║     ♻️  Déjà traités:          [already_fixed]               ║
║     ⏭️  Passés (skip):         [skipped]                     ║
║     ⚠️  Erreurs:               [errors]                      ║
║                                                              ║
║  📝 Commits créés: [applied + adapted]                       ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝

💡 Prochaines étapes suggérées:
   - Vérifier les commits: git log --oneline -[count]
   - Pousser les changements: git push
   - Répondre aux commentaires sur GitHub
```

### 10. Afficher l'Historique des Commits

Si des commits ont été créés, afficher :

```bash
git log --oneline -[nombre de commits créés]
```

## Avantages de cette Architecture

1. **Contexte Léger** : L'orchestrateur ne garde que les compteurs
2. **Traitement Isolé** : Chaque commentaire a son propre contexte
3. **Parallélisation Possible** : Plusieurs sub-agents peuvent tourner en parallèle si besoin
4. **Résultats Structurés** : Format RESULT standardisé pour agrégation
5. **Résilience** : Une erreur sur un commentaire n'affecte pas les autres

## Guidelines Importantes

### DO:
- ✅ Toujours utiliser le Task tool pour chaque commentaire
- ✅ Parser correctement le RESULT retourné
- ✅ Garder les compteurs à jour
- ✅ Afficher l'avancement après chaque traitement
- ✅ Respecter les commandes utilisateur (stop, skip, status)

### DON'T:
- ❌ Ne jamais traiter un commentaire directement (déléguer au sub-agent)
- ❌ Ne jamais push automatiquement
- ❌ Ne pas accumuler le contexte des commentaires précédents
- ❌ Ne pas ignorer les erreurs retournées par les sub-agents

## Exemple de Session

```
User: /pr-review

Claude: [Affiche welcome message]
Claude: [AskUserQuestion pour le premier commentaire]

User: [Colle un commentaire CodeRabbit]

Claude: 🚀 Lancement du sub-agent pour traiter ce commentaire...
Claude: [Task tool -> pr-comment-processor]

[Sub-agent traite, demande confirmation, applique, commit]

Claude:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Résultat: ✅ APPLIED
Commit: a1b2c3d - 🐛 fix(Header): remove unused useState import
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 Session Progress: ✅ 1 | ❌ 0 | 🔄 0 | ⏭️ 0 | ⚠️ 0
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prochain commentaire ? (ou "stop" pour terminer)

User: stop

Claude: [Affiche résumé final]
```

---

Maintenant, traitez selon le mode déterminé par : `$ARGUMENTS`
