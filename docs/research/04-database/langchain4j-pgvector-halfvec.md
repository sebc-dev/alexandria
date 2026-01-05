# Langchain4j pgvector halfvec Support: Not Native, Workaround Available

**Langchain4j-pgvector does NOT natively support PostgreSQL's halfvec type**, but the underlying pgvector-java library fully supports it. This creates a straightforward workaround path: pre-create tables with halfvec columns and use raw JDBC with the `PGhalfvec` class for embedding operations, while leveraging Langchain4j for other pipeline components.

## Current support status summary

| Component | Version | halfvec Support | Status |
|-----------|---------|-----------------|--------|
| langchain4j-pgvector | 1.0.1-beta3 / 1.10.0-beta18 | ❌ Not Implemented | Hardcoded to `vector` type |
| pgvector-java | 0.1.6 | ✅ Fully Supported | `PGhalfvec` class available |
| pgvector extension | 0.8.1 | ✅ Native Support | Since v0.7.0 |
| PostgreSQL | 18 | ✅ Compatible | Full pgvector 0.8.1 support |

The **50% memory savings** target for Alexandria is achievable through the workaround approach outlined below.

## PgVectorEmbeddingStore builder API lacks vector type configuration

The `PgVectorEmbeddingStore.builder()` exposes these parameters—none related to vector precision:

- **Connection**: `datasource`, `host`, `port`, `database`, `user`, `password`
- **Schema**: `table`, `dimension`, `createTable`, `dropTableFirst`
- **Indexing**: `useIndex`, `indexListSize` (IVFFlat only)
- **Metadata**: `metadataStorageConfig` (JSON, JSONB, or COLUMN_PER_KEY)

**No `vectorType`, `dataType`, or `precision` parameter exists.** The table creation SQL hardcodes `vector(dimension)`:

```sql
CREATE TABLE IF NOT EXISTS <table> (
    embedding_id UUID NOT NULL PRIMARY KEY,
    embedding vector(<dimension>),  -- Always float32, never halfvec
    text TEXT,
    metadata JSON
);
```

## JDBC driver and dependency chain

Langchain4j-pgvector uses **standard pgjdbc** (org.postgresql:postgresql v42.7.7), not pgjdbc-ng. It depends on **pgvector-java v0.1.6**, which critically includes the `PGhalfvec` class added in v0.1.4. This means the halfvec serialization/deserialization infrastructure exists in Langchain4j's dependency tree—it's simply not wired up.

```xml
<!-- Current langchain4j-pgvector dependencies -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.7</version>
</dependency>
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.6</version>
</dependency>
```

## GitHub repository shows zero halfvec discussions

Searching the Langchain4j repository revealed **no issues, PRs, or feature requests** mentioning halfvec, sparsevec, or vector type configuration. This is an unaddressed feature gap. Filing a feature request at github.com/langchain4j/langchain4j would be the appropriate first step if native support is desired.

## pgvector-java fully supports halfvec operations

The pgvector-java library provides complete halfvec support through the `PGhalfvec` class:

```java
import com.pgvector.PGhalfvec;

// Create from float array (automatically converts to 16-bit)
PGhalfvec embedding = new PGhalfvec(new float[]{0.1f, 0.2f, 0.3f, ...});

// JDBC insert
PreparedStatement ps = conn.prepareStatement(
    "INSERT INTO embeddings (id, embedding) VALUES (?, ?)");
ps.setObject(1, uuid);
ps.setObject(2, new PGhalfvec(vectorData));
ps.executeUpdate();

// JDBC query with cosine distance
PreparedStatement query = conn.prepareStatement(
    "SELECT * FROM embeddings ORDER BY embedding <=> ? LIMIT 10");
query.setObject(1, new PGhalfvec(queryVector));
ResultSet rs = query.executeQuery();
```

## Recommended workaround for Alexandria project

For BGE-M3 embeddings (1024 dimensions) on PostgreSQL 18 + pgvector 0.8.1, use this **hybrid approach**:

### Step 1: Create table with halfvec column manually

```sql
-- Create extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create table with halfvec (NOT vector)
CREATE TABLE alexandria_embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding halfvec(1024),  -- 16-bit floats, ~50% memory savings
    text TEXT,
    metadata JSONB
);

-- Create HNSW index with cosine distance for BGE-M3
CREATE INDEX alexandria_embeddings_hnsw_idx 
ON alexandria_embeddings 
USING hnsw (embedding halfvec_cosine_ops) 
WITH (m = 16, ef_construction = 64);
```

### Step 2: Configure Langchain4j to skip table creation

```java
// Disable auto table creation - use pre-created halfvec table
PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
    .datasource(dataSource)
    .table("alexandria_embeddings")
    .dimension(1024)
    .createTable(false)  // Critical: prevents overwriting halfvec schema
    .build();
```

### Step 3: Use raw JDBC for halfvec operations

```java
import com.pgvector.PGhalfvec;
import com.pgvector.PGvector;
import java.sql.*;
import java.util.UUID;

public class AlexandriaHalfvecStore {
    
    private final DataSource dataSource;
    
    public AlexandriaHalfvecStore(DataSource dataSource) throws SQLException {
        this.dataSource = dataSource;
        // Register pgvector types
        try (Connection conn = dataSource.getConnection()) {
            PGvector.registerTypes((org.postgresql.PGConnection) conn);
        }
    }
    
    /**
     * Insert embedding as halfvec (16-bit float)
     */
    public UUID insertEmbedding(float[] embedding, String text, String metadata) 
            throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = """
            INSERT INTO alexandria_embeddings (embedding_id, embedding, text, metadata)
            VALUES (?, ?, ?, ?::jsonb)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, new PGhalfvec(embedding));  // Auto-converts to 16-bit
            ps.setString(3, text);
            ps.setString(4, metadata);
            ps.executeUpdate();
        }
        return id;
    }
    
    /**
     * Query similar embeddings using cosine distance with HNSW index
     */
    public List<SearchResult> findSimilar(float[] queryVector, int limit) 
            throws SQLException {
        String sql = """
            SELECT embedding_id, text, metadata, 
                   1 - (embedding <=> ?) AS score
            FROM alexandria_embeddings
            ORDER BY embedding <=> ?
            LIMIT ?
            """;
        
        List<SearchResult> results = new ArrayList<>();
        PGhalfvec query = new PGhalfvec(queryVector);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, query);
            ps.setObject(2, query);
            ps.setInt(3, limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                        rs.getObject("embedding_id", UUID.class),
                        rs.getString("text"),
                        rs.getString("metadata"),
                        rs.getDouble("score")
                    ));
                }
            }
        }
        return results;
    }
    
    public record SearchResult(UUID id, String text, String metadata, double score) {}
}
```

### Step 4: Batch insert for efficiency

```java
/**
 * Batch insert with halfvec - use COPY for best performance
 */
public void batchInsert(List<EmbeddingEntry> entries) throws SQLException {
    String sql = """
        INSERT INTO alexandria_embeddings (embedding_id, embedding, text, metadata)
        VALUES (?, ?, ?, ?::jsonb)
        """;
    
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (EmbeddingEntry entry : entries) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, new PGhalfvec(entry.embedding()));
                ps.setString(3, entry.text());
                ps.setString(4, entry.metadata());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }
}
```

## Memory savings verification

For **1024-dimension BGE-M3 embeddings**:

| Type | Storage per Vector | Index Page Efficiency |
|------|-------------------|----------------------|
| `vector(1024)` | 4,104 bytes | ~1 vector per 8KB page |
| `halfvec(1024)` | 2,056 bytes | ~3 vectors per 8KB page |
| **Savings** | **~50%** | **3x density improvement** |

Verify with:
```sql
SELECT pg_column_size('[1,2,3,...1024 values...]'::vector(1024)) as vector_size,
       pg_column_size('[1,2,3,...1024 values...]'::halfvec(1024)) as halfvec_size;
```

## HNSW index configuration for halfvec

```sql
-- Optimal HNSW parameters for 1024-dim halfvec with cosine similarity
CREATE INDEX CONCURRENTLY alexandria_hnsw_idx 
ON alexandria_embeddings 
USING hnsw (embedding halfvec_cosine_ops) 
WITH (
    m = 16,              -- Connections per layer (16-64 typical)
    ef_construction = 64 -- Build-time quality (64-200 typical)
);

-- Set search parameter before queries
SET hnsw.ef_search = 40;  -- Query-time accuracy (higher = more accurate, slower)
```

## Alternative workaround: Extend PgVectorEmbeddingStore

For tighter Langchain4j integration, extend the store class:

```java
public class HalfvecEmbeddingStore extends PgVectorEmbeddingStore {
    
    // Override add() methods to use PGhalfvec instead of PGvector
    @Override
    public String add(Embedding embedding) {
        // Custom implementation using PGhalfvec
    }
    
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(
            Embedding referenceEmbedding, int maxResults, double minScore) {
        // Custom implementation with halfvec queries
    }
}
```

## Conclusion

**Status: Not Supported (with straightforward workaround)**

Langchain4j-pgvector **does not support halfvec natively**—no builder parameter exists and the `vector` type is hardcoded. However, because pgvector-java 0.1.6 (already in the dependency chain) fully supports `PGhalfvec`, a clean workaround exists: pre-create tables with halfvec schema and use raw JDBC with `PGhalfvec` for embedding operations. This achieves the **50% memory savings** target for Alexandria while maintaining compatibility with the broader Langchain4j ecosystem for RAG pipeline components. Filing a feature request on the Langchain4j GitHub for a `vectorType` builder parameter would benefit the community.