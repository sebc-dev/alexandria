---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments: []
workflowType: 'product-brief'
lastStep: 5
project_name: 'alexandria'
user_name: 'Negus'
date: '2025-12-24'
---

# Product Brief: alexandria

<!-- Content will be appended sequentially through collaborative workflow steps -->

## Executive Summary

Alexandria est un système de gouvernance technique automatisée pour Claude Code, conçu pour éliminer la dérive technique causée par les agents IA. En tant que projet personnel, Alexandria sert un double objectif: résoudre un problème concret de qualité de code dans le développement assisté par IA, et pratiquer les architectures RAG dans un contexte directement utile.

Le système agit comme un "Active Compliance Filter" qui fusionne proactivement les conventions projet avec la documentation technique pertinente, garantissant que Claude Code génère du code conforme dès la première itération plutôt que de détecter les violations après coup.

**Objectif principal:** Passer de 3-5 itérations par feature (avec rappels manuels constants) à 1 itération parfaite, transformant Claude Code d'un "assistant qui hallucine" en "pair programmer qui connaît le projet par cœur".

---

## Core Vision

### Problem Statement

En tant que développeur unique utilisant Claude Code pour gérer plusieurs projets, le risque principal est la **dérive technique silencieuse**. Les agents IA accèdent à une connaissance universelle générique ou obsolète, et sans garde-fous stricts, introduisent:

- **Dette technique invisible**: Détectée tardivement (code review, bugs, revisites)
- **Violations de conventions**: Code non conforme aux standards projet
- **Technologies obsolètes**: Utilisation de patterns ou APIs dépassés
- **Friction cognitive constante**: Nécessité de rappeler manuellement les conventions à chaque tâche

Le processus actuel repose sur des fichiers markdown manuels qu'il faut penser à inclure au bon moment - et quand on oublie, on paie le prix en refactoring, bugs, failles de sécurité et problèmes de performance.

### Problem Impact

**Impact immédiat:**
- **Temps perdu**: 10+ minutes par feature à rappeler les conventions
- **Itérations multiples**: 3-5 cycles de correction par tâche
- **Frustration maximale**: "Rappelle-toi comment on fait ICI" répété sans cesse
- **Review lourde**: Vérification ligne par ligne nécessaire

**Impact à long terme:**
- Bugs introduits par non-respect des conventions
- Refactoring coûteux de code non-conforme
- Difficultés de maintenance croissantes
- Failles de sécurité et problèmes de performance

**Coût cognitif:**
- Impossible de gérer efficacement plus de 2-3 projets simultanés
- Chaque switch de projet nécessite de recharger mentalement les bonnes conventions
- Perte de confiance dans le code généré

### Why Existing Solutions Fall Short

**Solutions explorées:**

1. **Fichiers markdown manuels** (approche actuelle)
   - ❌ Maintenance lourde et oublis fréquents
   - ❌ Ajoutent du bruit au contexte LLM
   - ❌ Documentation séparée des conventions (pas pratique)

2. **CodeRabbit** (review automatisée)
   - ❌ Détection tardive (après génération complète)
   - ❌ Coûts élevés en re-génération (2-3x tokens)
   - ❌ Ne prévient pas les choix architecturaux incompatibles

3. **Serveurs MCP** (accès documentation)
   - ❌ Fournissent la doc mais sans filtrage de conformité
   - ❌ Pas de stratification Convention > Documentation

4. **Cognee** (RAG pour code)
   - ❌ Architecture trop lourde (3 bases de données: relationnelle + vectorielle + graph)
   - ❌ Projet encore trop jeune et immature

5. **Letta/Letta Code** (mémoire agent)
   - ❌ Dépendance à API externe
   - ❌ Limites/coûts incertains, perte de contrôle

6. **LightRAG, Mem0** (RAG indirect)
   - ❌ Pas centré sur le code
   - ❌ RAG classique sans hiérarchie de contexte

**Le problème fondamental:** Toutes les solutions sont soit **réactives** (détection tardive), soit **lourdes** (over-engineered), soit **externes** (perte de contrôle). Aucune ne fait de **filtrage actif de conformité** au moment où l'agent génère le code.

### Proposed Solution

Alexandria résout le problème fondamental via un **Active Compliance Filter** qui fusionne proactivement conventions projet et documentation technique.

**Architecture en 3 layers:**

1. **Layer 1 - Conventions** (Priorité absolue)
   - Règles non-négociables du projet
   - Patterns imposés, interdictions, linting
   - Présentées comme des "lois" à l'agent

2. **Layer 2 - Documentation technique** (Vocabulaire contraint)
   - APIs, frameworks, bibliothèques
   - Documentation toujours à jour (crawling automatique)
   - Contextualisée par les conventions Layer 1

3. **Layer 3 - Reformulation** (Synthèse mono-approche)
   - LLM de reformulation fusionne Conv + Doc
   - Génère un "guide d'implémentation contraint"
   - Élimine les patterns contradictoires

**Workflow d'utilisation:**

```
Développeur → Demande feature
     ↓
Alexandria Skill (auto/manuel)
     ↓
Active Compliance Filter
     ↓
Claude Code reçoit contexte fusionné
     ↓
Code parfait dès 1ère génération ✓
```

**Intégration native:**
- Skill Alexandria appelé automatiquement aux moments opportuns
- Custom slash commands intégrant le skill
- Custom sub-agents avec Alexandria pré-configuré
- Workflow Brief→PRD→Epic→Story→Commit avec contexte maintenu

**Caractéristiques clés:**
- ✅ **Auto-hébergé**: Contrôle total, coûts maîtrisés
- ✅ **Personnalisable**: Architecture adaptable aux besoins
- ✅ **Proactif**: Prévient vs détecte
- ✅ **Contextuel**: S'adapte au projet actif
- ✅ **Toujours à jour**: Crawling automatique des docs

### Key Differentiators

**vs RAG Classique:**
- RAG traditionnel retourne info brute sans hiérarchie (5 docs "error handling" de frameworks différents)
- Alexandria stratifie: Conventions (Layer 1) > Doc (Layer 2) > Reformulation (Layer 3)
- L'agent ne peut plus "choisir" entre patterns contradictoires

**vs Review Post-Génération:**
- Validation réactive détecte après génération complète → cycles coûteux (2-3x tokens)
- Choix architecturaux incompatibles nécessitent refactor profond
- Alexandria prévient en amont → génération conforme dès le départ
- **Évite 70% des itérations** et garantit cohérence structurelle

**vs Solutions Externes (Letta, Cognee):**
- Auto-hébergé → aucune limite, aucun coût récurrent
- Contrôle total de l'architecture et des données
- Personnalisation illimitée selon besoins évolutifs
- Pas de dépendance à des services tiers

**Valeur unique:**
Le moment "wow" où développer avec Claude Code passe de:
- **Avant**: 10 min de "rappelle-toi comment on fait ICI"
- **Après**: 30 sec de génération parfaite

Transformer Claude Code de "stagiaire à surveiller" en "senior dev qui connaît ton projet par cœur".

---

## Target Users

### Primary Users

**Persona: Sébastien "Negus" Chauveau - Le Développeur Multi-Projets**

**Contexte:**
- Développeur full-time (Java/Groovy, Spring/Grails) le jour
- Entrepreneur web en parallèle, gérant son agence web en solo
- Jongle entre deux environnements: PC travail (local) + serveur maison (agence)
- Utilise intensivement Claude Code pour accélérer le développement

**Expérience Actuelle du Problème:**

Sébastien fait face à une **friction cognitive constante** dans son workflow de développement assisté par IA:

- **Workarounds manuels lourds**: Consolide son expertise en fichiers markdown via recherches internet, crée des custom slash commands/agents/skills pour y accéder
- **Contexte pollué**: Utilise des serveurs MCP pour la documentation, mais le contexte se remplit vite de bruit inutile
- **Détection tardive**: Découvre les violations de conventions lors des code reviews (matin/soir) ou pire, via bugs en production
- **Rappels constants**: Doit penser à inclure les bonnes conventions à chaque tâche, oublis fréquents
- **Stress croissant**: Actuellement 5/10 sur projets perso, mais anticipe 7-8/10 avec vrais clients

**Impact:**
- **Temps perdu**: 10+ minutes par feature à rappeler manuellement les conventions
- **Itérations multiples**: 3-5 cycles de correction par tâche
- **Gestion multi-projets limitée**: Difficile de maintenir qualité et conventions sur plusieurs projets simultanés
- **Perte de confiance**: Doit vérifier chaque ligne générée par Claude Code

**Motivations:**
- Développer efficacement pour son emploi ET son agence web
- Permettre à Claude Code de tourner en **autonomie maximale** sur projets agence
- Gestion automatique du contexte documentation/conventions **sans intervention manuelle**
- Préparer la scalabilité avant l'arrivée des vrais clients

**Objectifs:**
- Réduire drastiquement les interventions pour inclure le contexte adéquat
- Augmenter la qualité du code et le respect des conventions
- Générer du code conforme dès la première itération
- Gérer sereinement plusieurs projets en parallèle avec deux instances Alexandria (travail + maison)

**Vision de Succès:**

**Quotidien idéal:**
- Vérifier et relancer Claude Code seulement quelques fois dans la journée
- Reviews matin/soir sans réels problèmes à corriger
- CodeRabbit ne trouve pratiquement rien à redire
- Confiance totale dans le code généré

**Moment "Aha!" initial:**
- Un commit où ni CodeRabbit ni lui n'ont pratiquement rien à corriger
- Code parfait du premier coup, conventions respectées

**Succès ultime:**
- Terminer un projet client complet avec **que des journées comme celle décrite ci-dessus**
- Passer d'un stress 7-8/10 à 2-3/10 même sous pression client

---

### Secondary Users

**Utilisateur: Claude Code (Agent IA)**

**Contexte:**
Claude Code est l'agent IA qui consomme le contexte fourni par Alexandria pour générer du code conforme.

**Besoins:**
- Recevoir un contexte **clair et non-ambigu**
- Contexte **stratifié** avec hiérarchie explicite (Conventions > Documentation)
- Accès aux conventions projet présentées comme des "lois" non-négociables
- Documentation technique pertinente et à jour comme "vocabulaire contraint"
- Guide d'implémentation fusionné éliminant les patterns contradictoires

**Problème Actuel:**
- **Confusion contextuelle**: Reçoit de l'information brute sans hiérarchie (ex: 5 docs "error handling" de frameworks différents)
- **Choix libres dangereux**: Peut "choisir" entre patterns contradictoires, menant à des violations de conventions
- **Contexte pollué**: MCP servers ajoutent du bruit, contexte rapidement saturé
- **Hallucinations de règles**: Invente des patterns basés sur connaissance universelle générique/obsolète

**Vision de Succès:**
- Générer du code conforme dès la première itération
- Pas de "devinettes" entre approches contradictoires
- Respect automatique des conventions sans rappels manuels
- Utilisation systématique des dernières versions/APIs des technologies

**Interaction avec Primary User:**
- Consomme passivement le contexte fourni par Alexandria
- Applique automatiquement les contraintes fusionnées Conv + Doc
- Sollicite Alexandria via slash commands, sub-agents et skills aux moments opportuns

---

### User Journey

**Phase 1: Discovery & Adoption**

**Discovery (Canaux):**
- GitHub (repo open-source potentiel)
- Discord (communautés dev/IA)
- LinkedIn (réseau professionnel)
- Blogs techniques (Medium, DEV Community)

**Onboarding Initial:**

1. **Installation via Claude Code Marketplace**
   - Installer les skills Alexandria
   - Temps estimé: 15-20 minutes

2. **Setup Infrastructure (Technique mais faisable)**
   - Installation images Docker (PostgreSQL, Crawl4AI, DocLing) depuis Docker Hub
   - Récupération dump base PostgreSQL
   - Configuration variables d'environnement
   - Temps estimé: 30-45 minutes

3. **Premier Projet Setup**
   - Ajout conventions initiales du projet
   - Configuration documentation technique pertinente
   - Test du workflow avec première feature
   - Temps estimé: 1-2 heures

**Seuil d'acceptabilité:**
Setup initial peut être technique mais doit rester accessible pour un développeur intermédiaire.

---

**Phase 2: Core Daily Usage**

**Matin (Review Session):**
1. CodeRabbit fait review automatique du code généré la veille
2. Sébastien passe sur la review manuelle
3. **Alexandria intervient**: Quand CodeRabbit suggère un fix via prompt
   - Claude Code analyse le prompt CodeRabbit
   - Alexandria compare avec documentation technique + conventions
   - Détermine si le fix est justifié et conforme
   - Applique uniquement si validation positive

**Journée (Développement Autonome):**
- Claude Code utilise **automatiquement** Alexandria via:
  - Custom slash commands dédiés
  - Sub-agents pré-configurés avec Alexandria
  - Skills forcés dans situations critiques (génération API, gestion erreurs, etc.)
- Workflow transparent: Sébastien demande features, Claude Code génère avec contexte adéquat

**Soir (Review & Développement):**
- Même process de review matin (CodeRabbit + manuelle)
- Développement additionnel avec Alexandria actif
- Commits avec confiance croissante

**Interactions Clés:**
- **Automatiques**: 80% du temps, Alexandria fonctionne en arrière-plan
- **Manuelles**: 20% du temps, ajustements conventions ou ajout nouvelles docs

---

**Phase 3: Success Moments**

**Premier Succès (Court Terme - Semaines):**
- **Moment**: Un commit complexe où ni CodeRabbit ni Sébastien n'ont pratiquement rien à corriger
- **Réaction**: "Ok, ça marche vraiment. Code parfait du premier coup."
- **Impact**: Confiance initiale validée, ROI temps investi confirmé

**Succès Intermédiaire (Moyen Terme - Mois):**
- Première semaine complète avec uniquement des "journées parfaites"
- Gestion fluide de 3-4 projets simultanés sans friction
- Stress émotionnel qui reste à 5/10 malgré montée en charge

**Succès Ultime (Long Terme - Trimestre):**
- **Moment**: Fin d'un projet client complet avec 100% de journées parfaites
- **Caractéristiques**:
  - Zéro itération inutile
  - Reviews ultra-rapides (scan diagonal vs ligne par ligne)
  - Respect total conventions sans rappel manuel
- **Réaction**: "Comment je faisais avant? Impossible de revenir en arrière."

---

**Phase 4: Long-Term Evolution**

**Évolution Continue (6-12 mois):**

**Affinement Progressif:**
- Optimisation des liens Documentation ↔ Conventions
- Ajout de nouvelles docs selon projets/besoins
- Enrichissement des conventions avec retours d'expérience

**Features Futures Prévues:**

1. **Mémoire à Long Terme**
   - Indexation de tous les logs Claude Code
   - Recherche sémantique sur historique de développement
   - Apprentissage des patterns spécifiques utilisateur

2. **Système d'Avancée Projet Interne**
   - Complément à Jira/Confluence
   - Mémoire long terme **cross-instances** (PC perso ↔ PC travail ↔ Serveur)
   - Synchronisation état projet entre environnements
   - Continuité contextuelle même en switchant de machine

**Routine Établie:**
- Alexandria devient invisible: fonctionne automatiquement, pas besoin d'y penser
- Maintenance minimale: ajout occasionnel de nouvelles conventions/docs
- Scalabilité: gestion facile de 5+ projets simultanés
- Confiance totale: review diagonale vs ligne par ligne

---

## Success Metrics

### User Success Metrics (Métriques de Succès Utilisateur)

Le succès d'Alexandria se mesure par l'**élimination de la friction cognitive** dans le workflow de développement assisté par IA de Sébastien.

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
Premier commit complexe où ni CodeRabbit ni Sébastien n'ont pratiquement rien à corriger (≤2 commentaires mineurs).

**Succès Ultime:**
Terminer un projet client complet avec 100% de "journées parfaites" (métriques quotidiennes atteintes sans exception).

---

### Business Objectives (Objectifs Personnels)

Alexandria sert un **double objectif**: résoudre un problème concret de qualité de code ET maîtriser les architectures RAG.

**Court Terme (3 mois):**

1. **Déploiement Production**
   - Alexandria fonctionnel sur 2 machines (PC travail + serveur maison)
   - Infrastructure Docker stable (PostgreSQL, Crawl4AI, DocLing)
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
   - **Mémoire Long Terme**: Indexation logs Claude Code avec recherche sémantique
   - **Cross-Instances**: Synchronisation état projet entre PC perso ↔ PC travail ↔ Serveur
   - Continuité contextuelle même en switchant de machine

2. **Scalabilité Prouvée**
   - Gestion fluide de 5+ projets simultanés
   - 0 intervention manuelle par jour (80% du temps)
   - Maintenance minimale (ajout occasionnel conventions/docs)

3. **Maîtrise Apprentissage**
   - Architecture RAG stratifiée complètement maîtrisée et documentée
   - Code source organisé, commenté, maintenable
   - Potentiel open-source: Repo GitHub présentable

**Indépendance Agence:**
Alexandria est un **facilitateur**, pas un pré-requis. L'agence peut démarrer avec clients même si Alexandria n'est pas parfait - il rendra simplement le développement plus fluide et réduira le stress technique.

---

### Key Performance Indicators (KPIs)

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

**KPIs d'Évolution (Roadmap 6-12 Mois):**

🎯 **Features Avancées:**
- Mémoire Long Terme opérationnelle
- Système cross-instances synchronisé
- Recherche sémantique sur logs fonctionnelle

🎯 **Scalabilité:**
- 5+ projets gérés simultanément
- 0 intervention manuelle 4 jours sur 5
- Temps maintenance <30 min/semaine

🎯 **Maîtrise Technique:**
- Architecture RAG documentée (README, diagrammes)
- Code commenté et structuré
- Tests automatisés sur composants critiques

**North Star KPI (Étoile Polaire):**

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

---

**Principe de Mesure:**

Toutes les métriques doivent être:
- ✅ **Mesurables**: Chiffres concrets, pas d'estimations vagues
- ✅ **Actionnables**: Permettent d'identifier problèmes et ajustements
- ✅ **Centrées utilisateur**: Reflètent la valeur réelle pour Sébastien
- ✅ **Alignées vision**: Supportent l'objectif "Active Compliance Filter"

---

## MVP Scope

### Core Features (Fonctionnalités Essentielles MVP)

Le MVP d'Alexandria se concentre sur le **Active Compliance Filter** fonctionnel avec les composants techniques minimaux nécessaires.

**1. Ingestion & Indexation de Contenu**

- **Upload manuel** de fichiers markdown (conventions et documentation)
- **Distinction manuelle** Convention vs Documentation lors de l'upload
- Stockage dans PostgreSQL avec pgvector pour recherche sémantique
- **Interface web basique** pour upload et CRUD (création, lecture, mise à jour, suppression)

**2. Active Compliance Filter (Cœur du Système)**

Architecture en **3 layers obligatoires** pour le MVP:

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

---

### Out of Scope for MVP

Ces fonctionnalités sont **explicitement exclues** du MVP pour maintenir le focus et livrer rapidement:

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

**❌ UI Web Avancée**
- Pas de dashboard sophistiqué
- Pas de visualisations de données
- **MVP**: Interface web basique (upload + CRUD uniquement)
- **Rationale**: Fonctionnalité suffit pour validation
- **Timeline**: Roadmap Phase 2 (UI avancée avec intégration Jira/Confluence)

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

---

### MVP Success Criteria

Le MVP sera considéré comme **réussi** lorsque les critères suivants sont atteints:

**Critères de Validation Technique:**

✅ **Déploiement Fonctionnel**
- Alexandria installé et opérationnel sur 2 machines (PC travail + serveur maison)
- Infrastructure Docker stable (PostgreSQL + pgvector)
- Skills Claude Code fonctionnels via Marketplace
- Interface web d'upload/CRUD accessible

✅ **Active Compliance Filter Validé**
- Les 3 layers (Conv > Doc > Reformulation) fonctionnent ensemble
- LLM de reformulation produit des guides cohérents
- Pas de patterns contradictoires dans le contexte fusionné

✅ **Intégration Claude Code Fluide**
- Skills, slash commands et sub-agents opérationnels
- Workflow complet: Upload → Interrogation → Contexte fusionné → Code conforme

**Critères de Validation Métrique (KPIs 3 Mois):**

✅ **Interventions Manuelles**
- **≤1 intervention/jour** pendant **1 semaine consécutive**
- Baseline: 3-5/jour → Cible: ≤1/jour

✅ **Taux de Commits Parfaits**
- **80% (4/5)** de commits avec ≤2 commentaires CodeRabbit
- Validation via tracking manuel hebdomadaire

✅ **Configuration Minimale**
- ≥3 projets configurés avec conventions + documentation
- ≥5 documentations techniques indexées
- Liens Conv ↔ Doc validés sur cas réels

**Critères de Validation Utilisateur:**

✅ **Réduction de Friction Cognitive**
- Sébastien constate une **diminution tangible** des rappels manuels
- Workflow de développement plus fluide et naturel
- Confiance croissante dans le code généré par Claude Code

✅ **Stabilité Opérationnelle**
- **2 semaines consécutives** avec métriques quotidiennes atteintes
- Zéro régression dans workflow existant
- Intégration harmonieuse avec CodeRabbit

**Gates de Décision:**

🎯 **Validation MVP Complète:**
Si **TOUS** les critères ci-dessus sont atteints, le MVP est validé et prêt pour évolution.

🎯 **Trigger de Scaling:**
Après **2 semaines de MVP stable** (métriques atteintes sans interruption), décision de passer aux features post-MVP selon roadmap.

🎯 **Go/No-Go:**
- **GO**: Métriques atteintes → Continuer vers roadmap post-MVP
- **PIVOT**: Métriques partielles → Ajuster approche, réitérer
- **NO-GO**: Métriques non atteintes après ajustements → Réévaluer concept

---

### Future Vision (Post-MVP Roadmap)

Si le MVP est validé avec succès, Alexandria évoluera selon cette roadmap progressive:

**Phase 1: Crawling Semi-Automatique (Court Terme)**

- Intégration **Crawl4AI** pour ingestion de documentation web
- Crawling manuel déclenché par l'utilisateur (pas encore automatique)
- Parsing et indexation de docs HTML/PDF
- **Objectif**: Faciliter l'ajout de nouvelles documentations techniques

**Phase 2: UI Avancée + Gestion de Projet (Moyen Terme)**

- **Interface web sophistiquée** pour gestion complète
- Intégration **Jira/Confluence** pour synchronisation de données projet
- Dashboard de visualisation des conventions/docs
- Gestion avancée des liens Conv ↔ Doc
- **Objectif**: Améliorer l'expérience utilisateur et productivité

**Phase 3: Crawling Automatique (Moyen Terme)**

- Crawling automatique périodique des documentations
- Détection automatique de mises à jour (nouvelles versions frameworks/APIs)
- Notification utilisateur des changements détectés
- **Objectif**: Documentation toujours à jour sans intervention manuelle

**Phase 4: Mémoire Long Terme (Long Terme)**

- Indexation complète des **logs Claude Code**
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

🎯 **SaaS Potentiel (Très Long Terme)**
- Version hébergée pour utilisateurs ne voulant pas self-host
- Modèle freemium ou abonnement
- **Note**: Très exploratoire, pas de timeline définie

**Expansion d'Écosystème:**

🌐 **Usage Professionnel**
- Déploiement au travail (environnement Java/Groovy/Spring/Grails)
- Validation dans contexte entreprise
- Retours d'expérience multi-environnements

🌐 **Intégration Multi-Agents**
- Support d'autres agents IA au-delà de Claude Code
- Abstraction de l'interface MCP pour compatibilité élargie
- **Note**: Focus actuel reste écosystème personnel Claude Code

---

**Principe de Scope Management:**

- ✅ **Focus MVP**: Livrer les 3 layers + intégration Claude Code fonctionnels
- ✅ **Validation d'abord**: Prouver la valeur du concept avant complexification
- ✅ **Roadmap progressive**: Chaque phase construit sur la précédente
- ✅ **Flexibilité**: Ajuster roadmap selon learnings et besoins émergents
- ✅ **Pas de scope creep**: Dire "non" aux features hors roadmap jusqu'à validation MVP
