# Transaction Isolation (F18)

**Verdict architectural:** READ COMMITTED suffit pour l'ingestion document/chunks avec pgvector. Les garanties d'atomicite transactionnelle de PostgreSQL assurent que DELETE + INSERT sont atomiques meme avec ce niveau d'isolation.

---

## Comparaison READ COMMITTED vs REPEATABLE READ

| Aspect | READ COMMITTED | REPEATABLE READ |
|--------|----------------|-----------------|
| Snapshot | Par statement | Par transaction |
| Dirty reads | Impossible | Impossible |
| Non-repeatable reads | Possible | Evite |
| Phantom reads | Possible | Evite (PostgreSQL depasse le standard SQL) |
| Comportement sur conflit concurrent | Re-evalue WHERE apres attente | Erreur `could not serialize access` |
| Retry necessaire | Rarement | Oui, obligatoire |

**Choix:** READ COMMITTED - les cas limites de REPEATABLE READ sont acceptables pour la nature approximative de la recherche vectorielle.

---

## Specification

| Aspect | Specification |
|--------|---------------|
| Isolation Level | **READ COMMITTED** (defaut PostgreSQL) |
| Rationale | Bon compromis perf/consistance, retry non requis, atomicite garantie |
| Ingestion | Transaction unique englobant document + tous les chunks |
| Upsert | ON CONFLICT pour parent + DELETE/INSERT pour chunks |
| Search | Pas de transaction (read-only, snapshot suffisant) |
| Concurrence | Advisory locks transaction-level (voir F26) |

---

## Atomicite du pattern DELETE + INSERT

**Garantie essentielle:** Une transaction PostgreSQL est toujours atomique, meme avec READ COMMITTED. Si l'INSERT echoue apres un DELETE reussi, **tout est rollback automatiquement**.

Le DELETE "reussi" dans une transaction non-commitee n'est visible que par cette transaction. En cas d'echec ou de ROLLBACK explicite, les modifications du DELETE sont annulees.

### SAVEPOINTS pour controle granulaire

Pour les scenarios complexes necessitant un rollback partiel :

```sql
BEGIN;
DELETE FROM documents WHERE id = 123;
SAVEPOINT after_delete;
INSERT INTO chunks (...) VALUES (...);  -- Si echec ici
ROLLBACK TO after_delete;               -- Annule INSERT, conserve DELETE
COMMIT;
```

---

## Configuration postgres.js

```typescript
import postgres from 'postgres';

const sql = postgres(DATABASE_URL, {
  max: 20,          // Pool size pour concurrence (15-20 recommande)
  idle_timeout: 20  // Fermeture connexions inactives
});

// Transaction avec isolation level explicite
await sql.begin('ISOLATION LEVEL READ COMMITTED', async sql => {
  // Upsert document avec ON CONFLICT
  const [doc] = await sql`
    INSERT INTO documents (id, source, title, content, tags, version, updated_at)
    VALUES (gen_random_uuid(), ${source}, ${title}, ${content}, ${tags}, ${version}, NOW())
    ON CONFLICT (source) DO UPDATE
    SET title = EXCLUDED.title,
        content = EXCLUDED.content,
        tags = EXCLUDED.tags,
        version = EXCLUDED.version,
        updated_at = NOW()
    RETURNING id
  `;

  // Delete old chunks
  await sql`DELETE FROM chunks WHERE document_id = ${doc.id}`;

  // Batch insert new chunks (plus performant que boucle)
  await sql`
    INSERT INTO chunks ${sql(chunks.map((c, i) => ({
      document_id: doc.id,
      chunk_index: i,
      content: c.content,
      embedding: c.embedding,
      metadata: c.metadata
    })))}
  `;

  return doc;
});
// COMMIT automatique, ROLLBACK sur erreur
```

---

## Comportement pgvector en transaction

**Visibilite des embeddings:** Les embeddings non-commites sont **jamais visibles** aux autres transactions. PostgreSQL verifie la visibilite au niveau du heap apres le scan d'index.

**Impact HNSW:** Les modifications de structure du graphe peuvent affecter les chemins de traversee, mais cela est acceptable etant donne la nature approximative de la recherche vectorielle.

| Aspect | HNSW | IVFFlat |
|--------|------|---------|
| Modifications in-place | Oui (liens voisins) | Non |
| Impact REPEATABLE READ | Potentiel (chemins) | Minimal |
| Recommandation | READ COMMITTED OK | READ COMMITTED OK |

---

## Retry pattern (si REPEATABLE READ/SERIALIZABLE utilise)

```typescript
async function withRetry<T>(fn: () => Promise<T>, maxRetries = 3): Promise<T> {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await fn();
    } catch (error: unknown) {
      const pgError = error as { code?: string };
      if (pgError.code === '40001' && i < maxRetries - 1) {
        logger.warn({ attempt: i + 1 }, 'Serialization failure, retrying...');
        continue;
      }
      throw error;
    }
  }
  throw new Error('Max retries exceeded');
}
```

**Note:** Ce pattern n'est pas necessaire avec READ COMMITTED pour notre use case.
