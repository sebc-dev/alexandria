# Drizzle ORM + pgvector integration for Alexandria RAG server

**Drizzle ORM natively supports pgvector since v0.31.0**, including `halfvec` for your 50% memory savings requirement. The current stable stack—**drizzle-orm 0.45.1** and **postgres.js 3.4.7**—provides full compatibility with PostgreSQL 18.1 and pgvector 0.8.1. For your single-user Alexandria server with 1024-dimensional halfvec embeddings, you can implement vector similarity search entirely through Drizzle's type-safe API without raw SQL or community packages.

## Current stable versions and compatibility

As of January 2026, the recommended package versions are:

| Package | Version | Release Date | Notes |
|---------|---------|--------------|-------|
| drizzle-orm | **0.45.1** | December 2025 | Stable; v1.0.0-beta.2 available |
| drizzle-kit | **0.22.0+** | Required for migrations | pgvector support since this version |
| postgres | **3.4.7** | May 2025 | Bun-native, fastest PostgreSQL driver |

Drizzle officially supports postgres.js through the `drizzle-orm/postgres-js` subpackage. Both packages are actively maintained, and postgres.js explicitly supports Bun runtime (since Bun 0.5.0). The combination has been production-stable throughout 2025 with no known compatibility issues between latest versions.

## Native pgvector support in Drizzle

Drizzle added native pgvector support in **v0.31.0 (May 2024)**, which means your project can use built-in types and distance functions without custom implementations. All four pgvector column types are supported:

- **`vector`** — standard float32 vectors
- **`halfvec`** — half-precision float16 vectors (your target type)
- **`sparsevec`** — sparse vector representation
- **`bit`** — binary vectors for Hamming distance

Import these types directly from `drizzle-orm/pg-core`:

```typescript
import { pgTable, serial, text, vector, halfvec, index } from 'drizzle-orm/pg-core';
import { cosineDistance, l2Distance, innerProduct } from 'drizzle-orm';
```

Drizzle also provides **six distance functions** that map to pgvector operators: `l2Distance` (`<->`), `cosineDistance` (`<=>`), `innerProduct` (`<#>`), `l1Distance` (`<+>`), `hammingDistance` (`<~>`), and `jaccardDistance` (`<%>`).

## Complete schema example for Alexandria

Here's a production-ready schema using `halfvec(1024)` with HNSW indexing for your RAG server:

```typescript
// schema.ts
import { pgTable, serial, text, timestamp, uuid, index } from 'drizzle-orm/pg-core';
import { halfvec } from 'drizzle-orm/pg-core';

export const documents = pgTable('documents', {
  id: uuid('id').defaultRandom().primaryKey(),
  title: text('title').notNull(),
  content: text('content').notNull(),
  embedding: halfvec('embedding', { dimensions: 1024 }),  // 50% memory vs vector(1024)
  createdAt: timestamp('created_at').defaultNow().notNull(),
  updatedAt: timestamp('updated_at').defaultNow().notNull(),
}, (table) => [
  // HNSW index for cosine similarity (best for normalized embeddings)
  index('documents_embedding_idx')
    .using('hnsw', table.embedding.op('halfvec_cosine_ops')),
]);

export const chunks = pgTable('chunks', {
  id: serial('id').primaryKey(),
  documentId: uuid('document_id').references(() => documents.id).notNull(),
  content: text('content').notNull(),
  embedding: halfvec('embedding', { dimensions: 1024 }),
  chunkIndex: serial('chunk_index'),
}, (table) => [
  index('chunks_embedding_idx')
    .using('hnsw', table.embedding.op('halfvec_cosine_ops')),
]);
```

The generated SQL correctly produces `halfvec(1024)` columns. For HNSW indexes on halfvec columns, use operator classes like `halfvec_cosine_ops`, `halfvec_l2_ops`, or `halfvec_ip_ops`.

## Vector operations and similarity search

Inserting embeddings works with standard JavaScript arrays—Drizzle handles the serialization:

```typescript
await db.insert(chunks).values({
  documentId: docId,
  content: chunkText,
  embedding: embeddingArray,  // number[] with 1024 elements
});
```

For **cosine similarity search** (typical for RAG retrieval):

```typescript
import { cosineDistance, desc, gt, sql } from 'drizzle-orm';

async function findSimilarChunks(queryEmbedding: number[], limit = 5, threshold = 0.3) {
  const similarity = sql<number>`1 - (${cosineDistance(chunks.embedding, queryEmbedding)})`;
  
  return db
    .select({
      id: chunks.id,
      content: chunks.content,
      similarity,
    })
    .from(chunks)
    .where(gt(similarity, threshold))
    .orderBy(desc(similarity))
    .limit(limit);
}
```

For **L2 distance** (nearest neighbor without normalization):

```typescript
import { l2Distance } from 'drizzle-orm';

const nearest = await db
  .select()
  .from(chunks)
  .orderBy(l2Distance(chunks.embedding, queryEmbedding))
  .limit(10);
```

## postgres.js connection configuration for single-user RAG server

For Alexandria's mono-user scenario with potentially slow vector queries, configure postgres.js conservatively:

```typescript
// db.ts
import postgres from 'postgres';
import { drizzle } from 'drizzle-orm/postgres-js';
import * as schema from './schema';

const sql = postgres(process.env.DATABASE_URL!, {
  // Pool settings for single-user
  max: 2,                      // Allow 2 concurrent connections (1 for slow vector, 1 for metadata)
  idle_timeout: 30,            // Close idle connections after 30 seconds
  connect_timeout: 30,         // Connection establishment timeout
  max_lifetime: 60 * 30,       // Recycle connections every 30 minutes
  
  // Critical for slow vector similarity queries
  connection: {
    application_name: 'alexandria-rag',
    statement_timeout: 300000,  // 5 minutes for large vector scans
  },
  
  // Performance optimizations
  prepare: true,               // Enable prepared statements
  fetch_types: false,          // Skip array type fetching (not needed for vectors)
  
  // Bun compatibility (works out of box)
  // No special configuration needed for Bun 1.3.5+
});

export const db = drizzle(sql, { schema });
export { sql };  // Export for raw queries if needed
```

**Key settings explained**: The `max: 2` allows one connection for potentially slow vector similarity searches while keeping another available for quick metadata queries. The **5-minute statement timeout** prevents runaway queries but accommodates large-scale vector scans. For Bun 1.3.5+, no special configuration is required—postgres.js has native Bun support.

## Migration setup for pgvector extension

Drizzle doesn't auto-create PostgreSQL extensions. Create a custom migration:

```bash
bunx drizzle-kit generate --custom
```

Then add to your first migration:

```sql
-- 0000_enable_pgvector.sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Run migrations with:

```bash
bunx drizzle-kit migrate
```

## Alternative approaches if native support proves insufficient

While native support covers most use cases, two scenarios might require alternatives:

**Custom type for edge cases**: If you encounter the known bug (#4674) where `halfvec` generates quoted type names in migrations, define a custom type:

```typescript
import { customType } from 'drizzle-orm/pg-core';

const halfvecCustom = customType<{
  data: number[];
  driverData: string;
  config: { dimensions: number };
}>({
  dataType(config) {
    return `halfvec(${config?.dimensions ?? 1024})`;
  },
  toDriver: (value) => JSON.stringify(value),
  fromDriver: (value) => JSON.parse(value as string),
});
```

**Raw SQL for complex operations**: For advanced pgvector features not yet in Drizzle's API:

```typescript
const results = await sql`
  SELECT id, content, embedding <=> ${JSON.stringify(queryEmbed)}::halfvec AS distance
  FROM chunks
  WHERE embedding <=> ${JSON.stringify(queryEmbed)}::halfvec < 0.5
  ORDER BY distance
  LIMIT 10
`;
```

## Conclusion

Your Alexandria stack is well-supported: **Drizzle 0.45.1 + postgres.js 3.4.7** provides type-safe pgvector integration with native `halfvec(1024)` support for 50% memory savings. Use HNSW indexes with `halfvec_cosine_ops` for optimal RAG retrieval performance. The single-user postgres.js configuration with `max: 2` connections and 5-minute statement timeout handles slow similarity queries gracefully. No community packages are needed—Drizzle's native support since v0.31.0 covers vector columns, distance functions, and index definitions through its standard schema API.