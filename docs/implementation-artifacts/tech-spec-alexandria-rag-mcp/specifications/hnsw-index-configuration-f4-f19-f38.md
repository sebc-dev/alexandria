# HNSW Index Configuration (F4, F19, F38)

**Paramètres de l'index:**

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| `m` | 16 | Connexions par layer (build-time) |
| `ef_construction` | 64 | Qualité de construction |
| `ef_search` | 40 | Qualité de recherche (query-time) |

**Configuration ef_search (F38):**

```sql
-- UNIQUEMENT dans la migration initiale (pas dans le code applicatif)
ALTER DATABASE alexandria SET hnsw.ef_search = 40;
```

**Clarification F38:** On utilise **UNIQUEMENT** `ALTER DATABASE`, pas de `SET` per-session dans le code. Cela évite la duplication et garantit la cohérence.

**Rationale ef_search=40:**
- Défaut pgvector: 40
- Bon compromis recall/latency pour <10K vecteurs
- Peut être augmenté à 100+ pour meilleur recall si needed
