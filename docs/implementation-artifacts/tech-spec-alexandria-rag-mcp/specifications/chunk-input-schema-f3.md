# Chunk Input Schema (F3)

Le fichier de chunks JSON doit suivre ce schema:

```typescript
// Schema Zod pour validation
const ChunkInputSchema = z.object({
  chunks: z.array(z.object({
    content: z.string().min(1).max(8000),  // F24: Max ~2000 tokens
    chunk_index: z.number().int().min(0),
    metadata: z.object({
      heading: z.string().optional(),
      source_lines: z.tuple([z.number(), z.number()]).optional(),
    }).optional(),
  })).min(1).max(500),  // F24: Max 500 chunks par document
});
```

**Limites de taille (F24):**

| Limite | Valeur | Rationale |
|--------|--------|-----------|
| Chunk content max | 8000 chars | ~2000 tokens, safe pour E5 (512 token limit avec marge) |
| Chunks par document | 500 max | Éviter OOM et timeouts |
| Warning threshold | 4000 chars | Log warning si chunk > 4000 chars |

**Comportement dépassement:**
- Content > 8000 chars → Erreur VALIDATION_ERROR avec message explicite
- Si chunk proche de la limite, log warning mais accepter

**Exemple de fichier chunks.json:**

```json
{
  "chunks": [
    {
      "content": "## Installation\n\nRun `bun install` to install dependencies.",
      "chunk_index": 0,
      "metadata": {
        "heading": "Installation",
        "source_lines": [1, 5]
      }
    }
  ]
}
```
