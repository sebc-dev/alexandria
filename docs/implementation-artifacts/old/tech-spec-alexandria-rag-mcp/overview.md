# Overview

## Problem Statement

Claude Code n'a pas accès à la documentation technique et aux conventions de code spécifiques à un projet. Le contexte doit être copié/collé manuellement ou rechargé à chaque session, ce qui crée de la friction et des incohérences.

## Solution

Un serveur MCP exposant une base de connaissances interrogeable par recherche sémantique. Les documents sont pré-découpés par un LLM externe (Gemini CLI ou Claude Code), vectorisés avec multilingual-e5-small en local, et stockés dans PostgreSQL+pgvector.

## Scope

**In Scope:**

- Serveur MCP (TypeScript + Bun)
- Ingestion manuelle de documents (Markdown, llms.txt, llms-full.txt)
- Chunking sémantique pré-calculé par LLM externe
- Embeddings locaux via multilingual-e5-small
- Stockage PostgreSQL 18 + pgvector 0.8.1
- Métadonnées: source, date, tags, version
- Recherche sémantique via MCP tools
- Configuration pour local dev + self-hosted

**Out of Scope:**

- Ingestion automatique (file watchers)
- PDF ou autres formats de fichiers
- Embedding models cloud (OpenAI, Voyage AI)
- UI/dashboard de gestion
- Multi-tenancy
- Authentification/permissions
