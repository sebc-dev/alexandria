# SQLite RAG

## What This Is

Un système de Retrieval-Augmented Generation (RAG) personnel utilisant SQLite avec sqlite-vector pour indexer et rechercher de la documentation technique. Exposé via un serveur MCP pour intégration avec Claude Code.

## Core Value

Claude Code peut accéder à ma documentation technique personnelle pendant l'implémentation pour respecter mes conventions et bonnes pratiques.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Indexer des fichiers markdown (documentation, conventions, bonnes pratiques)
- [ ] Utiliser un modèle d'embedding local (pas d'API externe)
- [ ] Stocker les embeddings dans SQLite avec sqlite-vector
- [ ] Exposer la recherche via serveur MCP
- [ ] Recherche sémantique sur la documentation indexée
- [ ] Commande CLI pour indexer/réindexer manuellement
- [ ] Skill Claude Code pour déclencher l'indexation
- [ ] Retourner les documents pertinents avec leur contexte

### Out of Scope

- Indexation automatique (watch mode) — complexité inutile, commande manuelle suffit
- Interface web — usage via Claude Code uniquement
- API externe pour embeddings — doit fonctionner offline
- Multi-utilisateur — usage personnel uniquement

## Context

**Cas d'usage principal :**
Pendant une implémentation basée sur des specs, Claude Code lance une recherche sur la documentation de la technologie utilisée. Il récupère la documentation pertinente avec conventions et bonnes pratiques pour implémenter correctement.

**Contenu à indexer :**
- Documentation de frameworks/librairies (React, Node, etc.)
- Conventions de code personnelles
- Bonnes pratiques maintenues manuellement
- Format : fichiers markdown

**Intégration :**
- Serveur MCP connecté à Claude Code
- Claude Code utilise les tools MCP pour chercher dans la base

## Constraints

- **Stack**: TypeScript — langage principal
- **Database**: SQLite + sqlite-vector — stockage embeddings
- **Embeddings**: Modèle local — pas de dépendance API externe
- **Interface**: MCP Server — intégration Claude Code native
- **Usage**: Personnel — pas besoin de gestion multi-utilisateur

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| SQLite + sqlite-vector | Léger, portable, pas de serveur DB séparé | — Pending |
| Modèle embedding local | Fonctionne offline, gratuit, données privées | — Pending |
| MCP Server | Intégration native Claude Code | — Pending |
| Indexation manuelle | Simple, contrôlé, pas de overhead watch mode | — Pending |

---
*Last updated: 2026-01-19 after initialization*
