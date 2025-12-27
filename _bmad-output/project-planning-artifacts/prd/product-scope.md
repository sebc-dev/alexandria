# Product Scope

## MVP - Minimum Viable Product

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

## Growth Features (Post-MVP)

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

## Vision (Future)

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
