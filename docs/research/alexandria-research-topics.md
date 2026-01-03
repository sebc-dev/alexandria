# Alexandria RAG MCP Server - Guide de Recherche

**Date**: 2026-01-03
**Objectif**: Valider les choix techniques, identifier les versions à jour, clarifier les zones d'ombre.

---

## Prompt de Contexte Projet (Instructions Claude Project)

Ce prompt doit être copié dans les **Instructions de Projet** d'un projet Claude dédié à la recherche Alexandria.

```
# Contexte Projet Alexandria

Tu es un assistant de recherche technique spécialisé pour le projet Alexandria.

## Description du Projet

Alexandria est un serveur RAG (Retrieval-Augmented Generation) conçu pour Claude Code. Il permet de stocker et rechercher de la documentation technique et des conventions de code via recherche sémantique.

## Architecture Cible

- **Type**: Serveur MCP (Model Context Protocol) + Skills Claude Code
- **Pattern**: Architecture Hexagonale (Domain/Application/Infrastructure)
- **Usage**: Mono-utilisateur, développeur utilisant Claude Code quotidiennement

## Stack Technique Envisagée

| Composant | Choix Initial | À Valider |
|-----------|---------------|-----------|
| Runtime | Bun 1.3.5+ | Version actuelle, compatibilité |
| Langage | TypeScript | Config optimale Bun |
| Database | PostgreSQL 18 + pgvector 0.8.1 | Versions réelles disponibles |
| Vector Type | halfvec (1024D) | Support, performance |
| ORM | Drizzle + postgres.js | Support pgvector/halfvec |
| Embeddings | Qwen3-Embedding-0.6B | Existence, specs exactes |
| Reranker | bge-reranker-v2-m3 | Existence, specs exactes |
| Inference | Infinity sur RunPod | Config, déploiement |

## Contraintes Hardware (Self-hosted)

- CPU: Intel Core i5-4570 (4c/4t @ 3.2-3.6 GHz, Haswell 2013)
- RAM: 24 GB DDR3-1600
- Pas de GPU local (embeddings/reranking déportés sur RunPod)

## Sources de Données

- Fichiers Markdown
- Fichiers texte
- Format llms.txt / llms-full.txt (standard https://llmstxt.org/)
- Estimation: centaines de documents (taille documentation technique typique)

## Objectifs de la Recherche

1. **Valider** les choix techniques (versions, compatibilités)
2. **Identifier** les versions stables actuelles (janvier 2026)
3. **Clarifier** les zones d'ombre techniques
4. **Documenter** les best practices actuelles
5. **Proposer** des alternatives si un choix initial est problématique

## Format de Réponse Attendu

Pour chaque recherche, structure ta réponse ainsi:

### [Sujet]

**Status**: ✅ Validé | ⚠️ À modifier | ❌ Bloquant

**Résumé**:
- Réponse concise à la question

**Détails**:
- Informations complètes trouvées
- Versions exactes avec dates
- Configurations recommandées

**Impact sur Alexandria**:
- Ce que ça change pour le projet
- Décisions à prendre

**Sources**:
- [Nom](URL) - Description
- Documentation officielle privilégiée

## Règles

- Privilégie les sources officielles et la documentation récente (2025-2026)
- Indique clairement quand une information est incertaine
- Si un choix technique est obsolète ou problématique, propose une alternative
- Donne des exemples de code/config quand pertinent
- Sois précis sur les numéros de version
```

---

## Prompts de Recherche Individuels

Chaque prompt ci-dessous lance une recherche spécifique dans une nouvelle conversation du projet Claude.

---

### 1. Runtime & Stack

#### 1.1 Bun Runtime

```
Recherche: Bun Runtime pour serveur MCP

Questions à investiguer:
1. Quelle est la version stable actuelle de Bun en janvier 2026?
2. Bun est-il compatible avec les packages suivants?
   - postgres.js (driver PostgreSQL)
   - drizzle-orm
   - @modelcontextprotocol/sdk (SDK MCP officiel)
3. Quelles sont les limitations connues de Bun vs Node.js pour:
   - Les serveurs long-running
   - Les connexions WebSocket/SSE
   - La gestion de processus stdio
4. Best practices pour déployer Bun en production sur Linux
5. Y a-t-il des problèmes connus avec Bun + TypeScript strict?

Contexte: Le serveur MCP Alexandria tournera en self-hosted sur un serveur Linux avec i5-4570 et 24GB RAM.
```

#### 1.2 Drizzle ORM + postgres.js

```
Recherche: Drizzle ORM avec pgvector et postgres.js

Questions à investiguer:
1. Versions stables actuelles de Drizzle ORM et postgres.js (janvier 2026)
2. Drizzle supporte-t-il nativement pgvector?
   - Si oui, depuis quelle version?
   - Support du type `vector` standard
   - Support du type `halfvec`
3. Si pas de support natif, comment intégrer pgvector?
   - Custom types Drizzle
   - Raw SQL queries
   - Extensions communautaires
4. Configuration recommandée du pool de connexions postgres.js pour:
   - Serveur mono-utilisateur
   - Requêtes vectorielles (potentiellement lentes)
5. Exemples de schema Drizzle avec colonnes vectorielles

Contexte: On veut stocker des embeddings 1024D en halfvec pour économiser 50% de mémoire.
```

---

### 2. Base de Données

#### 2.1 PostgreSQL + pgvector Versions

```
Recherche: PostgreSQL et pgvector - versions et compatibilité halfvec

Questions à investiguer:
1. PostgreSQL 18 est-il sorti en janvier 2026?
   - Si non, quelle est la dernière version stable?
   - Fonctionnalités pertinentes pour RAG dans les versions récentes
2. pgvector - version stable actuelle
   - Le type `halfvec` existe-t-il? Depuis quelle version?
   - Si halfvec n'existe pas, quelles alternatives pour réduire la taille des vecteurs?
3. Performance halfvec vs vector pour 1024 dimensions:
   - Économie mémoire réelle
   - Impact sur la précision des recherches
   - Impact sur la vitesse des requêtes
4. Configuration PostgreSQL optimale pour serveur 24GB RAM avec workload vectoriel

Contexte: Serveur self-hosted i5-4570, 24GB RAM, centaines de documents, usage mono-utilisateur.
```

#### 2.2 Indexation Vectorielle pgvector

```
Recherche: Indexation HNSW pgvector pour RAG

Questions à investiguer:
1. HNSW vs IVFFlat - quel index pour ~1000-5000 vecteurs 1024D?
   - Trade-offs performance/précision/mémoire
   - Recommandation pour notre échelle
2. Paramètres HNSW optimaux:
   - `m` (connections par noeud) - valeur recommandée pour 1024D
   - `ef_construction` - valeur recommandée
   - `ef_search` - valeur recommandée au runtime
3. Stratégie de création d'index:
   - Créer avant ou après bulk insert?
   - Impact sur la vitesse d'insertion
   - Maintenance de l'index (REINDEX nécessaire?)
4. Index partiel/filtré - pertinent pour filtrer par source/tags?
5. Opérateurs de distance:
   - `<->` (L2) vs `<=>` (cosine) vs `<#>` (inner product)
   - Lequel utiliser avec des embeddings normalisés?

Contexte: RAG avec ~1000 documents initialement, potentiellement 5000+. Requêtes avec filtres metadata (source, tags).
```

---

### 3. Embeddings

#### 3.1 Qwen3-Embedding Validation

```
Recherche: Modèle d'embedding Qwen3-Embedding-0.6B

Questions à investiguer:
1. Le modèle "Qwen3-Embedding-0.6B" existe-t-il?
   - Nom exact sur HuggingFace
   - Si n'existe pas, quel est le modèle Qwen embedding le plus proche?
2. Spécifications exactes:
   - Dimensions de sortie (1024D?)
   - Longueur de contexte max (32K tokens?)
   - Taille du modèle (paramètres, VRAM requise)
3. Usage du modèle:
   - Nécessite-t-il des prefixes? ("query:", "passage:" comme E5?)
   - Les embeddings sont-ils normalisés (L2)?
   - Quel opérateur de similarité utiliser?
4. Performance:
   - Benchmarks MTEB ou similaires
   - Comparaison avec BGE-M3, E5-large, Nomic-embed
5. Compatibilité Infinity:
   - Le modèle est-il supporté par Infinity?
   - Configuration requise

Contexte: On cherche un modèle embedding performant, pas trop gros, pour tourner sur GPU cloud (RunPod).
```

#### 3.2 Alternatives Embedding Models

```
Recherche: Comparatif modèles d'embedding pour RAG documentation technique

Questions à investiguer:
1. Top modèles d'embedding en janvier 2026 pour RAG:
   - Benchmarks MTEB actuels
   - Focus sur retrieval de documentation technique
2. Comparatif détaillé pour notre use case:
   | Modèle | Dims | Context | VRAM | Score Retrieval | Notes |
   - BGE-M3
   - Nomic-embed-text-v1.5/v2
   - E5-mistral-7b-instruct
   - Modèles Qwen embedding
   - Jina embeddings v3
3. Modèles avec dimensions flexibles (Matryoshka):
   - Avantages pour réduire à 512D ou 768D?
   - Impact sur la qualité
4. Support multilingue:
   - Documentation en français et anglais
   - Quel modèle gère le mieux le mix?
5. Recommandation finale pour Alexandria:
   - Meilleur ratio qualité/coût/vitesse
   - Compatible Infinity

Contexte: RAG pour documentation technique, usage mono-utilisateur, budget GPU cloud à optimiser.
```

#### 3.3 Stratégie de Chunking

```
Recherche: Stratégie de chunking pour documentation technique markdown

Questions à investiguer:
1. Taille optimale des chunks pour RAG documentation:
   - Nombre de tokens recommandé
   - Études/benchmarks récents
2. Stratégies de chunking:
   - Fixed-size avec overlap
   - Sémantique (par section markdown)
   - Recursive character splitting
   - Sentence-based
   - Quelle stratégie pour markdown technique?
3. Overlap entre chunks:
   - Pourcentage recommandé
   - Impact sur la qualité vs duplication
4. Gestion spéciale:
   - Blocs de code (ne pas couper au milieu)
   - Tableaux markdown
   - Listes à puces
   - Headers comme metadata
5. Librairies TypeScript/JavaScript pour chunking:
   - LangChain.js text splitters
   - Alternatives légères
6. Chunking spécifique llms.txt:
   - Structure du format
   - Comment découper intelligemment

Contexte: Documents markdown de documentation technique, code examples inclus, mix français/anglais.
```

---

### 4. Reranking

#### 4.1 BGE Reranker Validation

```
Recherche: Modèle de reranking bge-reranker-v2-m3

Questions à investiguer:
1. Le modèle "bge-reranker-v2-m3" existe-t-il?
   - Nom exact sur HuggingFace
   - Si n'existe pas, quel est le modèle BGE reranker actuel?
2. Spécifications exactes:
   - Longueur de contexte max (query + document)
   - Taille du modèle (VRAM requise)
   - Latence typique pour rerank de 20-50 documents
3. Performance:
   - Benchmarks vs Cohere Rerank, cross-encoders
   - Amélioration typique vs semantic search seul
4. Compatibilité Infinity:
   - Supporté nativement?
   - Endpoint API pour reranking
5. Architecture recommandée:
   - Récupérer N candidats, rerank vers K résultats
   - Valeurs N et K optimales

Contexte: Reranking pour améliorer la précision du RAG, exécuté sur GPU cloud via Infinity.
```

---

### 5. Infinity Server

#### 5.1 Configuration Infinity

```
Recherche: Infinity Embedding Server - configuration et déploiement

Questions à investiguer:
1. Infinity - version stable actuelle (janvier 2026)
   - Repo GitHub officiel
   - Dernière release
2. Fonctionnalités:
   - Support simultané embedding + reranking?
   - Modèles supportés (vérifier nos choix)
   - API format (OpenAI-compatible?)
3. Configuration pour notre stack:
   - Config YAML/JSON exemple pour Qwen embedding + BGE reranker
   - Batching - taille optimale
   - Paramètres de performance
4. Endpoints API:
   - /embeddings ou /embed?
   - /rerank?
   - Format requête/réponse
5. Gestion des erreurs:
   - Codes d'erreur
   - Rate limiting
   - Health check endpoint

Contexte: On veut un serveur unique qui gère embedding et reranking pour Alexandria.
```

#### 5.2 Déploiement RunPod

```
Recherche: Déploiement Infinity sur RunPod

Questions à investiguer:
1. GPU recommandé pour nos modèles:
   - Qwen embedding (~0.6B params)
   - BGE reranker
   - VRAM minimum requise
   - Options RunPod: RTX 3090, RTX 4090, A10, T4, L4
2. Options de déploiement:
   - Pod dédié vs Serverless
   - Trade-offs pour usage mono-utilisateur quotidien
   - Cold start serverless - acceptable?
3. Template/Image Docker:
   - Image Infinity officielle pour RunPod?
   - Dockerfile custom si nécessaire
4. Coût estimé:
   - $/heure pour GPU recommandé
   - Estimation mensuelle pour usage 8h/jour
5. Configuration réseau:
   - Exposer le port Infinity
   - HTTPS/TLS
   - Authentication
6. Persistance:
   - Besoin de volume pour les modèles?
   - Cache des modèles

Contexte: Budget à optimiser, usage développeur solo ~8h/jour en semaine.
```

---

### 6. MCP Protocol

#### 6.1 SDK MCP TypeScript

```
Recherche: SDK MCP officiel pour TypeScript/Bun

Questions à investiguer:
1. SDK MCP officiel:
   - Package npm (@modelcontextprotocol/sdk?)
   - Version actuelle
   - Documentation officielle
2. Compatibilité Bun:
   - Testé/supporté officiellement?
   - Issues connues?
   - Workarounds si problèmes
3. Structure serveur MCP:
   - Boilerplate minimal
   - Définition des tools
   - Gestion des resources
4. Transports supportés:
   - stdio (pour Claude Code CLI)
   - HTTP/SSE
   - Lequel pour notre use case
5. Exemple de serveur MCP minimal en TypeScript:
   - Setup projet
   - Tool definition
   - Handler implementation

Contexte: Serveur MCP pour exposer les fonctionnalités RAG à Claude Code.
```

#### 6.2 Design des Outils MCP pour RAG

```
Recherche: Design des outils MCP pour un serveur RAG

Questions à investiguer:
1. Outils RAG standards:
   - search/query - recherche sémantique
   - ingest - ajout de documents
   - list_sources - lister les sources indexées
   - delete - suppression de documents
   - Autres outils utiles?
2. Schema JSON pour chaque outil:
   - Paramètres d'entrée
   - Format de sortie
   - Best practices MCP
3. Gestion des résultats volumineux:
   - Pagination
   - Limite de tokens
   - Truncation intelligente
4. Metadata dans les réponses:
   - Score de similarité
   - Source du chunk
   - Contexte additionnel
5. Exemples de serveurs MCP RAG existants:
   - Implémentations open source
   - Patterns à suivre/éviter

Contexte: Outils MCP pour recherche dans documentation technique, utilisés par Claude Code.
```

#### 6.3 Skills Claude Code

```
Recherche: Création de Skills Claude Code avec intégration MCP

Questions à investiguer:
1. Qu'est-ce qu'un Skill Claude Code?
   - Documentation officielle
   - Différence avec MCP tools
2. Comment créer un Skill:
   - Structure de fichier
   - Format de définition
   - Emplacement dans le projet
3. Intégration Skill + MCP:
   - Un Skill peut-il appeler un serveur MCP?
   - Ou le Skill EST le wrapper du MCP tool?
   - Best practice pour combiner les deux
4. Exemples de Skills existants:
   - Skills built-in Claude Code
   - Skills communautaires
5. Configuration dans Claude Code:
   - Fichier settings
   - Activation des Skills

Contexte: On veut que l'utilisateur puisse invoquer le RAG via /search ou commande naturelle.
```

---

### 7. Architecture

#### 7.1 Architecture Hexagonale TypeScript

```
Recherche: Architecture Hexagonale en TypeScript pour serveur MCP

Questions à investiguer:
1. Structure de dossiers recommandée:
   ```
   src/
   ├── domain/
   ├── application/
   └── infrastructure/
   ```
   - Détail de chaque couche
   - Où placer quoi
2. Implémentation des Ports & Adapters:
   - Port = interface TypeScript
   - Adapter = implémentation
   - Exemples concrets pour:
     - Repository documents/chunks
     - Service d'embedding (Infinity)
     - Base de données (pgvector)
3. Où placer le serveur MCP:
   - Infrastructure (adapter)?
   - Application (use case)?
   - Dedicated entrypoint?
4. Dependency Injection en TypeScript:
   - Sans framework lourd
   - Factory pattern
   - Composition root
5. Exemple de projet TypeScript hexagonal:
   - Repos GitHub de référence
   - Patterns à suivre

Contexte: Projet RAG avec pgvector, Infinity, MCP. Testabilité et maintenabilité prioritaires.
```

---

### 8. Format llms.txt

#### 8.1 Spécification llms.txt

```
Recherche: Format llms.txt - spécification complète

Questions à investiguer:
1. Spécification officielle (https://llmstxt.org/):
   - Structure du fichier
   - Champs obligatoires vs optionnels
   - Syntaxe exacte
2. Différence llms.txt vs llms-full.txt:
   - Quand utiliser l'un vs l'autre
   - Contenu attendu dans chaque
3. Exemples réels:
   - Sites qui implémentent llms.txt
   - Exemples de fichiers bien formés
4. Parsing:
   - Parser TypeScript/JavaScript existant?
   - Regex ou grammaire formelle?
   - Gestion des erreurs de format
5. Stratégie d'ingestion pour RAG:
   - Découper par section?
   - Garder les URLs comme metadata?
   - Récupérer le contenu des URLs linkées?

Contexte: Support du format llms.txt comme source de données pour Alexandria RAG.
```

---

### 9. Sécurité & Production

#### 9.1 Sécurité et Résilience

```
Recherche: Sécurité et résilience pour serveur MCP RAG

Questions à investiguer:
1. Gestion des secrets:
   - Credentials Infinity/RunPod
   - Connection string PostgreSQL
   - Best practice: env vars, fichier config, secret manager?
2. Communication sécurisée:
   - MCP sur stdio - sécurité implicite?
   - HTTPS vers Infinity sur RunPod
   - Authentification API Infinity
3. Résilience appels externes:
   - Retry logic pour Infinity (embedding, rerank)
   - Exponential backoff
   - Timeouts recommandés
   - Circuit breaker - nécessaire pour mono-utilisateur?
4. Validation des entrées:
   - Sanitization des queries
   - Limite de taille des documents à ingérer
   - Protection injection
5. Health checks:
   - Vérifier connexion PostgreSQL
   - Vérifier disponibilité Infinity
   - Endpoint ou mécanisme MCP

Contexte: Serveur self-hosted, usage mono-utilisateur, mais bonnes pratiques de production.
```

---

### 10. Performance

#### 10.1 Optimisation PostgreSQL + pgvector

```
Recherche: Optimisation PostgreSQL pour RAG vectoriel sur hardware limité

Questions à investiguer:
1. Configuration PostgreSQL pour 24GB RAM:
   - shared_buffers
   - effective_cache_size
   - work_mem (important pour opérations vectorielles)
   - maintenance_work_mem
2. Configuration spécifique pgvector:
   - hnsw.ef_search
   - Parallélisation des requêtes vectorielles
3. Estimation capacité:
   - Combien de vecteurs 1024D halfvec dans 24GB?
   - Performance attendue (requêtes/sec)
   - Taille table pour 5000 documents avec chunks
4. Optimisations requêtes:
   - Requête hybride (filtre + similarité) - ordre des opérations
   - Explain analyze pour debug
   - Indexes sur colonnes de filtre (source, tags)
5. Maintenance:
   - VACUUM pour tables vectorielles
   - REINDEX périodique nécessaire?
   - Monitoring à mettre en place

Contexte: i5-4570, 24GB RAM, PostgreSQL avec pgvector, centaines de documents.
```

---

## Checklist de Recherche

| # | Sujet | Status | Notes |
|---|-------|--------|-------|
| 1.1 | Bun Runtime | ⬜ | |
| 1.2 | Drizzle + postgres.js | ⬜ | |
| 2.1 | PostgreSQL + pgvector versions | ⬜ | CRITIQUE |
| 2.2 | Indexation HNSW | ⬜ | |
| 3.1 | Qwen3-Embedding validation | ⬜ | CRITIQUE |
| 3.2 | Alternatives embedding | ⬜ | |
| 3.3 | Stratégie chunking | ⬜ | |
| 4.1 | BGE Reranker validation | ⬜ | |
| 5.1 | Infinity configuration | ⬜ | CRITIQUE |
| 5.2 | Déploiement RunPod | ⬜ | |
| 6.1 | SDK MCP TypeScript | ⬜ | CRITIQUE |
| 6.2 | Design outils MCP RAG | ⬜ | |
| 6.3 | Skills Claude Code | ⬜ | |
| 7.1 | Architecture Hexagonale TS | ⬜ | |
| 8.1 | Format llms.txt | ⬜ | |
| 9.1 | Sécurité et Résilience | ⬜ | |
| 10.1 | Optimisation PostgreSQL | ⬜ | |

---

## Résultats de Recherche

*(Section à compléter avec les findings de chaque recherche)*
