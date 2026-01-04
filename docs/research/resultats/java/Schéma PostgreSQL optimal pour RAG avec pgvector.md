# Schéma PostgreSQL optimal pour RAG avec pgvector

Pour Project Alexandria, la configuration optimale combine une **architecture normalisée documents/chunks** avec des colonnes dédiées pour les filtres fréquents et JSONB pour les métadonnées variables. L'utilisation de `halfvec(1024)` réduit le stockage de 50% avec une perte de précision négligeable, et un index HNSW avec `m=16, ef_construction=128` offre le meilleur compromis rappel/performance pour **50K vecteurs**.

La décision la plus importante : **Langchain4j PgVectorEmbeddingStore** utilise une table plate simpliste qui ne répond pas aux besoins d'un RAG production. Ce rapport recommande un schéma personnalisé qui étend ces capacités tout en restant compatible avec l'écosystème Langchain4j.

---

## Architecture normalisée vs table plate : le choix de la normalisation

Le consensus de l'industrie favorise fortement l'approche **normalisée à deux tables** (documents + chunks) plutôt qu'une table plate unique. Cette architecture offre des avantages décisifs pour la maintenabilité et la gestion des mises à jour.

**Avantages de la normalisation pour Project Alexandria :**
- Les suppressions en cascade (`ON DELETE CASCADE`) éliminent automatiquement les chunks orphelins lors de la ré-ingestion
- L'indexation séparée permet d'optimiser HNSW sur les vecteurs et GIN/B-tree sur les métadonnées sans compromis
- Le versioning de documents devient trivial avec une colonne `content_hash` pour détecter les changements
- Les jointures sont négligeables en performance pour **50K chunks** sur 24GB RAM

**Quand utiliser une table plate :** Uniquement pour des prototypes rapides ou des systèmes où les documents ne sont jamais modifiés. Langchain4j utilise cette approche par défaut avec une colonne `metadata JSON`, ce qui explique ses limitations pour les cas d'usage production.

Le schéma Langchain4j par défaut crée : `embedding_id UUID`, `embedding vector(N)`, `text TEXT`, `metadata JSON` — suffisant pour démarrer, mais insuffisant pour gérer efficacement les mises à jour de documents multi-chunks.

---

## Schéma SQL complet recommandé

```sql
-- Extensions requises
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Table des documents sources
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename        TEXT UNIQUE NOT NULL,           -- Identifiant unique du fichier source
    title           TEXT,                           -- Titre extrait ou nom du document
    source_type     TEXT DEFAULT 'file',            -- 'file', 'url', 'api'
    content_hash    TEXT NOT NULL,                  -- SHA-256 pour détection de changements
    total_chunks    INTEGER,                        -- Nombre total de chunks (calculé à l'ingestion)
    meta            JSONB DEFAULT '{}',             -- Métadonnées variables (auteur, tags, etc.)
    file_modified   TIMESTAMPTZ,                    -- Date de modification du fichier source
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table des chunks avec embeddings
CREATE TABLE document_chunks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index         INTEGER NOT NULL,           -- Position dans le document (0, 1, 2...)
    content             TEXT NOT NULL,              -- Texte du chunk
    token_count         INTEGER,                    -- Nombre approximatif de tokens
    embedding           halfvec(1024),              -- BGE-M3 embeddings en demi-précision
    
    -- Contexte hiérarchique (breadcrumb headers)
    breadcrumb          JSONB,                      -- {"h1": "...", "h2": "...", "h3": "..."}
    section_path        TEXT,                       -- "Introduction > Contexte > Objectifs"
    
    -- Recherche full-text
    fts                 TSVECTOR GENERATED ALWAYS AS (to_tsvector('french', content)) STORED,
    
    created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, chunk_index)
);

-- Trigger pour updated_at automatique
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_documents_modtime
    BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();
```

**Justification des colonnes :**

| Colonne | Justification |
|---------|---------------|
| `content_hash` | Détecte si un document a changé sans comparer le contenu complet — critique pour les mises à jour efficaces |
| `chunk_index` + `UNIQUE` constraint | Garantit l'ordre de reconstruction et empêche les doublons lors de ré-ingestions partielles |
| `breadcrumb JSONB` | Stocke la hiérarchie h1/h2/h3 de manière flexible ; permet de filtrer par section |
| `section_path TEXT` | Version textuelle pour affichage direct dans les résultats RAG |
| `token_count` | Permet d'estimer si le contexte tient dans la fenêtre LLM avant de l'envoyer |
| `fts TSVECTOR` | Recherche hybride (sémantique + lexicale) pour améliorer le rappel |

---

## Configuration des index avec paramètres optimaux

```sql
-- 1. Index HNSW pour similarité vectorielle (cosine)
-- m=16: optimal pour 50K vecteurs (default)
-- ef_construction=128: meilleur rappel que default (64), build raisonnable
CREATE INDEX idx_chunks_embedding ON document_chunks 
USING hnsw (embedding halfvec_cosine_ops) 
WITH (m = 16, ef_construction = 128);

-- 2. Index B-tree pour navigation dans les documents
CREATE INDEX idx_chunks_document_order ON document_chunks (document_id, chunk_index);

-- 3. Index GIN pour recherche full-text
CREATE INDEX idx_chunks_fts ON document_chunks USING gin (fts);

-- 4. Index GIN pour métadonnées JSONB (utiliser jsonb_path_ops: 36x plus petit)
CREATE INDEX idx_documents_meta ON documents USING gin (meta jsonb_path_ops);

-- 5. Index B-tree pour recherche par filename (updates)
CREATE INDEX idx_documents_filename ON documents (filename);

-- 6. Index pour breadcrumb search (optionnel)
CREATE INDEX idx_chunks_breadcrumb ON document_chunks USING gin (breadcrumb jsonb_path_ops);
```

**Paramètres HNSW détaillés :**

| Paramètre | Valeur | Impact |
|-----------|--------|--------|
| `m` | 16 | Connexions par nœud. 16 est optimal pour ≤100K vecteurs. Augmenter à 32 si >200K |
| `ef_construction` | 128 | Qualité du graphe. 128 offre ~98% de rappel vs 95% avec default 64 |
| `ef_search` (runtime) | 100 | À configurer par session : `SET hnsw.ef_search = 100;` |

**Estimation stockage pour 50K chunks :**
- Vecteurs halfvec(1024) : `2 × 1024 + 8 = 2,056 bytes` × 50K = **~100 MB**
- Index HNSW : ~150-200 MB
- Texte + métadonnées : ~200-500 MB selon taille chunks
- **Total estimé : 500 MB - 1 GB** (largement dans les capacités de 24GB RAM)

---

## Stratégies de mise à jour et gestion des documents

Pour un système RAG production, la stratégie **DELETE + INSERT** est recommandée plutôt qu'UPSERT, car un document peut avoir un nombre variable de chunks après modification.

```sql
-- Procédure de mise à jour d'un document
CREATE OR REPLACE FUNCTION upsert_document(
    p_filename TEXT,
    p_title TEXT,
    p_content_hash TEXT,
    p_meta JSONB DEFAULT '{}'
) RETURNS UUID AS $$
DECLARE
    v_doc_id UUID;
    v_existing_hash TEXT;
BEGIN
    -- Vérifier si le document existe et a changé
    SELECT id, content_hash INTO v_doc_id, v_existing_hash
    FROM documents WHERE filename = p_filename;
    
    IF v_doc_id IS NOT NULL THEN
        IF v_existing_hash = p_content_hash THEN
            -- Document inchangé, retourner l'ID existant
            RETURN v_doc_id;
        ELSE
            -- Document modifié: supprimer (CASCADE supprime les chunks)
            DELETE FROM documents WHERE id = v_doc_id;
        END IF;
    END IF;
    
    -- Insérer nouveau document
    INSERT INTO documents (filename, title, content_hash, meta)
    VALUES (p_filename, p_title, p_content_hash, p_meta)
    RETURNING id INTO v_doc_id;
    
    RETURN v_doc_id;
END;
$$ LANGUAGE plpgsql;

-- Insertion batch de chunks (après upsert_document)
INSERT INTO document_chunks (document_id, chunk_index, content, token_count, embedding, breadcrumb)
VALUES 
    ($1, 0, 'Premier chunk...', 150, '[...]'::halfvec(1024), '{"h1": "Introduction"}'),
    ($1, 1, 'Deuxième chunk...', 180, '[...]'::halfvec(1024), '{"h1": "Introduction", "h2": "Contexte"}');

-- Mise à jour du total_chunks après insertion
UPDATE documents SET total_chunks = (
    SELECT COUNT(*) FROM document_chunks WHERE document_id = $1
) WHERE id = $1;
```

**Gestion des chunks orphelins :** Le `ON DELETE CASCADE` sur la foreign key élimine automatiquement tous les chunks quand le document parent est supprimé — c'est la solution la plus propre et la plus fiable.

---

## Requêtes types pour RAG

### Recherche sémantique avec contexte étendu

```sql
-- Configurer ef_search pour cette session
SET hnsw.ef_search = 100;

-- Requête principale RAG avec métadonnées
WITH ranked_chunks AS (
    SELECT 
        c.id,
        c.document_id,
        c.chunk_index,
        c.content,
        c.breadcrumb,
        c.token_count,
        d.filename,
        d.title,
        1 - (c.embedding <=> $1::halfvec(1024)) AS similarity
    FROM document_chunks c
    JOIN documents d ON c.document_id = d.id
    WHERE c.embedding IS NOT NULL
    ORDER BY c.embedding <=> $1::halfvec(1024)
    LIMIT 10
)
SELECT * FROM ranked_chunks WHERE similarity >= 0.7;
```

### Reconstruction du contexte avec chunks voisins

```sql
-- Récupérer chunk + contexte environnant (±2 chunks)
WITH target AS (
    SELECT document_id, chunk_index
    FROM document_chunks
    WHERE id = $1  -- ID du chunk trouvé
)
SELECT 
    c.chunk_index,
    c.content,
    c.breadcrumb,
    CASE WHEN c.chunk_index = t.chunk_index THEN true ELSE false END AS is_target
FROM document_chunks c
JOIN target t ON c.document_id = t.document_id
WHERE c.chunk_index BETWEEN t.chunk_index - 2 AND t.chunk_index + 2
ORDER BY c.chunk_index;
```

### Recherche hybride (sémantique + full-text)

```sql
-- Combiner similarité vectorielle et recherche textuelle
WITH semantic AS (
    SELECT id, 1 - (embedding <=> $1::halfvec(1024)) AS semantic_score
    FROM document_chunks
    ORDER BY embedding <=> $1::halfvec(1024)
    LIMIT 50
),
lexical AS (
    SELECT id, ts_rank(fts, plainto_tsquery('french', $2)) AS lexical_score
    FROM document_chunks
    WHERE fts @@ plainto_tsquery('french', $2)
)
SELECT 
    c.*,
    COALESCE(s.semantic_score, 0) * 0.7 + COALESCE(l.lexical_score, 0) * 0.3 AS hybrid_score
FROM document_chunks c
LEFT JOIN semantic s ON c.id = s.id
LEFT JOIN lexical l ON c.id = l.id
WHERE s.id IS NOT NULL OR l.id IS NOT NULL
ORDER BY hybrid_score DESC
LIMIT 10;
```

---

## Configuration PostgreSQL 18 optimisée

```ini
# === postgresql.conf pour 24GB RAM ===

# Mémoire partagée (25% RAM)
shared_buffers = 6GB

# Cache effectif (75% RAM - inclut cache OS)
effective_cache_size = 18GB

# Mémoire par opération (conservative pour vecteurs)
work_mem = 64MB

# Critique pour construction HNSW
maintenance_work_mem = 2GB

# WAL buffers
wal_buffers = 64MB

# === PostgreSQL 18: Async I/O (jusqu'à 3x plus rapide) ===
io_method = 'io_uring'              # Linux uniquement; 'worker' sinon
effective_io_concurrency = 200
maintenance_io_concurrency = 10

# === Parallélisme ===
max_parallel_workers_per_gather = 4
max_parallel_maintenance_workers = 4  # Pour builds HNSW/GIN
max_parallel_workers = 8

# === Checkpoints ===
checkpoint_completion_target = 0.9
checkpoint_timeout = 15min
max_wal_size = 4GB
min_wal_size = 1GB

# === Autovacuum agressif pour tables à fort turnover ===
autovacuum_max_workers = 4
autovacuum_naptime = 30s
autovacuum_vacuum_cost_limit = 2000
```

**Configuration autovacuum par table** (recommandée pour `document_chunks`) :

```sql
ALTER TABLE document_chunks SET (
    autovacuum_vacuum_scale_factor = 0.02,   -- Vacuum à 2% dead tuples (vs 20% default)
    autovacuum_vacuum_threshold = 500,
    autovacuum_analyze_scale_factor = 0.01,
    autovacuum_vacuum_cost_delay = 0          -- Pas de throttling
);
```

---

## Trade-offs et décisions de design expliqués

### halfvec vs vector

| Aspect | halfvec(1024) | vector(1024) |
|--------|---------------|--------------|
| Stockage | **2 KB/vecteur** | 4 KB/vecteur |
| Index HNSW | ~50% plus petit | Référence |
| Précision | IEEE 754 binary16 | IEEE 754 binary32 |
| Rappel | **~99.5% vs full precision** | 100% (référence) |
| Build time | **2-3x plus rapide** | Référence |

**Recommandation :** `halfvec(1024)` est optimal pour BGE-M3. La perte de précision est négligeable pour la recherche sémantique, et les gains en stockage/performance sont significatifs.

### JSONB metadata vs colonnes dédiées

| Critère | JSONB | Colonnes dédiées | Recommandation |
|---------|-------|------------------|----------------|
| Filtrage fréquent | Plus lent (index GIN) | Rapide (B-tree) | Colonnes pour `document_id`, `chunk_index` |
| Schéma flexible | Aucune migration | ALTER TABLE requis | JSONB pour `breadcrumb`, `meta` |
| Requêtes complexes | Syntaxe `->`, `->>` | SQL standard | Colonnes pour critères fréquents |
| Taille index | GIN plus gros | B-tree compact | `jsonb_path_ops` réduit de 36x |

**Approche hybride adoptée :** Colonnes dédiées pour les champs critiques (`document_id`, `chunk_index`, `content`), JSONB pour les métadonnées variables (`breadcrumb`, `meta`).

### chunk_index vs linked list (previous/next_chunk_id)

| Approche | Avantages | Inconvénients |
|----------|-----------|---------------|
| `chunk_index` | Simple, tri SQL natif, pas de maintenance | Insertion au milieu = renumérotation |
| Linked list | O(1) navigation, insertion flexible | Mise à jour de 2+ records par modification |

**Recommandation :** `chunk_index` suffit pour 99% des cas RAG. Les documents markdown sont recréés entièrement, pas modifiés chunk par chunk.

---

## Compatibilité avec Langchain4j

Langchain4j 1.0.1 `PgVectorEmbeddingStore` utilise un schéma simplifié. Pour utiliser le schéma personnalisé recommandé tout en gardant la compatibilité :

```java
// Option 1: Créer la table manuellement, désactiver createTable
PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
    .dataSource(dataSource)
    .table("document_chunks")          // Utiliser notre table
    .dimension(1024)
    .createTable(false)                 // Table déjà créée
    .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
    .build();

// Option 2: Wrapper custom pour requêtes avancées
// Implémenter EmbeddingStore avec accès direct au schéma optimisé
```

**Limitation Langchain4j à noter :** Le store par défaut utilise IVFFlat, pas HNSW. Pour HNSW, créer l'index manuellement avec le schéma ci-dessus.

---

## Checklist d'implémentation finale

1. **Créer les extensions** : `vector`, `pg_trgm`
2. **Créer les tables** avec le schéma recommandé
3. **Créer les index** après chargement initial des données (plus rapide)
4. **Configurer postgresql.conf** avec les paramètres recommandés
5. **Configurer autovacuum** par table pour `document_chunks`
6. **Implémenter la fonction `upsert_document`** pour les mises à jour
7. **Tester avec `EXPLAIN ANALYZE`** que les index sont utilisés
8. **Monitorer** avec `pg_stat_user_tables` et les nouvelles métriques PostgreSQL 18

Ce schéma supporte efficacement **10K-50K chunks** sur un serveur 24GB RAM, avec des requêtes de similarité en **<50ms** et une maintenance automatisée via autovacuum optimisé.