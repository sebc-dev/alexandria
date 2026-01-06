# Phase 12: Database Schema

> **Note**: Cette phase n'utilise pas de TDD direct. Le schema SQL est validé indirectement via les tests d'intégration des phases précédentes (Testcontainers).

- [ ] **Task 34: Create database schema SQL**
  - File: `src/main/resources/schema.sql` (ou migration Flyway `V1__init.sql`)
  - Action:
    ```sql
    -- Enable pgvector extension
    CREATE EXTENSION IF NOT EXISTS vector;

    -- Main table
    CREATE TABLE document_embeddings (
        embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        embedding vector(1024) NOT NULL,
        text TEXT NOT NULL,
        metadata JSONB NOT NULL DEFAULT '{}'
    );

    -- HNSW index for vector similarity search
    CREATE INDEX idx_embeddings_hnsw ON document_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128);

    -- B-tree indexes for filtering
    CREATE INDEX idx_metadata_source_uri ON document_embeddings
    ((metadata->>'sourceUri'));

    CREATE INDEX idx_metadata_document_hash ON document_embeddings
    ((metadata->>'documentHash'));

    -- GIN index for full metadata queries
    CREATE INDEX idx_metadata_gin ON document_embeddings
    USING gin (metadata);
    ```
  - Notes: HNSW params optimized for quality (m=16, ef_construction=128). Runtime: SET hnsw.ef_search=100

- [ ] **Task 35: Validate schema with integration test**
  - File: `src/test/java/dev/alexandria/DatabaseSchemaValidationTest.java`
  - Action: @SpringBootTest with Testcontainers verifying:
    - Vector extension loaded
    - Table exists with correct columns
    - HNSW index exists
    - Indexes on metadata fields exist
  - Notes: Ce test valide que le schema est correctement appliqué
