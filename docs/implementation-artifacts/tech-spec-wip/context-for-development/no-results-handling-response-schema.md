# No-Results Handling & Response Schema

**Principe clé:** Ne jamais retourner une réponse vide sans explication. Différencier les 4 scénarios d'échec:

| Scénario | Cause | Message utilisateur | Action |
|----------|-------|---------------------|--------|
| Empty database | `count() == 0` | "Knowledge base not indexed yet" | Status système |
| No vector matches | Tous scores < seuil | "No documents match. Try rephrasing." | Log pour analyse corpus |
| Reranker filters all | Scores rerank < seuil | "Found related but not directly relevant" | Offrir résultats loosely related |
| Malformed query | < 3 chars ou stopwords | "Please provide more details" | Retour immédiat sans recherche |

```java
package dev.alexandria.core;

/**
 * Validation des requêtes avant recherche - évite les appels inutiles.
 */
public class QueryValidator {
    private static final int MIN_CHARS = 3;
    private static final int MIN_MEANINGFUL_TOKENS = 2;
    private static final Set<String> STOPWORDS = Set.of(
        "the", "a", "an", "is", "it", "to", "of", "and", "or", "in", "on", "at"
    );

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
                "Your query needs more specific terms.");
        }

        return ValidationResult.valid();
    }

    public record ValidationResult(boolean valid, QueryProblem problem, String message) {
        public static ValidationResult valid() { return new ValidationResult(true, null, null); }
        public static ValidationResult invalid(QueryProblem p, String msg) { return new ValidationResult(false, p, msg); }
    }

    public enum QueryProblem { TOO_SHORT, TOO_VAGUE, EMPTY }
}
```

```java
package dev.alexandria.core;

/**
 * Réponse MCP structurée avec métadonnées de confiance.
 * Permet à Claude Code de différencier les scénarios d'échec.
 */
public record McpSearchResponse(
    List<SearchResult> results,
    SearchMetadata metadata
) {
    public record SearchResult(
        String content,
        String source,
        String section,           // Breadcrumb H1 > H2 > H3
        double relevanceScore,
        RelevanceLevel confidence
    ) {}

    public record SearchMetadata(
        SearchStatus status,
        String message,           // User-facing explanation (null si SUCCESS)
        int totalCandidates,      // Avant filtrage
        int returnedResults       // Après filtrage
    ) {}

    public enum SearchStatus { SUCCESS, PARTIAL, NO_RESULTS, ERROR }
    public enum RelevanceLevel { HIGH, MEDIUM, LOW }

    // Factory methods
    public static McpSearchResponse success(List<SearchResult> results, int candidates) {
        return new McpSearchResponse(results,
            new SearchMetadata(SearchStatus.SUCCESS, null, candidates, results.size()));
    }

    public static McpSearchResponse partial(List<SearchResult> results, int candidates, String caveat) {
        return new McpSearchResponse(results,
            new SearchMetadata(SearchStatus.PARTIAL, caveat, candidates, results.size()));
    }

    public static McpSearchResponse noResults(String message) {
        return new McpSearchResponse(List.of(),
            new SearchMetadata(SearchStatus.NO_RESULTS, message, 0, 0));
    }

    public static McpSearchResponse error(String message) {
        return new McpSearchResponse(List.of(),
            new SearchMetadata(SearchStatus.ERROR, message, 0, 0));
    }
}
```

```java
/**
 * Logique tiered response dans RetrievalService.
 * Seuils de confiance pour classification des résultats.
 */
public class RetrievalService {
    // Seuils de confiance (scores normalisés 0-1 après reranking)
    private static final double THRESHOLD_HIGH = 0.7;    // HIGH confidence
    private static final double THRESHOLD_MEDIUM = 0.4;  // MEDIUM confidence
    private static final double THRESHOLD_LOW = 0.2;     // LOW (fallback vector)

    private final JdbcTemplate jdbcTemplate;  // Injecté pour count()

    /**
     * Compte le nombre d'embeddings stockés.
     * Note: PgVectorEmbeddingStore n'a pas de méthode count() native,
     * on utilise donc une requête SQL directe.
     */
    private long countEmbeddings() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_embeddings", Long.class);
    }

    public McpSearchResponse search(String query) {
        // 1. Validation
        var validation = queryValidator.validate(query);
        if (!validation.valid()) {
            return McpSearchResponse.error(validation.message());
        }

        // 2. Check empty database
        if (countEmbeddings() == 0) {
            return McpSearchResponse.noResults(
                "The knowledge base is empty. Documents need to be indexed first.");
        }

        // 3. Vector search + Reranking
        var candidates = vectorSearch(query, ragProperties.getRetrieval().getTopKInitial());
        var reranked = rerank(query, candidates);

        // 4. Tiered response
        var highConfidence = filter(reranked, THRESHOLD_HIGH);
        if (highConfidence.size() >= 2) {
            return McpSearchResponse.success(toResults(highConfidence, RelevanceLevel.HIGH), candidates.size());
        }

        var mediumConfidence = filter(reranked, THRESHOLD_MEDIUM);
        if (!mediumConfidence.isEmpty()) {
            return McpSearchResponse.partial(toResults(mediumConfidence, RelevanceLevel.MEDIUM),
                candidates.size(), "Results may be loosely related to your query.");
        }

        // 5. Fallback: raw vector results if reranker too aggressive
        if (!candidates.isEmpty() && candidates.get(0).score() > 0.6) {
            var fallback = candidates.subList(0, Math.min(3, candidates.size()));
            return McpSearchResponse.partial(toResults(fallback, RelevanceLevel.LOW),
                candidates.size(), "Found related content, though it may not directly answer your question.");
        }

        return McpSearchResponse.noResults(
            "No relevant documentation found. Try rephrasing with different keywords.");
    }
}
```

**Notes:**
- `THRESHOLD_HIGH/MEDIUM/LOW` sont des constantes, pas dans `RagProperties` (rarement modifiées)
- Fallback sur vector brut si `score > 0.6` évite les faux négatifs du reranker
- Status `PARTIAL` permet à Claude de nuancer sa réponse

**Reporté à v2:**
- Hybrid search (BM25 + vector) via `ts_vector` PostgreSQL
- Query suggestions basées sur similarité corpus
