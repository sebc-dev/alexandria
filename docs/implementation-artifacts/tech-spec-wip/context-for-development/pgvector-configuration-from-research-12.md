# pgvector Configuration (from research + #12)

```sql
-- Table structure (Langchain4j crée automatiquement avec createTable=true)
-- Note: Langchain4j utilise ces noms de colonnes par défaut
CREATE TABLE document_embeddings (
    embedding_id UUID PRIMARY KEY,  -- Langchain4j: configurable via idColumn()
    embedding vector(1024),         -- Langchain4j: configurable via embeddingColumn()
    text TEXT,                      -- Langchain4j: configurable via textColumn()
    metadata JSONB                  -- Langchain4j: configurable via metadataColumn()
);

-- HNSW index optimized for 1024D cosine similarity
CREATE INDEX idx_embeddings_hnsw ON document_embeddings
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);

-- B-tree indexes pour opérations de mise à jour (DELETE par document)
CREATE INDEX idx_doc_source_uri ON document_embeddings ((metadata->>'sourceUri'));
CREATE INDEX idx_doc_hash ON document_embeddings ((metadata->>'documentHash'));

-- Index GIN pour requêtes flexibles sur métadonnées (optionnel)
CREATE INDEX idx_metadata_gin ON document_embeddings USING GIN (metadata jsonb_path_ops);

-- Runtime query settings
SET hnsw.ef_search = 100;
SET hnsw.iterative_scan = on;  -- Nouveau 0.8.x pour filtres JSONB
```

**Index B-tree justification:**
- `idx_doc_source_uri`: Lookup rapide pour DELETE par document lors des mises à jour
- `idx_doc_hash`: Détection de doublons par contenu (v2) et vérification de changement

PostgreSQL config for 24GB RAM single-user:
- `shared_buffers = 6GB`
- `effective_cache_size = 18GB`
- `work_mem = 64MB`
- `maintenance_work_mem = 2GB`
