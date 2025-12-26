# MVP Scope

## Core Features (Fonctionnalités Essentielles MVP)

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

## Out of Scope for MVP

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

## MVP Success Criteria

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

## Future Vision (Post-MVP Roadmap)

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
