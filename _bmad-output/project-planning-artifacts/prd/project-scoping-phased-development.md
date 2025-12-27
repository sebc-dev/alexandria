# Project Scoping & Phased Development

## MVP Strategy & Philosophy

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

## MVP Feature Set (Phase 1 - 3 Mois)

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

## Post-MVP Features

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

## Risk Mitigation Strategy

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

