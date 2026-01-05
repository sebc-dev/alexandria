# Handling "no results" in RAG systems: A practical implementation guide

Retrieval-Augmented Generation systems fail gracefully when they distinguish between *empty knowledge*, *semantic distance*, *quality filtering*, and *query problems*—returning different responses for each scenario rather than generic errors. For your Java stack with Langchain4j, pgvector, and BGE-reranker-v2-m3, this guide provides specific thresholds, message templates, and fallback strategies proven in production RAG deployments.

## The four failure scenarios require distinct responses

Each no-results scenario signals something different to users and requires tailored handling:

| Scenario | Technical Cause | User-Facing Message | Internal Action |
|----------|-----------------|---------------------|-----------------|
| **Empty database** | Document count = 0 | "The knowledge base hasn't been indexed yet." | Return system status, suggest contacting admin |
| **No vector matches** | All cosine similarities < 0.5 | "No documents match your query. Try rephrasing with different keywords." | Trigger keyword fallback, log for corpus gap analysis |
| **Reranker filters all** | Reranker scores < threshold despite vector hits | "I found related content but nothing directly relevant to your question." | Offer to show loosely related results |
| **Malformed query** | < 3 chars or only stopwords | "Please provide a more detailed question." | Return immediately without search |

The critical insight from production deployments: **never return nothing without explanation**. LlamaIndex's infamous "Empty Response" string demonstrates what happens when frameworks don't differentiate failure modes—users receive zero actionable information.

## Threshold values for your two-stage pipeline

Your architecture—vector search producing candidates, then BGE-reranker-v2-m3 filtering—requires calibrated thresholds at both stages.

### pgvector cosine similarity thresholds

Since pgvector returns **cosine distance** (lower = more similar), convert to similarity via `1 - distance`:

```java
// Langchain4j EmbeddingSearchRequest configuration
EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(embedding)
    .maxResults(30)        // Generous first-stage retrieval
    .minScore(0.55)        // Conservative vector threshold
    .build();
```

Production-tested thresholds for first-stage vector search:
- **0.55–0.65**: Recommended for retrieval candidates (high recall, let reranker filter)
- **0.70–0.75**: Standard for direct retrieval without reranking
- **0.80+**: Near-duplicate detection only

### BGE-reranker-v2-m3 score interpretation

**Critical implementation detail**: BGE rerankers output raw logits that can be negative. You must normalize with sigmoid for interpretable 0–1 scores:

```java
// Raw scores from BGE-reranker-v2-m3 examples:
// Irrelevant pair: -5.65 → normalized: 0.0035
// Highly relevant: +5.26 → normalized: 0.9948

// When using Infinity server, request normalized scores
// or apply sigmoid: 1 / (1 + Math.exp(-rawScore))
```

Normalized threshold recommendations for reranking:
- **< 0.3**: Filter completely—likely irrelevant
- **0.3–0.5**: Show only if nothing better exists, with caveat
- **0.5–0.8**: Show normally as "related content"
- **> 0.8**: High confidence, show prominently

## Decision framework: show results with warnings or filter entirely?

Research from Baymard Institute shows **50% of e-commerce sites fail no-results UX**—the primary mistake being dead-ends without alternatives. For RAG systems serving developer tools like Claude Code, the stakes are higher: wrong information is worse than no information.

**Recommended tiered approach:**

```java
public RetrievalResult handleResults(List<ScoredDoc> reranked, List<ScoredDoc> vectorRaw) {
    double STRICT = 0.7, MODERATE = 0.4, MINIMUM = 0.2;
    
    var strict = filter(reranked, STRICT);
    if (strict.size() >= 2) {
        return new RetrievalResult(strict, Confidence.HIGH, 
            "Found relevant documentation:");
    }
    
    var moderate = filter(reranked, MODERATE);
    if (!moderate.isEmpty()) {
        return new RetrievalResult(moderate, Confidence.MEDIUM,
            "These results may be relevant, but I'm not fully confident:");
    }
    
    // Fallback: return raw vector results if reranker was too aggressive
    if (vectorRaw.get(0).score() > 0.6) {
        return new RetrievalResult(vectorRaw.subList(0, 3), Confidence.LOW,
            "I found some related content, though it may not directly answer your question:");
    }
    
    return RetrievalResult.empty("I couldn't find relevant information in the knowledge base.");
}
```

The key insight: **raw vector search results serve as a fallback** when the reranker filters everything. If vector similarity was reasonable (> 0.6), the content is semantically related even if the reranker—which optimizes for query-passage relevance—doesn't score it highly.

## Query analysis prevents wasted computation

Pre-validation catches problematic queries before they hit your vector database:

```java
public class QueryValidator {
    private static final int MIN_CHARS = 3;
    private static final int MIN_MEANINGFUL_TOKENS = 2;
    private static final Set<String> STOPWORDS = Set.of("the", "a", "an", "is", "it", "to", "of");
    
    public ValidationResult validate(String query) {
        if (query == null || query.trim().length() < MIN_CHARS) {
            return ValidationResult.invalid(QueryProblem.TOO_SHORT,
                "Please provide at least 3 characters.");
        }
        
        String[] tokens = query.toLowerCase().split("\\s+");
        long meaningfulTokens = Arrays.stream(tokens)
            .filter(t -> t.length() > 2 && !STOPWORDS.contains(t))
            .count();
            
        if (meaningfulTokens < MIN_MEANINGFUL_TOKENS) {
            return ValidationResult.invalid(QueryProblem.TOO_VAGUE,
                "Your query needs more specific terms. Try adding keywords.");
        }
        
        return ValidationResult.valid();
    }
}
```

For ambiguous queries, consider **query expansion** using an LLM to generate a hypothetical answer document (HyDE technique), then searching with that embedding—this significantly improves recall for vague questions.

## Fallback strategy: hybrid search when semantic search fails

Academic research on RAG failure modes (Barnett et al., "Seven Failure Points When Engineering RAG Systems") identifies **FP2: Missed Top-Ranked Documents** as a common issue—the answer exists but semantic search misses it. Hybrid search combining vector and keyword (BM25) approaches provides resilience.

```sql
-- PostgreSQL hybrid search with Reciprocal Rank Fusion
WITH vector_results AS (
    SELECT id, content, 
           ROW_NUMBER() OVER (ORDER BY embedding <=> $1) as vrank
    FROM documents LIMIT 30
),
keyword_results AS (
    SELECT id, content,
           ROW_NUMBER() OVER (ORDER BY ts_rank(content_tsv, plainto_tsquery($2)) DESC) as krank
    FROM documents 
    WHERE content_tsv @@ plainto_tsquery($2) LIMIT 30
)
SELECT COALESCE(v.id, k.id) as id,
       COALESCE(v.content, k.content) as content,
       1.0/(60 + COALESCE(vrank, 1000)) + 1.0/(60 + COALESCE(krank, 1000)) as rrf_score
FROM vector_results v
FULL OUTER JOIN keyword_results k ON v.id = k.id
ORDER BY rrf_score DESC LIMIT 10;
```

**When to trigger keyword fallback:**
- Vector search returns fewer than 3 results above minimum threshold
- Query contains technical identifiers, error codes, or exact strings
- User explicitly requests "exact match" or uses quotes

## Response schema for MCP server integration

Your MCP server should return structured responses that let Claude Code distinguish between scenarios:

```java
public record McpSearchResponse(
    List<SearchResult> results,
    SearchMetadata metadata
) {
    public record SearchResult(
        String content,
        String source,
        double relevanceScore,
        RelevanceLevel confidence  // HIGH, MEDIUM, LOW
    ) {}
    
    public record SearchMetadata(
        SearchStatus status,        // SUCCESS, PARTIAL, NO_RESULTS, ERROR
        String message,             // User-facing explanation
        int totalCandidates,        // Pre-filter count
        int returnedResults,        // Post-filter count
        boolean fallbackUsed,       // Whether keyword fallback triggered
        List<String> suggestions    // Query reformulation suggestions
    ) {}
}

// Example responses:
// Successful retrieval
{ "status": "SUCCESS", "message": null, "totalCandidates": 45, "returnedResults": 5 }

// Partial/low confidence
{ "status": "PARTIAL", "message": "Results may be loosely related to your query", 
  "totalCandidates": 45, "returnedResults": 3 }

// No results  
{ "status": "NO_RESULTS", "message": "No relevant documentation found. Try rephrasing.",
  "suggestions": ["authentication setup", "API key configuration"] }

// System error (clearly differentiated)
{ "status": "ERROR", "message": "Search service temporarily unavailable. Please retry.",
  "errorCode": "VECTOR_DB_TIMEOUT" }
```

## Differentiating "no content found" from "system error"

User-facing messages must clearly distinguish these scenarios:

| Scenario | Status Code | User Message | Log/Debug Info |
|----------|-------------|--------------|----------------|
| Valid query, no matches | 200 OK | "No relevant results found" | Log query for corpus gap analysis |
| Below threshold | 200 OK | "Found related but low-confidence results" | Include scores in metadata |
| Empty database | 200 OK | "Knowledge base not yet indexed" | Alert admin, check index status |
| Invalid query | 400 Bad Request | "Query too short/vague" | No search attempted |
| Vector DB timeout | 503 Service Unavailable | "Search temporarily unavailable" | Full stack trace, trigger fallback |
| Reranker failure | 500/503 | "Search error—please retry" | Degrade to vector-only |

**Implementation pattern:**

```java
public McpSearchResponse search(String query) {
    try {
        var validation = queryValidator.validate(query);
        if (!validation.isValid()) {
            return McpSearchResponse.invalidQuery(validation.message());
        }
        
        if (embeddingStore.count() == 0) {
            return McpSearchResponse.emptyDatabase(
                "The knowledge base is empty. Documents need to be indexed first.");
        }
        
        var results = performSearch(query);
        // ... normal flow
        
    } catch (VectorDbException e) {
        log.error("Vector DB failure", e);
        return McpSearchResponse.systemError(
            "Search temporarily unavailable. Please try again.",
            "VECTOR_DB_ERROR");
    } catch (RerankerException e) {
        log.warn("Reranker failed, falling back to vector-only", e);
        return performVectorOnlySearch(query);  // Graceful degradation
    }
}
```

## Query suggestion strategies

When returning empty results, suggest reformulations based on:

1. **Synonym expansion**: Use embedding similarity to find related terms in your corpus
2. **Category suggestions**: If you have document categories, suggest browsing related topics
3. **Query simplification**: Remove specific terms and suggest broader searches

```java
public List<String> generateSuggestions(String failedQuery, List<String> corpusTopics) {
    // Find semantically similar topics from your indexed content
    var queryEmbedding = embeddingModel.embed(failedQuery).content();
    
    return corpusTopics.stream()
        .map(topic -> new Scored<>(topic, 
            cosineSimilarity(queryEmbedding, embeddingModel.embed(topic).content())))
        .filter(s -> s.score() > 0.4 && s.score() < 0.9)  // Related but not identical
        .sorted(Comparator.comparingDouble(Scored::score).reversed())
        .limit(3)
        .map(Scored::item)
        .toList();
}
```

## Conclusion

Effective no-results handling in RAG requires **scenario-specific responses**, not generic errors. Your Langchain4j + pgvector + BGE-reranker stack should implement tiered thresholds (0.55 for vector retrieval, 0.5–0.8 for reranking), structured response metadata that distinguishes confidence levels, and hybrid search fallback for resilience. The key architectural insight: let your reranker be aggressive (it optimizes relevance), but keep raw vector results available as a fallback—semantic similarity captures relationships the reranker may miss. For MCP integration with Claude Code, return explicit status codes and messages that enable the consuming agent to explain *why* no results were found, not just *that* they weren't found.