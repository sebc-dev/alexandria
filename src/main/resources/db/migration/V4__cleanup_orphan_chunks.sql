-- Clean up historical orphan chunks with no source association.
-- After this migration, the source_id FK population fix in IngestionService
-- ensures all new chunks get source_id populated during ingestion.
DELETE FROM document_chunks WHERE source_id IS NULL;
