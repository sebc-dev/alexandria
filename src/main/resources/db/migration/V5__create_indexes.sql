-- HNSW index for cosine similarity vector search
-- Using pgvector defaults: m=16, ef_construction=64
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for PostgreSQL full-text search
CREATE INDEX idx_document_chunks_text_fts
    ON document_chunks
    USING gin (to_tsvector('english', text));

-- Index for looking up chunks by source
CREATE INDEX idx_document_chunks_source_id
    ON document_chunks (source_id);
