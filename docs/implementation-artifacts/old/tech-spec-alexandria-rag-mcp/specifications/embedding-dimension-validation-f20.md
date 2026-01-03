# Embedding Dimension Validation (F20)

**Validation explicite:**

```typescript
const EXPECTED_DIMENSIONS = 384;

function validateEmbedding(embedding: number[]): void {
  if (embedding.length !== EXPECTED_DIMENSIONS) {
    throw new EmbeddingDimensionError(
      `Expected ${EXPECTED_DIMENSIONS} dimensions, got ${embedding.length}`
    );
  }
}
```

**Points de validation:**
1. Après génération par Transformers.js, avant stockage
2. Erreur code: -31007 EMBEDDING_DIMENSION_MISMATCH
