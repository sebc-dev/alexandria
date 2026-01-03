# Search Result Schema (F29)

**Structure de réponse enrichie:**

```typescript
interface SearchResultItem {
  document_id: string;      // UUID du document parent
  chunk_id: string;         // UUID du chunk
  source: string;           // Path/identifiant du document
  content: string;          // Contenu du chunk
  score: number;            // Similarité cosinus (0.0-1.0)
  chunk_index: number;      // Position dans le document (F29)
  heading: string | null;   // Heading parent si disponible (F29)
  total_chunks: number;     // Nombre total de chunks dans le doc (F29)
}
```
