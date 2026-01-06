# Infinity API (validated)

Endpoint unique RunPod exposant deux APIs avec formats différents:

**Embeddings** - `POST /v1/embeddings` - Format OpenAI-compatible
- Utilisable via `langchain4j-open-ai` avec `baseUrl` custom
- BGE-M3 produit des vecteurs 1024 dimensions

```java
// Via Langchain4j OpenAI module (pas de wrapper custom nécessaire)
// Configuré comme bean dans LangchainConfig.java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .baseUrl("http://localhost:7997/v1")  // Infinity endpoint
    .apiKey("EMPTY")  // Si pas d'auth configurée
    .modelName("BAAI/bge-m3")
    .build();
```

**Reranking** - `POST /rerank` - Format Cohere (NON OpenAI-compatible)
- Nécessite client HTTP custom (pas de support Langchain4j natif)
- bge-reranker-v2-m3, 512 tokens max par paire query/document

```bash
# Request format (style Cohere)
curl -X POST http://<runpod>/rerank \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "BAAI/bge-reranker-v2-m3",
    "query": "Question de recherche",
    "documents": ["Doc 1", "Doc 2", "Doc 3"]
  }'

# Response format
{
  "results": [
    {"index": 0, "relevance_score": 0.95},
    {"index": 2, "relevance_score": 0.72},
    {"index": 1, "relevance_score": 0.31}
  ]
}
```
