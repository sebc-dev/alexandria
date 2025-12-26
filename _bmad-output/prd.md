---
stepsCompleted: [1, 2, 3, 4, 7, 8, 9, 10, 11]
inputDocuments:
  - '/home/negus/dev/alexandria/_bmad-output/project-planning-artifacts/product-brief-alexandria-2025-12-24.md'
documentCounts:
  briefs: 1
  research: 0
  brainstorming: 0
  projectDocs: 0
workflowType: 'prd'
lastStep: 11
project_name: 'alexandria'
user_name: 'Negus'
date: '2025-12-25'
completedDate: '2025-12-26'
---

# Product Requirements Document - alexandria

**Author:** Negus
**Date:** 2025-12-25

## Executive Summary

Alexandria est un système de gouvernance technique automatisée pour Claude Code, conçu pour éliminer la dérive technique causée par les agents IA. Le système agit comme un **Active Compliance Filter** qui fusionne proactivement les conventions projet avec la documentation technique pertinente, garantissant que Claude Code génère du code conforme dès la première itération plutôt que de détecter les violations après coup.

**Objectif principal:** Transformer le développement assisté par IA en passant de 3-5 itérations par feature (avec rappels manuels constants) à 1 itération parfaite, transformant Claude Code d'un "assistant qui hallucine" en "pair programmer qui connaît le projet par cœur".

**Le problème résolu:** En tant que développeur multi-projets utilisant intensivement Claude Code, vous faites face à une friction cognitive constante. Les agents IA accèdent à une connaissance universelle générique ou obsolète, introduisant de la dette technique invisible, des violations de conventions, et nécessitant 10+ minutes par feature à rappeler manuellement les conventions avec 3-5 cycles de correction par tâche.

**Utilisateurs cibles:** Vous-même (développeur jonglant entre emploi full-time et agence web) comme utilisateur primaire, et Claude Code (agent IA consommant le contexte) comme utilisateur secondaire.

### What Makes This Special

Alexandria se différencie par trois innovations majeures:

**1. Active Compliance Filter (Architecture Stratifiée 3 Layers)**
- **Layer 1 - Conventions:** Règles non-négociables présentées comme des "lois" à l'agent
- **Layer 2 - Documentation:** APIs et frameworks comme "vocabulaire contraint" contextualisé par Layer 1
- **Layer 3 - Reformulation:** LLM fusionnant Conv + Doc, éliminant les patterns contradictoires

Cette stratification empêche l'agent de "choisir" entre approches contradictoires - contrairement aux RAG traditionnels qui retournent de l'information brute sans hiérarchie.

**2. Approche Proactive vs Réactive**
- Prévient en amont plutôt que détecter après génération complète
- Évite 70% des itérations inutiles et garantit cohérence structurelle
- Code conforme dès la première génération vs cycles coûteux de correction (2-3x tokens)

**3. Auto-Hébergement avec Contrôle Total**
- Contrôle total vs solutions externes (Letta, Cognee) avec leurs limites et coûts
- Architecture adaptable aux besoins évolutifs
- Personnalisation illimitée et indépendance complète

**Le moment "wow":** Quand développer avec Claude Code passe de "10 min de rappels manuels" à "30 sec de génération parfaite".

## Project Classification

**Technical Type:** Developer Tool
**Domain:** Scientific/AI
**Complexity:** Medium
**Project Context:** Greenfield - nouveau projet
**Périmètre technique:** Claude Code CLI + Plan Max (pas d'API/SDK externe)

**Implications de classification:**

**Developer Tool** nécessite:
- Documentation API/Skills complète et exemples d'usage
- Installation facilitée (Docker Hub, Marketplace)
- Intégration fluide avec l'écosystème Claude Code (MCP server, skills, slash commands, sub-agents)

**Scientific/AI Domain** requiert:
- Validation méthodologique de l'architecture RAG stratifiée
- Métriques de performance rigoureuses (conformité, itérations, interventions manuelles)
- Reproductibilité et documentation technique détaillée
- Standards de qualité élevés pour prouver la valeur du concept

**Complexity Medium** implique:
- Architecture technique sophistiquée mais sans régulations externes
- Validation approfondie nécessaire avant scaling
- MVP focalisé sur validation du concept core avant features avancées

## Success Criteria

### User Success

Le succès d'Alexandria se mesure par l'**élimination de la friction cognitive** dans le workflow de développement assisté par IA.

**Métriques Comportementales Clés:**

1. **Interventions Manuelles (Quotidien)**
   - **Actuellement**: 3-5 arrêts/jour pour fournir contexte/conventions à Claude Code
   - **Cible**: ≤1 intervention/jour
   - **Idéal**: 0 intervention (fonctionnement 100% automatique)
   - **Mesure**: Nombre d'arrêts quotidiens pour inclure manuellement documentation ou conventions

2. **Itérations par Feature**
   - **Actuellement**: 1-3 cycles de correction par feature/story
   - **Cible**: 1 itération (code parfait dès première génération)
   - **Mesure**: Nombre moyen d'itérations nécessaires pour finaliser une feature

3. **Taux de Commits Parfaits**
   - **Définition**: Commit avec ≤2 commentaires CodeRabbit (mineurs)
   - **Cible**: 80% (4 commits sur 5)
   - **Mesure**: % de commits validés sans modifications substantielles post-review

**Indicateurs de Réussite Utilisateur:**

- **Confiance**: Review en scan diagonal vs ligne par ligne
- **Stress**: Maintien à 5/10 même avec montée en charge (vs 7-8/10 anticipé sans Alexandria)
- **Productivité**: Gestion fluide de 3-4 projets simultanés (vs 2-3 actuellement)
- **Autonomie**: Claude Code fonctionne sans supervision constante

**Moment "Ça Marche" (Validation Initiale):**

1 semaine complète sans aller-retour de recadrage avec Claude Code et avec <2 commentaires par review CodeRabbit.

**Succès Ultime:**

Terminer un projet client complet avec 100% de "journées parfaites" (métriques quotidiennes atteintes sans exception).

### Business Success

Alexandria sert un **double objectif**: résoudre un problème concret de qualité de code ET maîtriser les architectures RAG.

**Court Terme (3 mois):**

1. **Déploiement Production**
   - Alexandria fonctionnel sur 2 machines (PC travail + serveur maison)
   - Infrastructure Docker stable (PostgreSQL + pgvector)
   - Skills Claude Code opérationnels via Marketplace

2. **Validation Technique**
   - Architecture RAG stratifiée (Conv > Doc > Reformulation) prouvée
   - Active Compliance Filter validé sur cas réels
   - Minimum 3 projets configurés avec conventions + documentation

3. **Stabilité Opérationnelle**
   - 1 semaine complète avec métriques quotidiennes atteintes
   - Zéro régression dans workflow existant
   - Intégration fluide avec CodeRabbit

**Moyen Terme (6-12 mois):**

1. **Features Avancées**
   - Mémoire Long Terme: Indexation logs Claude Code avec recherche sémantique
   - Gestion fluide de 5+ projets simultanés

2. **Scalabilité Prouvée**
   - 0 intervention manuelle par jour (80% du temps)
   - Maintenance minimale (ajout occasionnel conventions/docs)
   - Temps maintenance <30 min/semaine

3. **Maîtrise Apprentissage**
   - Architecture RAG stratifiée complètement maîtrisée et documentée
   - Code source organisé, commenté, maintenable
   - Potentiel open-source: Repo GitHub présentable

**Indépendance Agence:**

Alexandria est un **facilitateur**, pas un pré-requis. L'agence peut démarrer avec clients même si Alexandria n'est pas parfait - il rendra simplement le développement plus fluide et réduira le stress technique.

### Technical Success

**Approche Pragmatique - Validation par l'Usage:**

Plutôt que de fixer des métriques techniques arbitraires, le succès technique est défini par:

**Performance:**
- **Critère**: Assez rapide pour ne pas casser le flow de développement
- **Validation**: Vous ne devez pas "attendre" après Alexandria
- **Mesure indirecte**: Si les métriques utilisateur sont atteintes, la performance est acceptable

**Qualité du Retrieval:**
- **Critère**: Les conventions/docs retournées sont celles dont vous avez réellement besoin
- **Validation**: Pertinence ressentie lors de l'usage
- **Mesure indirecte**: Reflétée dans le taux d'interventions manuelles

**Qualité de Reformulation:**
- **Critère**: Le code généré respecte les conventions injectées
- **Validation**: Mesurée par le North Star KPI (conformité 100%)
- **Mesure**: % de conformité aux conventions dans le code généré

**Stabilité Infrastructure:**
- **Critère**: Zéro bug bloquant le workflow
- **Validation**: Fonctionnement continu sans interruption
- **Mesure**: Disponibilité et fiabilité sur 2 semaines consécutives

**Principe d'Optimisation:**

Les optimisations techniques (temps de réponse, qualité retrieval, reformulation) se feront en fonction de l'usage réel et des retours d'expérience, pas de chiffres théoriques fixés à l'avance.

### Measurable Outcomes

**KPI North Star (Étoile Polaire):**

> **Zéro Hallucination de Règle Acceptée**
>
> L'agent Claude Code doit respecter **100% des conventions injectées** par Alexandria.
>
> **Mesure**: % de conformité aux conventions dans le code généré (détection via review CodeRabbit + manuelle).
>
> **Validation**: Un commit est conforme si aucune violation de convention n'est détectée.

**Métriques de Validation Intermédiaires:**

- **Semaine 1-4**: 60% conformité (phase rodage)
- **Mois 2-3**: 80% conformité (stabilisation)
- **Mois 4+**: 95-100% conformité (objectif atteint)

**KPIs Opérationnels (Tracking Quotidien):**

| Métrique | Baseline | Cible | Idéal | Mesure |
|----------|----------|-------|-------|--------|
| **Interventions manuelles** | 3-5/jour | ≤1/jour | 0/jour | Nombre d'arrêts pour contexte manuel |
| **Itérations/feature** | 1-3 cycles | 1 cycle | 1 cycle | Moyenne itérations jusqu'à validation |
| **Commits parfaits** | ~40% | 80% (4/5) | 100% | % commits avec ≤2 commentaires |

**KPIs de Projet (Milestones 3 Mois):**

✅ **Déploiement:**
- Alexandria installé et fonctionnel sur 2 machines
- Toutes dépendances Docker opérationnelles
- Skills intégrés dans Claude Code

✅ **Configuration Initiale:**
- ≥3 projets avec conventions complètes
- ≥5 documentations techniques indexées
- Liens Conv ↔ Doc validés

✅ **Validation Stabilité:**
- 1 semaine avec métriques quotidiennes atteintes
- Zéro bug bloquant workflow
- Intégration CodeRabbit fluide

**Principe de Mesure:**

Toutes les métriques doivent être:
- ✅ **Mesurables**: Chiffres concrets, pas d'estimations vagues
- ✅ **Actionnables**: Permettent d'identifier problèmes et ajustements
- ✅ **Centrées utilisateur**: Reflètent la valeur réelle
- ✅ **Alignées vision**: Supportent l'objectif "Active Compliance Filter"

## Product Scope

### MVP - Minimum Viable Product

Le MVP d'Alexandria se concentre sur le **Active Compliance Filter** fonctionnel avec les composants techniques minimaux nécessaires.

**1. Ingestion & Indexation de Contenu**

- **Upload manuel** de fichiers markdown (conventions et documentation)
- **Distinction manuelle** Convention vs Documentation lors de l'upload
- Stockage dans PostgreSQL avec pgvector pour recherche sémantique
- **Upload/CRUD via MCP tools** (`alexandria_upload_convention`) et slash command (`/alexandria-config`)

**2. Active Compliance Filter (Cœur du Système)**

Architecture en **3 layers obligatoires**:

**Layer 1 - Conventions (Priorité Absolue)**
- Stockage et indexation des règles non-négociables du projet
- Patterns imposés, interdictions, règles de linting
- Présentées comme "lois" à l'agent IA

**Layer 2 - Documentation Technique (Vocabulaire Contraint)**
- Stockage et indexation de documentation technique (APIs, frameworks)
- Contextualisée par les conventions Layer 1
- Liée aux conventions pertinentes

**Layer 3 - Reformulation (Synthèse Mono-Approche)**
- LLM de reformulation fusionnant Conv + Doc
- Génération d'un "guide d'implémentation contraint"
- Élimination des patterns contradictoires

**3. Recherche & Retrieval**

- **Recherche sémantique** avec pgvector
- **Liens intelligents** entre documentation et conventions
- Retrieval contextuel basé sur la requête de l'agent

**4. Intégration Claude Code**

- **Skills de récupération**: Interrogation d'Alexandria depuis Claude Code
- **Slash commands**: Commandes dédiées intégrant le skill Alexandria
- **Sub-agents**: Agents personnalisés avec Alexandria pré-configuré
- Serveur MCP pour l'interface avec Claude Code
- **Périmètre**: Claude Code CLI + Plan Max uniquement

**5. Workflow Utilisateur MVP**

```
1. Upload manuel → Distinction Conv/Doc
2. Indexation automatique → Liens Conv ↔ Doc
3. Interrogation via skill/command/sub-agent
4. Active Filter fusionne Conv + Doc via LLM
5. Contexte fusionné livré à Claude Code
6. Code généré conforme dès 1ère itération ✓
```

**6. Logging Basique**

- Logs d'utilisation d'Alexandria (requêtes, retrievals)
- Tracking manuel des métriques de succès
- Pas d'analytics automatisé dans le MVP

**MVP Success Criteria:**

Le MVP sera considéré comme **réussi** lorsque:

✅ **Déploiement Fonctionnel**
- Alexandria installé et opérationnel sur 2 machines
- Infrastructure Docker stable (PostgreSQL + pgvector)
- Skills Claude Code fonctionnels via Marketplace
- MCP server fonctionnel avec tous les tools accessibles

✅ **Active Compliance Filter Validé**
- Les 3 layers fonctionnent ensemble
- LLM de reformulation produit des guides cohérents
- Pas de patterns contradictoires dans le contexte fusionné

✅ **Validation Métrique (3 Mois)**
- ≤1 intervention/jour pendant 1 semaine consécutive
- 80% commits parfaits avec ≤2 commentaires CodeRabbit
- ≥3 projets configurés avec conventions + documentation

✅ **Stabilité Opérationnelle**
- 2 semaines consécutives avec métriques atteintes
- Zéro régression dans workflow existant
- Intégration harmonieuse avec CodeRabbit

### Growth Features (Post-MVP)

**Explicitement EXCLUS du MVP** pour maintenir le focus et livrer rapidement:

**❌ Ingestion Automatisée**
- Pas de crawling automatique de documentation (Crawl4AI, DocLing)
- Pas de mise à jour automatique des documentations
- **Rationale**: Upload manuel suffit pour valider l'approche core
- **Timeline**: Post-MVP (roadmap définie)

**❌ Détection Automatique Convention vs Documentation**
- Pas de classification automatique par IA
- L'utilisateur spécifie manuellement lors de l'upload
- **Rationale**: Simplification du MVP, la distinction est claire pour l'utilisateur
- **Timeline**: Potentiel enhancement futur si besoin avéré

**❌ Mémoire à Long Terme**
- Pas d'indexation des logs Claude Code
- Pas de recherche sémantique sur historique de développement
- Pas d'apprentissage des patterns utilisateur
- **Rationale**: Feature avancée, non-essentielle pour validation du concept core
- **Timeline**: Roadmap Phase 4 (après UI avancée et crawling auto)

**❌ Cross-Instance Synchronisation**
- Pas de synchronisation entre instances (PC travail ↔ PC perso ↔ Serveur)
- Instances indépendantes avec leurs propres données
- **Rationale**: Complexité technique élevée, valeur limitée pour utilisateur solo
- **Timeline**: Non prévu (désintérêt confirmé)

**❌ UI Web (Basique ou Avancée)**
- Pas d'interface web dans le MVP (ni basique ni avancée)
- Pas de dashboard ou visualisations
- **MVP**: Tout via MCP tools et slash commands uniquement
- **Rationale**: Intégration 100% dans Claude Code, pas besoin d'interface externe
- **Timeline**: Potentiel roadmap Phase 2 si besoin avéré (UI avancée avec intégration Jira/Confluence)

**❌ Analytics & Tracking Automatisé**
- Pas de dashboard pour visualiser les KPIs
- Pas de graphiques ou rapports automatiques
- Tracking manuel des métriques de succès
- **Rationale**: Validation manuelle suffit pour MVP
- **Timeline**: Post-MVP si besoin de monitoring avancé

**❌ Reranking Avancé**
- Pas de stratégies de reranking sophistiquées
- Recherche sémantique basique avec pgvector suffit
- **Rationale**: Optimisation prématurée, valider l'approche d'abord
- **Timeline**: Enhancement futur si qualité retrieval insuffisante

### Vision (Future)

Si le MVP est validé avec succès, Alexandria évoluera selon cette roadmap progressive:

**Phase 1: Crawling Semi-Automatique (Court Terme)**

- Intégration Crawl4AI pour ingestion de documentation web
- Crawling manuel déclenché par l'utilisateur (pas encore automatique)
- Parsing et indexation de docs HTML/PDF
- **Objectif**: Faciliter l'ajout de nouvelles documentations techniques

**Phase 2: UI Avancée + Gestion de Projet (Moyen Terme)**

- Interface web sophistiquée pour gestion complète
- Intégration Jira/Confluence pour synchronisation de données projet
- Dashboard de visualisation des conventions/docs
- Gestion avancée des liens Conv ↔ Doc
- **Objectif**: Améliorer l'expérience utilisateur et productivité

**Phase 3: Crawling Automatique (Moyen Terme)**

- Crawling automatique périodique des documentations
- Détection automatique de mises à jour (nouvelles versions frameworks/APIs)
- Notification utilisateur des changements détectés
- **Objectif**: Documentation toujours à jour sans intervention manuelle

**Phase 4: Mémoire Long Terme (Long Terme)**

- Indexation complète des logs Claude Code
- Recherche sémantique sur historique de développement
- Apprentissage des patterns spécifiques de l'utilisateur
- Recommandations intelligentes de conventions basées sur l'usage
- **Objectif**: Alexandria "apprend" de l'historique pour améliorer continuellement

**Features Avancées Additionnelles:**

🎯 **Reranking Sophistiqué**
- Stratégies de reranking avancées pour améliorer qualité du retrieval
- Hybrid search (sémantique + keyword + métadonnées)
- Scoring personnalisé basé sur contexte projet

🎯 **Site Web & Documentation Publique**
- Site web officiel Alexandria
- Documentation complète pour utilisateurs et contributeurs
- Guides d'intégration et exemples d'usage

🎯 **Open-Source & Communauté**
- Publication du repo GitHub
- Contribution communautaire (issues, PRs, discussions)
- Potentiel package/distribution facilitée

**Expansion d'Écosystème:**

🌐 **Usage Professionnel**
- Déploiement au travail (environnement Java/Groovy/Spring/Grails)
- Validation dans contexte entreprise
- Retours d'expérience multi-environnements

## User Journeys

### Journey 1: Negus - De la Friction au Flow

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

### Journey 2: Negus Admin - Onboarding d'un Nouveau Projet

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

### Journey 3: Claude Code - De la Confusion à la Conformité

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

### Journey Requirements Summary

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

## Developer Tool Specific Requirements

### Project-Type Overview

Alexandria est un **Developer Tool** conçu pour s'intégrer nativement dans l'écosystème Claude Code. Contrairement aux outils classiques nécessitant des interfaces séparées, Alexandria fonctionne entièrement via le Marketplace Claude Code et son serveur MCP, permettant une expérience fluide et intégrée directement dans le workflow de développement.

**Philosophie d'intégration:**
- Zéro friction d'installation (tout via Marketplace)
- Zéro interface externe (100% dans Claude Code)
- Zéro configuration manuelle complexe (MCP tools + slash commands)

### Technical Architecture Considerations

**Agnosticité du Contenu Traité (Non de l'Implémentation)**

Alexandria est conçu pour **traiter** des conventions et documentation pour n'importe quel langage de programmation ou stack technologique:

- **Contenu traité agnostique**: Peut stocker et gérer conventions pour Python, TypeScript, Java, Groovy, Go, Rust, etc.
- **Framework agnostique**: Documentation FastAPI, Spring, NestJS, Django, Express, etc.
- **Domaine agnostique**: Backend, frontend, mobile, DevOps, data science, etc.

**Implémentation d'Alexandria (Stack Technique Fixe - MVP):**

- **Runtime**: Bun 1.3.5 (JavaScript runtime ultra-rapide, acquis par Anthropic déc 2025, 3-4x plus performant que Node.js)
- **Framework Web**: Hono 4.11.1 (TypeScript, framework web minimaliste ~12KB, edge-ready, multi-runtime)
- **Langage**: TypeScript 5.9.7 (strict mode, typage statique complet, dernière branche 5.x stable)
- **ORM**: Drizzle ORM 0.36.4 (type-safe, performant, support natif pgvector via `drizzle-orm/pg-core`)
- **Validation**: Zod 4.2.1 (17 déc 2025, bundle 57% plus petit, 3x plus rapide, runtime validation type-safe, MCP protocol compliance)
- **Base de données**: PostgreSQL 17.7 (13 nov 2025) avec extension pgvector 0.8.1 (iterative scans, HNSW optimisé)
- **Embeddings**: OpenAI Embeddings API (text-embedding-3-small ou text-embedding-3-large)
- **LLM Reformulation**: Claude Haiku 4.5 (claude-haiku-4-5, 15 oct 2025, 73.3% SWE-bench, $1/M input $5/M output) via sub-agent Claude Code (Layer 3)
- **Architecture**: Hexagonale (permet expérimentation et swap de stack si besoin)

**Notes importantes:**
- Bun acquis par Anthropic (2 déc 2025): excellente compatibilité future garantie avec Claude Code
- Zod 4.2.1 choisi (dernière version, breaking changes depuis 3.x mais performances 3x meilleures, bundle 57% plus petit)
- PostgreSQL 17.7 recommandé pour pgvector 0.8.1 (abandonne support PostgreSQL 12)
- Claude Haiku 4.5 significativement meilleur que 3.5 (73.3% SWE-bench Verified)
- Drizzle ORM 0.36.4 stable, v1.0 en pré-release (fév 2025) à surveiller pour migration future

**Principe fondamental:** Ce qui compte n'est pas le langage **d'implémentation** d'Alexandria, mais sa capacité à gérer conventions + documentation de manière structurée pour **n'importe quel langage de projet utilisateur**. Le système RAG stratifié fonctionne indépendamment des technologies des projets traités.

**Gestion Multi-Technologie:**

Via la table pivot `convention_technologies`:
- Une convention peut déclarer plusieurs technologies: `["python", "fastapi", "pydantic"]`
- Une documentation appartient à une technologie: `technology_id: "fastapi"`
- Le linking automatique fonctionne pour toute combinaison de technologies

**Architecture Hexagonale (Ports & Adapters) - Contrainte Fondamentale**

Alexandria **DOIT** être implémenté selon l'architecture hexagonale pour garantir:

**Objectifs de l'Hexagonale:**

1. **Expérimentation Technique Facilitée**
   - Double objectif du projet: résoudre le problème concret ET maîtriser les architectures RAG
   - Permet de swap les technologies d'implémentation sans refonte du core métier
   - Exemples de swaps possibles:
     - PostgreSQL+pgvector → Qdrant/Weaviate/Chroma
     - Claude Haiku reformulation → GPT-4/Mistral/Llama local
     - Bun+Hono → Node.js+Express, Deno, Python+FastAPI
     - Embeddings OpenAI → Voyage/Cohere/local (sentence-transformers)

2. **Évolution Post-MVP Non-Destructive**
   - Roadmap prévoit changements technologiques (Crawl4AI, UI web, Jira/Confluence)
   - Ajout de nouveaux adapters sans toucher au domaine métier
   - Réduction drastique du risque de régression

3. **Testabilité Critique**
   - NFR23 exige tests unitaires et d'intégration
   - Ports mockables pour tester la logique métier isolément
   - Tests d'intégration par adapter (PostgreSQL, Qdrant, etc.)
   - Validation pipeline Layer 1→2→3 indépendamment

4. **Isolation Naturelle des 3 Layers RAG**
   - Layer 1, 2, 3 correspondent à des ports distincts du domaine
   - Chaque layer testable et remplaçable indépendamment

**Ports Primaires Identifiés (Driven by External Actors):**

- `MCPServerPort`: Interface MCP protocol (7 tools: retrieve, validate, upload, list, read, delete, list_projects)
- `SkillPort`: Interface skills auto-invoqués par Claude Code
- `SlashCommandPort`: Interface commandes interactives utilisateur

**Ports Secondaires Identifiés (Driving Infrastructure):**

- `ConventionRepositoryPort`: Storage + vector search conventions (Layer 1)
- `DocumentationRepositoryPort`: Storage + retrieval documentation technique (Layer 2)
- `TechnologyLinkerPort`: Gestion liens convention ↔ technologies via pivot table
- `EmbeddingGeneratorPort`: Génération embeddings vectoriels (OpenAI, Voyage, local)
- `LLMRefinerPort`: Reformulation LLM Layer 3 via sub-agent Claude Code (pas d'API directe)
- `LoggerPort`: Observabilité et metrics (NFR26-33)

**Adapters Initiaux (MVP - Bun/Hono/TypeScript + Drizzle + Zod):**

- `DrizzleConventionAdapter` (implémente ConventionRepositoryPort avec Drizzle ORM + pgvector, schemas Zod pour validation)
- `DrizzleDocumentationAdapter` (implémente DocumentationRepositoryPort avec Drizzle ORM)
- `DrizzleTechnologyLinkerAdapter` (implémente TechnologyLinkerPort avec Drizzle ORM, gestion pivot table)
- `ClaudeCodeSubAgentAdapter` (implémente LLMRefinerPort - invoque sub-agent `alexandria-reformulation` configuré avec Haiku)
- `OpenAIEmbeddingAdapter` (implémente EmbeddingGeneratorPort avec OpenAI Embeddings API, validation Zod responses)
- `BunConsoleLoggerAdapter` (implémente LoggerPort avec structured logging JSON)
- `ZodValidationMiddleware` (validation MCP tools inputs/outputs, configuration .env, slash commands parameters)

**Adapters Futurs (Expérimentation Post-MVP):**

- `QdrantVectorAdapter`, `ChromaVectorAdapter`, `WeaviateVectorAdapter` (alternatives vector stores)
- Adapters reformulation alternatifs (sub-agents avec différents modèles ou API directes si besoin)
- `VoyageEmbeddingAdapter`, `CohereEmbeddingAdapter` (alternatives embeddings API)
- `LocalEmbeddingAdapter` (sentence-transformers via ONNX ou transformers.js, pas d'API externe)
- `PrometheusMetricsAdapter`, `DatadogAdapter` (alternatives observabilité)
- Runtime alternatifs: Node.js, Deno, Python+FastAPI (si swap nécessaire)

**Bénéfices Concrets pour KPIs:**

- **Maintenabilité** (NFR21-25): Code découplé, documentation claire des responsabilités
- **Tests** (NFR23): Coverage facilité par isolation des ports
- **Expérimentation**: Swap rapide de stack pour comparer performances/coûts
- **Évolution**: Roadmap post-MVP implémentable sans dette technique

**Rôle de Drizzle ORM dans l'Architecture Hexagonale:**

Drizzle ORM est utilisé **exclusivement dans les adapters** (ports secondaires), jamais dans le domain core:

**Bénéfices Drizzle:**
- Type-safety complète: Schemas Drizzle → Types TypeScript inférés
- Queries type-safe: Autocomplétion complète, erreurs compile-time
- Support pgvector natif: Requêtes vectorielles optimisées
- Migrations avec Drizzle Kit: Gestion schéma versionnée
- Performance: ~10x moins d'overhead que Prisma
- Bun compatible: Excellent support, zero config

**Adapters utilisant Drizzle:**
- `DrizzleConventionAdapter`: CRUD conventions + vector search Layer 1
- `DrizzleDocumentationAdapter`: CRUD documentation technique
- `DrizzleTechnologyLinkerAdapter`: Gestion pivot table `convention_technologies`

**Principe hexagonal respecté:**
Le domain core **ne connaît pas Drizzle**, il utilise uniquement les ports abstraits (`ConventionRepositoryPort`, etc.). Si swap vers Prisma/TypeORM nécessaire, seuls les adapters changent.

**Rôle de Zod dans l'Architecture Hexagonale:**

Zod assure la **validation aux boundaries** (entrées/sorties du système):

**Cas d'usage critiques:**

1. **MCP Protocol Compliance (NFR11)**
   - Validation inputs MCP tools (retrieve, validate, upload, list, read, delete)
   - Validation outputs format JSON conforme
   - Schemas Zod → Types TypeScript (type-safety end-to-end)

2. **Configuration Validation (NFR7 - Fail Fast)**
   - Validation `.env` au startup (ALEXANDRIA_DB_URL, OPENAI_API_KEY)
   - Erreur immédiate si credentials manquantes ou invalides
   - Types inférés pour config utilisée dans l'app

3. **Slash Commands Parameters (NFR13)**
   - Validation paramètres optionnels avec defaults
   - Messages d'erreur clairs si invalides
   - Type-safe command routing

4. **API Boundaries (Ports Primaires)**
   - Validation inputs à l'entrée des ports (`MCPServerPort`, `SlashCommandPort`)
   - Garantit contrat des ports respecté
   - Prévention injections et données malformées

5. **External Services Validation (Ports Secondaires)**
   - Validation réponses OpenAI API (embeddings format)
   - Validation données PostgreSQL avant insertion
   - Detection erreurs API early

**Middleware Zod:**
`ZodValidationMiddleware` intégré dans Hono pour validation automatique requêtes/réponses MCP.

**Principe hexagonal respecté:**
Zod valide aux **frontières** uniquement (ports), le domain core travaille avec types TypeScript déjà validés.

**Distribution & Installation**

**Marketplace Claude Code - Point d'Entrée Unique:**

Tout passe par le Marketplace Claude Code pour garantir une installation simplifiée:

1. **Skills Alexandria:**
   - `alexandria-context` (skill principal)
   - `alexandria-validate` (skill de validation)

2. **Slash Commands:**
   - `/alexandria-query`
   - `/alexandria-validate`
   - `/alexandria-config`
   - `/alexandria-list`
   - `/alexandria-read`
   - `/alexandria-delete`

3. **Sub-Agent:**
   - `alexandria-reformulation` (Agent Haiku Layer 3)

4. **MCP Server:**
   - `alexandria-mcp-server` avec ses tools

**Skills Crawl4AI & DocLing:**

Skills complémentaires (post-MVP) disponibles via Marketplace:
- Contiennent les images Docker nécessaires
- Permettent aux développeurs de récupérer et lancer les containers
- Installation automatisée via le Marketplace

**Aucun Package Manager Externe:**

- ❌ Pas de `npm install alexandria`
- ❌ Pas de `pip install alexandria`
- ✅ Installation 100% via Marketplace Claude Code

**Configuration Requise:**

Variables d'environnement minimales:
- `ALEXANDRIA_DB_URL` - Connexion PostgreSQL + pgvector (ex: `postgresql://user:pass@localhost:5432/alexandria`)
- `OPENAI_API_KEY` - Clé API OpenAI pour génération embeddings
- Configuration additionnelle via fichier `.env` ou MCP tool `alexandria_config`

**Notes:**
- Pas besoin de `CLAUDE_API_KEY` car la reformulation Layer 3 utilise un sub-agent Claude Code (`alexandria-reformulation`) invoqué via le système de sub-agents, pas d'appel API direct
- Runtime: Bun (compatible avec ecosystem npm/node, installation via `bun install`)

### Integration Architecture

**Skills Alexandria**

**1. `alexandria-context` (Skill Principal - Auto-Invoqué)**

- **Trigger:** Auto-invoqué par Claude Code pendant génération de code/documentation
- **Flow:**
  1. Reçoit requête contextuelle de Claude Code
  2. Appelle MCP tool `alexandria_retrieve_context`
  3. Délègue au sub-agent `alexandria-reformulation` (Layer 3)
  4. Retourne contexte fusionné à Claude Code
- **Usage:** Transparent pour l'utilisateur, fonctionne automatiquement

**2. `alexandria-validate` (Skill de Validation Post-Code)**

- **Trigger:** Invoqué après génération de code pour validation
- **Flow:**
  1. Reçoit code généré
  2. Appelle MCP tool `alexandria_validate_code`
  3. Compare code contre conventions projet
  4. Retourne rapport de conformité
- **Usage:** Auto-invoqué ou manuel via slash command

**Slash Commands**

**1. `/alexandria-query <question>` (Test & Debug)**

- **Objectif:** Test manuel des requêtes Alexandria avec affichage debug
- **Output:**
  - Contexte retrieval Layer 1 (conventions trouvées)
  - Contexte retrieval Layer 2 (docs liées via technologies)
  - Contexte reformulé Layer 3 (guide fusionné)
  - Métriques de pertinence et temps de réponse
- **Usage:** Validation efficacité des conventions/docs uploadées

**2. `/alexandria-validate <code_snippet>` (Validation Explicite)**

- **Objectif:** Validation explicite du code contre conventions (post-review)
- **Input:** Code snippet ou fichier
- **Output:**
  - Liste violations de conventions détectées
  - Suggestions de correction
  - Score de conformité
- **Usage:** Phase review manuelle ou debugging

**3. `/alexandria-config` (Menu Interactif CRUD)**

- **Objectif:** Point d'entrée interactif pour toutes les opérations CRUD
- **Menu Interactif:**
  1. Upload convention/documentation (Create)
  2. List conventions/documentations (List)
  3. View convention/documentation (Read)
  4. Delete convention/documentation (Delete)
  5. Setup nouveau projet avec technologies
- **Usage:** Gestion complète knowledge base via interface conversationnelle

**4. `/alexandria-list [type] [technology]` (Liste Rapide)**

- **Objectif:** Liste rapide des documents sans menu interactif
- **Paramètres optionnels:**
  - `type`: convention | documentation | all (défaut: all)
  - `technology`: filter par technologie spécifique
- **Output:** Liste documents avec ID, nom, type, technologies, date
- **Usage:** Consultation rapide de l'inventaire

**5. `/alexandria-read <document_id>` (Lecture Document)**

- **Objectif:** Afficher le contenu complet d'un document spécifique
- **Input:** `document_id`
- **Output:**
  - Contenu markdown complet
  - Métadonnées (type, technologies, projet, date création)
- **Usage:** Consulter une convention/doc avant modification ou référence

**6. `/alexandria-delete <document_id>` (Suppression Document)**

- **Objectif:** Supprimer un document de la knowledge base
- **Input:** `document_id`
- **Confirmation:** Demande confirmation avant suppression définitive
- **Process:** Supprime document + embeddings + liens `convention_technologies`
- **Output:** Confirmation suppression avec détails du document supprimé
- **Usage:** Nettoyage knowledge base, suppression docs obsolètes

**Sub-Agent Alexandria**

**`alexandria-reformulation` (Agent Haiku - Layer 3 Spécialisé)**

- **Modèle:** Claude Haiku 3.5 (optimisation coût vs Sonnet)
- **Invocation:** Via système de sub-agents Claude Code (pas d'API directe depuis Alexandria)
- **Rôle:** Fusion conventions + docs en guide cohérent mono-approche
- **Responsabilités:**
  1. Reçoit conventions (Layer 1) + documentation (Layer 2) brutes
  2. Analyse contradictions entre sources
  3. Élimine syntaxe obsolète vs actuelle
  4. Fusionne en guide d'implémentation contraint
  5. Valide structure et pertinence
  6. Retourne contexte optimisé à Claude Code
- **Autonomie:** Fonctionnement automatique, pas d'intervention utilisateur
- **Intégration:** Alexandria invoque le sub-agent via `ClaudeCodeSubAgentAdapter` (port hexagonal)

**MCP Server Architecture**

**`alexandria-mcp-server`**

Serveur MCP exposant les tools Alexandria pour intégration Claude Code.

**MCP Tools:**

**1. `alexandria_retrieve_context`**

- **Fonction:** Retrieval RAG Layer 1+2 (pgvector + JOIN technologies)
- **Input:** Query utilisateur (text)
- **Process:**
  - Recherche vectorielle conventions pertinentes (Layer 1)
  - Récupère `technology_id` des conventions via table pivot
  - JOIN SQL pour ramener docs liées aux mêmes technologies (Layer 2)
- **Output:** Conventions + Documentation brutes (avant reformulation)

**2. `alexandria_validate_code`**

- **Fonction:** Validation code contre conventions projet
- **Input:** Code snippet + project_id
- **Process:**
  - Récupère conventions projet via project_id
  - Compare code contre règles via LLM
  - Détecte violations et non-conformités
- **Output:** Rapport de conformité avec violations détectées

**3. `alexandria_upload_convention`**

- **Fonction:** Upload nouvelle convention/doc dans knowledge base
- **Input:**
  - Fichier markdown (convention ou documentation)
  - Type: `convention` | `documentation`
  - Technologies: `["python", "fastapi"]` (pour conventions)
  - Technology_id: `"fastapi"` (pour documentation)
  - Project_id: identifiant projet
- **Process:**
  - Parse fichier markdown
  - Génère embeddings vectoriels (pgvector)
  - Insère dans PostgreSQL avec métadonnées
  - Crée liens via `convention_technologies` si convention
- **Output:** Confirmation upload avec ID généré

**4. `alexandria_list_projects`**

- **Fonction:** Liste projets configurés avec leurs conventions
- **Input:** (optionnel) filter par technologies
- **Output:**
  - Liste projets avec métadonnées
  - Nombre conventions par projet
  - Nombre documentations par technologie
  - Technologies configurées

**5. `alexandria_list_documents`**

- **Fonction:** Liste détaillée des documents (conventions et documentations)
- **Input:**
  - `project_id` (optionnel) - Filter par projet
  - `type` (optionnel) - convention | documentation | all (défaut: all)
  - `technology` (optionnel) - Filter par technologie spécifique
- **Output:**
  - Liste documents avec métadonnées complètes:
    - `document_id`, `name`, `type`, `technologies`, `project_id`
    - `created_date`, `size` (nombre caractères/tokens)
    - `embedding_count` (nombre chunks vectorisés)

**6. `alexandria_read_document`**

- **Fonction:** Récupère le contenu complet d'un document spécifique
- **Input:** `document_id`
- **Output:**
  - Contenu markdown complet
  - Métadonnées:
    - Type (convention/documentation)
    - Technologies associées
    - Project_id
    - Date création et dernière modification
    - Informations embeddings (nombre, dimensions)

**7. `alexandria_delete_document`**

- **Fonction:** Supprime un document et toutes ses données associées
- **Input:** `document_id`
- **Process:**
  - Supprime document de la table principale
  - Supprime tous les embeddings vectoriels associés
  - Supprime tous les liens dans `convention_technologies`
  - Cascade suppression complète
- **Output:**
  - Confirmation suppression
  - Détails du document supprimé (nom, type, technologies)
  - Nombre d'embeddings supprimés
  - Nombre de liens supprimés

### API Surface & Integration Points

**Pas d'API REST Externe:**

Alexandria n'expose **pas d'API REST** publique. Toute interaction se fait via:
- Skills (invoqués par Claude Code)
- Slash Commands (tapés par utilisateur)
- MCP Tools (appelés par skills/sub-agents)

**Protocole MCP Uniquement:**

- Communication Claude Code ↔ Alexandria via protocole MCP
- Sécurité et authentification gérées par Claude Code
- Pas besoin de gérer tokens/auth custom

### Documentation Requirements

**Documentation via GitHub Pages:**

**1. Guide d'Installation**

- Prérequis (Docker, Claude Code)
- Installation via Marketplace
- Configuration PostgreSQL + pgvector
- Variables d'environnement
- Vérification installation

**2. Descriptif des Composants**

- **Skills:** Fonctionnement `alexandria-context` et `alexandria-validate`
- **Slash Commands:** Usage de tous les commands (query, validate, config, list, read, delete)
- **Sub-Agent:** Rôle `alexandria-reformulation` et architecture Layer 3
- **MCP Server:** Description server et 7 tools disponibles (retrieve, validate, upload, list_projects, list_documents, read, delete)

**3. Exemples d'Utilisation**

- Onboarding nouveau projet (upload conventions + docs via `/alexandria-config`)
- Configuration technologies et linking
- Consultation knowledge base (`/alexandria-list`, `/alexandria-read`)
- Maintenance (suppression docs obsolètes via `/alexandria-delete`)
- Test requêtes avec `/alexandria-query`
- Workflow quotidien avec auto-invocation `alexandria-context`
- Validation post-code avec `alexandria-validate`

**Pas de Code Examples/Migration Guides dans MVP:**

- ❌ Pas d'exemples de code programmatique
- ❌ Pas de guides de migration
- ✅ Focus sur usage pratique via Claude Code

### Implementation Considerations

**Workflow Développeur Type:**

```
1. Installation (une fois)
   - Install skills via Marketplace
   - Configure PostgreSQL + MCP server
   - Setup variables d'environnement

2. Onboarding Projet (par projet)
   - `/alexandria-config` pour upload conventions
   - `/alexandria-config` pour upload docs techniques
   - Déclare technologies pour linking automatique

3. Validation Setup (test)
   - `/alexandria-query "comment créer API endpoint FastAPI?"`
   - Vérifie contexte retourné (conventions + docs fusionnées)
   - Ajuste si nécessaire

4. Usage Quotidien (automatique)
   - Claude Code invoque automatiquement `alexandria-context`
   - Code généré conforme dès première itération
   - `/alexandria-validate` en post-review si besoin

5. Maintenance Knowledge Base (périodique)
   - `/alexandria-list` pour voir inventaire complet
   - `/alexandria-read <doc_id>` pour consulter une convention/doc
   - `/alexandria-delete <doc_id>` pour supprimer docs obsolètes
   - `/alexandria-config` menu interactif pour gestion CRUD complète
```

**Séparation des Responsabilités:**

- **Skills:** Orchestration et invocation MCP tools
- **Sub-Agent:** Intelligence Layer 3 (reformulation)
- **MCP Server:** Logique métier RAG + PostgreSQL
- **PostgreSQL + pgvector:** Stockage et recherche vectorielle

**Pas d'Interface Web:**

- ❌ Pas de frontend à développer dans MVP
- ❌ Pas de serveur web séparé
- ✅ Interaction 100% via Claude Code

**Scalabilité Architecture:**

- PostgreSQL handle multi-projets via `project_id`
- pgvector optimisé pour millions d'embeddings
- MCP server stateless, peut scaler horizontalement (futur)
- Sub-agent Haiku économique en coûts

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach:** Platform MVP - Fondations pour Apprentissage & Validation

Alexandria suit une approche **Platform MVP** focalisée sur:
1. **Validation du concept core**: Prouver que l'Active Compliance Filter 3 layers fonctionne
2. **Apprentissage technique**: Maîtriser les architectures RAG stratifiées
3. **Solution immédiate**: Résoudre la friction cognitive dans le workflow quotidien

**Critère de Succès MVP:**
Le MVP sera considéré réussi quand le développement quotidien devient fluide grâce au contexte optimal fourni par Alexandria, mesuré par ≤1 intervention/jour et 80% commits parfaits pendant 1 semaine consécutive.

**Ressources Requises:**
- **Équipe**: Solo développeur (vous-même)
- **Timeline**: 3 mois pour MVP fonctionnel
- **Infrastructure**: 2 machines (PC travail + serveur maison), Docker, PostgreSQL + pgvector
- **Budget**: Coûts LLM optimisés (Haiku 3.5 pour reformulation)

### MVP Feature Set (Phase 1 - 3 Mois)

**Core User Journeys Supportés:**

1. **Journey Principal - Workflow 4 Phases (Negus)**
   - Génération documentation (epics/stories) avec contexte Alexandria
   - Écriture code conforme via auto-invocation `alexandria-context`
   - Review simplifiée (scan diagonal vs ligne par ligne)
   - Tests fonctionnels avec moins de bugs

2. **Journey Admin - Onboarding Projet (Negus)**
   - Upload conventions/docs via `/alexandria-config` et MCP tools
   - Configuration technologies et linking automatique
   - Validation setup via `/alexandria-query`
   - Configuration complète en 2h (vs 2-3 semaines)

3. **Journey IA - Transformation Claude Code**
   - Retrieval contexte via `alexandria_retrieve_context` (Layer 1+2)
   - Reformulation via sub-agent Haiku (Layer 3)
   - Génération code conforme dès première itération
   - Zéro hallucination de règles

**Must-Have Capabilities (MVP):**

**1. Active Compliance Filter Complet (3 Layers)**
- ✅ Layer 1: Recherche vectorielle conventions (pgvector)
- ✅ Layer 2: Linking automatique docs via `convention_technologies`
- ✅ Layer 3: Sub-agent reformulation (Haiku 3.5)

**2. Ingestion & Indexation**
- ✅ Upload manuel fichiers markdown
- ✅ Distinction manuelle Convention vs Documentation
- ✅ Configuration multi-technologies
- ✅ Stockage PostgreSQL + pgvector

**3. Intégration Claude Code Complète**
- ✅ Skills: `alexandria-context` (auto-invoqué), `alexandria-validate`
- ✅ Slash Commands: `/alexandria-query`, `/alexandria-validate`, `/alexandria-config`, `/alexandria-list`, `/alexandria-read`, `/alexandria-delete`
- ✅ Sub-Agent: `alexandria-reformulation`
- ✅ MCP Server: `alexandria-mcp-server` avec 7 tools (retrieve, validate, upload, list_projects, list_documents, read, delete)

**4. Validation & Observabilité**
- ✅ Logging requêtes et retrievals
- ✅ Test/debug via `/alexandria-query`
- ✅ Tracking manuel métriques (interventions, commits, reviews)

**5. Infrastructure & Déploiement**
- ✅ Docker containers (PostgreSQL + pgvector + MCP server)
- ✅ Déploiement sur 2 machines
- ✅ Distribution via Marketplace Claude Code
- ✅ Documentation GitHub Pages

**MVP Boundaries - Explicitement EXCLUS:**

- ❌ Crawling automatique documentation (Crawl4AI, DocLing)
- ❌ Détection auto Convention vs Documentation
- ❌ Mémoire Long Terme (indexation logs Claude Code)
- ❌ Cross-Instance Synchronisation
- ❌ UI Web (basique ou avancée)
- ❌ Analytics & tracking automatisé
- ❌ Reranking sophistiqué

**Rationale Exclusions:**
Ces features ajoutent de la complexité sans valider le concept core. Le MVP doit prouver que l'Active Compliance Filter fonctionne - le reste peut être ajouté itérativement si le concept est validé.

### Post-MVP Features

**Phase 2: Crawling Semi-Automatique (Court Terme - Post 3 Mois)**

**Objectif:** Faciliter l'ajout de nouvelles documentations techniques

**Features:**
- Intégration Crawl4AI pour ingestion documentation web
- Crawling manuel déclenché par utilisateur (pas automatique)
- Parsing et indexation docs HTML/PDF
- Skills Crawl4AI & DocLing via Marketplace

**Success Criteria Phase 2:**
- Ajout documentation nouvelle framework en <15 min (vs 1h+ manuel)
- Crawling de 5+ documentations officielles testées

**Phase 3: UI Avancée + Intégrations (Moyen Terme - 6-12 Mois)**

**Objectif:** Améliorer expérience utilisateur et productivité

**Features:**
- Interface web sophistiquée (si besoin avéré)
- Intégration Jira/Confluence pour synchronisation données projet
- Dashboard visualisation conventions/docs
- Gestion avancée liens Conv ↔ Doc

**Success Criteria Phase 3:**
- Gestion fluide de 5+ projets simultanés
- Maintenance <30 min/semaine

**Phase 4: Mémoire Long Terme (Long Terme - 12+ Mois)**

**Objectif:** Alexandria "apprend" de l'historique pour amélioration continue

**Features:**
- Indexation complète logs Claude Code
- Recherche sémantique sur historique développement
- Apprentissage patterns spécifiques utilisateur
- Recommandations intelligentes conventions basées sur usage

**Success Criteria Phase 4:**
- 0 intervention/jour (80% du temps)
- Recommandations contextuelles pertinentes

**Features Avancées Additionnelles (Opportunistes):**

🎯 **Reranking Sophistiqué** - Si qualité retrieval insuffisante
🎯 **Crawling Automatique** - Détection auto mises à jour docs
🎯 **Site Web Public** - Documentation complète et community
🎯 **Open-Source** - Publication repo GitHub si bénéfice communauté
🌐 **Usage Professionnel** - Déploiement travail (Java/Groovy/Spring/Grails)

### Risk Mitigation Strategy

**Technical Risks:**

**Risque #1: Architecture RAG Stratifiée Inefficace**
- **Impact**: Contexte fusionné non pertinent ou contradictoire
- **Probabilité**: Medium
- **Mitigation**:
  - Validation Layer par Layer pendant développement
  - Tests avec requêtes réelles sur projets existants
  - Itération rapide sur reformulation LLM (Layer 3)
  - Métriques de pertinence dès MVP
- **Fallback**: Simplifier à RAG 2 layers si Layer 3 ajoute peu de valeur

**Risque #2: Performance Recherche Vectorielle**
- **Impact**: Temps réponse trop lent, casse le flow
- **Probabilité**: Low
- **Mitigation**:
  - pgvector optimisé pour millions embeddings
  - Index HNSW pour recherche rapide
  - Cache résultats fréquents
  - Tests performance dès MVP
- **Fallback**: Hybrid search (vectorielle + keyword) si nécessaire

**Risque #3: Coûts LLM Reformulation**
- **Impact**: Coûts prohibitifs avec usage intensif
- **Probabilité**: Low
- **Mitigation**:
  - Haiku 3.5 (économique) vs Sonnet pour Layer 3
  - Cache reformulations identiques
  - Monitoring coûts dès MVP
- **Fallback**: Reformulation simplifiée ou désactivable

**Market Risks:**

**Risque #1: Concept Non Validé**
- **Impact**: Active Compliance Filter ne réduit pas friction
- **Probabilité**: Medium
- **Mitigation**:
  - Mesure métriques dès semaine 1 MVP
  - Validation empirique rapide (1 semaine avec métriques)
  - Itération basée sur données réelles
- **Validation**: Si ≤1 intervention/jour atteint en semaine 4, concept validé

**Risque #2: Complexité Setup Trop Élevée**
- **Impact**: Configuration 2h devient 2 jours
- **Probabilité**: Low-Medium
- **Mitigation**:
  - Documentation claire étape par étape
  - Scripts automatisation setup
  - Exemples conventions pré-configurés
- **Validation**: Onboarding nouveau projet réussi en <3h

**Resource Risks:**

**Risque #1: Temps Développement Sous-Estimé**
- **Impact**: MVP prend 6 mois vs 3 mois prévus
- **Probabilité**: Medium-High (projet apprentissage)
- **Mitigation**:
  - Scope strict MVP - pas de feature creep
  - Priorisation ruthless des must-have
  - Acceptation que apprentissage prend du temps
- **Contingency**: Phase 1 allongée acceptable car double objectif (solution + apprentissage)

**Risque #2: Abandon du Projet**
- **Impact**: Projet jamais terminé
- **Probabilité**: Low-Medium
- **Mitigation**:
  - MVP minimal réellement utile (motivation maintenue)
  - Bénéfices immédiats dès fonctionnement partiel
  - Pas de pression externe (projet personnel)
- **Validation**: Si amélioration workflow visible semaine 4-8, motivation maintenue

**Risque #3: Agence Démarre Avant MVP Terminé**
- **Impact**: Besoin Alexandria avant qu'il soit prêt
- **Probabilité**: Medium
- **Mitigation**:
  - Alexandria est facilitateur, pas pré-requis
  - Agence peut démarrer sans Alexandria
  - MVP priorisé si agence démarre (motivation accrue)
- **Plan B**: Workflow manuel classique fonctionne (état actuel)

**Success Validation Checkpoints:**

- **Semaine 4**: Premier retrieval fonctionnel, test `/alexandria-query`
- **Semaine 8**: Layer 3 reformulation opérationnel, premier code généré conforme
- **Semaine 12 (3 mois)**: MVP complet, 1 semaine avec métriques atteintes


## Functional Requirements

### Convention & Documentation Management

- **FR1**: Users can upload convention documents in markdown format to the knowledge base
- **FR2**: Users can upload technical documentation in markdown format to the knowledge base
- **FR3**: Users can manually specify document type (convention vs documentation) during upload
- **FR4**: Users can list all conventions and documentations with filtering options
- **FR5**: Users can filter documents by type (convention, documentation, or all)
- **FR6**: Users can filter documents by associated technology
- **FR7**: Users can filter documents by project identifier
- **FR8**: Users can view complete content of any stored document
- **FR9**: Users can view document metadata (type, technologies, project, creation date)
- **FR10**: Users can delete conventions or documentations from the knowledge base
- **FR11**: Users can access an interactive CRUD menu for all document operations
- **FR12**: System can confirm deletion operations before permanent removal
- **FR13**: System can cascade delete associated embeddings when document is removed
- **FR14**: System can cascade delete technology links when document is removed
- **FR15**: System can parse markdown files and extract content for storage

### Active Compliance Filter (3-Layer RAG Architecture)

- **FR16**: System can perform semantic vector search on conventions (Layer 1)
- **FR17**: System can retrieve conventions based on query similarity using pgvector
- **FR18**: System can present conventions as "non-negotiable laws" to the AI agent
- **FR19**: System can automatically link technical documentation to conventions via technology identifiers (Layer 2)
- **FR20**: System can retrieve documentation associated with convention technologies using SQL JOIN
- **FR21**: System can contextualize documentation based on Layer 1 convention rules
- **FR22**: System can invoke LLM reformulation agent to fuse conventions and documentation (Layer 3)
- **FR23**: System can eliminate contradictory patterns between conventions and documentation
- **FR24**: System can eliminate obsolete syntax in favor of current best practices
- **FR25**: System can generate a unified implementation guide from multi-source context
- **FR26**: System can validate structural coherence of fused context
- **FR27**: System can optimize context for AI agent consumption
- **FR28**: System can ensure Layer 3 output is mono-approach (single recommended path)

### Context Retrieval & Delivery

- **FR29**: System can receive contextual queries from Claude Code agents
- **FR30**: System can generate vector embeddings for search queries
- **FR31**: System can retrieve relevant conventions based on semantic similarity
- **FR32**: System can identify technology identifiers from retrieved conventions
- **FR33**: System can retrieve all documentation linked to identified technologies
- **FR34**: System can aggregate conventions and documentation before reformulation
- **FR35**: System can deliver fused context to Claude Code agents
- **FR36**: System can deliver context optimized for code generation tasks
- **FR37**: System can deliver context optimized for documentation generation tasks
- **FR38**: System can maintain context relevance throughout retrieval pipeline

### Code Validation & Conformity

- **FR39**: System can validate generated code against project conventions
- **FR40**: System can detect convention violations in code snippets
- **FR41**: System can detect non-conformities in code structure
- **FR42**: System can generate conformity reports with detected violations
- **FR43**: System can provide correction suggestions for violations
- **FR44**: System can calculate conformity scores for code submissions
- **FR45**: System can compare code patterns against convention rules
- **FR46**: System can identify missing required patterns from conventions
- **FR47**: Users can request explicit validation of code snippets
- **FR48**: Users can validate code at file level
- **FR49**: System can report validation results with detailed violation descriptions

### Claude Code Integration

- **FR50**: Skills can be auto-invoked by Claude Code during code generation
- **FR51**: Skills can retrieve context transparently without user intervention
- **FR52**: Skills can invoke MCP tools for context retrieval
- **FR53**: Skills can delegate to reformulation sub-agent
- **FR54**: Skills can return fused context to Claude Code
- **FR55**: Users can invoke validation skills manually via slash commands
- **FR56**: Users can query Alexandria context using slash commands
- **FR57**: Users can configure Alexandria using interactive slash commands
- **FR58**: Users can list documents using slash commands with optional parameters
- **FR59**: Users can read document content using slash commands
- **FR60**: Users can delete documents using slash commands
- **FR61**: Sub-agents can receive conventions and documentation as input
- **FR62**: Sub-agents can analyze contradictions between multiple sources
- **FR63**: Sub-agents can produce coherent unified guides
- **FR64**: Sub-agents can operate autonomously without user intervention
- **FR65**: Sub-agents can use economical LLM models (Haiku 3.5) for cost optimization
- **FR66**: MCP Server can expose tools via Model Context Protocol
- **FR67**: MCP Server can handle retrieve context requests
- **FR68**: MCP Server can handle validate code requests
- **FR69**: MCP Server can handle upload convention requests
- **FR70**: MCP Server can handle list projects requests
- **FR71**: MCP Server can handle list documents requests
- **FR72**: MCP Server can handle read document requests
- **FR73**: MCP Server can handle delete document requests
- **FR74**: MCP Server can communicate with Claude Code using MCP protocol

### Project & Technology Configuration

- **FR75**: Users can configure multiple independent projects
- **FR76**: Users can assign unique identifiers to projects
- **FR77**: Users can declare multiple technologies per convention
- **FR78**: Users can associate multiple technologies with documentation (exemple: FastAPI doc → ["python", "fastapi"])
- **FR79**: System can create technology-convention associations via pivot table
- **FR80**: System can maintain technology-documentation relationships
- **FR81**: System can retrieve conventions for specific project identifiers
- **FR82**: System can retrieve all conventions associated with specific technologies
- **FR83**: System can retrieve all documentation for specific technologies
- **FR84**: Users can list all configured projects with metadata
- **FR85**: Users can view technology configuration per project
- **FR86**: Users can view convention count per project
- **FR87**: Users can view documentation count per technology
- **FR88**: System can store and manage conventions for any programming language used by user projects (Python, TypeScript, Java, Groovy, Go, Rust, etc.)
- **FR89**: System can store and manage documentation for any framework used by user projects (FastAPI, Spring, NestJS, Django, Express, etc.)
- **FR90**: Users can onboard new projects with technology setup
- **FR91**: System can maintain document-project associations for multi-project sharing
- **FR104**: Users can associate documents (conventions or documentation) with multiple projects
- **FR105**: System can retrieve documents shared across multiple projects
- **FR106**: System can maintain project-document associations via pivot table

### Testing, Debugging & Observability

- **FR92**: Users can test retrieval queries with debug output
- **FR93**: System can display Layer 1 retrieval results (conventions found)
- **FR94**: System can display Layer 2 retrieval results (linked documentation)
- **FR95**: System can display Layer 3 reformulation output (fused guide)
- **FR96**: System can display relevance metrics for retrieved content
- **FR97**: System can display response time metrics
- **FR98**: System can log all retrieval requests with timestamps
- **FR99**: System can log all validation requests with results
- **FR100**: System can log upload operations with document metadata
- **FR101**: Users can manually track success metrics (interventions, commits, reviews)
- **FR102**: System can provide visibility into retrieval pipeline for debugging
- **FR103**: System can validate setup effectiveness before production use

## Non-Functional Requirements

### Performance

**NFR1: Temps de Réponse Retrieval Queries**
- p50 (médiane): ≤3 secondes pour `/alexandria-query` end-to-end
- p95 (95e percentile): ≤5 secondes pour requêtes complexes
- p99 (99e percentile): ≤10 secondes dans worst-case scenarios
- Timeout rate: 0% (aucune requête ne doit timeout)

**NFR2: Performance Layer 1 (Vector Search)**
- Recherche vectorielle sur pgvector: ≤1 seconde pour 95% des requêtes
- Support de >10,000 embeddings sans dégradation significative

**NFR3: Performance Layer 2 (SQL Joins)**
- Récupération documentation via JOIN `convention_technologies`: ≤500ms
- Indexation optimale sur `technology_id` et `project_id`

**NFR4: Performance Layer 3 (LLM Reformulation)**
- Reformulation via Haiku 3.5: ≤2 secondes pour contexte standard (<5000 tokens)
- Pas de retry automatique pour éviter latence cumulée

**NFR5: Performance Upload/Indexation**
- Upload convention/doc markdown: Acceptation immédiate (<200ms)
- Génération embeddings: Asynchrone, pas de blocage utilisateur
- Notification completion indexation disponible

**NFR6: Concurrent Request Handling**
- Support minimum 5 requêtes simultanées sans dégradation
- Isolation transactions PostgreSQL pour éviter conflicts

### Security

**NFR7: Credentials Management**
- Support stockage via variables d'environnement système
- Support lecture fichier `.env` pour configuration locale
- Validation présence credentials au démarrage (fail fast si manquants)

**NFR8: API Keys Protection**
- Credentials PostgreSQL (`ALEXANDRIA_DB_URL`) protégées en mémoire
- `OPENAI_API_KEY` jamais loggée ni exposée dans outputs
- Pas de stockage credentials en clair dans code source ou repos (utiliser `.env` exclu du git via `.gitignore`)
- Pas besoin de `CLAUDE_API_KEY` dans Alexandria (reformulation via sub-agent Claude Code)

**NFR9: Knowledge Base Access**
- Pas d'authentification requise pour MCP server (usage local uniquement)
- Accès PostgreSQL restreint via credentials (pas d'accès public)

**NFR10: Data Privacy**
- Conventions et documentation projet stockées localement uniquement (PostgreSQL local)
- Reformulation Layer 3 via sub-agent Claude Code (géré par Claude Code lui-même, pas d'API directe)
- Embeddings via OpenAI API (chunks de texte envoyés pour vectorisation uniquement, pas de stockage OpenAI)
- Logs ne doivent pas contenir code snippets complets (privacy)
- `.env` avec credentials doit être exclu du git (.gitignore)

### Integration

**NFR11: MCP Protocol Compliance**
- Serveur MCP 100% conforme au protocole Model Context Protocol
- Support toutes opérations MCP tools définies (retrieve, validate, upload, list, read, delete)
- Réponses format JSON valide selon spécification MCP

**NFR12: Claude Code Skills Integration**
- Skills auto-invocables par Claude Code sans erreur
- Skills peuvent appeler MCP tools de manière fiable
- Retour contexte formaté compatible avec ingestion Claude Code

**NFR13: Slash Commands Reliability**
- Tous slash commands fonctionnels depuis Claude Code CLI
- Paramètres optionnels gérés correctement (avec defaults appropriés)
- Messages d'erreur clairs si paramètres invalides

**NFR14: Sub-Agent Communication**
- Sub-agent `alexandria-reformulation` peut être invoqué via le système de sub-agents de Claude Code
- Input/output format stable et documenté
- Pas de breaking changes dans interface sub-agent
- Communication via protocole interne Claude Code (pas d'appel API direct depuis Alexandria)

**NFR15: PostgreSQL + pgvector Dependency**
- Détection automatique disponibilité PostgreSQL au démarrage
- Vérification extension pgvector installée et fonctionnelle
- Message d'erreur clair si infrastructure manquante

### Reliability

**NFR16: Fail Fast Behavior**
- Si PostgreSQL inaccessible: Erreur immédiate avec message explicite (pas de retry silencieux)
- Si sub-agent reformulation inaccessible: Erreur Layer 3 avec fallback possible (retour Layer 1+2 brut)
- Si MCP server crash: Logging stack trace complet pour debugging

**NFR17: Error Messages Quality**
- Tous messages d'erreur incluent contexte actionnable (ex: "PostgreSQL unreachable at DB_URL, verify connection")
- Pas de stack traces techniques exposés à l'utilisateur final (loggés seulement)
- Codes d'erreur distincts pour chaque failure mode

**NFR18: Data Integrity**
- Transactions PostgreSQL pour opérations multi-étapes (upload + embedding generation)
- Rollback automatique si échec partiel (pas de données orphelines)
- Validation schéma avant insertion (reject invalid documents)

**NFR19: Graceful Degradation**
- Si Layer 3 reformulation échoue: Retour Layer 1+2 context brut avec warning

**NFR20: Uptime Requirements**
- MCP server disponible tant que Claude Code est actif
- Pas de memory leaks causant crash après usage prolongé
- Recovery automatique après restart (pas de corruption état)

### Maintainability

**NFR21: Code Documentation Complète**
- JSDoc/TSDoc pour toutes fonctions publiques et classes TypeScript
- Explications logique complexe RAG (Layers 1-3) via commentaires inline
- Documentation architecture dans README.md
- Types TypeScript stricts (strict mode activé dans tsconfig.json)

**NFR22: Code Organization**
- Séparation claire responsabilités (skills / sub-agent / MCP server / database layer)
- Modules découplés pour faciliter tests unitaires
- Conventions de nommage cohérentes (TypeScript/JavaScript standards, linting via ESLint/Biome)

**NFR23: Tests Coverage**
- Tests unitaires pour logique métier critique (retrieval, reformulation, CRUD)
- Tests intégration pour pipeline complet Layer 1→2→3
- Tests MCP tools pour validation protocole

**NFR24: Configuration Management**
- Tous paramètres configurables externalisés (pas de hardcoding)
- Fichier config exemple fourni (`.env.example`)
- Documentation complète variables d'environnement requises

**NFR25: Dependency Management**
- Liste explicite dépendances (package.json avec bun.lockb pour lock exact)
- Versions pinned pour reproductibilité (éviter breaking changes)
- Dockerfile fourni pour installation simplifiée (image Bun officielle)
- Support Bun native (pas besoin de Node.js, runtime ultra-rapide)

### Observability

**NFR26: Logging Verbose avec Debug Mode**
- Mode verbose activable via variable d'environnement `ALEXANDRIA_LOG_LEVEL=DEBUG`
- Logs structurés (JSON format recommandé pour parsing)
- Rotation logs automatique pour éviter saturation disque

**NFR27: Métriques Techniques Essentielles (MUST-HAVE)**

Logging automatique pour chaque requête:
- ✅ Timestamp requête (ISO 8601 format)
- ✅ Type d'opération (query, validate, upload, delete, read, list)
- ✅ Query text (pour debugging retrieval)
- ✅ Project ID et technologies concernées
- ✅ Résultat (success/error)
- ✅ Message d'erreur détaillé si échec

**NFR28: Performance Metrics par Layer**

Logging temps d'exécution:
- ✅ Temps Layer 1 (vector search pgvector)
- ✅ Temps Layer 2 (SQL joins technologies)
- ✅ Temps Layer 3 (LLM reformulation Haiku)
- ✅ Temps total end-to-end
- ✅ Nombre conventions récupérées (Layer 1)
- ✅ Nombre documentations récupérées (Layer 2)

**NFR29: Métriques de Pertinence**

Logging qualité retrieval:
- ✅ Similarity scores des chunks vectoriels récupérés
- ✅ Nombre résultats avant/après filtrage
- ✅ Technologies matchées vs technologies demandées
- ✅ Nombre total documents scannés

**NFR30: Pipeline Visibility (SHOULD-HAVE)**

Debugging mode expose:
- ✅ Layer 1 output: IDs conventions + similarity scores
- ✅ Layer 2 output: IDs docs liés + relations technologies
- ✅ Layer 3 input: Taille contexte avant reformulation (tokens)
- ✅ Layer 3 output: Taille contexte après reformulation (tokens)
- ✅ Tokens LLM consommés (Haiku 3.5 API usage)

**NFR31: Opérations CRUD Tracking**

Logging modifications knowledge base:
- ✅ Upload: doc type, taille fichier, technologies déclarées, project ID
- ✅ Delete: doc ID, type, raison suppression (si fournie par utilisateur)
- ✅ Read: doc ID accédé, timestamp
- ✅ List: filters appliqués, nombre résultats retournés

**NFR32: Validation Requests Logging**

Logging validation code:
- ✅ Code snippet hash (pour privacy - pas code complet)
- ✅ Nombre violations détectées
- ✅ Types violations (par catégorie de convention)
- ✅ Conformity score calculé (0-100%)

**NFR33: Metrics Export**
- Logs exportables pour analyse externe (fichiers texte/JSON)
- Pas de dashboard intégré dans MVP (tracking manuel acceptable)
