-- Fix GIN index expression to match LangChain4j's hybrid search SQL.
-- LangChain4j uses: to_tsvector(config, coalesce(text, ''))
-- Old V1 index used:  to_tsvector('english', text)
-- PostgreSQL treats these as different expressions, so the old index is NOT used
-- for hybrid search queries, causing sequential scans.

DROP INDEX IF EXISTS idx_document_chunks_text_fts;

CREATE INDEX idx_document_chunks_text_fts
    ON document_chunks
    USING gin (to_tsvector('english', coalesce(text, '')));
