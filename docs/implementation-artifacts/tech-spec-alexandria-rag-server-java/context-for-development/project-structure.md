# Project Structure

```
src/main/java/dev/alexandria/
├── core/
│   ├── Document.java                    # POJO simple
│   ├── DocumentChunk.java               # Chunk avec metadata
│   ├── ChunkMetadata.java               # Record metadata (8 champs)
│   ├── RetrievalService.java            # Logique métier RAG + tiered response
│   ├── IngestionService.java            # Orchestration ingestion
│   ├── DocumentUpdateService.java       # Detection changements + DELETE/INSERT
│   ├── QueryValidator.java              # Validation requêtes avant recherche
│   ├── McpSearchResponse.java           # Response schema avec SearchMetadata
│   ├── AlexandriaMarkdownSplitter.java  # Custom DocumentSplitter (code blocks, tables)
│   ├── LlmsTxtParser.java               # Parser llms.txt format (llmstxt.org)
│   ├── AlexandriaException.java         # Exception racine avec ErrorCategory
│   └── ErrorCategory.java               # Enum catégories d'erreurs
│
├── adapters/
│   ├── InfinityRerankClient.java        # Client HTTP pour /rerank (format Cohere)
│   ├── McpTools.java                    # @McpTool annotations Spring AI
│   └── McpResponseFormatter.java        # Dual-format (JSON + Markdown)
│
├── config/
│   ├── LangchainConfig.java             # Beans Langchain4j + Tokenizer
│   ├── McpConfig.java                   # Config MCP HTTP Streamable
│   ├── HttpClientConfig.java            # RestClient beans avec timeouts
│   ├── ResilienceConfig.java            # Resilience4j RetryRegistry
│   ├── RagProperties.java               # @ConfigurationProperties RAG pipeline
│   ├── TimeoutProperties.java           # @ConfigurationProperties timeouts
│   └── VirtualThreadConfig.java         # TaskDecorator MDC propagation
│
├── filter/
│   └── CorrelationIdFilter.java         # X-Correlation-Id + MDC
│
├── health/
│   ├── InfinityEmbeddingHealthIndicator.java  # Health check Infinity /health
│   ├── RerankingHealthIndicator.java          # Health check reranking service
│   └── PgVectorHealthIndicator.java           # Health check pgvector extension + index
│
├── cli/
│   └── IngestCommand.java               # CLI Picocli pour bulk ingestion
│
└── AlexandriaApplication.java

src/test/java/dev/alexandria/test/
├── PgVectorTestConfiguration.java       # Testcontainers pgvector
├── InfinityStubs.java                   # WireMock stubs Infinity API
├── EmbeddingFixtures.java               # Générateur vecteurs 1024D
└── McpTestSupport.java                  # Helper client MCP
```

**Principes :**
- Pas d'interfaces/ports abstraits - adapters directs
- Pas de DDD aggregates - simples POJOs
- @Retry Resilience4j sur les clients HTTP (pas de circuit breaker)
- Virtual Threads gérés par Spring Boot - pas de config manuelle
