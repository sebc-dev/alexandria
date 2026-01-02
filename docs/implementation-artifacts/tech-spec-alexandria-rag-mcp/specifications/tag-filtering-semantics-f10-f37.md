# Tag Filtering Semantics (F10, F37)

| Comportement | Spécification |
|--------------|---------------|
| Logic | **OR** - document matche si au moins un tag correspond |
| Case | **Case-insensitive** - "TypeScript" == "typescript" |
| Match | **Exact match** après normalisation lowercase |
| Stockage (F37) | **Original case préservé** - stocké tel que fourni |
| Comparaison (F37) | `lower(tag) = lower(search_tag)` en SQL |

**Implémentation SQL:**
```sql
-- Stockage: tags tel quel ['TypeScript', 'API']
-- Recherche case-insensitive:
SELECT * FROM documents
WHERE EXISTS (
  SELECT 1 FROM unnest(tags) AS t
  WHERE lower(t) = ANY(SELECT lower(unnest($1::text[])))
);
```

**Affichage:** Les tags retournés gardent leur casse originale.
