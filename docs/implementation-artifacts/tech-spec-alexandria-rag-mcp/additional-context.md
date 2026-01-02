# Additional Context

## Dependencies

**Runtime:**

```json
{
  "@modelcontextprotocol/sdk": "^1.22.0",
  "@huggingface/transformers": "^3.0.0",
  "drizzle-orm": "^0.44.0",
  "postgres": "^3.4.0",
  "zod": "^3.25.0",
  "pino": "^9.0.0"
}
```

**⚠️ Notes critiques:**
- **MCP SDK**: La version 2.0.0 n'existe pas (pré-alpha sur main). Utiliser ^1.22.0 en production.
- **Zod**: Avec zod 3.25.0, utiliser `import { z } from "zod/v3"` pour compatibilité MCP (issue #1429).
- **postgres**: Utiliser le package npm `postgres`, PAS `bun:sql` (bugs connus en production).

**Note F39:** Le package npm `pgvector` n'est **pas nécessaire**. Drizzle-orm supporte nativement pgvector via `drizzle-orm/pg-core` et l'opérateur `<=>` est utilisé directement dans les queries SQL brutes avec `postgres`.

**Development:**

```json
{
  "@types/bun": "latest",
  "drizzle-kit": "^0.30.0",
  "typescript": "^5.7.0",
  "testcontainers": "^10.0.0",
  "pino-pretty": "^11.0.0"
}
```

**Testcontainers Compatibility (F27):**
- Image: `pgvector/pgvector:pg18` (PostgreSQL 18 + pgvector 0.8.1)
- Docker version: 20.10+
- testcontainers 10.x supporte cette image
- Note: L'image `ankane/pgvector` est obsolète, utiliser `pgvector/pgvector` (organisation officielle)

**External Services:**

- PostgreSQL 18 avec extension pgvector 0.8.1
- Aucun service cloud requis

## Database Schema

```sql
-- Extension pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Configuration globale ef_search (F19)
ALTER DATABASE alexandria SET hnsw.ef_search = 40;

-- Documents source
CREATE TABLE documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source TEXT NOT NULL UNIQUE,
  title TEXT,
  content TEXT NOT NULL,
  tags TEXT[],
  version TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Chunks avec embeddings
CREATE TABLE chunks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  embedding vector(384) NOT NULL,
  chunk_index INTEGER NOT NULL,
  metadata JSONB,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(document_id, chunk_index)  -- F35: Garantit unicité index par document
);

-- Index HNSW avec paramètres optimisés (F4, F19)
CREATE INDEX chunks_embedding_idx ON chunks
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);

-- Index pour filtrage par tags
CREATE INDEX documents_tags_idx ON documents USING GIN (tags);

-- Index pour lookup par source
CREATE INDEX documents_source_idx ON documents (source);
```

## Migration Rollback (F45)

**Down migration (drizzle/0000_initial_down.sql):**
```sql
DROP INDEX IF EXISTS documents_source_idx;
DROP INDEX IF EXISTS documents_tags_idx;
DROP INDEX IF EXISTS chunks_embedding_idx;
DROP TABLE IF EXISTS chunks;
DROP TABLE IF EXISTS documents;
DROP EXTENSION IF EXISTS vector;
```

**Note:** Drizzle-kit ne génère pas automatiquement les down migrations. Le fichier ci-dessus doit être créé manuellement pour permettre un rollback complet.

## Logging Strategy (F15)

**Log Levels:**
- `debug`: Requêtes détaillées, embeddings, SQL queries
- `info`: Démarrage, ingestion réussie, recherches, model state transitions (F23)
- `warn`: Retries, thresholds non atteints, chunks > 4000 chars (F24), concurrent conflicts (F26)
- `error`: Échecs, rollbacks, erreurs MCP

**Format:** JSON structuré en production, pretty en dev

```typescript
logger.info({
  event: 'model_state_change',
  from: 'loading',
  to: 'loaded',
  duration_ms: 45000
});
```

## Testing Strategy

**Unit Tests (Bun test):**

- Domain entities: validation taille (F24), error codes
- Application use cases: retry jitter (F22), upsert atomicity (F25)
- Coverage cible: 80%+

**Integration Tests (Testcontainers) (F27):**

- Image: `pgvector/pgvector:pg18`
- Repository: CRUD, upsert rollback (F25), concurrent ingestion (F26)
- Embedder: génération embeddings réels, dimension validation (F20)
- Database: migrations, indexes, ef_search config (F19)

**E2E Tests:**

- MCP flow complet avec tous les error scenarios
- CLI commands avec --dry-run validation
- Health check state machine (F23)
- Graceful shutdown avec model loading (F31)

## Feature Numbering Reference

**Omitted Feature Numbers:**

| Numéro | Raison | Contexte |
|--------|--------|----------|
| F9 | Non défini lors de la planification | Gap dans la séquence initiale |
| F13 | Non défini lors de la planification | Gap dans la séquence initiale |

Ces deux numéros ont été délibérément omis de la spécification et ne correspondent à aucune exigence fonctionnelle. La numérotation F1-F45 utilise des identifiants non-continus pour des raisons historiques de planification.

## Notes

**Risques identifiés:**

- Premier chargement du modèle E5 peut prendre 1-2 min (download ~100MB)
- Testcontainers nécessite Docker pour les tests d'intégration
- pgvector 0.8.1 doit être installé dans l'image PostgreSQL

**Limitations connues:**

- Pas de mise à jour partielle de documents (upsert = delete + re-create)
- Pas de pagination des résultats (limit uniquement)
- Chunks pré-calculés requis (pas de chunking automatique)
- Tag filtering OR only (pas de AND logic dans MVP)
- Max 8000 chars par chunk, 500 chunks par document (F24)

**Évolutions futures (hors scope MVP):**

- Support PDF via pdf.js
- Watcher filesystem pour ingestion auto
- Cache des embeddings fréquents
- Reranking avec cross-encoder
- Support SSE pour déploiement remote
- Tag filtering AND logic
- Pagination avec curseur
- Chunks plus longs avec chunking automatique
