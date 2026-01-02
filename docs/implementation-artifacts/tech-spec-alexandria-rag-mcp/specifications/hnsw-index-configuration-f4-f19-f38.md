# HNSW Index Configuration (F4, F19, F38)

**Paramètres de l'index (validés):**

| Paramètre | Valeur | Défaut pgvector | Statut |
|-----------|--------|-----------------|--------|
| `m` | 16 | 16 | ✅ Optimal pour <10K vecteurs |
| `ef_construction` | 100 | 64 | ✅ Production RAG (meilleur recall) |
| `ef_search` | 100 | 40 | ✅ Production standard |
| `iterative_scan` | relaxed_order | strict_order | ✅ Important pour filtrage par tags |

**Configuration ef_search (F38):**

```sql
-- UNIQUEMENT dans la migration initiale (pas dans le code applicatif)
ALTER DATABASE alexandria SET hnsw.ef_search = 100;

-- Important pour les requêtes avec filtrage (tags)
ALTER DATABASE alexandria SET hnsw.iterative_scan = relaxed_order;

-- Optionnel: mémoire pour construction (utile si scale-up futur)
ALTER DATABASE alexandria SET maintenance_work_mem = '256MB';
```

**Clarification F38:** On utilise **UNIQUEMENT** `ALTER DATABASE`, pas de `SET` per-session dans le code. La configuration persiste après redémarrage (stockée dans `pg_db_role_setting`).

**Configuration dynamique possible:** Le paramètre `ef_search` peut être ajusté par session si nécessaire pour des requêtes spécifiques nécessitant plus de précision.

**Rationale des paramètres:**

| Paramètre | Choix | Justification |
|-----------|-------|---------------|
| m=16 | Conserver défaut | Recall 95-98%, m=32 n'apporterait que +1-2% pour +40% latence |
| ef_construction=100 | **Augmenté** | Meilleur recall pour RAG production (qualité construction) |
| ef_search=100 | **Production standard** | Recall ~92-95%, bon équilibre recall/latence |
| iterative_scan | **relaxed_order** | Récupère plus de résultats si filtrage (pgvector 0.8+) |

**Impact ef_search sur recall:**

| ef_search | Recall@10 | Latence relative | Usage |
|-----------|-----------|------------------|-------|
| 40 (défaut) | ~85-90% | Baseline | Haute throughput |
| **100** | **~92-95%** | **2.5×** | **Production standard (recommandé)** |
| 120 | ~95-97% | 3× | Haute précision |
| 200+ | ~98%+ | 5× | Précision critique |

**Note pgvector 0.8+:** Avec `iterative_scan`, la contrainte ef_search ≥ LIMIT est assouplie. ef_search = 100 offre un bon équilibre recall/latence pour la plupart des cas RAG.

**Estimations pour 10K vecteurs à 384 dimensions:**

| Métrique | Estimation |
|----------|------------|
| Taille index HNSW | ~18-25 Mo |
| Temps construction | 3-6 secondes (ef_construction=100) |
| Latence requête | <2 ms (ef_search=100) |
| Mémoire RAM | ~50 Mo (index + cache) |
| Stockage par vecteur | 1544 bytes (4 × 384 + 8) |

## Optimisation: Inner Product (vecteurs normalisés)

**⚠️ Considération future pour optimisation:**

Lorsque les embeddings sont **normalisés L2** (norme = 1), le produit scalaire devient équivalent à la similarité cosinus. L'opérateur `<#>` (negative inner product) est alors **~3× plus rapide** que `<=>` (distance cosinus).

```sql
-- Alternative optimisée SI embeddings normalisés
CREATE INDEX chunks_embedding_idx ON chunks
  USING hnsw (embedding vector_ip_ops)
  WITH (m = 16, ef_construction = 100);

-- Requête (note: <#> retourne le négatif du produit scalaire)
SELECT *, -(embedding <#> query_embedding) AS similarity
FROM chunks
ORDER BY embedding <#> query_embedding ASC
LIMIT 10;
```

**Prérequis:** Les embeddings doivent être normalisés avec `normalize: true` dans transformers.js (voir F17).

**Décision MVP:** On conserve `vector_cosine_ops` pour simplicité et clarté sémantique. L'optimisation `vector_ip_ops` peut être envisagée en évolution future si les performances deviennent un enjeu.
