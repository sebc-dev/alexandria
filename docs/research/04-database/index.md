# 04 - Database

Research documents for PostgreSQL and pgvector configuration.

## Documents

| File | Description |
|------|-------------|
| [postgresql-pgvector-schema.md](postgresql-pgvector-schema.md) | Optimal PostgreSQL schema for RAG with pgvector |
| [langchain4j-pgvector-initialization.md](langchain4j-pgvector-initialization.md) | Langchain4j pgvector schema initialization guide |
| [langchain4j-pgvector-halfvec.md](langchain4j-pgvector-halfvec.md) | Langchain4j pgvector halfvec support status |
| [langchain4j-pgvector-jdbc.md](langchain4j-pgvector-jdbc.md) | PgVectorEmbeddingStore uses pure JDBC |
| [postgresql-18-docker-pgdata.md](postgresql-18-docker-pgdata.md) | PostgreSQL 18 Docker PGDATA path changes |
| [postgresql-jdbc-prepare-threshold.md](postgresql-jdbc-prepare-threshold.md) | JDBC prepareThreshold=0 for pgvector |

## Key Findings

- PostgreSQL 18.1 + pgvector 0.8.1 is the target stack
- vector(1024) type (halfvec not supported by Langchain4j)
- HNSW index with m=16, ef_construction=128
- hnsw.iterative_scan=on for JSONB filter optimization
- Langchain4j creates IVFFlat by default, HNSW must be created manually
