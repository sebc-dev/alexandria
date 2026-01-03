# Indexation HNSW pgvector pour RAG : guide technique complet

Pour une application RAG avec **1000-5000 vecteurs de dimension 1024** sur pgvector 0.8.1, la configuration optimale privilégie **l'opérateur inner product** (`<#>`) avec un index HNSW paramétré à `m=24-32` et `ef_construction=100-128`. À cette échelle modeste, un **scan séquentiel sans index** reste compétitif (~36-50ms) avec un recall parfait de 100% — l'index HNSW n'apporte un gain significatif qu'au-delà de 10 000 vecteurs ou si la latence sous 10ms est critique.

---

## HNSW vs IVFFlat : l'index HNSW domine à petite échelle

La documentation officielle pgvector établit clairement que HNSW surpasse IVFFlat en termes de compromis vitesse/recall, particulièrement pour les datasets de petite taille. Les benchmarks AWS montrent des performances respectives de **~1.5ms** (HNSW) contre **~2.4ms** (IVFFlat) sur 58 000 vecteurs, avec un throughput **15.5× supérieur** pour HNSW (40.5 QPS vs 2.6 QPS).

| Métrique | HNSW | IVFFlat | Scan séquentiel |
|----------|------|---------|-----------------|
| Latence requête | ~1.5ms | ~2.4ms | ~36-50ms |
| Recall (défaut) | ~98% | ~95% | **100%** |
| Temps de build (1M vecteurs) | 4065s | 128s | N/A |
| Taille index (1M, 50D) | 729 MB | 257 MB | 0 |
| Fonctionne sur table vide | ✅ Oui | ❌ Non | N/A |

**IVFFlat est déconseillé à votre échelle** : avec `lists = rows/1000`, vous n'obtiendriez que 1-5 clusters, rendant l'index quasi-inutile. Le clustering k-means nécessite un volume de données substantiel pour être efficace.

Le seuil critique se situe autour de **10 000 vecteurs** : en-dessous, le scan séquentiel offre un recall parfait avec une latence acceptable ; au-delà de **50 000 vecteurs**, HNSW devient incontournable. Pour 1000-5000 vecteurs en 1024D, l'overhead mémoire estimé de l'index HNSW est de **~40 MB** (formula : `rows × dimensions × 4 bytes × 2`).

---

## Paramètres HNSW optimaux pour vecteurs haute dimension

Les valeurs par défaut de pgvector (`m=16`, `ef_construction=64`, `ef_search=40`) sont calibrées pour des cas génériques. Pour des vecteurs de **1024 dimensions**, le paper HNSW original (Malkov & Yashunin) recommande des valeurs de `m` plus élevées : "Higher M work better on datasets with high intrinsic dimensionality."

### Recommandations spécifiques pour 1024D

| Paramètre | Défaut | Recommandé | Impact |
|-----------|--------|------------|--------|
| `m` | 16 | **24-32** | Plus de connexions = meilleur recall en haute dimension |
| `ef_construction` | 64 | **100-128** | Doit être ≥2× m pour un graphe de qualité |
| `ef_search` | 40 | **100-200** | Ajustable runtime selon recall cible |

Le paramètre `m` contrôle le nombre maximum de connexions par nœud dans le graphe. Pour les embeddings haute dimension comme ceux de Qwen3, la plage **24-48** est recommandée par Google Cloud et Neon. L'impact mémoire reste négligeable à votre échelle : environ **10 bytes supplémentaires par connexion** par élément.

```sql
-- Configuration optimale pour 1024D / embeddings normalisés
CREATE INDEX idx_docs_embedding ON documents 
USING hnsw (embedding vector_ip_ops)  -- inner product pour vecteurs normalisés
WITH (m = 32, ef_construction = 128);

-- Ajustement runtime pour différents niveaux de recall
SET hnsw.ef_search = 100;   -- 95%+ recall
SET hnsw.ef_search = 200;   -- 99%+ recall
```

La règle `ef_construction ≥ 2 × m` assure une construction de graphe robuste. Pour `ef_search`, la valeur doit toujours être supérieure ou égale au `LIMIT` de vos requêtes.

---

## Stratégie de création et maintenance de l'index

La documentation officielle pgvector est explicite : **créez l'index APRÈS le bulk insert initial**. Les insertions avec un index HNSW existant sont environ **5× plus lentes** qu'en son absence. Cette différence devient significative lors du chargement initial de données.

### Workflow optimal de chargement

```sql
-- 1. Créer la table sans index
CREATE TABLE documents (
    id bigserial PRIMARY KEY,
    content text,
    source text NOT NULL,
    tags text[],
    embedding vector(1024)
);

-- 2. Bulk insert des données
COPY documents (content, source, tags, embedding) FROM STDIN;

-- 3. Configurer la mémoire pour la construction d'index
SET maintenance_work_mem = '1GB';  -- Suffisant pour 5000 vecteurs
SET max_parallel_maintenance_workers = 4;  -- pgvector 0.6.0+

-- 4. Créer l'index APRÈS le chargement
CREATE INDEX idx_embedding ON documents 
USING hnsw (embedding vector_ip_ops) 
WITH (m = 32, ef_construction = 128);

-- 5. Mettre à jour les statistiques
ANALYZE documents;
```

Pour la maintenance continue, pgvector recommande de **REINDEX avant VACUUM** car le vacuuming des index HNSW peut être lent. La qualité de l'index HNSW peut se dégrader après de nombreuses suppressions en raison des "trous" laissés dans le graphe.

```sql
-- Maintenance périodique (hebdomadaire ou après opérations batch)
REINDEX INDEX CONCURRENTLY idx_embedding;
VACUUM ANALYZE documents;
```

Le paramètre `maintenance_work_mem` est critique : pgvector affiche un avertissement lorsque le graphe dépasse cette limite. Pour 5000 vecteurs en 1024D, **512MB-1GB** est largement suffisant.

---

## Filtrage metadata : pgvector utilise le post-filtering

Un point technique crucial : pgvector applique les filtres `WHERE` **après** le scan de l'index HNSW, pas avant. Le processus est : (1) l'index retourne les `ef_search` voisins les plus proches, (2) PostgreSQL filtre ces résultats selon les critères WHERE.

### Problème avec filtres très sélectifs

Si seulement 10% de vos données correspondent au filtre et `ef_search=40`, vous n'obtiendrez en moyenne que ~4 résultats au lieu des 10 demandés. La solution depuis pgvector 0.8.0 est le **scan itératif** :

```sql
-- Activer le scan itératif pour filtres sélectifs
SET hnsw.iterative_scan = 'relaxed_order';  -- ou 'strict_order'
SET hnsw.ef_search = 100;
SET hnsw.max_scan_tuples = 20000;  -- limite de tuples à scanner

SELECT id, content, embedding <#> $1 AS distance
FROM documents
WHERE source = $2 AND tags && $3
ORDER BY embedding <#> $1
LIMIT 10;
```

### Index partiels : supportés mais limités

pgvector **supporte les index partiels** sur colonnes vectorielles, utiles si une valeur de filtre domine vos requêtes :

```sql
-- Index partiel pour source spécifique (ex: 80% des requêtes)
CREATE INDEX idx_embedding_web ON documents 
USING hnsw (embedding vector_ip_ops) 
WHERE (source = 'web');
```

Les **index composites vector+metadata ne sont PAS supportés**. La stratégie recommandée combine un index HNSW sur le vecteur avec des index B-tree/GIN séparés sur les colonnes de filtrage :

```sql
CREATE INDEX idx_source ON documents (source);
CREATE INDEX idx_tags ON documents USING gin (tags);
```

---

## Opérateurs de distance : inner product pour embeddings normalisés

Pour des embeddings normalisés (norme L2 = 1), les trois opérateurs principaux produisent des **classements équivalents** grâce aux relations mathématiques suivantes :

| Relation | Formule (vecteurs normalisés) |
|----------|-------------------------------|
| Inner product = Cosine similarity | A·B = cos(θ) |
| L2² = 2(1 - cosine similarity) | ‖A-B‖² = 2(1 - A·B) |

La documentation officielle pgvector recommande explicitement : **"If vectors are normalized to length 1, use inner product for best performance."** L'opérateur `<#>` (inner product négatif) est **3-5× plus rapide** que `<=>` (cosine) car il évite le calcul des normes.

```sql
-- IMPORTANT: <#> retourne le négatif du inner product
-- Pour le score de similarité réel :
SELECT id, (embedding <#> query_vec) * -1 AS similarity
FROM documents
ORDER BY embedding <#> query_vec  -- ASC = plus similaire en premier
LIMIT 10;

-- Vérifier que vos embeddings sont bien normalisés
SELECT id, vector_norm(embedding) AS norm 
FROM documents LIMIT 5;  -- Doit retourner ~1.0
```

**Point critique** : l'opérateur utilisé dans la requête **doit correspondre** à la classe d'opérateur de l'index :

| Opérateur | Index Class | Usage |
|-----------|-------------|-------|
| `<#>` | `vector_ip_ops` | **Recommandé** pour normalisés |
| `<=>` | `vector_cosine_ops` | Vecteurs non-normalisés |
| `<->` | `vector_l2_ops` | Distance euclidienne |

---

## Configuration PostgreSQL recommandée

```sql
-- Paramètres mémoire
SET shared_buffers = '1GB';           -- 25% RAM disponible
SET effective_cache_size = '3GB';     -- 50-75% RAM
SET maintenance_work_mem = '1GB';     -- Pour création d'index
SET work_mem = '64MB';                -- Par opération

-- Parallélisme (pgvector 0.6.0+)
SET max_parallel_maintenance_workers = 4;
SET max_parallel_workers = 8;

-- Paramètres pgvector runtime
SET hnsw.ef_search = 100;             -- Recall ~95%
SET hnsw.iterative_scan = 'relaxed_order';  -- Pour filtres
```

---

## Conclusion : recommandations concrètes

Pour votre cas spécifique (RAG mono-utilisateur, 1000-5000 vecteurs 1024D, embeddings Qwen3 normalisés), la configuration optimale combine pragmatisme et performance.

**Option 1 — Sans index (recommandée pour démarrer)** : À cette échelle, le scan séquentiel offre ~36-50ms de latence avec un **recall parfait de 100%** et zéro maintenance. C'est la solution la plus simple si moins de 50-100 requêtes/seconde.

**Option 2 — HNSW optimisé (si latence critique)** :
```sql
CREATE INDEX ON documents USING hnsw (embedding vector_ip_ops) 
WITH (m = 32, ef_construction = 128);
SET hnsw.ef_search = 100;
```

L'utilisation de `vector_ip_ops` avec l'opérateur `<#>` maximise les performances pour vos embeddings normalisés. L'index partiel n'est justifié que si un filtre `source` ou `tags` concentre plus de 70% de vos requêtes. Pour le filtrage metadata général, activez `hnsw.iterative_scan` et maintenez des index B-tree/GIN séparés sur les colonnes de filtrage.