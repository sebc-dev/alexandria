# Chunk Input Schema (F3)

Le fichier de chunks JSON doit suivre ce schema:

```typescript
// Schema Zod pour validation
const ChunkInputSchema = z.object({
  chunks: z.array(z.object({
    content: z.string().min(1).max(2000),  // F24: Hard limit - au-delà = troncature silencieuse par E5
    chunk_index: z.number().int().min(0),
    metadata: z.object({
      heading: z.string().optional(),
      source_lines: z.tuple([z.number(), z.number()]).optional(),
    }).optional(),
  })).min(1).max(500),  // F24: Max 500 chunks par document
});
```

**⚠️ Contrainte critique du modèle E5:**

Le modèle multilingual-e5-small a une limite stricte de **512 tokens**. Tout contenu au-delà est **silencieusement tronqué** — l'embedding ne représente alors qu'une fraction du chunk stocké, créant une dissonance entre ce qui est cherché et ce qui est retourné.

**Ratios caractères/tokens (XLMRobertaTokenizer):**

| Type de contenu | Ratio chars/token | Max chars pour 512 tokens |
|-----------------|-------------------|---------------------------|
| Anglais technique | 4.0 – 4.75 | 2,048 – 2,432 |
| Français technique | 4.6 – 4.7 | 2,355 – 2,406 |
| JavaScript/TypeScript | 3.0 – 3.5 | 1,536 – 1,792 |
| **Markdown mixte (EN/FR + code)** | **3.0 – 4.0** | **1,536 – 2,048** |

**Limites de taille (F24) - RÉVISÉES:**

| Limite | Valeur | Rationale |
|--------|--------|-----------|
| **Chunk target** | 1,600-1,800 chars | Reste sous 512 tokens avec marge |
| **Hard limit** | 2,000 chars | Ne jamais dépasser |
| **Warning threshold** | 1,500 chars | Alerte avant zone critique |
| **Chunk overlap** | 150-200 chars (10-12%) | Cohérence contextuelle aux frontières |
| Chunks par document | 500 max | Éviter OOM et timeouts |

**Comportement dépassement:**
- Content > 2,000 chars → Erreur CONTENT_TOO_LARGE (-31008)
- Content > 1,500 chars → Log warning (approche limite tokens)
- Préfixe `passage: ` consomme ~2-3 tokens sur le budget

**Séparateurs Markdown recommandés (ordre de priorité):**
```typescript
const MARKDOWN_SEPARATORS = ["\n## ", "\n### ", "\n```", "\n\n", "\n", " "];
```

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
