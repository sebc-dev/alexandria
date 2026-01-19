--liquibase formatted sql

--changeset alexandria:002-schema
--comment: Create documents and chunks tables with indexes

-- =============================================================================
-- Table: documents
-- Stores metadata about indexed documentation files
-- =============================================================================
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    path TEXT NOT NULL UNIQUE,
    title TEXT,
    category TEXT,
    tags TEXT[] DEFAULT '{}',
    content_hash TEXT NOT NULL,
    frontmatter JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Index on tags for filtering
CREATE INDEX idx_documents_tags ON documents USING gin(tags);

-- Index on category for filtering
CREATE INDEX idx_documents_category ON documents(category);

-- =============================================================================
-- Table: chunks
-- Stores text chunks with embeddings for vector search
-- Supports hierarchical parent/child chunking strategy
-- =============================================================================
CREATE TABLE chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    parent_chunk_id UUID REFERENCES chunks(id) ON DELETE CASCADE,
    chunk_type TEXT NOT NULL CHECK (chunk_type IN ('parent', 'child')),
    content TEXT NOT NULL,
    embedding vector(384),
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Foreign key index for document lookups
CREATE INDEX idx_chunks_document_id ON chunks(document_id);

-- Foreign key index for parent chunk lookups (hierarchical retrieval)
CREATE INDEX idx_chunks_parent_chunk_id ON chunks(parent_chunk_id);

-- HNSW index for vector similarity search
-- m=16: connections per layer (default, good for 384 dimensions)
-- ef_construction=100: build-time accuracy (higher = better recall, slower build)
CREATE INDEX idx_chunks_embedding ON chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 100);

-- Full-text search index using 'simple' config (preserves technical terms)
CREATE INDEX idx_chunks_content_fts ON chunks
    USING gin(to_tsvector('simple', content));

-- =============================================================================
-- Function: update_updated_at
-- Automatically update updated_at timestamp on row modification
-- =============================================================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for documents table
CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();

--rollback DROP TRIGGER IF EXISTS trg_documents_updated_at ON documents;
--rollback DROP FUNCTION IF EXISTS update_updated_at();
--rollback DROP TABLE IF EXISTS chunks;
--rollback DROP TABLE IF EXISTS documents;
