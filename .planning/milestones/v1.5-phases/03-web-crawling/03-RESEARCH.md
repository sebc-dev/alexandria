# Phase 3: Web Crawling - Research

**Researched:** 2026-02-15
**Domain:** Web crawling via Crawl4AI sidecar, HTTP client integration, sitemap parsing
**Confidence:** MEDIUM-HIGH

## Summary

Phase 3 integrates the Crawl4AI sidecar (already defined in Docker Compose as `unclecode/crawl4ai:0.8.0`) with the Spring Boot application to crawl documentation sites and produce clean Markdown. The Crawl4AI Docker image exposes a REST API on port 11235 with endpoints for single-page crawling (`POST /crawl`) and deep recursive crawling via `BFSDeepCrawlStrategy`. The response includes Markdown output with configurable boilerplate removal via `PruningContentFilter`, plus discovered internal/external links.

Two approaches are viable for recursive crawling: (A) Crawl4AI's built-in deep crawl via `BFSDeepCrawlStrategy` passed in the REST API payload, or (B) Java-side URL discovery with per-page crawling. Approach A is simpler (single API call), but has a known bug with streaming mode ([GitHub #1205](https://github.com/unclecode/crawl4ai/issues/1205)) -- non-streaming mode works correctly. Approach B gives more control over ordering and error handling. **Recommendation: Use approach A (deep crawl) for the primary implementation since non-streaming mode works, with approach B as a fallback or future enhancement.**

A critical finding is that the Crawl4AI Docker API does NOT support URL seeding/sitemap features (`AsyncUrlSeeder`, `SeedingConfig` are Python-only, tracked as missing in [GitHub issue #1452](https://github.com/unclecode/crawl4ai/issues/1452)). Sitemap.xml parsing must be implemented on the Java side. Additionally, the current `docker-compose.yml` is missing `shm_size` for the Crawl4AI service, which is required for Chromium's shared memory -- without it, JavaScript-rendered pages will crash or render incorrectly.

**Primary recommendation:** Use Crawl4AI's `POST /crawl` endpoint with `BFSDeepCrawlStrategy` (non-streaming) for recursive crawling and `PruningContentFilter` for boilerplate removal. Use Spring Boot `RestClient` for HTTP communication. Parse sitemap.xml on the Java side using JDK StAX (zero-dependency). Implement the `ContentCrawler` interface defined in the architecture doc. Fix docker-compose.yml to add `shm_size: '1g'` to the crawl4ai service.

## Standard Stack

### Core
| Library/Tool | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Crawl4AI (Docker sidecar) | 0.8.0 | Web crawling with JS rendering, Markdown conversion, boilerplate removal | Already in Docker Compose; handles Chromium headless, HTML-to-Markdown, PruningContentFilter in one tool |
| Spring Boot RestClient | (included in spring-boot-starter-web 3.5.7) | HTTP client for Crawl4AI REST API | Modern fluent API, synchronous, no extra dependency, recommended for Spring Boot 3.2+ |
| Java StAX (javax.xml.stream) | JDK 21 built-in | Sitemap.xml parsing | Zero-dependency, streaming, memory-efficient; sitemap XML is simple enough to not warrant a library |
| WireMock | 3.x | HTTP stub for Crawl4AI in unit tests | Standard for testing HTTP clients in Spring ecosystem |
| Testcontainers GenericContainer | 1.21.1 (already in project) | Integration testing with real Crawl4AI container | Already used for PostgreSQL; GenericContainer supports any Docker image |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jackson Databind | (managed by Spring Boot) | JSON serialization/deserialization for Crawl4AI API payloads | Already on classpath via spring-boot-starter-web |
| Spring Retry | (managed by Spring Boot) | `@Retryable` on CrawlerClient HTTP calls | Architecture doc prescribes retry on network calls to sidecar |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Java StAX for sitemap parsing | crawler-commons (com.github.crawler-commons:crawler-commons:1.6) | External dependency handles edge cases (gzip, malformed XML) but sitemaps are simple enough for StAX; avoids adding a dependency for a small use case |
| RestClient | WebClient | WebClient adds spring-boot-starter-webflux dependency; overkill for synchronous sidecar calls to a local container |
| Crawl4AI deep crawl (BFSDeepCrawlStrategy) | Java-side BFS link-following with single-page `/crawl` calls | More control over error handling per page, but reimplements BFS logic that Crawl4AI provides natively |
| PruningContentFilter | BM25ContentFilter | BM25 requires a user query at crawl time; PruningContentFilter works without one, better for batch doc crawling |

**Dependencies to add (build.gradle.kts):**
```kotlin
// In libs.versions.toml
wiremock = "3.13.0"
spring-retry = "2.0.11"

// In libraries section
wiremock = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }

// In build.gradle.kts dependencies
implementation("org.springframework.retry:spring-retry")
implementation("org.springframework:spring-aspects")

// In test dependencies
testImplementation(libs.wiremock)
```

No additional HTTP client dependency needed -- RestClient ships with `spring-boot-starter-web` already in the project.

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/dev/alexandria/
├── config/
│   ├── EmbeddingConfig.java           # (existing)
│   └── CrawlerConfig.java            # RestClient bean for Crawl4AI, timeout/retry config
├── crawl/
│   ├── ContentCrawler.java            # Interface (per architecture.md)
│   ├── CrawlerClient.java            # Implements ContentCrawler, calls Crawl4AI REST API
│   ├── CrawlPageResult.java          # Record: per-page result (markdown, links, success)
│   ├── SitemapParser.java            # StAX-based sitemap.xml parser, handles sitemap indexes
│   └── dto/                          # Crawl4AI REST API DTOs
│       ├── Crawl4AiRequest.java      # Request body for POST /crawl
│       └── Crawl4AiResponse.java     # Response mapping records
```

### Pattern 1: CrawlerClient (Crawl4AI REST API Client)
**What:** A Spring-managed service implementing `ContentCrawler` that calls Crawl4AI's REST API via RestClient.
**When to use:** Every crawl operation goes through this client.
**Key design points:**
- RestClient configured as a bean in `CrawlerConfig` with base URL `http://crawl4ai:11235` (Docker Compose network hostname)
- Timeouts: connect=5s, read=120s (crawling JS-rendered pages can be slow)
- `@Retryable` for transient failures (network errors, 5xx from Crawl4AI)

**Example (CrawlerConfig bean):**
```java
// Source: Spring Boot RestClient docs + architecture.md pattern
@Configuration
public class CrawlerConfig {

    @Bean
    public RestClient crawl4aiRestClient(
            @Value("${alexandria.crawl4ai.base-url:http://crawl4ai:11235}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(factory)
            .build();
    }
}
```

### Pattern 2: Deep Crawl via REST API (BFSDeepCrawlStrategy)
**What:** Use Crawl4AI's built-in `BFSDeepCrawlStrategy` via the `/crawl` endpoint for recursive site crawling in a single API call.
**When to use:** Primary method for crawling a documentation site from a root URL.
**IMPORTANT:** Use non-streaming mode only. Streaming mode with deep crawl has known bugs in the Docker API ([GitHub #1205](https://github.com/unclecode/crawl4ai/issues/1205), duplicate of #1052). Non-streaming mode returns all results.

**REST API JSON payload for deep crawl:**
```json
{
  "urls": ["https://docs.example.com"],
  "browser_config": {
    "type": "BrowserConfig",
    "params": {
      "headless": true
    }
  },
  "crawler_config": {
    "type": "CrawlerRunConfig",
    "params": {
      "cache_mode": "bypass",
      "deep_crawl_strategy": {
        "type": "BFSDeepCrawlStrategy",
        "params": {
          "max_depth": 3,
          "max_pages": 500,
          "include_external": false
        }
      },
      "markdown_generator": {
        "type": "DefaultMarkdownGenerator",
        "params": {
          "content_filter": {
            "type": "PruningContentFilter",
            "params": {
              "threshold": 0.4,
              "threshold_type": "fixed",
              "min_word_threshold": 20
            }
          }
        }
      }
    }
  }
}
```

**Source:** [GitHub discussion #838](https://github.com/unclecode/crawl4ai/discussions/838) confirms the `type`/`params` serialization format for the REST API, including deep crawl strategy.

### Pattern 3: Sitemap-First Discovery (Java-Side)
**What:** Check for sitemap.xml before deep crawl. If available, parse URLs and crawl them individually (skipping deep crawl BFS).
**When to use:** Always attempt sitemap first -- it's faster and more complete than link-following.
**Why Java-side:** Crawl4AI Docker API lacks `AsyncUrlSeeder`/`SeedingConfig` endpoints (Python-only feature, tracked in [issue #1452](https://github.com/unclecode/crawl4ai/issues/1452)).

**Approach:**
1. HTTP GET `{rootUrl}/sitemap.xml` via RestClient
2. If 200 OK, parse with StAX to extract `<loc>` URLs
3. Handle sitemap index files (`<sitemapindex>`) by recursively fetching sub-sitemaps
4. Filter URLs by domain (stay within site scope)
5. Feed discovered URLs to Crawl4AI `/crawl` endpoint as batch (no deep_crawl_strategy needed)
6. If sitemap not found (404/error), fall back to deep crawl with `BFSDeepCrawlStrategy`

### Pattern 4: CrawlResult Response Mapping
**What:** Map the Crawl4AI JSON response to Java records.
**When to use:** Every crawl response needs deserialization.

**Crawl4AI response structure (per page):**
```json
{
  "success": true,
  "url": "https://docs.example.com/page",
  "status_code": 200,
  "markdown": {
    "raw_markdown": "# Page Title\n\nContent...",
    "fit_markdown": "# Page Title\n\nFiltered content...",
    "markdown_with_citations": "...",
    "references_markdown": "..."
  },
  "links": {
    "internal": [
      {"href": "/other-page", "text": "Link text", "title": "..."}
    ],
    "external": [
      {"href": "https://external.com", "text": "...", "domain": "external.com"}
    ]
  },
  "html": "<html>...</html>",
  "cleaned_html": "...",
  "metadata": {}
}
```

**Java records:**
```java
public record CrawlPageResult(
    boolean success,
    String url,
    @JsonProperty("status_code") Integer statusCode,
    MarkdownResult markdown,
    CrawlLinks links,
    @JsonProperty("error_message") String errorMessage
) {}

public record MarkdownResult(
    @JsonProperty("raw_markdown") String rawMarkdown,
    @JsonProperty("fit_markdown") String fitMarkdown
) {}

public record CrawlLinks(
    List<CrawlLink> internal,
    List<CrawlLink> external
) {}

public record CrawlLink(String href, String text) {}
```

### Anti-Patterns to Avoid
- **Building a custom HTML-to-Markdown converter:** Crawl4AI already does this with `DefaultMarkdownGenerator`. Do not use JSoup + custom conversion logic.
- **Building custom boilerplate detection heuristics:** Crawl4AI's `PruningContentFilter` handles text density analysis, link density, and tag importance scoring.
- **Using streaming mode with deep crawl:** Known bug in Docker API causes `'async_generator' object has no attribute 'status_code'` errors. Use non-streaming mode.
- **Ignoring Crawl4AI's link extraction:** The response includes `links.internal` -- do not re-parse the HTML to find links.
- **Using WebClient for Crawl4AI calls:** RestClient is simpler, synchronous, and sufficient. No need to add webflux dependency.
- **Not normalizing URLs before deduplication:** `https://docs.example.com/guide` vs `https://docs.example.com/guide/` vs `https://docs.example.com/guide#section` are all different strings but the same page.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML to Markdown conversion | Custom JSoup + string manipulation | Crawl4AI `DefaultMarkdownGenerator` | Handles edge cases: nested lists, code blocks with language tags, tables, inline HTML, image alt text |
| Boilerplate removal (nav, footer, sidebar) | Custom CSS selector heuristics | Crawl4AI `PruningContentFilter` | Uses text density and link density analysis, not brittle selectors; threshold is tunable (0.0-1.0) |
| JavaScript rendering | Selenium/Playwright in Java | Crawl4AI Chromium headless | Already bundled in Docker image; handles SPA hydration, shadow DOM, lazy loading |
| Recursive link following (BFS/DFS) | Custom URL frontier + visited set | Crawl4AI `BFSDeepCrawlStrategy` | Handles deduplication, depth tracking, page limits, URL filtering; single API call |
| HTTP/HTTPS fetching with JS | Java HttpClient + browser | Crawl4AI sidecar | Crawl4AI manages browser pool, cookie handling, user-agent, concurrent tabs |
| Sitemap XML parsing | Third-party sitemap library | JDK StAX (`javax.xml.stream`) | Sitemaps are simple XML with `<loc>` elements; StAX is zero-dependency, streaming, built into JDK |

**Key insight:** Crawl4AI is the crawling engine -- the Java side should be a thin orchestrator that sends requests and processes responses. The only Java-side parsing needed is sitemap.xml (because the Docker API lacks this feature) and JSON response deserialization.

## Common Pitfalls

### Pitfall 1: Missing `shm_size` in Docker Compose for Crawl4AI
**What goes wrong:** Chromium inside Crawl4AI crashes or renders pages incorrectly. JS-rendered pages return empty or partial content. Error messages include "session deleted because of page crash".
**Why it happens:** Docker's default shared memory (`/dev/shm`) is 64 MB. Chromium's multi-process architecture requires significantly more for rendering complex pages.
**How to avoid:** Add `shm_size: '1g'` (minimum) to the `crawl4ai` service in `docker-compose.yml`. The existing compose file does NOT have this setting.
**Warning signs:** Empty `fit_markdown` responses, Crawl4AI container logs showing Chromium crash errors, works for simple pages but fails on complex ones.

### Pitfall 2: Using Streaming Mode with Deep Crawl via Docker API
**What goes wrong:** Response handler crashes with `'async_generator' object has no attribute 'status_code'` or `'async_generator' object is not iterable` or `Object of type <class 'property'> is not JSON serializable`.
**Why it happens:** Known bug in Crawl4AI Docker API's `async_dispatcher.py`. When streaming + deep crawl is enabled, `arun()` returns an async generator instead of a result object. The code tries to access properties on this generator, causing attribute errors. ([GitHub #1205](https://github.com/unclecode/crawl4ai/issues/1205), duplicate of #1052.)
**How to avoid:** Use non-streaming mode (`stream: false` or omit from config). Non-streaming deep crawl works correctly, returning all pages.
**Warning signs:** 500 errors from `/crawl/stream` when `deep_crawl_strategy` is set.

### Pitfall 3: Crawl4AI REST API Type/Params Serialization Format
**What goes wrong:** Crawl4AI ignores configuration or returns errors. PruningContentFilter not applied, defaults used instead.
**Why it happens:** Complex nested objects in the REST API MUST use the `{"type": "ClassName", "params": {...}}` format. Dictionary values require `{"type": "dict", "value": {...}}` wrapping. This is Crawl4AI's custom serialization convention, not standard JSON.
**How to avoid:** Follow the exact serialization format from the [GitHub discussion #838](https://github.com/unclecode/crawl4ai/discussions/838). Build Java helper methods or request builder that produces the correct nested structure. Validate with Crawl4AI's `/playground` endpoint (http://localhost:11235/playground).
**Warning signs:** Configuration being ignored (default behavior instead of custom settings), `fit_markdown` identical to `raw_markdown`, content filter not applied.

### Pitfall 4: Sitemap Index Files (Sitemap of Sitemaps)
**What goes wrong:** Parser only finds a few URLs when the site has thousands of pages.
**Why it happens:** Large documentation sites (e.g., docs.spring.io) use sitemap index files (`<sitemapindex>` containing `<sitemap>` elements pointing to sub-sitemaps) rather than a single flat `<urlset>`.
**How to avoid:** Check for `<sitemapindex>` root element. If found, recursively fetch and parse each sub-sitemap. Limit recursion depth to 2 levels (per sitemap protocol spec).
**Warning signs:** Parsing returns only a handful of URLs for a site known to have many pages.

### Pitfall 5: Read Timeout for Deep Crawl of Large Sites
**What goes wrong:** `SocketTimeoutException` or `HttpTimeoutException` when deep-crawling a large documentation site.
**Why it happens:** Non-streaming deep crawl with max_pages=500 blocks until ALL pages are crawled. This can take several minutes. Default RestClient read timeout (30s) is far too short.
**How to avoid:** Set read timeout to at least 120s (or higher for very large sites). Consider using the async job endpoint (`POST /crawl/job` + poll `GET /job/{task_id}`) for sites expected to have 500+ pages.
**Warning signs:** Timeout exceptions during crawl operations, works for small sites but fails on large ones.

### Pitfall 6: Memory Pressure from Crawl4AI Container
**What goes wrong:** Crawl4AI container OOM-kills or causes host memory pressure, affecting Spring Boot app and PostgreSQL.
**Why it happens:** Crawl4AI runs Chromium with a browser pool. Deep crawl opens multiple tabs. Each rendered page consumes memory. Default `max_concurrent_pages` in Crawl4AI is 40, which is aggressive for a sidecar.
**How to avoid:** Add memory limits to docker-compose.yml (`deploy.resources.limits.memory: 4g`). Set `max_pages` limit in crawl config. The total stack memory budget is 14 GB; allocating 4 GB for Crawl4AI, 2 GB for the app, and 4-6 GB for PostgreSQL leaves headroom.
**Warning signs:** Crawl4AI container restarting, slow crawl performance, host swap usage increasing.

### Pitfall 7: URL Deduplication Failures
**What goes wrong:** Same page crawled multiple times, wasting time and producing duplicate chunks.
**Why it happens:** URLs differ in trailing slashes, fragments, query parameters, or case. `https://docs.example.com/guide` vs `https://docs.example.com/guide/` vs `https://docs.example.com/guide#section` are all different strings but the same content.
**How to avoid:** Normalize all URLs before adding to visited/discovered sets: remove fragments (`#...`), remove tracking query params, normalize trailing slashes, lowercase the host portion. Use `java.net.URI` for standard normalization.
**Warning signs:** Duplicate pages in crawl results; unexpectedly high page counts.

## Code Examples

Verified patterns from official sources:

### Complete Deep Crawl Request (Java)
```java
// Source: Crawl4AI self-hosting docs + GitHub discussion #838
public List<CrawlPageResult> deepCrawl(String rootUrl, int maxDepth, int maxPages) {
    Map<String, Object> payload = Map.of(
        "urls", List.of(rootUrl),
        "browser_config", Map.of(
            "type", "BrowserConfig",
            "params", Map.of("headless", true)
        ),
        "crawler_config", Map.of(
            "type", "CrawlerRunConfig",
            "params", Map.of(
                "cache_mode", "bypass",
                "deep_crawl_strategy", Map.of(
                    "type", "BFSDeepCrawlStrategy",
                    "params", Map.of(
                        "max_depth", maxDepth,
                        "max_pages", maxPages,
                        "include_external", false
                    )
                ),
                "markdown_generator", Map.of(
                    "type", "DefaultMarkdownGenerator",
                    "params", Map.of(
                        "content_filter", Map.of(
                            "type", "PruningContentFilter",
                            "params", Map.of(
                                "threshold", 0.4,
                                "threshold_type", "fixed",
                                "min_word_threshold", 20
                            )
                        )
                    )
                )
            )
        )
    );

    return restClient.post()
        .uri("/crawl")
        .body(payload)
        .retrieve()
        .body(new ParameterizedTypeReference<List<CrawlPageResult>>() {});
}
```

### Single Page Crawl (Java)
```java
// Source: Crawl4AI self-hosting docs
public CrawlPageResult crawlSinglePage(String url) {
    Map<String, Object> payload = Map.of(
        "urls", List.of(url),
        "browser_config", Map.of(
            "type", "BrowserConfig",
            "params", Map.of("headless", true)
        ),
        "crawler_config", Map.of(
            "type", "CrawlerRunConfig",
            "params", Map.of(
                "cache_mode", "bypass",
                "markdown_generator", Map.of(
                    "type", "DefaultMarkdownGenerator",
                    "params", Map.of(
                        "content_filter", Map.of(
                            "type", "PruningContentFilter",
                            "params", Map.of(
                                "threshold", 0.4,
                                "threshold_type", "fixed",
                                "min_word_threshold", 20
                            )
                        )
                    )
                )
            )
        )
    );

    List<CrawlPageResult> results = restClient.post()
        .uri("/crawl")
        .body(payload)
        .retrieve()
        .body(new ParameterizedTypeReference<List<CrawlPageResult>>() {});

    return results.getFirst();
}
```

### Sitemap.xml Parsing (Java StAX)
```java
// Source: JDK StAX docs + sitemaps.org protocol specification
public class SitemapParser {

    public List<String> parse(InputStream sitemapXml) {
        var urls = new ArrayList<String>();
        var factory = XMLInputFactory.newInstance();
        // Security: disable external entities to prevent XXE
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        var reader = factory.createXMLStreamReader(sitemapXml);
        boolean inLoc = false;
        var textBuilder = new StringBuilder();

        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    if ("loc".equals(reader.getLocalName())) {
                        inLoc = true;
                        textBuilder.setLength(0);
                    }
                }
                case XMLStreamConstants.CHARACTERS -> {
                    if (inLoc) textBuilder.append(reader.getText());
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("loc".equals(reader.getLocalName()) && inLoc) {
                        String url = textBuilder.toString().trim();
                        if (!url.isEmpty()) urls.add(url);
                        inLoc = false;
                    }
                }
            }
        }
        return urls;
    }

    public boolean isSitemapIndex(InputStream xml) {
        // Check root element: <sitemapindex> vs <urlset>
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        var reader = factory.createXMLStreamReader(xml);
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                return "sitemapindex".equals(reader.getLocalName());
            }
        }
        return false;
    }
}
```

### RestClient Configuration Bean
```java
// Source: Spring Framework RestClient docs
@Configuration
public class CrawlerConfig {

    @Bean
    public RestClient crawl4aiRestClient(
            @Value("${alexandria.crawl4ai.base-url:http://crawl4ai:11235}") String baseUrl,
            @Value("${alexandria.crawl4ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${alexandria.crawl4ai.read-timeout:120s}") Duration readTimeout) {

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
```

### WireMock Unit Test for CrawlerClient
```java
// Source: WireMock docs + architecture.md testing strategy
@ExtendWith(WireMockExtension.class)
class CrawlerClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private CrawlerClient client;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.builder()
            .baseUrl(wireMock.baseUrl())
            .build();
        client = new CrawlerClient(restClient);
    }

    @Test
    void crawl_returns_markdown_for_successful_page() {
        wireMock.stubFor(post(urlEqualTo("/crawl"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{
                      "success": true,
                      "url": "https://docs.example.com",
                      "status_code": 200,
                      "markdown": {
                        "raw_markdown": "# Example\\n\\nContent here",
                        "fit_markdown": "# Example\\n\\nFiltered content"
                      },
                      "links": {
                        "internal": [{"href": "/page2", "text": "Page 2"}],
                        "external": []
                      }
                    }]
                    """)));

        var result = client.crawlSinglePage("https://docs.example.com");

        assertThat(result.success()).isTrue();
        assertThat(result.markdown().fitMarkdown()).contains("# Example");
    }
}
```

### Testcontainers Integration Test with Crawl4AI
```java
// Source: Testcontainers GenericContainer docs
class CrawlerClientIT extends BaseIntegrationTest {

    static GenericContainer<?> crawl4ai = new GenericContainer<>(
            DockerImageName.parse("unclecode/crawl4ai:0.8.0"))
        .withExposedPorts(11235)
        .withCreateContainerCmdModifier(cmd ->
            cmd.getHostConfig().withShmSize(1024L * 1024L * 1024L))  // 1GB shm
        .waitingFor(Wait.forHttp("/health")
            .forPort(11235)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofMinutes(2)));

    static {
        crawl4ai.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("alexandria.crawl4ai.base-url", () ->
            "http://" + crawl4ai.getHost() + ":" + crawl4ai.getMappedPort(11235));
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Crawl4AI Python library only | Docker REST API with deep crawl | v0.7.x - v0.8.0 | Can now use Crawl4AI from any language via HTTP |
| No crash recovery for deep crawls | `resume_state` + `on_state_change` callbacks | v0.8.0 (Jan 2026) | Long-running crawls can resume from checkpoints |
| Manual link following | `BFSDeepCrawlStrategy` / `DFSDeepCrawlStrategy` | v0.7.x | Built-in recursive crawling with depth/page limits, URL filtering |
| `result.markdown` (string) | `result.markdown` (MarkdownGenerationResult object) | v0.8.x | Separate `raw_markdown`, `fit_markdown`, `markdown_with_citations` fields |
| No content filtering | `PruningContentFilter`, `BM25ContentFilter` | v0.7.x+ | Built-in boilerplate removal without custom code |
| RestTemplate (Spring) | RestClient (Spring Framework 6.1) | Spring Boot 3.2 (2023) | Modern fluent API, synchronous, no extra dependency |
| Hooks enabled by default (Docker API) | Hooks disabled by default | v0.8.0 | Security fix: prevented Remote Code Execution vulnerability |

**Deprecated/outdated:**
- `RestTemplate`: Still works but RestClient is the recommended replacement; deprecated in Spring Framework 7.0
- Crawl4AI `fit_markdown` as top-level string: Now nested inside `MarkdownGenerationResult` object
- Docker API hooks enabled by default: Disabled in v0.8.0 for security (RCE vulnerability fix)
- `file://` URLs in Crawl4AI API: Blocked in v0.8.0 (prevented arbitrary file read from server)

## Docker Compose Updates Required

The existing `docker-compose.yml` Crawl4AI service needs updates:

```yaml
crawl4ai:
  image: unclecode/crawl4ai:0.8.0
  networks:
    - alexandria
  shm_size: '1g'                    # REQUIRED: Chromium needs shared memory (default 64MB too small)
  deploy:
    resources:
      limits:
        memory: 4g                  # Prevent OOM on large crawls
      reservations:
        memory: 1g
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:11235/health"]
    interval: 10s
    timeout: 5s
    retries: 5
    start_period: 30s
```

**Changes from current docker-compose.yml:**
1. Added `shm_size: '1g'` -- without this, Chromium crashes on JS-rendered pages
2. Added memory limits/reservations -- prevents Crawl4AI from consuming all host memory during large crawls
3. Health endpoint `/health` confirmed correct (not `/monitor/health`)

## Application Configuration

```yaml
# application.yml additions
alexandria:
  crawl4ai:
    base-url: ${CRAWL4AI_URL:http://crawl4ai:11235}
    connect-timeout: 5s
    read-timeout: 120s
    max-depth: 3
    max-pages: 500
    pruning-threshold: 0.4
```

## Open Questions

1. **Deep crawl response format for multiple pages**
   - What we know: Non-streaming deep crawl returns results for all pages. The `/crawl` endpoint response includes markdown, links, and metadata per page. [GitHub discussion #838](https://github.com/unclecode/crawl4ai/discussions/838) confirms the `type`/`params` format works for deep_crawl_strategy.
   - What's unclear: Whether deep crawl returns a flat `List<CrawlResult>` or a wrapped response object. The exact JSON envelope for multi-page deep crawl results is not fully documented for the REST API.
   - Recommendation: Build an integration test against the real Crawl4AI container FIRST to verify the exact response shape before coding the full client. This is the highest-priority validation step.

2. **PruningContentFilter threshold tuning for documentation sites**
   - What we know: Threshold 0.4-0.5 with "fixed" type is the starting point. Lower values keep more content; higher values prune more aggressively. PruningContentFilter scores nodes by text density, link density, and tag importance.
   - What's unclear: Optimal threshold for technical documentation which has code blocks, tables, and structured content that might get incorrectly pruned.
   - Recommendation: Start with 0.4, make it configurable via application.yml. Validate with real documentation sites (e.g., Spring Boot docs, React docs) during integration testing. Include assertion that code blocks are preserved.

3. **Crawl4AI container startup time in CI**
   - What we know: Crawl4AI with Chromium takes 30-60s to start locally. The healthcheck `start_period` is 30s.
   - What's unclear: Whether startup is significantly slower in GitHub Actions runners.
   - Recommendation: Use `withStartupTimeout(Duration.ofMinutes(2))` in Testcontainers. If CI proves too slow, consider making Crawl4AI integration tests a separate test suite that runs less frequently.

4. **Rate limiting / politeness for crawling**
   - What we know: Crawl4AI has built-in browser pool management. `BFSDeepCrawlStrategy` does not expose explicit rate limiting parameters in the REST API.
   - What's unclear: Whether Crawl4AI applies any default delays between page requests, or if it hammers the target site aggressively.
   - Recommendation: For Phase 3, accept Crawl4AI's default behavior. Phase 7 (Crawl Operations) addresses crawl scope controls. If needed, `max_pages` provides an indirect throttle. Consider adding `wait_for` parameter in CrawlerRunConfig for politeness.

5. **Large response handling for deep crawls**
   - What we know: A deep crawl of 500 pages will produce a large JSON response (potentially 50+ MB) containing HTML + Markdown for each page. Non-streaming mode buffers everything server-side before responding.
   - What's unclear: Whether RestClient handles large responses gracefully without OOM, and whether Crawl4AI buffers the entire response in memory.
   - Recommendation: Start with `max_pages: 100` for initial implementation, increase after validating memory behavior. For very large sites (500+ pages), consider switching to the async job endpoint (`POST /crawl/job` + poll `GET /job/{task_id}`).

## Sources

### Primary (HIGH confidence)
- [Crawl4AI Self-Hosting Guide (v0.8.x)](https://docs.crawl4ai.com/core/self-hosting/) - REST API endpoints, request/response format, BrowserConfig/CrawlerRunConfig structure
- [Crawl4AI Deep Crawling (v0.8.x)](https://docs.crawl4ai.com/core/deep-crawling/) - BFS/DFS strategies, parameters (max_depth, max_pages, include_external), crash recovery, prefetch mode
- [Crawl4AI Markdown Generation (v0.8.x)](https://docs.crawl4ai.com/core/markdown-generation/) - DefaultMarkdownGenerator options, PruningContentFilter (threshold, threshold_type, min_word_threshold), BM25ContentFilter, content_source options, MarkdownGenerationResult structure
- [Crawl4AI CrawlResult (v0.8.x)](https://docs.crawl4ai.com/core/crawler-result/) - Complete response structure: url, success, markdown, links, status_code, metadata
- [Crawl4AI URL Seeding (v0.8.x)](https://docs.crawl4ai.com/core/url-seeding/) - SeedingConfig, sitemap source (Python-only, NOT available in Docker API)
- [GitHub Discussion #838](https://github.com/unclecode/crawl4ai/discussions/838) - REST API JSON serialization format with type/params pattern; confirmed deep_crawl_strategy format
- [GitHub Issue #1452](https://github.com/unclecode/crawl4ai/issues/1452) - Docker API feature gaps: URL seeding, chunking strategies, adaptive crawling NOT available via REST
- [GitHub Issue #1205](https://github.com/unclecode/crawl4ai/issues/1205) - Streaming + deep crawl bug confirmed in Docker API (closed as dup of #1052)
- [Crawl4AI v0.8.0 Release Notes](https://github.com/unclecode/crawl4ai/blob/main/docs/blog/release-v0.8.0.md) - Crash recovery, prefetch mode, security fixes

### Secondary (MEDIUM confidence)
- [Spring Boot RestClient Guide (Baeldung)](https://www.baeldung.com/spring-boot-restclient) - RestClient configuration, timeouts, fluent API
- [Spring Boot 3.2 HTTP Clients Comparison](https://www.inklattice.com/spring-boot-3-2-http-clients-resttemplate-vs-webclient-vs-restclient/) - RestClient vs WebClient vs RestTemplate for Spring Boot 3+
- [Testcontainers GenericContainer](https://java.testcontainers.org/features/creating_container/) - Custom Docker image testing, exposed ports, wait strategies
- [Docker Hub unclecode/crawl4ai](https://hub.docker.com/r/unclecode/crawl4ai) - Docker image tags (0.8.0, latest), shm_size recommendation (1g-3g)
- [Sitemaps.org Protocol](https://www.sitemaps.org/protocol.html) - Sitemap XML specification, sitemapindex format

### Tertiary (LOW confidence)
- Exact JSON envelope for deep crawl multi-page response via REST API -- confirmed the format works per GitHub discussion, but exact response wrapping (flat list vs object) needs integration test validation
- Optimal PruningContentFilter threshold for documentation sites specifically -- defaults documented but not validated for this use case

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Crawl4AI 0.8.0 Docker API documented for single/deep crawl; RestClient is Spring-standard; StAX is JDK-standard
- Architecture: HIGH - Follows patterns from architecture.md (ContentCrawler interface, CrawlerClient, CrawlerConfig); deep crawl format confirmed via GitHub discussion
- Pitfalls: HIGH - shm_size, streaming bug, type/params serialization, timeout issues are well-documented in issues/docs
- Sitemap parsing: MEDIUM - StAX approach is standard JDK but need arises because Docker API lacks sitemap support; sitemap index handling needs validation
- Deep crawl response format: MEDIUM - Confirmed to work non-streaming per docs and community reports, but exact multi-page JSON shape needs integration test

**Research date:** 2026-02-15
**Valid until:** 2026-03-15 (30 days - Crawl4AI is actively developed but version 0.8.0 is pinned)
