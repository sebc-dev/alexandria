# Overview

## Problem Statement

Claude Code n'a pas accès à la documentation technique à jour ni aux conventions de code du projet. Les développeurs perdent du temps à chercher manuellement dans la documentation ou à reformuler des questions. Besoin d'un système de recherche sémantique pour alimenter le contexte de Claude Code avec des informations pertinentes et actualisées.

## Solution

Serveur MCP en Java 25 exposant un RAG basé sur pgvector :
- **Transport MCP** : Spring Boot 3.5.9 + Spring AI MCP SDK 1.1.2 (HTTP Streamable via `/mcp`)
- **Pipeline RAG** : Langchain4j (embeddings, retrieval, reranking)
- **Embeddings** : BGE-M3 sur RunPod/Infinity
- **Retry** : Resilience4j 2.3.0 avec @Retry (exponential backoff)
- **Architecture** : Flat et simplifiée (YAGNI) - pas d'hexagonal, pas de DDD

## Scope

**In Scope:**
- Serveur MCP Java 25 avec Spring AI MCP SDK 1.1.2 (HTTP Streamable transport)
- Skills Claude Code pour recherche sémantique
- Ingestion de documents (markdown, texte, llms.txt)
- PostgreSQL 18 + pgvector 0.8.1 (vector 1024D)
- Embeddings via RunPod/Infinity (BGE-M3)
- Reranking (bge-reranker-v2-m3)
- Usage mono-utilisateur

**Out of Scope:**
- Système de mise à jour automatique des sources
- Système de cache (prévu pour version ultérieure)
- Support multi-utilisateur
- Interface web d'administration
