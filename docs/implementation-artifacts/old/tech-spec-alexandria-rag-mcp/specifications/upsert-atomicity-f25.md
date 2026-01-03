# Upsert Atomicity (F25)

**Pattern recommande:** `ON CONFLICT DO UPDATE` pour le document parent + `DELETE + INSERT` pour les chunks, le tout dans une transaction unique avec advisory lock.

---

## Comparaison ON CONFLICT vs DELETE/INSERT

| Critere | ON CONFLICT DO UPDATE | DELETE + INSERT |
|---------|----------------------|-----------------|
| Atomicite | Statement unique, atomique | Requiert transaction explicite |
| Performance | Plus rapide (un seul scan d'index) | Deux operations, plus d'I/O |
| Identite ligne | Meme CTID, triggers UPDATE | Nouveau CTID, triggers DELETE + INSERT |
| Concurrence | Garantit INSERT ou UPDATE | Sur en transaction, FK a gerer |
| Cas d'usage | Mise a jour de colonnes | Remplacement complet, CASCADE |

**Choix:** Combiner les deux - ON CONFLICT pour le parent (efficace), DELETE+INSERT pour les chunks (remplacement complet).

---

## Implementation recommandee

```typescript
import postgres from 'postgres';

const sql = postgres(DATABASE_URL, { max: 20 });

async function upsertDocument(
  source: string,
  content: string,
  title: string | null,
  tags: string[],
  version: string | null,
  chunks: ChunkInput[]
): Promise<Document> {
  return sql.begin('ISOLATION LEVEL READ COMMITTED', async sql => {
    // 1. Advisory lock pour eviter race condition (F26)
    const [{ acquired }] = await sql`
      SELECT pg_try_advisory_xact_lock(hashtext(${source})::bigint) as acquired
    `;

    if (!acquired) {
      throw new ConcurrentModificationError(
        `Document ${source} en cours de traitement`
      );
    }

    // 2. Upsert document parent avec ON CONFLICT (efficace)
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

    // 3. Supprimer anciens chunks (CASCADE implicite via FK optionnel)
    await sql`DELETE FROM chunks WHERE document_id = ${doc.id}`;

    // 4. Batch insert nouveaux chunks (plus performant que boucle)
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
  // COMMIT automatique, ROLLBACK sur erreur
}
```

---

## Garantie de rollback complet

**Principe fondamental:** Une transaction PostgreSQL est toujours atomique, meme avec READ COMMITTED.

| Etape | Si echec... |
|-------|-------------|
| Advisory lock | Pas de modification, erreur renvoyee |
| INSERT/UPDATE document | Rollback complet, rien ne change |
| DELETE chunks | Rollback complet, anciens chunks restaures |
| INSERT chunks | Rollback complet, document ET anciens chunks restaures |

Le DELETE "reussi" dans une transaction non-commitee n'est visible que par cette transaction. En cas d'echec, tout est annule.

---

## Batch insert vs boucle

**Preferer batch insert:**

```typescript
// Performant - un seul round-trip
await sql`
  INSERT INTO chunks ${sql(chunks.map((c, i) => ({
    document_id: docId,
    chunk_index: i,
    content: c.content,
    embedding: c.embedding
  })))}
`;

// A eviter - N round-trips
for (const chunk of chunks) {
  await sql`INSERT INTO chunks ...`;  // Lent!
}
```

---

## Comportement upsert=false

Quand upsert est desactive, le document ne doit pas exister :

```typescript
async function insertDocument(
  source: string,
  content: string,
  chunks: ChunkInput[],
  upsert: boolean
): Promise<Document> {
  return sql.begin('ISOLATION LEVEL READ COMMITTED', async sql => {
    // Advisory lock
    const [{ acquired }] = await sql`
      SELECT pg_try_advisory_xact_lock(hashtext(${source})::bigint) as acquired
    `;

    if (!acquired) {
      throw new ConcurrentModificationError(`Document ${source} en cours de traitement`);
    }

    if (!upsert) {
      // Verifier que le document n'existe pas
      const [existing] = await sql`
        SELECT id FROM documents WHERE source = ${source}
      `;
      if (existing) {
        throw new DuplicateSourceError(`Document ${source} existe deja`);
      }

      // Insert simple (pas de ON CONFLICT)
      const [doc] = await sql`
        INSERT INTO documents (source, content, title, tags, version)
        VALUES (${source}, ${content}, ${title}, ${tags}, ${version})
        RETURNING *
      `;

      // Insert chunks...
      return doc;
    }

    // Sinon upsert standard...
  });
}
```

---

## Tests recommandes

1. **Rollback apres echec INSERT chunks**
   - Verifier que l'ancien document est preserve

2. **Batch insert limite**
   - Tester avec 500 chunks (limite max)

3. **Concurrence**
   - Deux clients upsert meme source simultanement
   - Verifier qu'un seul reussit, l'autre recoit CONCURRENT_MODIFICATION
