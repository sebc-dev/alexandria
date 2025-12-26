# Communication Patterns

## **Logging Context Standards**

**Structured Logging Format:**
```typescript
// ✅ CORRECT: Champs standards obligatoires
logger.info('Convention uploaded successfully', {
  projectId: 'proj-123',                   // ✅ Toujours inclure
  conventionId: 'conv-456',
  layer: 'layer1',                         // ✅ 'layer1' | 'layer2' | 'mcp' | 'adapter' | 'domain'
  operation: 'upload',                     // ✅ Type opération
  latencyMs: 145
})

logger.info('Vector search completed', {
  projectId: 'proj-123',
  query: 'hexagonal architecture',
  layer: 'layer1',                         // Vector search
  operation: 'search',
  resultCount: 12,
  latencyMs: 450
})

logger.info('Technology linking completed', {
  projectId: 'proj-123',
  layer: 'layer2',                         // Technology linking
  operation: 'link',
  conventionCount: 5,
  documentationCount: 3,
  latencyMs: 120
})

logger.info('Raw context retrieved', {
  projectId: 'proj-123',
  layer: 'mcp',                            // MCP tool level
  operation: 'retrieve_raw_context',
  totalLatencyMs: 570                      // Layer 1 + Layer 2 combined
})

logger.error('Repository query failed', error, {
  projectId: 'proj-123',
  layer: 'adapter',
  operation: 'search',
  errorType: error.constructor.name        // ✅ Error class name
})

// ❌ INCORRECT
logger.info('Search done', {
  id: 'proj-123'                           // ❌ Pas de projectId
  // ❌ Manque layer, operation
})

logger.info('Sub-agent invoked', {
  layer: 'layer3'                          // ❌ N'EXISTE PAS dans Alexandria !
})
```

**Champs Layer Valides:**
- `'layer1'`: Vector search (HNSW + cosine similarity)
- `'layer2'`: Technology linking (SQL JOIN)
- `'mcp'`: MCP tool level (retrieve_raw_context, validate, upload, etc.)
- `'adapter'`: Adapter level (Drizzle, OpenAI, BunDualLogger)
- `'domain'`: Domain use-case level

**❌ PAS de `'layer3'`** : Layer 3 orchestré HORS Alexandria (Skill + Sub-agent externe)

---
