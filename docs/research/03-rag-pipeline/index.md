# 03 - RAG Pipeline

Research documents for the Retrieval-Augmented Generation pipeline implementation.

## Documents

| File | Description |
|------|-------------|
| [langchain4j-document-splitter.md](langchain4j-document-splitter.md) | Langchain4j DocumentSplitter capabilities |
| [langchain4j-infinity-integration.md](langchain4j-infinity-integration.md) | Langchain4j integration with Infinity server |
| [infinity-reranking-api.md](infinity-reranking-api.md) | Infinity reranking API technical guide (Cohere format) |
| [rag-pipeline-parameters.md](rag-pipeline-parameters.md) | Optimal parameters for BGE-M3 + reranking |
| [rag-no-results-handling.md](rag-no-results-handling.md) | Handling no-results scenarios |
| [markdown-splitter-edge-cases.md](markdown-splitter-edge-cases.md) | Markdown splitter edge cases |
| [rag-metadata-design.md](rag-metadata-design.md) | Optimal RAG metadata design |
| [rag-timeout-configuration.md](rag-timeout-configuration.md) | Timeout configuration for RAG pipeline |
| [runpod-cold-start-optimization.md](runpod-cold-start-optimization.md) | RunPod serverless cold start optimization |

## Key Findings

- BGE-M3 produces 1024-dimension vectors (8K token context)
- bge-reranker-v2-m3 uses Cohere format (not OpenAI-compatible)
- Top-K=50, Top-N=5 are optimal parameters
- Tiered response with HIGH/MEDIUM/LOW confidence levels
- Cold start timeouts: 30s embedding, 30s reranking
