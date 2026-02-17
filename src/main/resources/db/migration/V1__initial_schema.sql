-- pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Sources: documentation sites to index
CREATE TABLE sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url TEXT NOT NULL UNIQUE,
    name TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    last_crawled_at TIMESTAMPTZ,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Document chunks: text segments with embeddings (shared with LangChain4j PgVectorEmbeddingStore)
CREATE TABLE document_chunks (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(384) NOT NULL,
    text TEXT NOT NULL,
    metadata JSONB,
    source_id UUID REFERENCES sources(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ingestion state: tracks per-page content hash for incremental crawling
CREATE TABLE ingestion_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    page_url TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    last_ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(source_id, page_url)
);

-- HNSW index for cosine similarity vector search (m=16, ef_construction=64)
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for PostgreSQL full-text search
CREATE INDEX idx_document_chunks_text_fts
    ON document_chunks
    USING gin (to_tsvector('english', coalesce(text, '')));

-- B-tree index for looking up chunks by source
CREATE INDEX idx_document_chunks_source_id
    ON document_chunks (source_id);
