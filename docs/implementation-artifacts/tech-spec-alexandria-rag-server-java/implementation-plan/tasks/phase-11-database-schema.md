# Phase 11: Database Schema

- [ ] **Task 30: Create database schema SQL**
  - File: `src/main/resources/schema.sql` (or Flyway migration)
  - Action: CREATE TABLE document_embeddings (embedding_id UUID PRIMARY KEY, embedding vector(1024), text TEXT, metadata JSONB). CREATE INDEX HNSW, B-tree on sourceUri/documentHash, GIN on metadata
  - Notes: HNSW params: m=16, ef_construction=128. Runtime: SET hnsw.ef_search=100, hnsw.iterative_scan=on
