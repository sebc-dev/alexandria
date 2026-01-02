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
  "pino": "^9.0.0",
  "xstate": "^5.0.0"
}
```

**⚠️ Notes critiques:**

- **MCP SDK**: La version 2.0.0 n'existe pas (pré-alpha sur main). Utiliser ^1.22.0 en production.
- **Zod**: Avec zod 3.25.0, utiliser `import { z } from "zod/v3"` pour compatibilité MCP (issue #1429).
- **postgres**: Utiliser le package npm `postgres`, PAS `bun:sql` (bugs connus en production).
- **Pool size**: Configurer 15-20 connexions (`max: 20`) pour supporter la concurrence d'ingestion sans contention.
- **Transformers.js**: TOUJOURS utiliser `normalize: true` dans les options d'extraction (E5 ne normalise pas par défaut).
- **Threshold**: Le défaut est 0.82 (pas 0.5) — E5 utilise température 0.01 qui compresse les scores.
- **XState**: v5 pour la machine d'états du modèle ML (F23). Pattern `fromPromise` pour opérations async.

**Note F39:** Le package npm `pgvector` n'est **pas nécessaire**. Drizzle-orm supporte nativement pgvector via `drizzle-orm/pg-core` et l'opérateur `<=>` est utilisé directement dans les queries SQL brutes avec `postgres`.

## Bun Version Requirements

**⚠️ MINIMUM: Bun 1.3.5** (décembre 2025)

| Issue | Impact | Version corrigée |
|-------|--------|------------------|
| NAPI hot reload crashes | Crash lors de rechargement | ✅ Bun 1.3.5 |
| Bus error ARM64 macOS (Bun #3574) | Crash ONNX Darwin ARM64 | ⚠️ Utiliser onnxruntime-web |
| Windows file path encoding | Chemins modèle corrompus | ✅ Bun 1.2.4 ou WSL |

**Vérification:**

```bash
bun --version  # Doit afficher >= 1.3.5
```

## Transformers.js Configuration

**Configuration recommandée pour E5:**

```typescript
import { pipeline, env } from '@huggingface/transformers';

// OBLIGATOIRE: Configurer cache directory explicitement
// (corrige issue v3.0.0-alpha.5 qui re-téléchargeait à chaque run)
env.cacheDir = process.env.HF_CACHE_DIR || '~/.cache/huggingface';

// Charger le pipeline avec quantification
const extractor = await pipeline('feature-extraction', 'Xenova/multilingual-e5-small', {
  revision: 'main',
  dtype: 'q8',  // Quantifié int8 pour efficacité mémoire (~118MB vs 470MB fp32)
});

// Extraction avec normalisation OBLIGATOIRE
const output = await extractor(text, {
  pooling: 'mean',
  normalize: true,  // ⚠️ OBLIGATOIRE - E5 ne normalise pas par défaut
});
```

**Timeouts model loading:**

| Scénario | Timeout recommandé |
|----------|-------------------|
| Cold start (premier téléchargement) | 180 secondes |
| Warm cache (modèle local) | 60 secondes |

```typescript
const COLD_START_TIMEOUT = 180_000;  // 3 minutes
const WARM_CACHE_TIMEOUT = 60_000;   // 1 minute

const timeout = fs.existsSync(env.cacheDir) ? WARM_CACHE_TIMEOUT : COLD_START_TIMEOUT;
```

## ONNX Session Options

**Configuration pour stabilité et gestion mémoire:**

```typescript
import * as ort from 'onnxruntime-node';

const sessionOptions: ort.InferenceSession.SessionOptions = {
  enableCpuMemArena: false,     // Réduit sévérité fuite mémoire
  enableMemPattern: false,       // Gestion mémoire supplémentaire
  graphOptimizationLevel: 'all',
  executionMode: 'sequential',
  intraOpNumThreads: 1,          // Limite threads pour stabilité Bun
};
```

**⚠️ CRITIQUE - Disposal des tensors:**

ONNX Runtime a une fuite mémoire documentée (Issue #25325). Toujours disposer explicitement:

```typescript
async function getEmbedding(session: ort.InferenceSession, text: string): Promise<number[]> {
  const inputTensor = new ort.Tensor('float32', prepareInput(text), [1, tokenLength]);

  try {
    const results = await session.run({ input: inputTensor });
    const embeddings = Array.from(results.embeddings.data as Float32Array);

    // CRITIQUE: Disposer tous les tensors de sortie
    for (const key in results) {
      results[key].dispose();
    }
    return embeddings;
  } finally {
    inputTensor.dispose();  // Toujours disposer les inputs
  }
}
```

**Monitoring mémoire:**

- Trigger recreation de session si heap > 85% de la mémoire disponible
- Réutiliser les sessions plutôt que recréer (évite accumulation mémoire)
- Recreation atomique: créer nouvelle session AVANT disposer l'ancienne

## Transaction & Concurrence (F18, F25, F26)

**Isolation:** READ COMMITTED (defaut PostgreSQL) - suffisant pour l'ingestion document/chunks avec pgvector.

**Pattern upsert recommande:**

```typescript
import postgres from 'postgres';

const sql = postgres(DATABASE_URL, { max: 20 });

await sql.begin('ISOLATION LEVEL READ COMMITTED', async sql => {
  // 1. Advisory lock pour eviter race condition
  const [{ acquired }] = await sql`
    SELECT pg_try_advisory_xact_lock(hashtext(${source})::bigint) as acquired
  `;
  if (!acquired) throw new ConcurrentModificationError();

  // 2. ON CONFLICT pour parent (efficace)
  const [doc] = await sql`
    INSERT INTO documents (...) VALUES (...)
    ON CONFLICT (source) DO UPDATE SET ...
    RETURNING *
  `;

  // 3. DELETE + batch INSERT pour chunks
  await sql`DELETE FROM chunks WHERE document_id = ${doc.id}`;
  await sql`INSERT INTO chunks ${sql(chunks)}`;
});
```

**Concurrence:** Les advisory locks transaction-level (`pg_try_advisory_xact_lock`) evitent les race conditions lors d'ingestions simultanees. Liberation automatique au COMMIT/ROLLBACK.

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

-- Configuration globale HNSW (F19) - production standard
ALTER DATABASE alexandria SET hnsw.ef_search = 100;
ALTER DATABASE alexandria SET hnsw.iterative_scan = relaxed_order;

-- Documents source
-- Note: gen_random_uuid() natif depuis PostgreSQL 13
-- PostgreSQL 18+ offre uuidv7() pour UUIDs ordonnés chronologiquement (meilleures performances d'indexation)
CREATE TABLE documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source TEXT NOT NULL UNIQUE,  -- Index B-tree implicite créé par UNIQUE
  title TEXT,
  content TEXT NOT NULL,  -- TEXT supporte ~1GB, TOAST gère automatiquement
  tags TEXT[],
  version TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Chunks avec embeddings
CREATE TABLE chunks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  embedding vector(384) NOT NULL,  -- 1544 bytes par vecteur (4 × 384 + 8)
  chunk_index INTEGER NOT NULL,
  metadata JSONB DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(document_id, chunk_index)  -- F35: Index B-tree implicite, NE PAS dupliquer
);

-- Index HNSW avec paramètres production RAG (F4, F19)
-- vector_cosine_ops optimal pour sentence-transformers (embeddings normalisés)
CREATE INDEX chunks_embedding_idx ON chunks
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 100);

-- Index GIN pour filtrage par tags (optimise opérateur && overlap)
CREATE INDEX documents_tags_idx ON documents USING GIN (tags);

-- Index GIN pour metadata JSONB (jsonb_path_ops: 2-3× plus compact)
CREATE INDEX chunks_metadata_idx ON chunks USING GIN (metadata jsonb_path_ops);

-- NOTE: Pas d'index sur documents.source - UNIQUE crée automatiquement un B-tree
```

## Migration Rollback (F45)

**Down migration (drizzle/0000_initial_down.sql):**

```sql
DROP INDEX IF EXISTS chunks_metadata_idx;
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
- `warn`: Retries, thresholds non atteints, chunks > 1500 chars (F24 - approche limite tokens), concurrent conflicts (F26), circuit breaker half-open
- `error`: Échecs, rollbacks, erreurs MCP, circuit breaker open

**Format:** JSON structuré en production, pretty en dev

```typescript
logger.info({
  event: 'model_state_change',
  from: 'loading',
  to: 'loaded',
  duration_ms: 45000
});

logger.warn({
  event: 'circuit_breaker_open',
  failures: 5,
  window_ms: 60000,
  next_probe_ms: 30000
});
```

## Testing Strategy

**Unit Tests (Bun test):**

- Domain entities: validation taille (F24), error codes
- Application use cases: retry jitter (F22), upsert atomicity (F25)
- State machine: XState v5 transitions (F23)
- Circuit breaker: state transitions, failure counting
- Coverage cible: 80%+

**Integration Tests (Testcontainers) (F27):**

- Image: `pgvector/pgvector:pg18`
- Repository: CRUD, upsert rollback (F25), concurrent ingestion (F26)
- Embedder: génération embeddings réels, dimension validation (F20)
- Database: migrations, indexes, ef_search config (F19)
- Model loading: cold/warm cache timeouts, tensor disposal

**E2E Tests:**

- MCP flow complet avec tous les error scenarios
- CLI commands avec --dry-run validation
- Health check state machine (F23): three-probe pattern (live/ready/startup)
- Graceful shutdown avec model loading (F31)
- Circuit breaker behavior under failure scenarios

## Feature Numbering Reference

**Omitted Feature Numbers:**

| Numéro | Raison | Contexte |
|--------|--------|----------|
| F9 | Non défini lors de la planification | Gap dans la séquence initiale |
| F13 | Non défini lors de la planification | Gap dans la séquence initiale |

Ces deux numéros ont été délibérément omis de la spécification et ne correspondent à aucune exigence fonctionnelle. La numérotation F1-F45 utilise des identifiants non-continus pour des raisons historiques de planification.

## Notes

**Risques identifiés:**

- Premier chargement du modèle E5 peut prendre 1-3 min (download ~100MB) — timeout 180s
- Testcontainers nécessite Docker pour les tests d'intégration
- pgvector 0.8.1 doit être installé dans l'image PostgreSQL
- Fuite mémoire ONNX Runtime — disposal explicite et monitoring requis
- Compatibilité Bun/ONNX sur ARM64 macOS — considérer onnxruntime-web

**Limitations connues:**

- Pas de mise à jour partielle de documents (upsert = delete + re-create)
- Pas de pagination des résultats (limit uniquement)
- Chunks pré-calculés requis (pas de chunking automatique)
- Tag filtering OR only (pas de AND logic dans MVP)
- Max 2000 chars par chunk (E5 limite: 512 tokens), 500 chunks par document (F24)

**Évolutions futures (hors scope MVP):**

- Support PDF via pdf.js
- Watcher filesystem pour ingestion auto
- Cache des embeddings fréquents
- Reranking avec cross-encoder
- Support SSE pour déploiement remote
- Tag filtering AND logic
- Pagination avec curseur
- Chunking automatique intelligent (respect limite 512 tokens E5)
- Migration vers onnxruntime-web pour portabilité ARM64
