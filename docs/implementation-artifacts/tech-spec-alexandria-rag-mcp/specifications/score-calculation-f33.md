# Score Calculation (F33)

## Opérateur pgvector: distance vs similarité

L'opérateur `<=>` avec `vector_cosine_ops` retourne une **distance cosinus**, pas une similarité.

**Formule:** `distance = 1 - cos(θ)`

| Angle θ | cos(θ) | Distance | Interprétation |
|---------|--------|----------|----------------|
| 0° | 1 | **0** | Vecteurs identiques |
| 90° | 0 | **1** | Vecteurs orthogonaux |
| 180° | -1 | **2** | Vecteurs opposés |

**Range théorique:** [0, 2] (pas [0, 1] comme souvent supposé)

**Conversion obligatoire:**

```typescript
// La formule de conversion est: score = 1 - distance
// Produit une similarité dans le range théorique [-1, 1]
// En pratique avec embeddings sémantiques: [0, 1]
const similarity = 1 - distance;
```

## Implémentation de la recherche

```typescript
// Query avec conversion distance → similarité
const results = await sql`
  SELECT
    c.*,
    1 - (c.embedding <=> ${queryEmbedding}::vector) AS score
  FROM chunks c
  JOIN documents d ON c.document_id = d.id
  WHERE 1 - (c.embedding <=> ${queryEmbedding}::vector) >= ${threshold}
  ORDER BY c.embedding <=> ${queryEmbedding}::vector ASC
  LIMIT ${limit}
`;
```

**Note:** Le `WHERE` filtre par similarité, le `ORDER BY` trie par distance (ASC = plus proche en premier).

## Distribution des scores E5

**⚠️ Caractéristique critique du modèle `multilingual-e5-small`:**

E5 utilise une température de **0.01** pour la loss InfoNCE (contre 0.05 pour sentence-transformers standard). Cela compresse les scores dans une plage étroite où même des textes non-reliés obtiennent des scores élevés.

Citation des auteurs: *"For text embedding tasks, what matters is the relative order of the scores instead of the absolute values."*

| Type de comparaison | Score typique E5 |
|---------------------|------------------|
| Textes totalement non-reliés | **0.75 - 0.85** |
| Textes modérément pertinents | **0.85 - 0.90** |
| Textes très pertinents | **0.90 - 1.0** |

**Conséquence:** Un threshold de 0.5 n'éliminera pratiquement **aucun résultat**.

## Thresholds recommandés pour E5

| Threshold | Usage | Description |
|-----------|-------|-------------|
| 0.80 | Rappel élevé | Seuil minimum, plus de résultats |
| **0.82** | **Défaut recommandé** | **Compromis équilibré précision/rappel** |
| 0.85 | Précision élevée | Moins de résultats, plus pertinents |
| 0.87-0.90 | Strict | Haute précision uniquement |

## Optimisation: Inner Product (vecteurs normalisés)

Lorsque les embeddings sont **normalisés L2** (norme = 1), le produit scalaire devient équivalent à la similarité cosinus. L'opérateur `<#>` (negative inner product) est alors **~3× plus rapide**:

```sql
-- Si embeddings normalisés, utiliser inner product
CREATE INDEX ON chunks USING hnsw (embedding vector_ip_ops);

-- Requête optimisée (note: <#> retourne le négatif)
SELECT *, -(embedding <#> query_embedding) AS similarity
FROM chunks
ORDER BY embedding <#> query_embedding ASC
LIMIT 10;
```

**Prérequis:** Les embeddings doivent être normalisés avec `normalize: true` dans transformers.js (voir F17).

## Code de référence complet

```typescript
interface SearchResult {
  chunk_id: string;
  document_id: string;
  content: string;
  score: number;  // Similarité [0, 1]
  metadata: JsonValue;
}

async function search(
  queryEmbedding: number[],
  threshold: number = 0.82,
  limit: number = 10
): Promise<SearchResult[]> {
  const results = await sql`
    SELECT
      c.id as chunk_id,
      c.document_id,
      c.content,
      c.metadata,
      1 - (c.embedding <=> ${JSON.stringify(queryEmbedding)}::vector) AS score
    FROM chunks c
    WHERE 1 - (c.embedding <=> ${JSON.stringify(queryEmbedding)}::vector) >= ${threshold}
    ORDER BY c.embedding <=> ${JSON.stringify(queryEmbedding)}::vector ASC
    LIMIT ${limit}
  `;

  return results;
}
```
