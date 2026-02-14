CREATE TABLE ingestion_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    page_url TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    last_ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(source_id, page_url)
);
