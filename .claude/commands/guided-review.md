---
name: guided-review
description: Interactive guided code review on the current branch - file by file in optimal order
allowed-tools:
  - Bash(git diff:*)
  - Bash(git log:*)
  - Bash(git merge-base:*)
  - Bash(git branch:*)
  - Bash(git show:*)
  - Read
  - Grep
  - Glob
  - Task
---

<objective>
Guider l'utilisateur à travers une code review interactive de tous les changements sur la branche courante par rapport à la branche de base. Reviewer fichier par fichier dans un ordre optimal, expliquer chaque changement en détail, et dialoguer avec l'utilisateur.

Branche de base : $ARGUMENTS (défaut: master si non spécifié)
</objective>

<process>

## Étape 1 : Collecter le contexte

1. Identifier la branche courante : `git branch --show-current`
2. Déterminer la branche de base : utiliser `$ARGUMENTS` si fourni, sinon `master`
3. Trouver le merge-base : `git merge-base <base> HEAD`
4. Lister les fichiers modifiés avec stats : `git diff --name-status --stat <merge-base>..HEAD`
5. Récupérer l'historique des commits : `git log --oneline --reverse <merge-base>..HEAD`

Présenter un résumé initial :
- Nom de la branche et son objectif probable (déduit du nom et des commits)
- Nombre de fichiers modifiés, ajoutés, supprimés
- Nombre de commits
- Taille globale des changements (insertions/deletions)

## Étape 2 : Planifier l'ordre de review

Analyser les fichiers modifiés et les organiser selon cet ordre optimal :

**Priorité de review (du premier au dernier) :**
1. **Build/Configuration** (build.gradle, pom.xml, settings, CI) — Comprendre les dépendances ajoutées et le contexte technique
2. **Migrations/Schémas BDD** (SQL, Flyway, Liquibase) — Les changements de modèle de données informent tout le reste
3. **Modèles de domaine/Entités** — Les structures de données centrales dont dépend le reste du code
4. **Infrastructure/Fondations** (utilitaires, classes de base, composants partagés) — Les briques sur lesquelles les services s'appuient
5. **Logique métier/Services** — Le coeur de la feature, mieux compris après avoir vu les modèles
6. **API/Controllers/Endpoints** — Comment la feature est exposée, plus clair après avoir compris la logique
7. **Intégration/Wiring** (config DI, enregistrement de modules) — Comment tout se connecte
8. **Tests** — Valident la compréhension de tout ce qui précède
9. **Documentation** (README, docs, commentaires seuls) — Contexte final

Présenter le plan ordonné à l'utilisateur :
- Liste numérotée avec chemin du fichier et catégorie
- Brève explication de POURQUOI cet ordre est optimal pour cette review spécifique
- Demander confirmation avant de commencer

## Étape 3 : Review interactive fichier par fichier

Pour chaque fichier dans l'ordre planifié :

### 3a. En-tête du fichier
Afficher :
```
═══════════════════════════════════════════════════
📄 Fichier X/Y : `chemin/du/fichier` [CATÉGORIE]
═══════════════════════════════════════════════════
```

### 3b. Expliquer les changements
Pour chaque modification dans le fichier :
- **Ce qui a changé** : Décrire précisément la modification (ajout, modification, suppression, renommage)
- **Pourquoi ce choix** : Expliquer la décision de design derrière l'approche d'implémentation
- **Contexte** : Comment ce changement s'articule avec les autres fichiers de la review
- Si pertinent, mentionner les alternatives qui auraient pu être choisies

Utiliser `git diff <merge-base>..HEAD -- <fichier>` pour le diff spécifique.
Lire le fichier complet avec Read si le contexte est nécessaire pour comprendre.

### 3c. Observations de review

Analyser le code selon ces critères :

- **Architecture & couches** — Vérifier le respect des dépendances directionnelles (features → pas d'import d'adapters, adapters indépendants entre eux). Les nouveaux packages/classes doivent s'intégrer dans la structure existante (config, document, source, ingestion, search). Pas de logique métier dans les controllers ou la config.
- **Migrations Flyway** — Les fichiers SQL sont immutables une fois mergés. Vérifier la syntaxe pgvector (expressions d'index HNSW/GIN), la compatibilité avec LangChain4j (colonnes `embedding_id`, `embedding`, `text`, `metadata`), et que chaque migration est idempotente dans son effet.
- **Conventions LangChain4j / Spring AI** — Respect des nommages hardcodés du PgVectorEmbeddingStore. Métadonnées TextSegment en snake_case (`source_url`, `section_path`). Pas de mélange entre les APIs LangChain4j et Spring AI dans un même composant.
- **Immutabilité & records** — Préférer les Java records pour les DTOs et value objects. Validation dans les constructeurs compacts (pas de setters). Les entités JPA utilisent le pattern no-arg constructor + getters sans setters publics.
- **Robustesse** — Gestion des cas limites : requêtes vides, résultats sans métadonnées, embeddings de dimension incorrecte. Les Optional sont utilisés correctement (pas de `.get()` sans vérification). Pas de null retourné quand une collection vide est attendue.
- **Testabilité & couverture** — Tout nouveau code métier a des tests unitaires (Mockito) ET d'intégration (Testcontainers). Les tests d'intégration utilisent `BaseIntegrationTest` et configurent `api.version=1.44`. Les assertions sont expressives (AssertJ, pas de simple assertTrue).
- **Performance search** — Pas de N+1 queries. Les recherches hybrides utilisent les bons paramètres RRF (k=60). Les index sont exploités (pas de full table scan sur `document_chunks`). Attention aux embeddings recalculés inutilement.

Pour chaque observation, indiquer :
- 🟢 **Bon** : Pattern ou choix remarquable à noter
- 🟡 **Question** : Point à clarifier ou discuter
- 🔴 **Attention** : Problème potentiel à adresser

### 3d. Pause pour discussion

Après chaque fichier, demander explicitement :
- S'il y a des questions sur ce fichier
- Si l'utilisateur veut approfondir une partie spécifique
- S'il est prêt à passer au fichier suivant

**IMPORTANT** : Attendre la réponse de l'utilisateur avant de passer au fichier suivant. Ne JAMAIS avancer automatiquement.

## Étape 4 : Synthèse finale

Après tous les fichiers, fournir :
- Résumé de haut niveau de ce que la branche accomplit
- Patterns d'architecture et de design utilisés
- Préoccupations transversales identifiées
- Questions ouvertes ou suggestions d'amélioration
- Verdict global de la review

</process>

<guidelines>
- Toujours communiquer en français
- Être détaillé mais concis dans les explications
- Quand on explique le "pourquoi", considérer le contexte plus large du projet
- Si un fichier est volumineux, se concentrer d'abord sur les changements les plus significatifs
- Utiliser des extraits de code du diff pour illustrer les points
- Adapter le niveau de détail à la complexité du fichier
- Ne pas hésiter à lire d'autres fichiers du projet pour donner du contexte
</guidelines>
