---
title: 'Alexandria RAG MCP Server'
slug: 'alexandria-rag-mcp'
created: '2026-01-03'
status: 'in-progress'
stepsCompleted: [1]
tech_stack:
  - Bun 1.3.5+
  - TypeScript
  - PostgreSQL 18
  - pgvector 0.8.1
  - Drizzle ORM
  - postgres.js
  - Infinity (embedding server)
  - BGE-M3
  - bge-reranker-v2-m3
files_to_modify: []
code_patterns:
  - Hexagonal Architecture (Domain/Application/Infrastructure)
test_patterns: []
---

# Tech-Spec: Alexandria RAG MCP Server

**Created:** 2026-01-03

## Overview

### Problem Statement

Claude Code n'a pas accès à de la documentation technique à jour ni aux conventions de code du projet. Les développeurs perdent du temps à chercher manuellement dans la documentation ou à reformuler des questions. Besoin d'un système de recherche sémantique pour alimenter le contexte de Claude Code avec des informations pertinentes et actualisées.

### Solution

Serveur MCP + skills Claude Code exposant un RAG basé sur pgvector, avec embeddings BGE-M3 et reranking bge-reranker-v2-m3 via Infinity sur RunPod (single endpoint). Le système permet l'ingestion de documents techniques (markdown, texte, llms.txt) et la recherche sémantique pour enrichir le contexte de Claude Code.

### Scope

**In Scope:**
- Serveur MCP avec outils de recherche sémantique
- Skills Claude Code pour intégration fluide
- Ingestion de documents (markdown, texte, llms.txt/llms-full.txt)
- Stockage PostgreSQL 18 + pgvector 0.8.1 (halfvec 1024D)
- Embeddings via BGE-M3 (Infinity/RunPod, single endpoint avec reranker)
- Reranking via bge-reranker-v2-m3
- Architecture hexagonale (Domain/Application/Infrastructure)
- Usage mono-utilisateur

**Out of Scope:**
- Système de mise à jour automatique des sources
- Système de cache (prévu pour version ultérieure)
- Support multi-utilisateur
- Interface web d'administration

## Context for Development

### Codebase Patterns

- **Architecture Hexagonale**: Séparation stricte Domain/Application/Infrastructure
- **Runtime**: Bun 1.3.5+ pour performance et compatibilité TypeScript native
- **ORM**: Drizzle avec postgres.js pour typage fort et requêtes performantes
- **Vector Storage**: pgvector 0.8.1 avec halfvec pour réduire l'empreinte mémoire (512 bytes vs 4KB par vecteur 1024D)
- **Embedding Model**: BGE-M3 (1024 dimensions, 8K tokens context, hybrid retrieval dense+sparse+ColBERT)
- **Reranker**: bge-reranker-v2-m3 pour améliorer la précision des résultats
- **MCP Protocol**: Exposition des outils via Model Context Protocol

### Files to Reference

| File | Purpose |
| ---- | ------- |

*(Projet greenfield - pas de fichiers existants)*

### Technical Decisions

| Decision | Choix | Justification |
|----------|-------|---------------|
| Runtime | Bun | Performance, TypeScript natif, compatible Node.js |
| Database | PostgreSQL 18 + pgvector | Robuste, SQL standard, halfvec support |
| ORM | Drizzle + postgres.js | Type-safe, léger, performant |
| Embeddings | BGE-M3 | Compatible Infinity, hybrid retrieval, même famille que reranker |
| Reranker | bge-reranker-v2-m3 | État de l'art, multilingue |
| Hosting embeddings | Infinity sur RunPod | GPU cloud, contrôle total, coût maîtrisé |
| Architecture | Hexagonale | Testabilité, découplage, maintenabilité |
| Vector type | halfvec | 50% économie mémoire pour 1024D |

### Hardware Cible (Self-hosted)

- CPU: Intel Core i5-4570 (4c/4t @ 3.2-3.6 GHz)
- RAM: 24 GB DDR3-1600
- Pas de GPU local (embeddings déportés sur RunPod)

## Implementation Plan

### Tasks

*(À définir en Step 2 - Investigation)*

### Acceptance Criteria

*(À définir en Step 2 - Investigation)*

## Additional Context

### Dependencies

*(À investiguer en Step 2)*

### Testing Strategy

*(À définir en Step 2)*

### Notes

- Format llms.txt: Standard défini sur https://llmstxt.org/
- Usage prévu: mono-utilisateur, développeur utilisant Claude Code quotidiennement
- Centaines de documents (taille typique de documentation technique)
