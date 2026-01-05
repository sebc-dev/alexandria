# Java 25 RAG server architecture validated for Spring Boot 3.5.9

Your architectural choices for a single-user MCP semantic search server are **overwhelmingly sound**. Five of six decisions are validated as production-ready; only one requires minor adjustment. The combination of flat package structure, direct implementations, RestClient with Virtual Threads, and deferred caching aligns well with modern Spring Boot patterns for small applications. The key insight: **avoid premature architectural complexity** while preserving extensibility.

---

## Package structure: flat is appropriate for your scale

**Status: Validated**

The official Spring Boot documentation shows feature-based packaging but does *not* mandate it. For a mono-user RAG server with **10-15 classes**, a flat layer-based structure reduces ceremony without sacrificing clarity.

**Recommendation:** Keep `core/`, `adapters/`, `config/` but consider renaming to more Spring-conventional terms for discoverability:

```
com.example.ragserver
├── RagServerApplication.java       # Root package, @SpringBootApplication
├── config/                         # Spring @Configuration classes
│   ├── RagProperties.java
│   └── RestClientConfig.java
├── service/                        # Business logic (@Service)
│   ├── EmbeddingService.java
│   └── SearchService.java
├── adapter/                        # External integrations
│   ├── InfinityEmbeddingClient.java
│   └── PgVectorRepository.java
├── model/                          # Domain objects and DTOs
│   ├── DocumentChunk.java
│   └── SearchResult.java
└── mcp/                            # MCP-specific handlers
    └── McpToolHandler.java
```

**Key constraint:** Place `RagServerApplication.java` in the root package to ensure component scanning works correctly. The Spring Boot documentation explicitly warns against placing classes in the default package.

**When to reconsider:** Migrate to feature-based packaging once you exceed **3+ distinct features** with **5+ classes each**. The YAGNI principle applies—don't add architectural complexity until actually needed.

---

## Direct implementations without interfaces

**Status: Validated**

Skipping abstract interfaces is acceptable in the Spring Boot ecosystem when:
- Single implementation exists (and likely will remain so)
- Small codebase with single developer
- Business logic is straightforward

**Testing implications are minimal** because modern Mockito (2.x+) mocks concrete classes effectively:

```java
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {
    @Mock
    private InfinityEmbeddingClient embeddingClient;  // Concrete class, not interface
    
    @InjectMocks
    private EmbeddingService embeddingService;
    
    @Test
    void shouldReturnEmbeddingForText() {
        when(embeddingClient.embed(any())).thenReturn(new float[]{0.1f, 0.2f});
        
        var result = embeddingService.embedQuery("test query");
        
        assertThat(result).hasSize(2);
    }
}
```

**Integration testing with @MockBean also works:**

```java
@SpringBootTest
class SearchServiceIntegrationTest {
    @Autowired
    private SearchService searchService;
    
    @MockBean
    private PgVectorRepository vectorRepository;  // Concrete class
    
    @Test
    void shouldSearchDocuments() {
        when(vectorRepository.findSimilar(any(), anyInt()))
            .thenReturn(List.of(testDocument()));
        // ...
    }
}
```

**Langchain4j pattern:** The library uses interfaces for extension points (`EmbeddingModel`, `EmbeddingStore`) but concrete classes for data objects. Your direct implementation approach mirrors this pragmatic pattern.

**Limitations to monitor:** If you later need to mock `final` methods or classes, add this to `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`:
```
mock-maker-inline
```

---

## Java records for domain objects

**Status: Validated with nuance**

Java 25 LTS records are **fully mature** (stable since Java 16, 4+ years in production). Spring Boot 3.5.x and Jackson provide excellent out-of-box support.

**Use records for:**
| Object Type | Recommendation | Rationale |
|-------------|----------------|-----------|
| API DTOs | ✅ Record | Immutable contracts, automatic serialization |
| `DocumentChunk` | ✅ Record | Immutable after splitting |
| `SearchResult` | ✅ Record | Immutable query result |
| `EmbeddingResult` | ✅ Record | Immutable value object |
| Configuration | ✅ Record | `@ConfigurationProperties` support |

**Follow Langchain4j patterns for:**
| Object Type | Recommendation | Rationale |
|-------------|----------------|-----------|
| `TextSegment` | Use Langchain4j class | Library interop |
| `Document` | Use Langchain4j interface | Library interop |
| `Metadata` | Use Langchain4j class | Mutable by design |

**Practical examples:**

```java
// API DTOs - use records
public record RagQueryRequest(
    @NotBlank String query,
    @Min(1) @Max(10) int topK,
    @Nullable Double minScore
) {}

public record RagQueryResponse(
    String answer,
    List<SourceDocument> sources,
    long processingTimeMs
) {}

public record SourceDocument(
    String id,
    String content,
    double score,
    Map<String, String> metadata
) {}

// Internal value objects - use records
public record DocumentChunk(
    String documentId,
    int index,
    String content,
    int startOffset,
    int endOffset
) {}

// Configuration - records work well
@ConfigurationProperties(prefix = "rag.embedding")
public record EmbeddingProperties(
    @NotBlank String baseUrl,
    @NotBlank String model,
    @Min(1) int dimensions
) {}
```

**Critical constraint:** Records **cannot** be JPA entities (no default constructor, immutable fields). Use records as DTO projections in repositories:

```java
public record ChunkSummary(String id, String content, double score) {}

@Repository
public interface ChunkRepository extends JpaRepository<ChunkEntity, String> {
    @Query("""
        SELECT new com.example.ChunkSummary(c.id, c.content, c.score)
        FROM ChunkEntity c WHERE c.documentId = :docId
        """)
    List<ChunkSummary> findSummariesByDocument(@Param("docId") String documentId);
}
```

**Jackson configuration for Spring Boot 3.5.x** (optional, defaults work):
```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null
```

---

## Retry strategy: Langchain4j built-in + spring-retry

**Status: Validated**

**Key finding:** Langchain4j 1.10.0 includes **built-in retry mechanisms** via `maxRetries` parameter. Use this for embedding/LLM calls, and add spring-retry only for non-Langchain4j HTTP calls.

**Important context:** Spring Retry is now in **maintenance mode** (superseded by Spring Framework 7's native resilience features coming November 2025). For a new project targeting longevity, this is acceptable since Spring Framework 7 migration will provide a clean upgrade path.

**Architecture recommendation:**

```
┌─────────────────────────────────────────────────────────────┐
│  Langchain4j EmbeddingModel                                  │
│  └─ Built-in maxRetries=3 for Infinity embedding calls       │
├─────────────────────────────────────────────────────────────┤
│  Custom RestClient calls (if any beyond Langchain4j)         │
│  └─ spring-retry @Retryable for exponential backoff          │
└─────────────────────────────────────────────────────────────┘
```

**For Infinity embedding endpoint (OpenAI-compatible):**

```java
@Bean
public EmbeddingModel embeddingModel(EmbeddingProperties props) {
    return OpenAiEmbeddingModel.builder()
        .baseUrl(props.baseUrl())  // Infinity endpoint
        .modelName(props.model())  // BGE-M3
        .apiKey("not-needed")      // Self-hosted
        .maxRetries(3)             // Built-in retry!
        .timeout(Duration.ofSeconds(30))
        .build();
}
```

**For custom reranking calls (if not using Langchain4j ScoringModel):**

```java
@Configuration
@EnableRetry
public class RetryConfig {}

@Service
public class InfinityRerankClient {
    
    @Retryable(
        retryFor = {RestClientException.class, SocketTimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2, maxDelay = 5000)
    )
    public RerankResponse rerank(String query, List<String> documents) {
        return restClient.post()
            .uri("/rerank")
            .body(new RerankRequest(query, documents))
            .retrieve()
            .body(RerankResponse.class);
    }
    
    @Recover
    public RerankResponse recoverRerank(Exception e, String query, List<String> docs) {
        log.error("Rerank failed after retries", e);
        throw new RerankServiceException("Infinity rerank unavailable", e);
    }
}
```

**Dependencies (Maven):**
```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
    <version>2.0.12</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**When to choose Resilience4j instead:** If you need circuit breakers, rate limiting, or bulkheads beyond simple retry logic. For a single-user RAG server, spring-retry's simplicity is sufficient.

---

## RestClient with Virtual Threads for HTTP calls

**Status: Validated**

RestClient is the **recommended HTTP client for synchronous calls** in Spring Boot 3.5.x, and Java 25's JEP 491 eliminates the synchronized-block pinning issue that previously limited Virtual Thread performance.

**Why RestClient over WebClient:**
- **Simpler code:** Synchronous, imperative API vs reactive chains
- **Same performance:** Virtual Threads provide equivalent concurrency to reactive model
- **Better debugging:** Clearer stack traces
- **No extra dependencies:** WebClient requires spring-webflux

**Configuration for Infinity endpoints:**

```java
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient embeddingRestClient(RestClient.Builder builder,
                                          EmbeddingProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        
        JdkClientHttpRequestFactory factory = 
            new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        
        return builder
            .baseUrl(props.baseUrl())
            .requestFactory(factory)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
    
    @Bean
    public RestClient rerankRestClient(RestClient.Builder builder,
                                       RerankProperties props) {
        JdkClientHttpRequestFactory factory = 
            new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));
        
        return builder
            .baseUrl(props.baseUrl())
            .requestFactory(factory)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
```

**Enable Virtual Threads:**
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Use JdkClientHttpRequestFactory** for optimal Virtual Thread support (native Java 21+ HttpClient). Apache HttpClient5 is an alternative if you need advanced connection pooling features.

**spring-retry integration works seamlessly** with RestClient—the `@Retryable` annotation wraps the entire method call, including the blocking HTTP request.

---

## Deferred caching strategy

**Status: Validated**

For a **single-user application**, deferring caching is acceptable. However, design for caching from the start by using `@Cacheable` annotations—they're no-ops until you configure a cache provider.

**Caching priority when you add it later:**

| Priority | Cache Point | Value | Implementation |
|----------|-------------|-------|----------------|
| **1** | Document embeddings | Essential | Already handled by pgvector storage |
| **2** | Query embeddings | High | 60-500ms latency savings per call |
| **3** | Rerank results | Medium | Reduces Infinity calls for repeated queries |
| **4** | LLM responses | Low | Only if using generation; high cost savings |

**Design for future caching now:**

```java
@Service
public class EmbeddingService {
    
    // When ready to cache, just add @Cacheable and configure Caffeine
    // @Cacheable(value = "queryEmbeddings", key = "#query.hashCode()")
    public Embedding embedQuery(String query) {
        return embeddingModel.embed(query).content();
    }
}
```

**Minimal Caffeine setup when ready:**

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            "queryEmbeddings", "rerankResults"
        );
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofHours(1))
            .recordStats());
        return manager;
    }
}
```

**pgvector optimization is more impactful than caching** for your setup. Ensure proper HNSW indexing:

```sql
CREATE INDEX ON document_chunks 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);
```

---

## Anti-patterns to avoid

**1. Over-engineering for single-user scale**
Don't add hexagonal architecture, event sourcing, or CQRS for a 10-15 class project. Your flat structure is appropriate.

**2. Mixing reactive and imperative**
Since you're using RestClient + Virtual Threads, don't add WebClient "just in case." This creates cognitive overhead and debugging complexity.

**3. Premature abstraction**
Avoid creating interfaces "for testability" when Mockito handles concrete classes. Add interfaces only when you have actual multiple implementations.

**4. Ignoring Langchain4j's built-in features**
Don't wrap Langchain4j models with additional retry logic—use the built-in `maxRetries` parameter.

**5. Caching without measurement**
When you add caching, instrument with Micrometer metrics first. Caffeine's `recordStats()` provides hit/miss ratios.

---

## Coherence assessment

Your architecture choices form a **coherent, modern stack** for Spring Boot 3.5.9 and Java 25:

| Choice | Ecosystem Fit | Coherence |
|--------|--------------|-----------|
| Flat packages | Appropriate for scale | ✅ |
| Direct implementations | Matches Langchain4j patterns | ✅ |
| Java records | Full Spring Boot 3.5.x support | ✅ |
| spring-retry | Compatible, maintenance-mode acceptable | ✅ |
| RestClient + Virtual Threads | Recommended pattern for 2025 | ✅ |
| Deferred caching | Acceptable for single-user | ✅ |

**Accepted tradeoffs:**
- Flat structure limits scalability → acceptable for mono-user
- No interfaces reduces flexibility → acceptable for single implementation
- spring-retry maintenance mode → Spring Framework 7 provides migration path
- No caching initially → pgvector HNSW indexing provides adequate performance

---

## Version compatibility matrix

| Component | Your Version | Status |
|-----------|--------------|--------|
| Java | 25 LTS | ✅ Fully supported |
| Spring Boot | 3.5.9 | ✅ Latest stable |
| Langchain4j | 1.10.0 | ✅ Current release |
| PostgreSQL | 18.1 | ✅ Compatible |
| pgvector | 0.8.1 | ✅ HNSW support |
| Spring AI MCP SDK | 1.1.2 | ✅ SSE transport ready |
| spring-retry | 2.0.12 | ⚠️ Maintenance mode |

**Overall verdict:** Your architectural choices are well-suited for a single-user MCP RAG server. Proceed with confidence, monitoring for the Spring Framework 7 release (November 2025) for a potential migration of retry logic to native framework features.