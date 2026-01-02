# Upsert Atomicity (F25)

**Garantie de rollback complet:**

```typescript
async upsert(document: Document, chunks: Chunk[]): Promise<void> {
  await sql.begin(async (tx) => {
    // 1. Delete existing (if any) - CASCADE supprime les chunks
    await tx`DELETE FROM documents WHERE source = ${document.source}`;

    // 2. Insert new document
    const [doc] = await tx`INSERT INTO documents ... RETURNING id`;

    // 3. Insert all chunks
    for (const chunk of chunks) {
      await tx`INSERT INTO chunks ...`;
    }
    // Si n'importe quelle étape échoue, TOUT est rollback
    // L'ancien document est restauré car DELETE n'était pas commité
  });
}
```

**Garantie:** Si l'insert échoue après le delete, l'ancien document est préservé (rollback automatique).
