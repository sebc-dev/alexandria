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

**Plage -31xxx choisie** pour éviter les conflits avec :
- JSON-RPC 2.0 : plage -32768 à -32000 réservée aux erreurs prédéfinies du protocole
- MCP SDK : -32000 (ConnectionClosed), -32001 (RequestTimeout), -32002 (Resource Not Found)
- La plage -31xxx est explicitement libre pour les erreurs applicatives

| Code | Nom | Description |
|------|-----|-------------|
| -31001 | DOCUMENT_NOT_FOUND | Document ID inexistant |
| -31002 | VALIDATION_ERROR | Input invalide (Zod error) |
| -31003 | EMBEDDING_FAILED | Échec génération embedding |
| -31004 | DATABASE_ERROR | Erreur PostgreSQL |
| -31005 | DUPLICATE_SOURCE | Source déjà existante (si upsert=false) |
| -31006 | MODEL_LOAD_TIMEOUT | Timeout chargement modèle |
| -31007 | EMBEDDING_DIMENSION_MISMATCH | Vecteur n'a pas 384 dimensions (F20) |
| -31008 | CONTENT_TOO_LARGE | Chunk dépasse 2000 chars (F24 - limite E5 512 tokens) |
| -31009 | CONCURRENT_MODIFICATION | Conflit d'ingestion concurrente (F26) |

**Codes MCP SDK réservés (ne pas utiliser) :**

| Code | Nom SDK | Usage |
|------|---------|-------|
| -32700 | ParseError | JSON invalide reçu |
| -32600 | InvalidRequest | Structure JSON-RPC invalide |
| -32601 | MethodNotFound | Méthode inexistante |
| -32602 | InvalidParams | Paramètres incorrects |
| -32603 | InternalError | Erreur interne JSON-RPC |
| -32000 | ConnectionClosed | Connexion fermée |
| -32001 | RequestTimeout | Timeout requête |
| -32002 | ResourceNotFound | Ressource non trouvée (spec MCP) |

**Format erreur MCP:**

```json
{
  "code": -31002,
  "message": "Validation error: chunks[0].content must be non-empty string",
  "data": {
    "field": "chunks[0].content",
    "received": ""
  }
}
```

**Différenciation erreurs protocole vs erreurs d'outil :**

```typescript
// Erreur d'exécution d'outil - permet au LLM de voir l'erreur et s'adapter
return {
  content: [{ type: "text", text: "API rate limit exceeded" }],
  isError: true
};

// Erreur protocole - pour problèmes d'infrastructure
throw new McpError(-31001, "Document not found");
```
