# Architecture Decisions

| Decision | Choix | Justification |
|----------|-------|---------------|
| Runtime | Java 25 LTS (25.0.1) | Virtual threads matures (JEP 491), support jusqu'en 2030 |
| Framework | Spring Boot 3.5.9 + Framework 6.2.x | Compatibilité Langchain4j, Jakarta EE 10, support OSS jusqu'en juin 2026 |
| MCP Transport | HTTP Streamable | Endpoint unique `/mcp`, recommandé depuis MCP 2025-03-26 |
| RAG Library | Langchain4j 1.10.0 | GA stable, EmbeddingStore pgvector (beta18) |
| MCP SDK | Spring AI 1.1.2 GA | @McpTool annotations, version stable pour Boot 3.x |
| Database | PostgreSQL 18.1 + pgvector 0.8.1 | Robuste, SQL standard, HNSW index |
| Embeddings | BGE-M3 via langchain4j-open-ai | 1024D, baseUrl custom vers Infinity |
| Reranker | bge-reranker-v2-m3 | Client HTTP custom (format Cohere, non-OpenAI) |
| Vector type | vector(1024) | Langchain4j natif, halfvec non supporté |
| Retry | Resilience4j 2.3.0 | @Retry annotations, compatible Virtual Threads, metrics Micrometer |
| Architecture | Flat simplifiée | YAGNI - pas d'hexagonal ni DDD |
