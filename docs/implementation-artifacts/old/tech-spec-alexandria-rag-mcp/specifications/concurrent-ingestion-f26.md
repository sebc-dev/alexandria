# Concurrent Ingestion (F26)

**Solution recommandee:** Advisory locks transaction-level (`pg_try_advisory_xact_lock`) pour eviter les race conditions lors d'ingestions simultanees du meme document.

---

## Probleme des ingestions simultanees

Avec READ COMMITTED, deux clients ingerant le meme document simultanement peuvent creer une race condition :

```
Client A: SELECT (document n'existe pas)
Client B: SELECT (document n'existe pas)
Client A: INSERT → succes
Client B: INSERT → violation contrainte unique OU doublon
```

---

## Solution : Advisory Locks Transaction-Level

Les advisory locks sont ideaux car ils :
- Ne bloquent pas les lectures normales
- Sont automatiquement liberes au COMMIT/ROLLBACK
- Offrent excellente performance (stockage en memoire partagee)

```typescript
import postgres from 'postgres';

const sql = postgres(DATABASE_URL, { max: 20 });

async function ingestDocument(
  source: string,
  content: string,
  chunks: ChunkInput[]
): Promise<Document> {
  return sql.begin('ISOLATION LEVEL READ COMMITTED', async sql => {
    // Hash de la source comme cle de lock
    const [{ acquired }] = await sql`
      SELECT pg_try_advisory_xact_lock(hashtext(${source})::bigint) as acquired
    `;

    if (!acquired) {
      throw new ConcurrentModificationError(
        `Document ${source} en cours de traitement par un autre client`
      );
    }

    // Upsert securise - lock acquis
    const [doc] = await sql`
      INSERT INTO documents (source, content, title, tags, version, updated_at)
      VALUES (${source}, ${content}, ${title}, ${tags}, ${version}, NOW())
      ON CONFLICT (source) DO UPDATE
      SET content = EXCLUDED.content,
          title = EXCLUDED.title,
          tags = EXCLUDED.tags,
          version = EXCLUDED.version,
          updated_at = NOW()
      RETURNING *
    `;

    // Delete existing chunks
    await sql`DELETE FROM chunks WHERE document_id = ${doc.id}`;

    // Batch insert new chunks
    if (chunks.length > 0) {
      await sql`
        INSERT INTO chunks ${sql(chunks.map((c, i) => ({
          document_id: doc.id,
          chunk_index: i,
          content: c.content,
          embedding: c.embedding,
          metadata: c.metadata || null
        })))}
      `;
    }

    return doc;
  });
  // Lock automatiquement libere au commit/rollback
}
```

---

## Comportement par scenario

| Scenario | Comportement |
|----------|--------------|
| 2 clients, meme source, concurrent | Premier acquiert lock, second recoit CONCURRENT_MODIFICATION (-31009) |
| Client A commit, Client B retry | Client B peut maintenant acquerir le lock et proceder |
| Client A rollback | Lock libere, Client B peut acquerir |
| Deadlock | Impossible avec advisory locks (pas de wait, try immediate) |

---

## Comparaison des strategies de locking

| Strategie | Meilleur pour | Inconvenients |
|-----------|---------------|---------------|
| **Advisory lock (xact)** | Ingestion document unique | Application doit gerer le rejet |
| **SELECT FOR UPDATE** | Modification row existante | Bloque les autres workers |
| **FOR UPDATE SKIP LOCKED** | Queue de traitement | Vue inconsistante by design |
| **ON CONFLICT seul** | Upserts simples | Ne protege pas logique multi-step |

**Choix:** Advisory lock transaction-level - optimal pour l'atomicite document + chunks.

---

## Gestion d'erreur cote client

```typescript
class ConcurrentModificationError extends Error {
  readonly code = -31009;  // Error code MCP (voir F2/F8)

  constructor(message: string) {
    super(message);
    this.name = 'ConcurrentModificationError';
  }
}

// Retry pattern cote client (optionnel)
async function ingestWithRetry(
  source: string,
  content: string,
  chunks: ChunkInput[],
  maxRetries = 3,
  baseDelayMs = 100
): Promise<Document> {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      return await ingestDocument(source, content, chunks);
    } catch (error) {
      if (error instanceof ConcurrentModificationError && attempt < maxRetries - 1) {
        const delay = baseDelayMs * Math.pow(2, attempt);
        logger.warn({ source, attempt, delay }, 'Concurrent modification, retrying...');
        await new Promise(resolve => setTimeout(resolve, delay));
        continue;
      }
      throw error;
    }
  }
  throw new Error('Max retries exceeded');
}
```

---

## Logging

```typescript
// Log quand lock non acquis
logger.warn({
  event: 'concurrent_ingestion_blocked',
  source: source,
  action: 'rejected'
}, 'Document en cours de traitement par autre client');

// Log quand retry reussi (si retry implemente)
logger.info({
  event: 'concurrent_ingestion_retry_success',
  source: source,
  attempt: attempt
}, 'Ingestion reussie apres retry');
```
