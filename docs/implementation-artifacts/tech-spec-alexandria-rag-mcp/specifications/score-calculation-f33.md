# Score Calculation (F33)

**pgvector distance vs similarity:**

pgvector avec `vector_cosine_ops` retourne une **distance** (0 = identique, 2 = opposé), pas une similarité.

| Concept | Formule | Range |
|---------|---------|-------|
| Distance cosinus (pgvector) | `1 - cos(θ)` | 0.0 - 2.0 |
| Similarité cosinus (retournée) | `1 - distance` | -1.0 - 1.0 |

**Conversion obligatoire:**
```typescript
// Query pgvector
const results = await sql`
  SELECT *, embedding <=> ${queryEmbedding}::vector AS distance
  FROM chunks
  ORDER BY distance ASC
  LIMIT ${limit}
`;

// Conversion en similarité
const withScores = results.map(r => ({
  ...r,
  score: 1 - r.distance,  // distance 0 → score 1.0
}));
```

**Filtrage par threshold:**
```typescript
// Threshold 0.5 = distance max 0.5
const filtered = withScores.filter(r => r.score >= threshold);
```
