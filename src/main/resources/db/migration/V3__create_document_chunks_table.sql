CREATE TABLE document_chunks (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(384) NOT NULL,
    text TEXT NOT NULL,
    metadata JSONB,
    source_id UUID REFERENCES sources(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
