# Codebase Patterns

- **Runtime**: Java 25 LTS (25.0.1) avec Virtual Threads (JEP 491 - no pinning)
- **Framework**: Spring Boot 3.5.9 + Spring Framework 6.2.x
- **MCP Transport**: Spring AI MCP SDK 1.1.2 GA - HTTP Streamable (`/mcp` endpoint unique)
- **RAG Pipeline**: Langchain4j 1.10.0 (embeddings, retrieval) + client HTTP custom pour reranking
- **Chunking**: Custom AlexandriaMarkdownSplitter (préservation code blocks, breadcrumbs)
- **Vector Storage**: pgvector 0.8.1 avec vector(1024) via Langchain4j EmbeddingStore
- **Embedding Model**: BGE-M3 (1024 dimensions, 8K tokens context) via langchain4j-open-ai
- **Reranker**: bge-reranker-v2-m3 via endpoint Infinity `/rerank` (format Cohere, non-OpenAI)
- **Retry**: Resilience4j 2.3.0 avec @Retry (exponential backoff 1s→2s→4s, randomizedWaitFactor=0.1)
