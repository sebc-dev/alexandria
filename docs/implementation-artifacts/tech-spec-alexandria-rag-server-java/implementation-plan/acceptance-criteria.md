# Acceptance Criteria

## AC 1: MCP Server Startup
- [ ] Given a properly configured environment, when the application starts, then the MCP server is available at `/mcp` endpoint within 30 seconds
- [ ] Given the MCP server is running, when a client connects and lists tools, then `search_documents` and `ingest_document` are returned

## AC 2: Document Ingestion (CLI)
- [ ] Given a directory with markdown files, when running `java -jar alexandria.jar ingest /path/to/docs -r`, then all .md files are chunked and stored in pgvector
- [ ] Given a file with YAML front matter, when ingested, then metadata is extracted and stored in chunk metadata
- [ ] Given a code block in markdown, when chunked, then the code block is preserved intact (not split mid-block)

## AC 3: Document Ingestion (MCP)
- [ ] Given a single file path, when calling ingest_document tool, then the document is processed with progress updates (0.1 -> 0.3 -> 0.5 -> 0.7 -> 1.0)
- [ ] Given a directory with more than 5 files, when calling ingest_document tool, then an error is returned suggesting CLI usage

## AC 4: Semantic Search
- [ ] Given indexed documents, when calling search_documents with a relevant query, then results are returned with relevance scores and source references
- [ ] Given indexed documents, when calling search_documents, then results include dual-format response (JSON + Markdown)
- [ ] Given a query matching high-confidence documents (score > 0.7), when searching, then status is SUCCESS and confidence is HIGH

## AC 5: Tiered Response
- [ ] Given a query with medium-confidence matches (0.4-0.7), when searching, then status is PARTIAL with caveat message
- [ ] Given a query with no matches above threshold, when searching, then status is NO_RESULTS with helpful message
- [ ] Given an empty database, when searching, then message indicates "Knowledge base not indexed yet"

## AC 6: Query Validation
- [ ] Given a query with less than 3 characters, when searching, then error is returned immediately without embedding call
- [ ] Given a query with only stopwords, when searching, then error indicates "query needs more specific terms"

## AC 7: Retry Resilience
- [ ] Given Infinity API returns 503, when embedding request is made, then retry occurs with exponential backoff (1s -> 2s -> 4s)
- [ ] Given Infinity API fails 3 times then succeeds, when request completes, then result is returned successfully
- [ ] Given Infinity API fails all 4 attempts, when retries exhausted, then AlexandriaException(SERVICE_UNAVAILABLE) is thrown

## AC 8: Timeout Budget
- [ ] Given a cold start scenario (first request), when search completes, then total time is under 90 seconds
- [ ] Given insufficient time budget (<5s remaining), when reranking would occur, then reranking is skipped with LOW confidence results

## AC 9: Document Updates
- [ ] Given an existing document is modified, when re-ingested, then old chunks are deleted and new chunks inserted (DELETE + INSERT)
- [ ] Given an unchanged document, when re-ingested, then no database operations occur (NO_CHANGE)
- [ ] Given a document hash change, when detected, then update is triggered regardless of filename

## AC 10: Health Checks
- [ ] Given all services healthy, when accessing /actuator/health, then status is UP with details for infinity, reranking, pgvector
- [ ] Given Infinity endpoint unavailable, when health checked, then infinity indicator is DOWN

## AC 11: Error Handling
- [ ] Given any AlexandriaException thrown, when MCP tool responds, then isError=true with category-appropriate message and suggested action
- [ ] Given unexpected RuntimeException, when caught, then generic error message returned without exposing internals

## AC 12: Observability
- [ ] Given any request, when processed, then X-Correlation-Id is present in logs (generated if not provided)
- [ ] Given retry events, when /actuator/retryevents accessed, then retry history is visible
