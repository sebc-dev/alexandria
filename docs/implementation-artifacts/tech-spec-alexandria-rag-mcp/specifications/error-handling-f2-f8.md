# Error Handling (F2, F8)

## Embedding Errors

| Scenario | Comportement |
|----------|--------------|
| Échec chunk unique | Retry selon config (3 attempts total, backoff avec jitter) |
| Échec après retries | Abort transaction, rollback complet, erreur EMBEDDING_FAILED |
| OOM | Abort immédiat, erreur EMBEDDING_OOM |
| Model not loaded | Lazy load avec timeout 60s, erreur MODEL_LOAD_TIMEOUT |
| Dimension mismatch | Erreur EMBEDDING_DIMENSION_MISMATCH (F20) |

**Transaction guarantee:** L'ingestion est atomique. Soit tous les chunks sont persistés, soit aucun.

## MCP Error Codes

| Code | Nom | Description |
|------|-----|-------------|
| -32001 | DOCUMENT_NOT_FOUND | Document ID inexistant |
| -32002 | VALIDATION_ERROR | Input invalide (Zod error) |
| -32003 | EMBEDDING_FAILED | Échec génération embedding |
| -32004 | DATABASE_ERROR | Erreur PostgreSQL |
| -32005 | DUPLICATE_SOURCE | Source déjà existante (si upsert=false) |
| -32006 | MODEL_LOAD_TIMEOUT | Timeout chargement modèle |
| -32007 | EMBEDDING_DIMENSION_MISMATCH | Vecteur n'a pas 384 dimensions (F20) |
| -32008 | CONTENT_TOO_LARGE | Chunk dépasse 8000 chars (F24) |
| -32009 | CONCURRENT_MODIFICATION | Conflit d'ingestion concurrente (F26) |

**Format erreur MCP:**

```json
{
  "code": -32002,
  "message": "Validation error: chunks[0].content must be non-empty string",
  "data": {
    "field": "chunks[0].content",
    "received": ""
  }
}
```
