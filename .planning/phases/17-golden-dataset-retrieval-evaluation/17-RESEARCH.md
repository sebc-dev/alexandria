# Phase 17: Golden Dataset & Retrieval Evaluation - Research

**Researched:** 2026-01-25
**Domain:** Retrieval evaluation metrics with golden dataset in pure Java
**Confidence:** HIGH

## Summary

This phase implements golden dataset infrastructure and retrieval evaluation metrics for the Alexandria RAG system. The evaluation system requires a JSONL-formatted golden dataset containing queries with expected document IDs, and pure Java implementations of standard information retrieval metrics: Precision@k, Recall@k, MRR (Mean Reciprocal Rank), and NDCG@k (Normalized Discounted Cumulative Gain).

The research confirms that retrieval evaluation metrics are straightforward mathematical formulas that should be implemented directly in Java rather than importing external libraries. Jackson already in the project handles JSONL parsing efficiently via line-by-line streaming. The existing CLI infrastructure (Spring Shell 3.4.1) provides the foundation for an `evaluate` command that outputs formatted reports.

The golden dataset schema must include: `query`, `expected_doc_ids`, `requires_kg` (boolean for knowledge graph), `reasoning_hops` (int for multi-hop queries), and `question_type` (enum: factual, multi-hop, graph_traversal). This enables segmented evaluation as required by RETR-06.

**Primary recommendation:** Implement metrics as pure Java static utility methods in core package, use Jackson ObjectMapper for JSONL line-by-line parsing, add Spring Shell `evaluate` command that outputs console table and optional JSON report.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jackson databind | (Spring Boot managed) | JSONL parsing | Already in project, line-by-line streaming native |
| Spring Shell | 3.4.1 | CLI `evaluate` command | Already in project for CLI commands |
| JUnit 5 | (Spring Boot managed) | Metric unit testing | Already in project for tests |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AssertJ | (Spring Boot managed) | Fluent assertions | Already in project for test assertions |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Pure Java metrics | External library (ragevalinjava) | External dep adds complexity; metrics are simple formulas |
| JSONL | CSV | JSONL handles nested fields (expected_doc_ids array) cleanly |
| Spring Shell table | Plain text | Shell table provides consistent formatting |

**Installation:**
```xml
<!-- No new dependencies needed - Jackson and Spring Shell already present -->
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/fr/kalifazzia/alexandria/
  core/
    evaluation/
      GoldenQuery.java           # Record for JSONL line: query, expected_doc_ids, requires_kg, reasoning_hops, question_type
      QuestionType.java          # Enum: FACTUAL, MULTI_HOP, GRAPH_TRAVERSAL
      EvaluationResult.java      # Record: precision@k, recall@k, mrr, ndcg values per query
      EvaluationReport.java      # Record: aggregated metrics, breakdown by question type
      RetrievalMetrics.java      # Static utility: precisionAtK, recallAtK, mrr, ndcg calculations
      EvaluationService.java     # Orchestrates: load dataset, run searches, compute metrics
    port/
      GoldenDatasetLoader.java   # Port interface for loading golden dataset
  api/
    cli/
      EvaluationCommands.java    # Spring Shell command: evaluate --dataset path --output json|table
  infra/
    evaluation/
      JsonlGoldenDatasetLoader.java  # Jackson JSONL implementation

src/test/resources/
  evaluation/
    golden-dataset.jsonl         # Sample golden dataset for testing
```

### Pattern 1: Pure Java Metric Calculation
**What:** Implement metrics as static methods without external dependencies
**When to use:** For all retrieval metrics (Precision, Recall, MRR, NDCG)
**Example:**
```java
// Source: https://www.pinecone.io/learn/offline-evaluation/
public final class RetrievalMetrics {

    private RetrievalMetrics() {} // Static utility

    /**
     * Precision@K = |relevant in top K| / K
     */
    public static double precisionAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        if (k <= 0) return 0.0;
        int limit = Math.min(k, retrieved.size());
        long relevantInTopK = retrieved.stream()
            .limit(limit)
            .filter(relevant::contains)
            .count();
        return (double) relevantInTopK / k;
    }

    /**
     * Recall@K = |relevant in top K| / |all relevant|
     */
    public static double recallAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        if (relevant.isEmpty()) return 0.0;
        int limit = Math.min(k, retrieved.size());
        long relevantInTopK = retrieved.stream()
            .limit(limit)
            .filter(relevant::contains)
            .count();
        return (double) relevantInTopK / relevant.size();
    }

    /**
     * MRR = 1 / rank of first relevant result (0 if none found)
     */
    public static double reciprocalRank(List<UUID> retrieved, Set<UUID> relevant) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * NDCG@K = DCG@K / IDCG@K
     * Uses binary relevance (rel=1 if relevant, 0 otherwise)
     */
    public static double ndcgAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        int limit = Math.min(k, retrieved.size());
        double dcg = 0.0;
        int relevantCount = 0;

        for (int i = 0; i < limit; i++) {
            if (relevant.contains(retrieved.get(i))) {
                // DCG: rel_i / log2(i + 2) -- position 0 uses log2(2)=1
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
                relevantCount++;
            }
        }

        if (relevantCount == 0) return 0.0;

        // IDCG: perfect ranking (all relevant first)
        int idealLimit = Math.min(k, relevant.size());
        double idcg = 0.0;
        for (int i = 0; i < idealLimit; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        return dcg / idcg;
    }
}
```

### Pattern 2: JSONL Line-by-Line Parsing with Jackson
**What:** Read JSONL file line by line, parse each line as JSON object
**When to use:** Loading golden dataset from JSONL file
**Example:**
```java
// Source: https://cowtowncoder.medium.com/line-delimited-json-with-jackson-69c9e4cb6c00
public class JsonlGoldenDatasetLoader implements GoldenDatasetLoader {

    private final ObjectMapper objectMapper;

    public JsonlGoldenDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GoldenQuery> load(Path datasetPath) throws IOException {
        List<GoldenQuery> queries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(datasetPath)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue; // Skip empty lines
                try {
                    GoldenQuery query = objectMapper.readValue(line, GoldenQuery.class);
                    queries.add(query);
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException(
                        "Invalid JSON at line " + lineNumber + ": " + e.getMessage(), e);
                }
            }
        }
        return queries;
    }
}
```

### Pattern 3: Golden Dataset Record Schema
**What:** Java record matching JSONL schema with all required fields
**When to use:** Deserializing golden dataset entries
**Example:**
```java
// Source: Requirements RETR-05
public record GoldenQuery(
    String query,
    List<UUID> expectedDocIds,  // Maps to expected_doc_ids in JSONL
    boolean requiresKg,          // Maps to requires_kg
    int reasoningHops,           // Maps to reasoning_hops
    QuestionType questionType    // Maps to question_type
) {
    public GoldenQuery {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(expectedDocIds, "expectedDocIds cannot be null");
        Objects.requireNonNull(questionType, "questionType cannot be null");
        if (expectedDocIds.isEmpty()) {
            throw new IllegalArgumentException("expectedDocIds cannot be empty");
        }
        if (reasoningHops < 0) {
            throw new IllegalArgumentException("reasoningHops cannot be negative");
        }
        expectedDocIds = List.copyOf(expectedDocIds);
    }
}

public enum QuestionType {
    FACTUAL,
    MULTI_HOP,
    GRAPH_TRAVERSAL
}
```

### Pattern 4: Evaluation Report with Breakdown by Question Type
**What:** Aggregate metrics overall and segmented by question type
**When to use:** Final evaluation output
**Example:**
```java
// Source: Requirements RETR-06, RETR-07
public record EvaluationReport(
    OverallMetrics overall,
    Map<QuestionType, OverallMetrics> byQuestionType,
    List<QueryEvaluation> details,
    Instant evaluatedAt
) {
    public record OverallMetrics(
        double precisionAt5,
        double precisionAt10,
        double recallAt10,
        double recallAt20,
        double mrr,
        double ndcgAt10,
        int queryCount,
        boolean passed  // Overall pass/fail based on thresholds
    ) {}

    public record QueryEvaluation(
        String query,
        QuestionType questionType,
        double precisionAt5,
        double recallAt10,
        double mrr,
        double ndcgAt10,
        List<UUID> retrievedDocIds,
        List<UUID> expectedDocIds
    ) {}
}
```

### Anti-Patterns to Avoid
- **External metric libraries:** Metrics are simple formulas; adding dependencies adds complexity without benefit
- **Loading entire file into memory:** Use line-by-line streaming for large datasets
- **Hard-coded k values:** Make k configurable; use defaults from requirements (5, 10, 20)
- **Missing validation:** Validate JSONL schema on load; fail fast on malformed data
- **Blocking evaluation on empty dataset:** Handle empty dataset gracefully

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON parsing | Custom parser | Jackson ObjectMapper | Already in project, handles edge cases |
| CLI output formatting | Custom printf | Spring Shell TableBuilder | Consistent table formatting |
| UUID parsing | Custom regex | UUID.fromString | Standard library handles validation |
| File reading | FileInputStream | Files.newBufferedReader | Handles encoding, buffering |
| Log2 calculation | Custom implementation | Math.log(x) / Math.log(2) | Standard formula |

**Key insight:** The metrics themselves ARE the domain logic - implement them directly. The infrastructure (parsing, CLI, file I/O) should use existing libraries.

## Common Pitfalls

### Pitfall 1: Division by Zero in Metrics
**What goes wrong:** NaN or ArithmeticException when dividing by zero
**Why it happens:** Empty relevant set, k=0, or no results retrieved
**How to avoid:** Guard all divisions:
```java
// Always check before division
if (relevant.isEmpty()) return 0.0;
if (k <= 0) return 0.0;
double result = numerator / denominator;
return Double.isFinite(result) ? result : 0.0;
```
**Warning signs:** NaN values in output, inconsistent aggregations

### Pitfall 2: NDCG Logarithm Base Confusion
**What goes wrong:** Wrong NDCG values due to incorrect log base
**Why it happens:** Using natural log instead of log base 2
**How to avoid:** Always use `Math.log(x) / Math.log(2)` for log2
**Warning signs:** NDCG values that don't match expected benchmarks

### Pitfall 3: Off-by-One in Ranking Positions
**What goes wrong:** MRR and NDCG calculated incorrectly
**Why it happens:** Confusing 0-indexed vs 1-indexed positions
**How to avoid:**
- MRR: rank is 1-indexed, so first position is rank 1 (return 1/(i+1) for 0-indexed i)
- NDCG: use log2(position+1) where position is 1-indexed, so log2(i+2) for 0-indexed i
**Warning signs:** MRR values > 1, NDCG discounts not applied correctly

### Pitfall 4: JSONL Field Name Mismatch
**What goes wrong:** Jackson deserialization fails silently or throws exception
**Why it happens:** Java camelCase vs JSONL snake_case field names
**How to avoid:** Use `@JsonProperty` annotations:
```java
public record GoldenQuery(
    String query,
    @JsonProperty("expected_doc_ids") List<UUID> expectedDocIds,
    @JsonProperty("requires_kg") boolean requiresKg,
    @JsonProperty("reasoning_hops") int reasoningHops,
    @JsonProperty("question_type") QuestionType questionType
) {}
```
**Warning signs:** Null values after parsing, missing fields

### Pitfall 5: Search Results Return Chunk IDs, Not Document IDs
**What goes wrong:** Metrics compare chunk IDs with expected document IDs
**Why it happens:** SearchResult contains both childChunkId and documentId
**How to avoid:** Extract documentId from SearchResult, not childChunkId:
```java
List<UUID> retrievedDocIds = searchResults.stream()
    .map(SearchResult::documentId)
    .distinct()  // Deduplicate - multiple chunks from same doc
    .toList();
```
**Warning signs:** Zero precision/recall despite visible relevant results

### Pitfall 6: Duplicate Documents in Retrieved List
**What goes wrong:** Same document counted multiple times, inflating metrics
**Why it happens:** Multiple chunks from same document in search results
**How to avoid:** Deduplicate by document ID before metric calculation
**Warning signs:** Precision > 1.0 (impossible), inflated recall

## Code Examples

Verified patterns from official sources:

### JSONL Golden Dataset Format
```jsonl
{"query": "How to configure Spring Boot?", "expected_doc_ids": ["550e8400-e29b-41d4-a716-446655440000"], "requires_kg": false, "reasoning_hops": 0, "question_type": "factual"}
{"query": "What are the best practices for Java configuration?", "expected_doc_ids": ["550e8400-e29b-41d4-a716-446655440001", "550e8400-e29b-41d4-a716-446655440002"], "requires_kg": false, "reasoning_hops": 1, "question_type": "multi_hop"}
{"query": "Which documents reference the architecture guide?", "expected_doc_ids": ["550e8400-e29b-41d4-a716-446655440003"], "requires_kg": true, "reasoning_hops": 2, "question_type": "graph_traversal"}
```

### Evaluation Service Orchestration
```java
// Source: Core service layer pattern from project
@Service
public class EvaluationService {

    private final SearchService searchService;
    private final GoldenDatasetLoader datasetLoader;

    public EvaluationService(SearchService searchService, GoldenDatasetLoader datasetLoader) {
        this.searchService = searchService;
        this.datasetLoader = datasetLoader;
    }

    public EvaluationReport evaluate(Path datasetPath, int maxK) {
        List<GoldenQuery> queries = datasetLoader.load(datasetPath);
        List<QueryEvaluation> evaluations = new ArrayList<>();

        for (GoldenQuery golden : queries) {
            // Execute search
            List<SearchResult> results = searchService.hybridSearch(golden.query(), maxK);

            // Extract unique document IDs in order
            List<UUID> retrievedDocIds = results.stream()
                .map(SearchResult::documentId)
                .distinct()
                .toList();

            Set<UUID> relevant = new HashSet<>(golden.expectedDocIds());

            // Calculate metrics
            QueryEvaluation eval = new QueryEvaluation(
                golden.query(),
                golden.questionType(),
                RetrievalMetrics.precisionAtK(retrievedDocIds, relevant, 5),
                RetrievalMetrics.recallAtK(retrievedDocIds, relevant, 10),
                RetrievalMetrics.reciprocalRank(retrievedDocIds, relevant),
                RetrievalMetrics.ndcgAtK(retrievedDocIds, relevant, 10),
                retrievedDocIds,
                golden.expectedDocIds()
            );
            evaluations.add(eval);
        }

        return buildReport(evaluations);
    }

    private EvaluationReport buildReport(List<QueryEvaluation> evaluations) {
        // Aggregate overall and by question type
        // ...implementation...
    }
}
```

### CLI Evaluate Command
```java
// Source: Spring Shell pattern from AlexandriaCommands.java
@Command(command = "evaluate", description = "Evaluate retrieval against golden dataset")
public String evaluate(
    @Option(longNames = "dataset", shortNames = 'd', required = true,
            description = "Path to golden dataset JSONL file")
    String datasetPath,
    @Option(longNames = "output", shortNames = 'o', defaultValue = "table",
            description = "Output format: table or json")
    String outputFormat
) {
    Path path = Path.of(datasetPath).toAbsolutePath();
    if (!Files.exists(path)) {
        throw new IllegalArgumentException("Dataset file not found: " + path);
    }

    EvaluationReport report = evaluationService.evaluate(path, 20);

    return "json".equalsIgnoreCase(outputFormat)
        ? formatAsJson(report)
        : formatAsTable(report);
}
```

### Unit Test for Metrics
```java
// Source: Test patterns from project
class RetrievalMetricsTest {

    @Test
    void precisionAtK_halfRelevant_returns50Percent() {
        List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4));
        Set<UUID> relevant = Set.of(uuid(1), uuid(3));

        double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 4);

        assertThat(precision).isCloseTo(0.5, within(0.001));
    }

    @Test
    void mrr_relevantAtRankThree_returnsOneThird() {
        List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4));
        Set<UUID> relevant = Set.of(uuid(3));

        double mrr = RetrievalMetrics.reciprocalRank(retrieved, relevant);

        assertThat(mrr).isCloseTo(0.333, within(0.001));
    }

    @Test
    void ndcgAtK_perfectRanking_returnsOne() {
        List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3));
        Set<UUID> relevant = Set.of(uuid(1), uuid(2), uuid(3));

        double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 3);

        assertThat(ndcg).isCloseTo(1.0, within(0.001));
    }

    private UUID uuid(int seed) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", seed));
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| External evaluation libraries | Pure Java implementation | Current best practice | Simpler deps, full control |
| Separate evaluation scripts | Integrated CLI command | DevOps trend | Reproducible, version-controlled |
| CSV golden datasets | JSONL format | 2023+ | Better for nested arrays, streaming |
| Client-side percentiles | Histogram percentiles (metrics) | Micrometer pattern | Accurate aggregation |

**Deprecated/outdated:**
- **External Python scripts for evaluation:** Integrate into Java for single-language toolchain
- **CSV for golden datasets:** JSONL handles arrays (expected_doc_ids) naturally

## Open Questions

Things that couldn't be fully resolved:

1. **Pass/Fail Thresholds**
   - What we know: Requirements mention "pass/fail indication" but no specific thresholds
   - What's unclear: What metric values constitute pass vs fail
   - Recommendation: Make thresholds configurable, start with reasonable defaults (e.g., Precision@5 >= 0.6, NDCG@10 >= 0.7)

2. **Golden Dataset Size**
   - What we know: Industry guidance suggests ~100 queries for meaningful evaluation
   - What's unclear: How many queries are appropriate for Alexandria's use case
   - Recommendation: Start with 20-30 queries covering all three question types; expand as needed

3. **Graph Traversal Evaluation**
   - What we know: `requires_kg=true` indicates knowledge graph should be used
   - What's unclear: How to evaluate if graph was actually used vs just retrieved correct docs
   - Recommendation: For Phase 17, evaluate retrieval output only; graph usage tracking is future work

## Sources

### Primary (HIGH confidence)
- [Pinecone: Evaluation Measures in Information Retrieval](https://www.pinecone.io/learn/offline-evaluation/) - Precision@K, Recall@K, MRR, NDCG formulas
- [Evidently AI: NDCG Metric Explained](https://www.evidentlyai.com/ranking-metrics/ndcg-metric) - DCG/IDCG calculation details
- [Weaviate: Retrieval Evaluation Metrics](https://weaviate.io/blog/retrieval-evaluation-metrics) - Metric selection guidance
- [Jackson: Line-delimited JSON](https://cowtowncoder.medium.com/line-delimited-json-with-jackson-69c9e4cb6c00) - JSONL parsing pattern

### Secondary (MEDIUM confidence)
- [GitHub: ragevalinjava](https://github.com/vishalmysore/ragevalinjava) - Java RAG evaluation reference (verified approach aligns)
- [Medium: Building Golden Datasets](https://medium.com/data-science-at-microsoft/the-path-to-a-golden-dataset-or-how-to-evaluate-your-rag-045e23d1f13f) - Dataset structure guidance
- [Shaped: Precision@K and Recall@K](https://www.shaped.ai/blog/evaluating-recommendation-systems-part-1) - Metric calculation examples

### Tertiary (LOW confidence)
- [FutureAGI: RAG Evaluation Metrics 2025](https://futureagi.com/blogs/rag-evaluation-metrics-2025) - General patterns, not verified implementation
- [Dev.to: RAG Evaluation Guide](https://dev.to/kuldeep_paul/how-to-evaluate-your-rag-system-a-complete-guide-to-metrics-methods-and-best-practices-18ne) - Overview article

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Uses existing project dependencies (Jackson, Spring Shell)
- Metric formulas: HIGH - Well-documented mathematical definitions with multiple sources
- Architecture patterns: HIGH - Follows established project patterns (ports/adapters, records)
- JSONL parsing: HIGH - Jackson native support verified
- Pass/fail thresholds: LOW - Not specified in requirements, needs configuration

**Research date:** 2026-01-25
**Valid until:** 2026-03-25 (60 days - metrics and patterns are stable)
