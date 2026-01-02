# Context for Development

## Codebase Patterns

**Confirmed Clean Slate** - Projet greenfield, aucun code existant.

**Architecture:** Hexagonale (Ports & Adapters)

- `domain/` - Entités métier pures, aucune dépendance externe
- `application/` - Use cases, ports (interfaces)
- `infrastructure/` - Adapters (DB, embeddings, repositories)
- `mcp/` - Transport layer MCP

**Conventions:**

- TypeScript strict mode
- Bun runtime & test runner
- ESM modules
- Drizzle ORM pour les migrations et queries
- Structured logging avec Pino

## Files to Create

| File                                               | Purpose                                   |
| -------------------------------------------------- | ----------------------------------------- |
| `src/domain/document.ts`                           | Entités Document, Chunk, metadata types   |
| `src/domain/search.ts`                             | SearchQuery, SearchResult value objects   |
| `src/domain/errors.ts`                             | Erreurs domaine typées                    |
| `src/application/ports.ts`                         | Interfaces (Embedder, Repository)         |
| `src/application/ingest.ts`                        | Use case d'ingestion                      |
| `src/application/search.ts`                        | Use case de recherche                     |
| `src/infrastructure/db/schema.ts`                  | Drizzle schema avec pgvector              |
| `src/infrastructure/db/index.ts`                   | Connection pool et client                 |
| `src/infrastructure/embeddings/e5-embedder.ts`     | Wrapper Transformers.js                   |
| `src/infrastructure/repositories/document-repo.ts` | Implémentation PostgreSQL                 |
| `src/mcp/server.ts`                                | Serveur MCP principal                     |
| `src/mcp/tools/search-tool.ts`                     | Tool MCP pour recherche                   |
| `src/mcp/tools/ingest-tool.ts`                     | Tool MCP pour ingestion                   |
| `src/mcp/tools/delete-tool.ts`                     | Tool MCP pour suppression                 |
| `src/mcp/tools/health-tool.ts`                     | Tool MCP pour health check                |
| `src/mcp/tools/list-tool.ts`                       | Tool MCP pour lister les documents (F42)  |
| `src/cli/ingest.ts`                                | CLI d'ingestion standalone                |

## Technical Decisions

| Decision          | Choice                                       | Rationale                                    |
| ----------------- | -------------------------------------------- | -------------------------------------------- |
| Runtime           | Bun                                          | Performance, TypeScript natif, test runner   |
| Database          | PostgreSQL 18 + pgvector 0.8.1               | Une seule DB pour vecteurs et metadata       |
| ORM               | Drizzle + postgres.js                        | Support natif pgvector, type-safe, léger     |
| Embedding Model   | Xenova/multilingual-e5-small@main            | Local, multilingue, 384 dim, branche stable  |
| Embedding Runtime | @huggingface/transformers (ONNX)             | Exécution locale performante                 |
| E5 Prefixes       | Auto-gérés                                   | `query:` pour recherche, `passage:` pour docs|
| Transport         | MCP SDK (stdio/SSE)                          | Intégration native Claude Code               |
| Validation        | Zod                                          | Requis par MCP SDK, réutilisé partout        |
| Ingestion         | Manuelle (CLI + MCP tool)                    | Contrôle total, simplicité MVP               |
| Logging           | Pino (structured JSON)                       | Observabilité, performance                   |
| CLI Parser        | Bun native (process.argv.slice(2) + parseArgs) | Zero deps, POSIX exit codes, allowNegative   |
| Transaction       | READ COMMITTED isolation                     | Standard PostgreSQL, bon compromis           |
