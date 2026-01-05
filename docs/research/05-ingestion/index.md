# 05 - Ingestion

Research documents for document ingestion strategies.

## Documents

| File | Description |
|------|-------------|
| [llms-txt-specification.md](llms-txt-specification.md) | llms.txt standard specification (llmstxt.org) |
| [llms-txt-java-implementation.md](llms-txt-java-implementation.md) | llms.txt Java parser implementation |
| [document-formats.md](document-formats.md) | Supported document formats for Alexandria |
| [ingestion-strategies.md](ingestion-strategies.md) | Ingestion strategies for MCP RAG server |
| [document-update-strategies.md](document-update-strategies.md) | Document update strategies with pgvector |

## Key Findings

- Hybrid CLI (Picocli) + MCP tool architecture
- Supported formats: Markdown, Text, llms.txt, HTML
- Custom AlexandriaMarkdownSplitter for markdown-aware chunking
- Hybrid identification: sourceUri (path) + documentHash (SHA-256)
- DELETE + INSERT transactional pattern for updates
