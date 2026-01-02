# MCP Tool Schemas (F34)

**Search Tool:**
```typescript
const SearchInputSchema = z.object({
  query: z.string().min(1).max(1000),
  threshold: z.number().min(0).max(1).default(0.5),
  limit: z.number().int().min(1).max(100).default(10),
  tags: z.array(z.string()).default([]),
});
```

**Ingest Tool:**
```typescript
const IngestInputSchema = z.object({
  source: z.string().regex(/^[a-zA-Z0-9/_.-]{1,500}$/),
  title: z.string().max(200).optional(),  // F36
  content: z.string().min(1),  // Full file content
  chunks: z.array(z.object({
    content: z.string().min(1).max(8000),
    chunk_index: z.number().int().min(0),
    metadata: z.object({
      heading: z.string().optional(),
      source_lines: z.tuple([z.number(), z.number()]).optional(),
    }).optional(),
  })).min(1).max(500),
  tags: z.array(z.string()).default([]),
  version: z.string().optional(),
  upsert: z.boolean().default(true),
});
```

**Delete Tool:**
```typescript
const DeleteInputSchema = z.object({
  source: z.string().regex(/^[a-zA-Z0-9/_.-]{1,500}$/),
});
```

**Health Tool:**
```typescript
const HealthInputSchema = z.object({});  // No input required

const HealthOutputSchema = z.object({
  database: z.enum(['connected', 'disconnected', 'error']),
  model: z.enum(['initial', 'loading', 'loaded', 'error']),  // F44
  documents_count: z.number().int(),
  chunks_count: z.number().int(),
});
```

**List Tool (F42):**
```typescript
const ListInputSchema = z.object({
  limit: z.number().int().min(1).max(100).default(50),
  offset: z.number().int().min(0).default(0),
  tags: z.array(z.string()).default([]),  // Filter by tags
});

const ListOutputSchema = z.object({
  documents: z.array(z.object({
    id: z.string().uuid(),
    source: z.string(),
    title: z.string().nullable(),
    tags: z.array(z.string()),
    version: z.string().nullable(),
    chunks_count: z.number().int(),
    created_at: z.string().datetime(),
    updated_at: z.string().datetime(),
  })),
  total: z.number().int(),
  has_more: z.boolean(),
});
```
