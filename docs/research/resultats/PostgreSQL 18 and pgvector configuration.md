# PostgreSQL 18 and pgvector: Optimal configuration for your RAG server

**PostgreSQL 18.1 is production-ready as of January 2026**, and pgvector 0.8.1 fully supports the `halfvec` type—a **50% storage reduction** with virtually identical recall. For your i5-4570 system with 24GB RAM running RAG on hundreds of documents, this combination delivers excellent performance without requiring significant hardware investment. The halfvec type, introduced in pgvector 0.7.0, stores 1024-dimension vectors in just 2,056 bytes versus 4,104 bytes for standard vectors, while benchmarks consistently show equal or better query performance.

## PostgreSQL 18 delivers major I/O improvements for vector workloads

PostgreSQL 18 reached general availability on **September 25, 2025**, with the current stable version being **18.1** (released November 13, 2025). The release introduces several features directly beneficial for vector search operations.

The most significant enhancement is the new **asynchronous I/O subsystem**, delivering up to **3× performance improvement** for read-heavy workloads like RAG applications. This system supports sequential scans, bitmap heap scans, and vacuum operations through configurable I/O methods (`worker`, `io_uring`, or `sync`). For your Haswell-era CPU, the `worker` method will provide the best compatibility.

Additional relevant features include **parallel GIN index builds** (useful for hybrid text+vector search), **UUIDv7 support** for time-ordered document identifiers with better indexing performance, and improved `EXPLAIN ANALYZE` output showing buffer usage by default. PostgreSQL 17.7 remains a solid alternative if you prefer proven stability, offering **20× reduction in vacuum memory usage** and streaming I/O for sequential reads.

## pgvector 0.8.1 supports halfvec with 4,000-dimension HNSW indexes

The current stable release is **pgvector v0.8.1**, fully compatible with PostgreSQL 18. The halfvec type was introduced in version **0.7.0** and is now mature and production-ready.

The dimension limits are critical for choosing your vector type:

| Type | Storage max | HNSW index max | Storage per 1024-dim vector |
|------|-------------|----------------|----------------------------|
| **vector** (32-bit) | 16,000 | **2,000** | 4,104 bytes |
| **halfvec** (16-bit) | 16,000 | **4,000** | 2,056 bytes |
| **bit** (binary) | unlimited | 64,000 | 136 bytes |

For 1024 dimensions, both vector and halfvec work within HNSW limits, but halfvec provides substantial advantages. Creating a halfvec column and index uses straightforward syntax:

```sql
CREATE TABLE documents (
    id bigserial PRIMARY KEY,
    content text,
    embedding halfvec(1024)
);

CREATE INDEX ON documents USING hnsw (embedding halfvec_cosine_ops) 
WITH (m = 24, ef_construction = 100);
```

Version 0.8.0 introduced **iterative index scans**, automatically expanding search when WHERE clause filtering reduces results—essential for RAG queries combining semantic search with metadata filters. Enable this with `SET hnsw.iterative_scan = relaxed_order;`.

## Halfvec benchmarks show 50% storage savings with zero recall loss

Comprehensive benchmarks from pgvector maintainer Jonathan Katz and AWS confirm halfvec as the optimal choice for most embedding workloads. The performance data is compelling:

**Storage and index size** reductions are consistent across datasets. Testing on dbpedia-openai-1000k (1M vectors, 1536 dimensions) showed index sizes dropping from 7,734 MB to 3,867 MB—exactly **2× smaller**. Production deployments at East Agile reported **57% total storage reduction** and **66% index size reduction** on 1.2M vectors.

**Recall remains virtually identical**. Across multiple benchmark datasets at ef_search=200:
- sift-128-euclidean: 95.4% (vector) vs 95.4% (halfvec)
- gist-960-euclidean: 78.0% vs 78.1%  
- dbpedia-1000k-angular: 96.8% vs 96.8%

AWS Aurora testing confirmed recall of **0.950** for both types on cohere-10m dataset. The precision loss from 32-bit to 16-bit floating point is negligible for normalized embeddings.

**Query performance actually improves** with halfvec due to better cache utilization:
- **Queries per second**: 2-16% higher (513→579 QPS on gist-960-euclidean)
- **p99 latency**: 4-14% faster (8.70ms→7.49ms on high-dimensional data)
- **Index build time**: 1.5-3× faster (377s→163s on dbpedia-1536-dim)

Jonathan Katz explicitly recommends: *"I feel comfortable recommending storing the vector in the table and quantizing to halfvec in the index... this is a really good starting point."*

The only caveats: halfvec uses IEEE-754 binary16 (range ±6.5×10⁴), so verify your embeddings don't exceed this range. Most modern embedding models (OpenAI, Cohere, sentence-transformers) produce normalized values well within bounds.

## Optimal PostgreSQL configuration for your 24GB single-user system

Your i5-4570 with 24GB DDR3 RAM is well-suited for a single-user RAG workload with hundreds of documents. Here's the optimized configuration:

```ini
# Memory Configuration (postgresql.conf)
shared_buffers = 6GB              # 25% of RAM
effective_cache_size = 18GB       # 75% for single-user dedicated system
work_mem = 256MB                  # Generous for single-user
maintenance_work_mem = 2GB        # Critical for HNSW index builds
wal_buffers = 64MB

# Parallel Processing (4-core system)
max_parallel_workers = 4
max_parallel_workers_per_gather = 2
max_parallel_maintenance_workers = 3

# Storage (adjust based on disk type)
random_page_cost = 1.1            # SSD: 1.1, HDD: 4.0
effective_io_concurrency = 200    # SSD: 200, HDD: 2

# Connections
max_connections = 20              # Low for single-user
```

**HNSW index parameters** require careful tuning for 1024-dimension vectors:

| Parameter | Recommended | Default | Purpose |
|-----------|-------------|---------|---------|
| **m** | 24 | 16 | Connections per node; higher improves recall for high dimensions |
| **ef_construction** | 100-128 | 64 | Build quality; higher = better recall, slower build |
| **hnsw.ef_search** | 100-200 | 40 | Query-time recall; default 40 is often insufficient |

The optimal index creation for your RAG use case:

```sql
-- Set memory for index building
SET maintenance_work_mem = '4GB';
SET max_parallel_maintenance_workers = 3;

-- Create index after loading data
CREATE INDEX CONCURRENTLY ON documents 
USING hnsw (embedding halfvec_cosine_ops) 
WITH (m = 24, ef_construction = 100);

-- Runtime setting for queries
SET hnsw.ef_search = 100;
```

**HNSW vs IVFFlat**: For your use case, HNSW is definitively the better choice. IVFFlat requires training data (rows/1000 lists minimum) and periodic reindexing, while HNSW works immediately with incremental updates. IVFFlat's advantages (faster builds, lower memory) only matter at millions of rows.

## Practical implementation recommendations

For a single-user RAG system on hundreds of technical documents, your 24GB system is more than adequate. With **halfvec at 2,056 bytes per vector**, you can store approximately **10 million vectors in 20GB**—far exceeding your document count even with aggressive chunking.

Consider this hybrid approach for maximum flexibility: store full-precision vectors in the table but index at half-precision:

```sql
CREATE TABLE documents (
    id bigserial PRIMARY KEY,
    content text,
    embedding vector(1024)  -- Full precision storage
);

-- Half-precision index with expression
CREATE INDEX ON documents 
USING hnsw ((embedding::halfvec(1024)) halfvec_cosine_ops)
WITH (m = 24, ef_construction = 100);

-- Query with explicit cast
SELECT id, content 
FROM documents 
ORDER BY embedding::halfvec(1024) <=> $1::halfvec(1024) 
LIMIT 10;
```

This preserves full precision for potential future use while gaining all halfvec index benefits.

## Conclusion

PostgreSQL 18.1 with pgvector 0.8.1 provides an excellent foundation for your RAG server. The key decisions are clear: **use halfvec** for its 50% storage reduction and slightly better query performance with no recall loss; **use HNSW** with m=24, ef_construction=100 for your 1024-dimension vectors; and **tune ef_search to 100-200** at query time for high-quality semantic search results.

Your Haswell-era hardware is fully capable of this workload—pgvector's SIMD optimizations (including F16C for halfvec) will utilize available CPU instructions, and the 24GB RAM comfortably exceeds requirements for hundreds of documents. The asynchronous I/O in PostgreSQL 18 provides additional performance gains that help compensate for DDR3 memory bandwidth limitations compared to newer systems.