# Target Users

## Primary Users

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

## Secondary Users

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

## User Journey

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
