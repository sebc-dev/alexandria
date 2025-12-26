# Alexandria

**Système de gouvernance technique automatisée pour Claude Code**

## Qu'est-ce que c'est?

Alexandria élimine la dérive technique causée par les agents IA en agissant comme un **Active Compliance Filter**. Le système fusionne proactivement les conventions projet avec la documentation technique pertinente, garantissant que Claude Code génère du code conforme dès la première itération.

## Problème résolu

**Avant Alexandria:**
- 3-5 itérations par feature
- 10+ minutes de rappels manuels des conventions
- Génération de code avec dette technique invisible
- Claude Code comme "assistant qui hallucine"

**Avec Alexandria:**
- 1 itération parfaite
- 30 secondes de génération conforme
- Code respectant conventions et architecture dès le départ
- Claude Code comme "pair programmer qui connaît le projet par cœur"

## Comment ça marche?

Architecture stratifiée en 3 couches:

1. **Layer 1 - Conventions**: Règles non-négociables (lois du projet)
2. **Layer 2 - Documentation**: APIs et frameworks contextualisés
3. **Layer 3 - Reformulation**: LLM fusionnant Conv + Doc, éliminant contradictions

Cette stratification empêche l'agent de "choisir" entre approches contradictoires - le RAG retourne du contexte fusionné et hiérarchisé plutôt que de l'information brute.

## Stack technique

- **Runtime**: Bun 1.3.5
- **Language**: TypeScript 5.9.7 (strict mode)
- **Web Framework**: Hono 4.11.1
- **Database**: PostgreSQL 17.7 + pgvector 0.8.1
- **ORM**: Drizzle 0.36.4
- **Validation**: Zod 4.2.1
- **LLM**: Claude Haiku 4.5 (reformulation)
- **Embeddings**: OpenAI text-embedding-3-small/large

## Documentation

- [Product Brief](./docs/brief/) - Vision et objectifs
- [PRD](./docs/prd/) - Spécifications fonctionnelles
- [Architecture](./docs/architecture/) - Décisions techniques
- [Project Context](./docs/project-context/) - Règles d'implémentation

## Statut

Projet en phase de conception - architecture validée, prêt pour implémentation.
