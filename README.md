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

## Dépendances - Stratégie de Versioning

Alexandria utilise une stratégie mixte de versioning pour équilibrer stabilité et flexibilité:

### Versions Exactes (Pinned) - Stabilité Critique

Ces dépendances sont essentielles à l'architecture du projet. Les versions exactes garantissent la reproductibilité et préviennent les breaking changes involontaires:

- **drizzle-orm 0.36.4** - ORM database (contrats persistance)
- **hono 4.11.1** - Web framework (contrats API)
- **zod 4.2.1** - Validation (contrats boundaries)
- **typescript 5.9.3** - Langage strict

**Mise à jour:** Nécessite test complet (unit + integration + architecture) et approbation senior

### Versions Flexibles (Caret ^) - Évolution Sécurisée

Ces dépendances peuvent évoluer avec des mises à jour mineures/patch sans impacter la couche métier:

- **@anthropic-ai/sdk ^0.32.1** - Client API Anthropic (minor/patch safe)
- **openai ^4.77.3** - Client API OpenAI (minor/patch safe)
- **DevDependencies** - Outils de développement (flexibility recommandée)

**Mise à jour:** Exécuter `bun update [package]` avec tests, reporter les breaking changes

### Processus de Mise à Jour des Dépendances

1. **Exact versions (critical):**
   - Créer PR avec justification technique
   - Vérifier: `bun run typecheck && bun run lint && bun test`
   - Tests d'architecture et intégration obligatoires
   - Approbation requise

2. **Flexible versions (API clients):**
   - Mettre à jour avec `bun update [package]`
   - Vérifier compatibilité API (changelog)
   - Tests recommandés
   - Self-merge si tests passent

3. **DevDependencies:**
   - Flex - mises à jour recommandées régulièrement
   - Juste vérifier que lint/format/typecheck restent opérationnels

## Installation

### Prérequis

- **Bun 1.3.5+** - Runtime JavaScript ultra-rapide
- **PostgreSQL 17.7** avec extension **pgvector 0.8.1**
- **Node.js 18+** (optionnel, pour compatibilité tooling)

### 1. Installation de Bun

```bash
# macOS / Linux
curl -fsSL https://bun.sh/install | bash

# Windows (WSL2 recommandé)
# Suivre les instructions sur https://bun.sh
```

Vérifier l'installation :

```bash
bun --version  # Doit afficher 1.3.5 ou supérieur
```

### 2. Installation de PostgreSQL + pgvector

#### macOS (Homebrew)

```bash
brew install postgresql@17
brew install pgvector
```

#### Linux (Ubuntu/Debian)

```bash
sudo apt install postgresql-17
sudo apt install postgresql-17-pgvector
```

#### Docker (Recommandé pour développement)

```bash
# Sera configuré dans Story 1.2 avec docker-compose.yml
```

### 3. Clone et Setup du Projet

```bash
# Cloner le repository
git clone https://github.com/votre-org/alexandria.git
cd alexandria

# Installer les dépendances
bun install

# Créer le fichier .env depuis le template
cp .env.example .env
```

### 4. Configuration de l'Environnement

Éditer `.env` avec vos valeurs :

```bash
# Database
ALEXANDRIA_DB_URL=postgresql://alexandria:password@localhost:5432/alexandria

# External Services
OPENAI_API_KEY=sk-...  # Votre clé API OpenAI

# Logging (optionnel)
LOG_LEVEL=INFO
LOG_RETENTION_DAYS=30
```

### 5. Vérification de l'Installation

```bash
# Vérifier que TypeScript compile sans erreurs
bun run typecheck

# Lancer le projet (démarrage minimal pour l'instant)
bun run dev
```

## Commandes de Développement

```bash
# Développement avec hot-reload
bun run dev

# Lancer le projet en production
bun run start

# Vérification de types TypeScript
bun run typecheck

# Linting
bun run lint

# Formatage du code
bun run format

# Tests
bun test                  # Tous les tests
bun test:unit            # Tests unitaires
bun test:integration     # Tests d'intégration
bun test:arch            # Tests d'architecture (Story 1.6)
```

## Structure du Projet

```
alexandria/
├── src/
│   ├── domain/              # Couche domaine (logique métier pure)
│   │   ├── entities/        # Entités du domaine
│   │   ├── value-objects/   # Value Objects immuables
│   │   ├── use-cases/       # Cas d'usage métier
│   │   └── errors/          # Erreurs domaine
│   ├── ports/               # Interfaces (contrats)
│   │   ├── primary/         # Ports primaires (entrées)
│   │   └── secondary/       # Ports secondaires (sorties)
│   ├── adapters/            # Implémentations
│   │   ├── primary/         # Adapters primaires (MCP Server, API)
│   │   └── secondary/       # Adapters secondaires (DB, OpenAI, Logging)
│   ├── config/              # Configuration
│   │   ├── env.schema.ts    # Validation Zod de l'environnement
│   │   └── constants.ts     # Constantes applicatives
│   └── shared/              # Code partagé
│       ├── errors/          # Classes d'erreurs de base
│       ├── types/           # Types TypeScript partagés
│       └── utils/           # Utilitaires
├── tests/                   # Tests
│   ├── unit/               # Tests unitaires
│   ├── integration/        # Tests d'intégration
│   ├── architecture/       # Tests d'architecture (ts-arch)
│   └── fixtures/           # Données de test
├── scripts/                # Scripts utilitaires
├── docs/                   # Documentation
└── logs/                   # Logs applicatifs
```

## Architecture

Alexandria suit une **architecture hexagonale stricte** :

- **Domain** : Pure TypeScript, aucune dépendance externe
- **Ports** : Interfaces pures définissant les contrats
- **Adapters** : Implémentations concrètes des ports

**Règles critiques** (validées automatiquement) :
- Le domain ne dépend JAMAIS des adapters
- Le domain ne dépend JAMAIS de librairies externes (Zod, Drizzle, Hono)
- Les ports sont de pures interfaces TypeScript
- Zod est utilisé UNIQUEMENT aux boundaries (adapters)

Voir [CONVENTIONS.md](./CONVENTIONS.md) pour les conventions de nommage et les règles d'architecture.

## Documentation

- [Product Brief](./_bmad-output/project-planning-artifacts/brief/) - Vision et objectifs
- [PRD](./_bmad-output/project-planning-artifacts/prd/) - Spécifications fonctionnelles
- [Architecture](./_bmad-output/project-planning-artifacts/architecture/) - Décisions techniques
- [Conventions](./CONVENTIONS.md) - Conventions de nommage et architecture

## Statut du Projet

🚧 **En développement actif** - Epic 1 en cours (Foundation & Infrastructure)

Voir [sprint-status.yaml](./_bmad-output/implementation-artifacts/sprint-status.yaml) pour l'avancement détaillé.
