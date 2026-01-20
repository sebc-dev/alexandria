# PostgreSQL 17 RAG Schema: Architecture complète avec pgvector et Apache AGE

Un système RAG performant nécessite une architecture de données soigneusement conçue. Cette conception intègre **pgvector** pour la recherche sémantique, **Apache AGE** pour les relations complexes, et une stratégie de **recherche hybride** combinant BM25 et similarité vectorielle via Reciprocal Rank Fusion.

## Décisions architecturales clés

Le schema adopte **HNSW plutôt qu'IVFFlat** pour l'indexation vectorielle car HNSW offre des requêtes **15x plus rapides** (40 QPS vs 2.6 QPS) et ne nécessite pas de reconstruction après les mises à jour. Pour 10k-50k vecteurs à 384 dimensions, HNSW consomme environ **150-200 MB** de mémoire, un coût acceptable pour les gains de performance.

Les embeddings sont stockés dans une **table séparée `chunk_embeddings`** plutôt que dans la table `chunks`. Ce choix facilite le versioning des modèles d'embedding et permet des migrations sans downtime lors du changement de modèle. La hiérarchie parent/child utilise une **foreign key auto-référentielle** plutôt qu'Apache AGE, réservant le graph aux relations complexes comme les références croisées et les liens de similarité sémantique.

---

## Script SQL complet du schema

```sql
-- =============================================================================
-- SCHEMA RAG POSTGRESQL 17 - COMPLET ET COMMENTÉ
-- Extensions: pgvector, Apache AGE
-- Modèle: all-MiniLM-L6-v2 (384 dimensions)
-- Chunking: hiérarchique (parent 1000 tokens → child 200 tokens)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. EXTENSIONS ET CONFIGURATION INITIALE
-- -----------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS vector;        -- pgvector pour embeddings
CREATE EXTENSION IF NOT EXISTS age;           -- Apache AGE pour graph
CREATE EXTENSION IF NOT EXISTS pg_trgm;       -- Trigrams pour recherche floue

-- Configuration AGE (requis pour chaque connexion)
LOAD 'age';
SET search_path = ag_catalog, "$user", public;

-- -----------------------------------------------------------------------------
-- 2. TABLE PROJECTS - ISOLATION MULTI-PROJET
-- -----------------------------------------------------------------------------

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT UNIQUE NOT NULL,
    description TEXT,
    settings JSONB DEFAULT '{}',              -- Config spécifique au projet
    default_language REGCONFIG DEFAULT 'english',
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

COMMENT ON TABLE projects IS 'Isolation multi-projet pour single user';
COMMENT ON COLUMN projects.settings IS 'Configuration projet: chunk_size, overlap, etc.';

CREATE INDEX idx_projects_slug ON projects(slug);

-- -----------------------------------------------------------------------------
-- 3. TABLE EMBEDDING_MODELS - VERSIONING DES MODÈLES
-- -----------------------------------------------------------------------------
-- Critique pour permettre le changement futur de modèle d'embedding

CREATE TABLE embedding_models (
    id SERIAL PRIMARY KEY,
    model_name TEXT NOT NULL,
    model_version TEXT NOT NULL,
    dimensions INTEGER NOT NULL,
    provider TEXT NOT NULL,                   -- 'huggingface', 'openai', etc.
    is_active BOOLEAN DEFAULT false,
    metadata JSONB DEFAULT '{}',              -- max_tokens, etc.
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    
    CONSTRAINT chk_dimensions CHECK (dimensions > 0 AND dimensions <= 8192),
    CONSTRAINT uq_model_version UNIQUE (model_name, model_version)
);

COMMENT ON TABLE embedding_models IS 'Permet migration entre modèles sans perte de données';

-- Modèle initial: all-MiniLM-L6-v2
INSERT INTO embedding_models (model_name, model_version, dimensions, provider, is_active, metadata)
VALUES (
    'all-MiniLM-L6-v2', 
    '1.0', 
    384, 
    'huggingface',
    true,
    '{"max_tokens": 256, "normalized": true}'::jsonb
);

-- -----------------------------------------------------------------------------
-- 4. TABLE DOCUMENTS - FICHIERS MARKDOWN AVEC FRONTMATTER
-- -----------------------------------------------------------------------------

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    
    -- Identification fichier
    path TEXT NOT NULL,                       -- Chemin relatif unique
    filename TEXT NOT NULL,
    title TEXT,                               -- Extrait du frontmatter ou H1
    
    -- Catégorisation (extrait du frontmatter)
    category TEXT,
    tags TEXT[] DEFAULT '{}',
    
    -- Contenu et hash pour détection changements
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,           -- SHA256 hex encoded
    
    -- Frontmatter YAML stocké en JSONB
    frontmatter JSONB DEFAULT '{}',
    
    -- Langue pour full-text search
    language REGCONFIG DEFAULT 'english',
    
    -- Timestamps
    file_modified_at TIMESTAMPTZ,             -- Date modification fichier source
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    
    -- Contraintes
    CONSTRAINT uq_document_path UNIQUE (project_id, path)
);

COMMENT ON TABLE documents IS 'Documents markdown avec frontmatter YAML';
COMMENT ON COLUMN documents.content_hash IS 'SHA256 pour détecter modifications fichier';
COMMENT ON COLUMN documents.frontmatter IS 'Métadonnées YAML parsées en JSONB';

-- Indexes documents
CREATE INDEX idx_documents_project_id ON documents(project_id);
CREATE INDEX idx_documents_category ON documents(category) WHERE category IS NOT NULL;
CREATE INDEX idx_documents_tags ON documents USING GIN(tags);
CREATE INDEX idx_documents_frontmatter ON documents USING GIN(frontmatter jsonb_path_ops);
CREATE INDEX idx_documents_content_hash ON documents(content_hash);

-- Trigger auto-génération content_hash et updated_at
CREATE OR REPLACE FUNCTION update_document_hash()
RETURNS TRIGGER AS $$
BEGIN
    NEW.content_hash := encode(sha256(NEW.content::bytea), 'hex');
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_documents_hash
    BEFORE INSERT OR UPDATE OF content ON documents
    FOR EACH ROW EXECUTE FUNCTION update_document_hash();

-- -----------------------------------------------------------------------------
-- 5. TABLE CHUNKS - HIÉRARCHIE PARENT/CHILD AUTO-RÉFÉRENTIELLE
-- -----------------------------------------------------------------------------
-- Structure unique avec FK auto-référentielle pour simplicité

CREATE TYPE chunk_type_enum AS ENUM ('parent', 'child');

CREATE TABLE chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    
    -- Hiérarchie: parent_chunk_id NULL = chunk parent
    parent_chunk_id UUID REFERENCES chunks(id) ON DELETE CASCADE,
    chunk_type chunk_type_enum NOT NULL,
    
    -- Contenu
    content TEXT NOT NULL,
    
    -- Métriques
    token_count INTEGER NOT NULL,
    char_start INTEGER NOT NULL,
    char_end INTEGER NOT NULL,
    
    -- Séquence et navigation
    sequence_number INTEGER NOT NULL,         -- Ordre dans le parent/document
    heading TEXT,                             -- Titre de section si applicable
    heading_level SMALLINT,                   -- Niveau H1-H6
    
    -- Métadonnées additionnelles
    metadata JSONB DEFAULT '{}',
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    
    -- Contraintes de cohérence
    CONSTRAINT chk_token_count CHECK (token_count > 0),
    CONSTRAINT chk_char_range CHECK (char_end > char_start),
    CONSTRAINT chk_hierarchy_coherence CHECK (
        (chunk_type = 'parent' AND parent_chunk_id IS NULL)
        OR (chunk_type = 'child' AND parent_chunk_id IS NOT NULL)
    )
);

COMMENT ON TABLE chunks IS 'Chunks hiérarchiques: parents (1000 tokens) contiennent children (200 tokens)';
COMMENT ON COLUMN chunks.parent_chunk_id IS 'NULL pour parent chunks, référence parent pour children';

-- Indexes chunks (critiques pour performance)
CREATE INDEX idx_chunks_document_id ON chunks(document_id);
CREATE INDEX idx_chunks_parent_id ON chunks(parent_chunk_id) WHERE parent_chunk_id IS NOT NULL;
CREATE INDEX idx_chunks_type ON chunks(chunk_type);
CREATE INDEX idx_chunks_sequence ON chunks(document_id, sequence_number);

-- Index pour récupération parent + children en une requête
CREATE INDEX idx_chunks_hierarchy ON chunks(document_id, chunk_type, sequence_number);

-- -----------------------------------------------------------------------------
-- 6. TABLE CHUNK_EMBEDDINGS - VECTEURS SÉPARÉS POUR VERSIONING
-- -----------------------------------------------------------------------------

CREATE TABLE chunk_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id UUID NOT NULL REFERENCES chunks(id) ON DELETE CASCADE,
    embedding_model_id INTEGER NOT NULL REFERENCES embedding_models(id),
    
    -- Vecteur 384 dimensions pour all-MiniLM-L6-v2
    embedding vector(384) NOT NULL,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    
    -- Un seul embedding par chunk par modèle
    CONSTRAINT uq_chunk_model UNIQUE (chunk_id, embedding_model_id)
);

COMMENT ON TABLE chunk_embeddings IS 'Embeddings versionnés, séparés des chunks pour migration facile';

-- INDEX HNSW PRINCIPAL - Paramètres optimisés pour 10k-50k vecteurs 384d
-- m=16: connections par layer (défaut optimal pour dimensions moyennes)
-- ef_construction=100: qualité du graph (balance build time/recall)
CREATE INDEX idx_embeddings_hnsw ON chunk_embeddings 
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 100);

-- Index pour filtrage par modèle
CREATE INDEX idx_embeddings_model ON chunk_embeddings(embedding_model_id);

-- Index pour lookup chunk_id
CREATE INDEX idx_embeddings_chunk ON chunk_embeddings(chunk_id);

-- -----------------------------------------------------------------------------
-- 7. FULL-TEXT SEARCH - CONFIGURATION MULTILINGUE FR/EN
-- -----------------------------------------------------------------------------

-- Colonne tsvector générée pour recherche hybride sur chunks
ALTER TABLE chunks ADD COLUMN search_vector tsvector 
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(heading, '')), 'A') ||
        setweight(to_tsvector('simple', content), 'B')
    ) STORED;

COMMENT ON COLUMN chunks.search_vector IS 'tsvector pour hybrid search, config simple pour FR/EN mixte';

-- Index GIN pour full-text search (fastupdate=off pour lectures fréquentes)
CREATE INDEX idx_chunks_fts ON chunks USING GIN(search_vector) WITH (fastupdate = off);

-- Colonne tsvector pour documents (titre + tags)
ALTER TABLE documents ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(array_to_string(tags, ' '), '')), 'B')
    ) STORED;

CREATE INDEX idx_documents_fts ON documents USING GIN(search_vector) WITH (fastupdate = off);

-- -----------------------------------------------------------------------------
-- 8. PARTIAL INDEXES PGVECTOR PAR PROJET (OPTIONNEL)
-- -----------------------------------------------------------------------------
-- Créer après avoir des données, pour les projets les plus actifs

-- Exemple: index partiel pour un projet spécifique (décommenter après création projet)
-- CREATE INDEX idx_embeddings_proj_xxx ON chunk_embeddings 
--     USING hnsw (embedding vector_cosine_ops)
--     WHERE chunk_id IN (
--         SELECT c.id FROM chunks c 
--         JOIN documents d ON d.id = c.document_id 
--         WHERE d.project_id = 'xxx-uuid'
--     );

-- -----------------------------------------------------------------------------
-- 9. APACHE AGE - GRAPH POUR RELATIONS COMPLEXES
-- -----------------------------------------------------------------------------

-- Créer le graph RAG
SELECT * FROM ag_catalog.create_graph('rag_graph');

-- Créer les labels de noeuds (optionnel, Cypher les crée auto)
SELECT create_vlabel('rag_graph', 'Document');
SELECT create_vlabel('rag_graph', 'Chunk');
SELECT create_vlabel('rag_graph', 'Entity');      -- Entités extraites (NER)
SELECT create_vlabel('rag_graph', 'Topic');       -- Topics/concepts

-- Créer les labels d'edges
SELECT create_elabel('rag_graph', 'REFERENCES');   -- Citations entre documents
SELECT create_elabel('rag_graph', 'SIMILAR_TO');   -- Similarité sémantique
SELECT create_elabel('rag_graph', 'MENTIONS');     -- Document/chunk mentionne entité
SELECT create_elabel('rag_graph', 'ABOUT');        -- Document traite de topic

-- INDEX GRAPH (critiques pour performance AGE)
CREATE INDEX idx_graph_doc ON rag_graph."Document" USING GIN (properties);
CREATE INDEX idx_graph_chunk ON rag_graph."Chunk" USING GIN (properties);
CREATE INDEX idx_graph_similar_start ON rag_graph."SIMILAR_TO" USING BTREE (start_id);
CREATE INDEX idx_graph_similar_end ON rag_graph."SIMILAR_TO" USING BTREE (end_id);
CREATE INDEX idx_graph_ref_start ON rag_graph."REFERENCES" USING BTREE (start_id);

-- -----------------------------------------------------------------------------
-- 10. FONCTIONS UTILITAIRES
-- -----------------------------------------------------------------------------

-- Fonction: Recherche hybride avec RRF (Reciprocal Rank Fusion)
CREATE OR REPLACE FUNCTION hybrid_search(
    query_text TEXT,
    query_embedding vector(384),
    p_project_id UUID,
    match_count INT DEFAULT 10,
    fulltext_weight FLOAT DEFAULT 0.5,
    semantic_weight FLOAT DEFAULT 0.5,
    rrf_k INT DEFAULT 60
)
RETURNS TABLE (
    chunk_id UUID,
    document_id UUID,
    content TEXT,
    parent_content TEXT,
    hybrid_score FLOAT,
    fulltext_rank INT,
    semantic_rank INT
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    active_model_id INT;
BEGIN
    -- Récupérer le modèle actif
    SELECT id INTO active_model_id FROM embedding_models WHERE is_active = true LIMIT 1;
    
    RETURN QUERY
    WITH
    -- Recherche full-text avec ranking
    fulltext AS (
        SELECT 
            c.id,
            ROW_NUMBER() OVER (
                ORDER BY ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', query_text)) DESC
            )::INT AS rank
        FROM chunks c
        JOIN documents d ON d.id = c.document_id
        WHERE c.search_vector @@ websearch_to_tsquery('simple', query_text)
          AND d.project_id = p_project_id
          AND c.chunk_type = 'child'          -- Recherche sur children uniquement
        LIMIT match_count * 2
    ),
    
    -- Recherche sémantique avec ranking
    semantic AS (
        SELECT 
            ce.chunk_id AS id,
            ROW_NUMBER() OVER (ORDER BY ce.embedding <=> query_embedding)::INT AS rank
        FROM chunk_embeddings ce
        JOIN chunks c ON c.id = ce.chunk_id
        JOIN documents d ON d.id = c.document_id
        WHERE d.project_id = p_project_id
          AND ce.embedding_model_id = active_model_id
          AND c.chunk_type = 'child'
        ORDER BY ce.embedding <=> query_embedding
        LIMIT match_count * 2
    ),
    
    -- Calcul RRF: score = Σ weight / (k + rank)
    rrf_scores AS (
        SELECT 
            COALESCE(f.id, s.id) AS id,
            (COALESCE(fulltext_weight / (rrf_k + f.rank), 0.0) +
             COALESCE(semantic_weight / (rrf_k + s.rank), 0.0)) AS score,
            f.rank AS ft_rank,
            s.rank AS sem_rank
        FROM fulltext f
        FULL OUTER JOIN semantic s ON f.id = s.id
    )
    
    SELECT 
        c.id AS chunk_id,
        c.document_id,
        c.content,
        pc.content AS parent_content,
        r.score AS hybrid_score,
        r.ft_rank AS fulltext_rank,
        r.sem_rank AS semantic_rank
    FROM rrf_scores r
    JOIN chunks c ON r.id = c.id
    LEFT JOIN chunks pc ON pc.id = c.parent_chunk_id
    ORDER BY r.score DESC
    LIMIT match_count;
END;
$$;

COMMENT ON FUNCTION hybrid_search IS 'Recherche hybride RRF combinant BM25-like + cosine similarity';

-- Fonction: Recherche sémantique pure avec contexte parent
CREATE OR REPLACE FUNCTION semantic_search(
    query_embedding vector(384),
    p_project_id UUID,
    match_count INT DEFAULT 10,
    similarity_threshold FLOAT DEFAULT 0.5
)
RETURNS TABLE (
    chunk_id UUID,
    document_id UUID,
    document_title TEXT,
    content TEXT,
    parent_content TEXT,
    similarity FLOAT
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    active_model_id INT;
BEGIN
    SELECT id INTO active_model_id FROM embedding_models WHERE is_active = true LIMIT 1;
    
    RETURN QUERY
    SELECT 
        c.id AS chunk_id,
        c.document_id,
        d.title AS document_title,
        c.content,
        pc.content AS parent_content,
        1 - (ce.embedding <=> query_embedding) AS similarity
    FROM chunk_embeddings ce
    JOIN chunks c ON c.id = ce.chunk_id
    JOIN documents d ON d.id = c.document_id
    LEFT JOIN chunks pc ON pc.id = c.parent_chunk_id
    WHERE d.project_id = p_project_id
      AND ce.embedding_model_id = active_model_id
      AND c.chunk_type = 'child'
      AND 1 - (ce.embedding <=> query_embedding) > similarity_threshold
    ORDER BY ce.embedding <=> query_embedding
    LIMIT match_count;
END;
$$;

-- Fonction: Vérifier si document a changé (via content_hash)
CREATE OR REPLACE FUNCTION document_needs_update(
    p_project_id UUID,
    p_path TEXT,
    p_content_hash CHAR(64)
)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
AS $$
    SELECT NOT EXISTS (
        SELECT 1 FROM documents 
        WHERE project_id = p_project_id 
          AND path = p_path 
          AND content_hash = p_content_hash
    );
$$;

-- -----------------------------------------------------------------------------
-- 11. VUES UTILES
-- -----------------------------------------------------------------------------

-- Vue: Chunks avec leur contexte complet
CREATE VIEW v_chunks_with_context AS
SELECT 
    c.id,
    c.document_id,
    d.title AS document_title,
    d.path AS document_path,
    d.project_id,
    c.chunk_type,
    c.content,
    c.token_count,
    c.heading,
    c.sequence_number,
    pc.content AS parent_content,
    pc.heading AS parent_heading,
    d.category,
    d.tags
FROM chunks c
JOIN documents d ON d.id = c.document_id
LEFT JOIN chunks pc ON pc.id = c.parent_chunk_id;

-- Vue: Stats par projet
CREATE VIEW v_project_stats AS
SELECT 
    p.id AS project_id,
    p.name,
    COUNT(DISTINCT d.id) AS document_count,
    COUNT(DISTINCT c.id) FILTER (WHERE c.chunk_type = 'parent') AS parent_chunk_count,
    COUNT(DISTINCT c.id) FILTER (WHERE c.chunk_type = 'child') AS child_chunk_count,
    COUNT(DISTINCT ce.id) AS embedding_count,
    SUM(c.token_count) AS total_tokens
FROM projects p
LEFT JOIN documents d ON d.project_id = p.id
LEFT JOIN chunks c ON c.document_id = d.id
LEFT JOIN chunk_embeddings ce ON ce.chunk_id = c.id
GROUP BY p.id, p.name;

-- -----------------------------------------------------------------------------
-- 12. ROW LEVEL SECURITY (optionnel, pour future multi-user)
-- -----------------------------------------------------------------------------

-- ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE chunks ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE chunk_embeddings ENABLE ROW LEVEL SECURITY;

-- CREATE POLICY documents_project_policy ON documents
--     FOR ALL USING (project_id = current_setting('app.project_id')::uuid);
```

---

## Diagramme conceptuel des tables et relations

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        POSTGRESQL 17 RAG SCHEMA                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐         ┌──────────────────┐                              │
│  │   PROJECTS   │ 1───┐   │ EMBEDDING_MODELS │                              │
│  ├──────────────┤     │   ├──────────────────┤                              │
│  │ id (PK)      │     │   │ id (PK)          │                              │
│  │ name         │     │   │ model_name       │                              │
│  │ slug         │     │   │ dimensions: 384  │                              │
│  │ settings     │     │   │ is_active        │                              │
│  └──────────────┘     │   └────────┬─────────┘                              │
│                       │            │                                         │
│                       │            │ 1                                       │
│                       ▼ N          │                                         │
│  ┌────────────────────────────┐    │                                         │
│  │        DOCUMENTS           │    │                                         │
│  ├────────────────────────────┤    │                                         │
│  │ id (PK)                    │    │                                         │
│  │ project_id (FK) ───────────┘    │                                         │
│  │ path, filename, title      │    │                                         │
│  │ content                    │    │                                         │
│  │ content_hash (SHA256)      │    │                                         │
│  │ frontmatter (JSONB)        │    │                                         │
│  │ tags[] (GIN indexed)       │    │                                         │
│  │ search_vector (tsvector)   │    │                                         │
│  └─────────────┬──────────────┘    │                                         │
│                │ 1                  │                                         │
│                │                    │                                         │
│                ▼ N                  │                                         │
│  ┌────────────────────────────┐    │                                         │
│  │         CHUNKS             │    │                                         │
│  ├────────────────────────────┤    │                                         │
│  │ id (PK)                    │    │                                         │
│  │ document_id (FK)           │    │                                         │
│  │ parent_chunk_id (FK self)──┼────┼──┐  ◄── Auto-référentiel               │
│  │ chunk_type (parent/child)  │    │  │      pour hiérarchie                 │
│  │ content                    │    │  │                                      │
│  │ token_count                │    │  │                                      │
│  │ sequence_number            │    │  │                                      │
│  │ search_vector (tsvector)   │    │  │                                      │
│  └─────────────┬──────────────┘    │  │                                      │
│                │ 1                  │  │                                      │
│                │                    │  │                                      │
│                ▼ N                  │  │                                      │
│  ┌────────────────────────────┐    │  │                                      │
│  │    CHUNK_EMBEDDINGS        │    │  │                                      │
│  ├────────────────────────────┤    │  │                                      │
│  │ id (PK)                    │    │  │                                      │
│  │ chunk_id (FK) ─────────────┼────┘  │                                      │
│  │ embedding_model_id (FK) ───┼───────┘                                      │
│  │ embedding vector(384)      │                                              │
│  │   └── HNSW index           │                                              │
│  └────────────────────────────┘                                              │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                        APACHE AGE GRAPH                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────┐      SIMILAR_TO        ┌─────────────────┐             │
│  │  (:Chunk)       │◄────────────────────►  │  (:Chunk)       │             │
│  │  chunk_id       │      score: 0.89       │  chunk_id       │             │
│  └────────┬────────┘                        └─────────────────┘             │
│           │                                                                  │
│           │ MENTIONS                                                         │
│           ▼                                                                  │
│  ┌─────────────────┐      ABOUT            ┌─────────────────┐              │
│  │  (:Entity)      │◄────────────────────  │  (:Document)    │              │
│  │  name, type     │                       │  doc_id         │              │
│  └─────────────────┘                       └────────┬────────┘              │
│                                                     │                        │
│                                                     │ REFERENCES             │
│                                                     ▼                        │
│                                            ┌─────────────────┐              │
│                                            │  (:Document)    │              │
│                                            │  doc_id         │              │
│                                            └─────────────────┘              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Choix de design et trade-offs détaillés

### HNSW vs IVFFlat: pourquoi HNSW gagne

Pour **10k-50k vecteurs de 384 dimensions**, HNSW surpasse IVFFlat sur tous les critères critiques pour un RAG. Les benchmarks montrent **1.5ms de latence** pour HNSW contre 2.4ms pour IVFFlat. Plus important encore, HNSW maintient sa performance après les mises à jour de données, tandis qu'IVFFlat nécessite des reconstructions périodiques de l'index. Le coût mémoire supplémentaire (~150MB vs ~80MB) est négligeable pour ce volume.

Les paramètres **m=16, ef_construction=100** offrent le meilleur équilibre. Augmenter `m` à 24 améliore le recall de 1-2% mais double le temps de construction. Pour les requêtes, `ef_search=60` fournit >95% de recall; augmenter à 100 pour >99% de recall si nécessaire.

### Embeddings: table séparée pour le versioning

Le stockage des embeddings dans `chunk_embeddings` plutôt que dans `chunks` permet:

- **Migration zero-downtime**: générer les nouveaux embeddings en parallèle avant de switcher le modèle actif
- **Multi-modèle**: conserver plusieurs embeddings par chunk pour A/B testing
- **Maintenance séparée**: VACUUM/ANALYZE indépendants pour la table vectorielle haute-fréquence

Le coût est un JOIN supplémentaire, mais l'index HNSW et la FK indexée garantissent des performances sub-milliseconde.

### Hiérarchie chunks: FK auto-référentielle vs Apache AGE

La relation parent/child utilise une **foreign key classique** plutôt que le graph AGE pour trois raisons:

1. **Simplicité**: `JOIN chunks pc ON pc.id = c.parent_chunk_id` est plus simple qu'une traversée Cypher
2. **Performance**: les FK avec index B-tree sont plus rapides pour les relations 1:N simples
3. **CASCADE automatique**: `ON DELETE CASCADE` maintient l'intégrité sans code applicatif

Apache AGE est réservé aux **relations complexes** où il excelle:
- **SIMILAR_TO**: liens de similarité sémantique avec score, bidirectionnels
- **REFERENCES**: citations entre documents avec contexte
- **MENTIONS**: extraction d'entités nommées (NER) et liens vers chunks

### Full-text search: configuration 'simple' pour multilingue

Le choix de `'simple'` plutôt que `'english'` ou `'french'` pour les tsvector est délibéré. La configuration 'simple' **tokenize sans stemming linguistique**, évitant les erreurs sur du contenu mixte FR/EN. Pour la documentation technique, les termes techniques (API, JSON, PostgreSQL) sont préservés tels quels.

L'alternative serait de stocker la langue par document et d'utiliser des tsvector dynamiques, mais cela complexifie les requêtes et empêche l'utilisation de colonnes GENERATED.

### RRF vs combinaison linéaire pour hybrid search

**Reciprocal Rank Fusion (k=60)** est préféré à la combinaison linéaire de scores car:

- Pas besoin de normaliser les scores (BM25: 0-∞ vs cosine: 0-1)
- Robuste aux outliers dans l'un ou l'autre ranking
- Empiriquement prouvé optimal avec k=60 sur de nombreux benchmarks

La formule `score = Σ weight / (k + rank)` donne des scores comparables entre les deux systèmes de recherche.

---

## Recommandations pour l'intégration Java/LangChain4j

### Configuration JDBC pour Apache AGE

```java
public class AgeConnection {
    
    public static void setupConnection(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("LOAD 'age'");
            stmt.execute("SET search_path = ag_catalog, \"$user\", public");
        }
    }
    
    // Pool de connexions: exécuter setup à chaque getConnection()
    public static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/ragdb");
        config.setConnectionInitSql(
            "LOAD 'age'; SET search_path = ag_catalog, \"$user\", public"
        );
        return new HikariDataSource(config);
    }
}
```

### Custom ContentRetriever pour LangChain4j

```java
public class PostgresHybridRetriever implements ContentRetriever {
    
    private final DataSource dataSource;
    private final EmbeddingModel embeddingModel;
    private final UUID projectId;
    
    @Override
    public List<Content> retrieve(Query query) {
        float[] embedding = embeddingModel.embed(query.text()).content().vector();
        String pgVector = Arrays.toString(embedding);
        
        String sql = "SELECT * FROM hybrid_search($1, $2::vector(384), $3, 10, 0.5, 0.5, 60)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, query.text());
            stmt.setString(2, pgVector);
            stmt.setObject(3, projectId);
            
            ResultSet rs = stmt.executeQuery();
            List<Content> results = new ArrayList<>();
            
            while (rs.next()) {
                String content = rs.getString("content");
                String parentContent = rs.getString("parent_content");
                
                // Enrichir avec le contexte parent
                String enrichedContent = parentContent != null 
                    ? "Context: " + parentContent + "\n\nRelevant excerpt: " + content
                    : content;
                    
                results.add(Content.from(enrichedContent));
            }
            return results;
        }
    }
}
```

### Pattern de migration d'embedding model

```java
public class EmbeddingMigration {
    
    public void migrateToNewModel(int newModelId, int batchSize) {
        String sql = """
            WITH batch AS (
                SELECT c.id FROM chunks c
                LEFT JOIN chunk_embeddings ce 
                    ON c.id = ce.chunk_id AND ce.embedding_model_id = ?
                WHERE ce.id IS NULL AND c.chunk_type = 'child'
                LIMIT ?
            )
            SELECT c.id, c.content FROM chunks c
            JOIN batch b ON c.id = b.id
            """;
        
        while (true) {
            List<ChunkData> batch = fetchBatch(sql, newModelId, batchSize);
            if (batch.isEmpty()) break;
            
            // Générer embeddings en batch
            List<float[]> embeddings = embeddingModel.embedAll(
                batch.stream().map(c -> c.content).toList()
            );
            
            // Insert avec COPY pour performance
            insertEmbeddingsBatch(batch, embeddings, newModelId);
        }
        
        // Switch model actif
        updateActiveModel(newModelId);
    }
}
```

### Configuration ef_search par session

```java
// Avant les requêtes de recherche, ajuster ef_search selon le cas d'usage
public void configureSearchPrecision(Connection conn, boolean highRecall) throws SQLException {
    int efSearch = highRecall ? 100 : 60;
    try (Statement stmt = conn.createStatement()) {
        stmt.execute("SET hnsw.ef_search = " + efSearch);
        // Pour pgvector 0.8+: activer iterative scan pour filtered queries
        stmt.execute("SET hnsw.iterative_scan = relaxed_order");
    }
}
```

---

## Index à créer maintenant vs optimisation ultérieure

### Index essentiels dès le départ

| Index | Type | Justification |
|-------|------|---------------|
| `idx_embeddings_hnsw` | HNSW | Recherche vectorielle - critique |
| `idx_chunks_fts` | GIN | Full-text search hybride |
| `idx_chunks_document_id` | B-tree | Cascade delete, jointures |
| `idx_chunks_parent_id` | B-tree | Récupération contexte parent |
| `idx_documents_project_id` | B-tree | Filtrage multi-projet |
| `idx_documents_content_hash` | B-tree | Détection changements fichiers |

### Index à ajouter selon l'usage

| Index | Condition d'ajout |
|-------|-------------------|
| Partial indexes HNSW par projet | Quand un projet dépasse 10k chunks |
| Index sur `chunks.metadata` (GIN) | Si requêtes fréquentes sur métadonnées |
| Index sur `documents.frontmatter` paths | Si filtres complexes sur frontmatter |
| Index graph AGE sur `SIMILAR_TO.score` | Si requêtes par seuil de similarité |

---

## Stratégie de migration pour changement de modèle d'embedding

```sql
-- 1. Ajouter le nouveau modèle
INSERT INTO embedding_models (model_name, model_version, dimensions, provider, is_active)
VALUES ('text-embedding-3-small', '1', 1536, 'openai', false);

-- 2. Si dimensions différentes, créer nouvelle table ou colonne
-- Option A: Table séparée pour dimensions différentes
CREATE TABLE chunk_embeddings_1536 (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id UUID NOT NULL REFERENCES chunks(id) ON DELETE CASCADE,
    embedding_model_id INTEGER NOT NULL REFERENCES embedding_models(id),
    embedding vector(1536) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_embeddings_1536_hnsw ON chunk_embeddings_1536 
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 100);

-- 3. Générer embeddings en parallèle (via application)
-- 4. Valider qualité avec requêtes de test
-- 5. Switcher le modèle actif
UPDATE embedding_models SET is_active = false WHERE model_name = 'all-MiniLM-L6-v2';
UPDATE embedding_models SET is_active = true WHERE model_name = 'text-embedding-3-small';

-- 6. Optionnel: supprimer anciens embeddings après période de validation
-- DELETE FROM chunk_embeddings WHERE embedding_model_id = 1;
```

Cette architecture offre une base solide et évolutive pour un système RAG production-ready, avec une flexibilité suffisante pour s'adapter aux évolutions futures des modèles d'embedding et des patterns de recherche.