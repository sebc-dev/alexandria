# User Journeys

## Journey 1: Negus - De la Friction au Flow

Negus démarre un nouveau sprint sur un projet client pour son agence web. Comme toujours, son workflow suit quatre phases distinctes: Documentation, Code, Review, et Tests fonctionnels. Mais aujourd'hui, quelque chose est différent - Alexandria est en production depuis deux semaines.

**Phase Documentation - Le Changement Commence**

Basé sur le PRD, Negus lance l'écriture des epics et stories qui guideront l'implémentation. Auparavant, il devait constamment vérifier la documentation FastAPI, rappeler les conventions de son projet, et s'assurer que les stories étaient cohérentes avec l'architecture existante. Maintenant, quand il demande à Claude Code de l'aider à rédiger les stories, l'agent interroge automatiquement Alexandria.

Les stories générées incluent déjà les bonnes pratiques FastAPI, respectent les patterns de son projet, et référencent les bonnes APIs. Negus fait quelques ajustements mineurs - mais là où il passait 30 minutes à corriger et enrichir chaque story, il ne passe plus que 5 minutes en validation rapide.

**Phase Code - L'Autonomie Retrouvée**

L'implémentation commence. Claude Code génère du code pour une nouvelle API endpoint. Normalement, Negus devrait intervenir 2-3 fois pour corriger:
- "Non, on utilise notre propre exception handler personnalisé"
- "Attention, FastAPI 0.104 a changé cette syntaxe"
- "Respecte notre pattern de validation Pydantic"

Mais cette fois, rien. Le code généré respecte déjà le exception handler custom, utilise la syntaxe FastAPI à jour, et applique le bon pattern Pydantic. Claude Code rencontre un problème avec un test imprévu - au lieu de deviner, il interroge Alexandria et obtient le contexte exact dont il a besoin.

Zéro intervention de Negus pendant cette feature complète.

**Phase Review - Le Moment de Vérité**

Negus pousse son commit et attend le verdict de CodeRabbit. Son cœur bat un peu - c'est toujours ce moment où les violations cachées remontent. Le rapport arrive: **1 commentaire mineur sur un typo dans un commentaire**.

Pas de "pourquoi as-tu utilisé cette ancienne syntaxe?". Pas de "cette approche n'est pas cohérente avec le reste du projet". Pas de "tu as oublié de gérer cette exception".

Negus fait sa propre review en scan diagonal - tout est propre, cohérent, conforme. 5 minutes vs 20 minutes habituellement.

**Phase Tests Fonctionnels - La Confirmation**

Les tests fonctionnels révèlent habituellement 1-2 bugs subtils liés à des mauvaises compréhensions ou des APIs mal utilisées. Cette fois: zéro bug. La feature fonctionne du premier coup.

**La Réalisation - Semaine Parfaite**

Vendredi après-midi, Negus fait le bilan de sa semaine. Il a livré 5 features complexes. Statistiques:

- Documentation: 85% de réduction du temps de correction (de 30 min à 5 min par story)
- Code: 1 intervention manuelle dans toute la semaine (vs 15-20 habituellement)
- Review: Moyenne de 1.2 commentaires CodeRabbit par commit (vs 5-8 avant)
- Tests: 1 seul bug fonctionnel découvert sur 5 features (vs 8-10 avant)

**C'est ça, la "semaine parfaite"**. Pas zéro bug, pas zéro intervention - mais une friction si faible que le développement ressemble enfin à du flow. Negus termine sa semaine à 17h au lieu de 19h30, et son niveau de stress est resté à 5/10 même avec la charge de travail élevée.

## Journey 2: Negus Admin - Onboarding d'un Nouveau Projet

Un nouveau client arrive avec un projet Node.js/TypeScript/Prisma. Negus doit configurer Alexandria pour ce nouvel environnement tech. Avant Alexandria, chaque nouveau projet signifiait des semaines de "rodage" avant que Claude Code génère du code cohérent.

**Étape 1: Research & Curation**

Negus collecte la connaissance nécessaire via plusieurs sources:
- Recherche Internet sur les best practices TypeScript 2024
- Discussion avec Claude (modèle Opus) pour identifier les patterns Prisma recommandés
- Deep Research Gemini pour analyser les anti-patterns courants en Node.js async/await
- Lecture rapide de "Effective TypeScript" (livre) pour patterns avancés
- Vidéos YouTube sur Prisma performance optimization

Il compile le tout en fichiers markdown structurés:
- `conventions-typescript-projet-X.md`: Règles non-négociables du projet
- `conventions-prisma-patterns.md`: Patterns de requêtes et transactions
- `doc-typescript-5.3-features.md`: Documentation technique TypeScript 5.3
- `doc-prisma-latest-api.md`: Référence API Prisma à jour

**Étape 2: Upload & Configuration**

Via le slash command `/alexandria-config` et les MCP tools, Negus upload ses fichiers:

1. **Upload Convention**
   - Sélectionne `conventions-typescript-projet-X.md`
   - Marque comme "Convention"
   - **Déclare technologies**: `["typescript", "nodejs", "prisma"]`
   - Alexandria insère automatiquement dans `convention_technologies` table pivot

2. **Upload Documentation**
   - Sélectionne `doc-typescript-5.3-features.md`
   - Marque comme "Documentation"
   - **Spécifie technology_id**: `typescript`
   - Répète pour doc Prisma avec `technology_id: prisma`

**Étape 3: Le Magic du Linking Automatique**

Negus n'a rien d'autre à faire. Le système fonctionne automatiquement:

```
Quand Claude Code demande contexte sur "Prisma query optimization":
1. Alexandria trouve convention pertinente par similarité vectorielle
2. Récupère ses technology_id via JOIN: ["typescript", "prisma"]
3. Ramène automatiquement TOUTES les docs liées à ces technologies
4. Résultat fusionné: Convention projet + Doc Prisma officielle + Doc TypeScript pertinente
```

C'est un **graph relationnel classique** qui garantit cohérence contextuelle - chaque règle voyage avec sa documentation technique, automatiquement.

**Étape 4: Validation & Testing**

Crucial: Negus doit tester l'efficacité avant de démarrer le développement réel.

Via le slash command `/alexandria-query`:
- Entre une requête test: `/alexandria-query "Comment créer une transaction Prisma avec gestion d'erreur?"`
- Voit le contexte retourné par Alexandria (conventions + docs fusionnées) avec affichage debug
- Valide que le Layer 3 (reformulation LLM) produit un guide cohérent
- Ajuste si nécessaire (ajoute conventions manquantes, précise liens)

**Le Résultat**

Temps de configuration: **2 heures** (vs 2-3 semaines de rodage manuel avant). Le projet démarre immédiatement avec du code conforme - dès le premier commit, CodeRabbit est satisfait.

## Journey 3: Claude Code - De la Confusion à la Conformité

Claude Code (agent Sonnet 4.5) reçoit une tâche: "Implémenter un endpoint POST /users avec validation Pydantic et gestion d'erreurs personnalisée."

**Avant Alexandria - Le Chaos**

Sans Alexandria, Claude Code plonge dans sa connaissance universelle:
- **Hallucinations**: Invente un `CustomValidator` qui n'existe pas dans le projet
- **Choix libres dangereux**: Utilise FastAPI 0.95 syntaxe (projet est en 0.104)
- **APIs obsolètes**: Référence `pydantic.validator` (deprecated en Pydantic v2)
- **Non-respect conventions**: Ignore le exception handler custom du projet
- **Incohérence**: Structure de réponse différente des autres endpoints

Résultat: 3 cycles de correction avec Negus, 8 commentaires CodeRabbit, 40 minutes perdues.

**Avec Alexandria - La Transformation**

Claude Code reçoit la même tâche. Mais cette fois, avant de générer du code, il interroge Alexandria.

**Le Flow Interne:**

1. **Requête initiale**: Claude Code (Sonnet 4.5) envoie: "FastAPI endpoint validation Pydantic exception handling"

2. **Alexandria RAG Pipeline**:
   - Layer 1: Trouve conventions pertinentes (recherche vectorielle)
   - Layer 2: Récupère docs techniques liées via `technology_id` JOIN
   - Layer 3: **Agent Haiku 3.5** (économique) reçoit Conv + Doc brutes

3. **Reformulation Intelligente** (Agent Haiku 3.5):
   - Analyse conventions + documentation
   - Élimine contradictions (syntaxe obsolète vs actuelle)
   - Fusionne en guide cohérent mono-approche
   - Valide structure et pertinence
   - Retourne contexte optimisé

4. **Livraison Contexte**: Claude Code (Sonnet 4.5) reçoit:
   ```
   CONVENTIONS PROJET:
   - Utiliser CustomExceptionHandler pour toutes erreurs API
   - Structure réponse: {success, data, error}
   - Validation Pydantic v2 avec Field et model_validator

   DOCUMENTATION TECHNIQUE:
   - FastAPI 0.104: @app.post() avec response_model
   - Pydantic v2: Field(), model_validator() [pas @validator deprecated]
   - Exception handling: raise HTTPException custom

   GUIDE IMPLÉMENTATION:
   [Fusion contextualisée sans ambiguïté]
   ```

**Le Moment Critique - Code Conforme**

Claude Code génère le code. Cette fois:
- ✅ Utilise `Field()` et `model_validator()` (Pydantic v2 correct)
- ✅ Applique FastAPI 0.104 syntaxe (`response_model_exclude_unset`)
- ✅ Intègre `CustomExceptionHandler` du projet
- ✅ Structure réponse cohérente: `{success, data, error}`
- ✅ Patterns identiques aux endpoints existants

**Résultat**: Code parfait dès première génération. CodeRabbit: 0 commentaire. Negus: 0 intervention. Tests: 0 bug.

**La Différence**

Le secret n'est pas Claude Code lui-même - c'est le **contexte optimal**:
- Bonnes conventions (pas toutes, juste celles pertinentes)
- Bonne documentation technique (à jour, pas obsolète)
- Bien mise en forme (fusionnée sans contradiction par Layer 3)

Quand ces trois éléments sont réunis, Claude Code passe de "stagiaire qui devine" à "senior dev qui connaît le projet par cœur".

## Journey Requirements Summary

Ces parcours utilisateurs révèlent les capacités essentielles suivantes pour Alexandria:

**Capacités Core - Active Compliance Filter:**
1. **Recherche vectorielle de conventions** (Layer 1 - similarité sémantique via pgvector)
2. **Linking automatique technologie-based** (Layer 2 - JOIN SQL via table pivot `convention_technologies`)
3. **Reformulation intelligente** (Layer 3 - Agent LLM Haiku 3.5 pour fusion/validation)
4. **Retrieval contextuel** intégré dans workflow Claude Code (MCP server, skills, slash commands)

**Capacités Ingestion & Gestion:**
5. **Upload via MCP tools** (`alexandria_upload_convention`) avec distinction Convention vs Documentation
6. **Configuration multi-technologies** par document (tags technology_id)
7. **Stockage PostgreSQL + pgvector** pour indexation sémantique
8. **Slash command config** (`/alexandria-config`) pour configuration rapide projets

**Capacités Intégration Claude Code:**
9. **Skills Alexandria** interrogeables depuis Claude Code
10. **Slash commands** dédiés pour queries Alexandria
11. **Sub-agents pré-configurés** avec Alexandria activé par défaut
12. **Agent reformulation économique** (Haiku 3.5 vs Sonnet pour optimisation coût)

**Capacités Mesure & Validation:**
13. **Logging requêtes** et retrievals pour debugging
14. **Slash command test** (`/alexandria-query`) pour valider contexte fusionné avant usage production
15. **Tracking manuel métriques** (interventions, commits parfaits, commentaires CodeRabbit)

**Bénéfices Mesurables Démontrés:**

- **Phase Documentation**: 85% réduction temps correction (30 min → 5 min)
- **Phase Code**: 95% réduction interventions manuelles (15-20 → 1 par semaine)
- **Phase Review**: 80% réduction commentaires CodeRabbit (5-8 → 1.2 par commit)
- **Phase Tests**: 85% réduction bugs fonctionnels (8-10 → 1 par 5 features)
- **Configuration nouveau projet**: De 2-3 semaines rodage → 2 heures setup
