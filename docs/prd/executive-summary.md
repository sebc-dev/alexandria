# Executive Summary

Alexandria est un système de gouvernance technique automatisée pour Claude Code, conçu pour éliminer la dérive technique causée par les agents IA. Le système agit comme un **Active Compliance Filter** qui fusionne proactivement les conventions projet avec la documentation technique pertinente, garantissant que Claude Code génère du code conforme dès la première itération plutôt que de détecter les violations après coup.

**Objectif principal:** Transformer le développement assisté par IA en passant de 3-5 itérations par feature (avec rappels manuels constants) à 1 itération parfaite, transformant Claude Code d'un "assistant qui hallucine" en "pair programmer qui connaît le projet par cœur".

**Le problème résolu:** En tant que développeur multi-projets utilisant intensivement Claude Code, vous faites face à une friction cognitive constante. Les agents IA accèdent à une connaissance universelle générique ou obsolète, introduisant de la dette technique invisible, des violations de conventions, et nécessitant 10+ minutes par feature à rappeler manuellement les conventions avec 3-5 cycles de correction par tâche.

**Utilisateurs cibles:** Vous-même (développeur jonglant entre emploi full-time et agence web) comme utilisateur primaire, et Claude Code (agent IA consommant le contexte) comme utilisateur secondaire.

## What Makes This Special

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
