# Stack Research: Hybrid Vector-Graph RAG in Java

**Source:** User-provided research (2025-2026 optimal stack)

## Recommended Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Framework | LangChain4j 1.0+ | Superior graph integration, lighter than Spring AI |
| Database | PostgreSQL 17 | Unified vector + graph |
| Vector Store | pgvector 0.8.1 | HNSW indexing, mature |
| Graph DB | Apache AGE 1.6.0 | Cypher queries in PostgreSQL |
| Embeddings | all-MiniLM-L6-v2 | In-process ONNX, 384 dim, ~100MB RAM |

## LangChain4j Advantages

- First-class Neo4j support (adaptable to AGE)
- `AllMiniLmL6V2EmbeddingModel` — 24MB in-process ONNX
- Modular architecture, lighter memory footprint
- Mature pgvector integration with JSONB metadata

```java
EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
    .host("localhost").port(5432).database("ragdb")
    .table("embeddings").dimension(384)
    .useIndex(true).indexListSize(100)
    .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
    .build();
```

## pgvector Configuration

Use HNSW over IVFFlat:
- Better speed-recall tradeoffs
- No reindexing as data changes

```sql
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

SET hnsw.ef_search = 100;
```

## Apache AGE Usage

```sql
CREATE EXTENSION age;
LOAD 'age';
SET search_path = ag_catalog, "$user", public;

SELECT * FROM cypher('documents', $$
    MATCH (d:Document)-[:REFERENCES]->(cited:Document)
    WHERE d.topic = 'machine-learning'
    RETURN d.title, cited.title
$$) AS (source agtype, target agtype);
```

## Embedding Model

**all-MiniLM-L6-v2 via ONNX:**
- ~100MB RAM
- 200-400 embeddings/second on 4 cores
- 384 dimensions
- Zero external dependencies

```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
Response<Embedding> response = embeddingModel.embed("your text");
float[] vector = response.content().vector();
```

## Maven Dependencies

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.4</version>
</dependency>
```

## Pitfalls to Avoid

1. **HNSW memory exhaustion**: Keep index builds under `maintenance_work_mem`
2. **Connection pooling**: Each connection uses `work_mem` (128MB)
3. **Embedding dimension mismatch**: Changing models requires re-embedding
4. **Graph over-engineering**: For few thousand docs, recursive CTEs often suffice
5. **AGE search_path**: Always set before queries or they fail silently

## Graph RAG Strategy

**Recommended: Lightweight hierarchical approach**

1. **Hierarchical chunking** — Parent (1000 tokens) / Child (200 tokens)
2. **Metadata graph** — author→doc, topic→doc relationships
3. **Skip full knowledge graphs** — Overkill for few thousand docs
