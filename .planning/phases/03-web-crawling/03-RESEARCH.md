# Phase 3: Web Crawling - Research

**Researched:** 2026-02-15
**Domain:** Web crawling via Crawl4AI sidecar, page discovery, HTML-to-Markdown conversion
**Confidence:** MEDIUM-HIGH

## Summary

Phase 3 integrates the existing Crawl4AI Docker sidecar (`unclecode/crawl4ai:0.8.0`, already in docker-compose.yml) with the Spring Boot application to crawl documentation sites and produce clean Markdown. The Crawl4AI REST API runs on port 11235 and provides endpoints for single-page and batch crawling with built-in Chromium headless browser, HTML-to-Markdown conversion, and content filtering.

A critical architectural finding is that **deep/recursive crawling via the REST API is unreliable**. The `deep_crawl_strategy` parameter exists in `CrawlerRunConfig` but the Docker server's `api.py` shows no explicit handling of it, and the serialization path (`to_dict()` method) omits `deep_crawl_strategy`. The recommended approach is **Java-side URL discovery** (sitemap.xml parsing + link extraction from crawl results) with Crawl4AI used for per-page crawling and Markdown extraction. This gives the Java application full control over crawl ordering, deduplication, and error handling.

**Primary recommendation:** Use Crawl4AI's `POST /crawl` endpoint for individual page crawling with `PruningContentFilter` for boilerplate removal. Handle URL discovery (sitemap.xml + recursive link following) on the Java side using crawler-commons for sitemap parsing and link extraction from Crawl4AI's `links.internal` response field.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Crawl4AI (Docker sidecar) | 0.8.0 | Headless browser crawling + HTML-to-Markdown | Already in docker-compose.yml; handles JS rendering, boilerplate removal, Markdown conversion in one service |
| Spring RestClient | (Spring Boot 3.5.7) | HTTP client for Crawl4AI REST API | Recommended sync HTTP client in Spring Framework 6.x+; fluent API, built-in Jackson JSON |
| crawler-commons | 1.6 | Sitemap.xml parsing | Industry-standard Java library for sitemap/robots.txt parsing; SAX-based, handles malformed XML |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jackson (via Spring Boot) | (managed) | JSON serialization for Crawl4AI request/response DTOs | Automatic with RestClient |
| Testcontainers GenericContainer | 1.21.1 | Crawl4AI container in integration tests | Testing crawl service without Docker Compose |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Java-side URL discovery | Crawl4AI deep_crawl_strategy via REST | Deep crawl via REST is unreliable (serialization gap); Java-side gives full control over ordering, dedup, and error recovery |
| crawler-commons for sitemaps | Hand-rolled JAXB parsing | Sitemap format is simple XML, but crawler-commons handles edge cases (sitemap indexes, malformed XML, gzip) |
| RestClient | WebClient | WebClient is reactive; we use virtual threads + sync RestClient which is simpler and sufficient |
| PruningContentFilter | BM25ContentFilter | BM25 requires a user query; PruningContentFilter works without one, better for generic doc crawling |

**Dependencies to add (build.gradle.kts):**
```kotlin
// In libs.versions.toml:
// crawler-commons = "1.6"
// In libraries section:
// crawler-commons = { module = "com.github.crawler-commons:crawler-commons", version.ref = "crawler-commons" }

// In build.gradle.kts dependencies:
implementation(libs.crawler.commons)
```

No additional HTTP client dependency needed -- RestClient ships with `spring-boot-starter-web` already in the project.

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/dev/alexandria/
├── crawl/
│   ├── CrawlService.java           # Orchestrates crawl: sitemap -> URL discovery -> page crawl
│   ├── CrawlResult.java            # DTO for crawl output (url, markdown, links, success)
│   ├── Crawl4AiClient.java         # REST client for Crawl4AI sidecar
│   ├── Crawl4AiConfig.java         # RestClient bean + config properties
│   ├── Crawl4AiRequest.java        # Request DTO matching Crawl4AI schema
│   ├── Crawl4AiResponse.java       # Response DTO matching Crawl4AI schema
│   ├── PageDiscoveryService.java   # Sitemap + link-based URL discovery
│   └── SitemapParser.java          # Wrapper around crawler-commons SiteMapParser
├── config/
│   └── EmbeddingConfig.java        # (existing)
├── source/
│   └── Source.java                 # (existing)
└── ...
```

### Pattern 1: Sidecar Client with Spring RestClient
**What:** A dedicated `Crawl4AiClient` bean wrapping RestClient for all Crawl4AI HTTP calls.
**When to use:** All crawl operations.
**Example:**
```java
// Source: Spring Framework RestClient docs
@Component
public class Crawl4AiClient {

    private final RestClient restClient;

    public Crawl4AiClient(RestClient.Builder builder,
                          @Value("${alexandria.crawl4ai.base-url}") String baseUrl) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Crawl4AiResponse crawl(String url) {
        Crawl4AiRequest request = new Crawl4AiRequest(
            List.of(url),
            Map.of("type", "BrowserConfig", "params", Map.of("headless", true)),
            Map.of("type", "CrawlerRunConfig", "params", Map.of(
                "cache_mode", "bypass",
                "word_count_threshold", 50,
                "excluded_tags", List.of("nav", "footer", "header"),
                "markdown_generator", Map.of(
                    "type", "DefaultMarkdownGenerator",
                    "params", Map.of(
                        "content_filter", Map.of(
                            "type", "PruningContentFilter",
                            "params", Map.of("threshold", 0.48, "min_word_threshold", 20)
                        )
                    )
                )
            ))
        );

        return restClient.post()
                .uri("/crawl")
                .body(request)
                .retrieve()
                .body(Crawl4AiResponse.class);
    }
}
```

### Pattern 2: Java-Side Recursive URL Discovery
**What:** Crawl orchestration loop that discovers URLs from sitemap.xml first, falls back to BFS link following.
**When to use:** Every documentation site crawl.
**Example:**
```java
// Pseudocode for the crawl orchestration
public List<CrawlResult> crawlSite(String rootUrl) {
    Set<String> discovered = new LinkedHashSet<>();
    Set<String> visited = new HashSet<>();

    // Phase 1: Try sitemap.xml
    List<String> sitemapUrls = sitemapParser.parseFromRoot(rootUrl);
    if (!sitemapUrls.isEmpty()) {
        discovered.addAll(sitemapUrls);
    } else {
        // Phase 2: Fall back to recursive link crawling
        discovered.add(rootUrl);
    }

    List<CrawlResult> results = new ArrayList<>();
    while (!discovered.isEmpty()) {
        String url = discovered.iterator().next();
        discovered.remove(url);
        if (!visited.add(url)) continue;

        Crawl4AiResponse response = crawl4AiClient.crawl(url);
        if (response.isSuccess()) {
            results.add(toResult(response));
            // Extract internal links for further discovery (only if no sitemap)
            if (sitemapUrls.isEmpty()) {
                response.getInternalLinks().stream()
                    .filter(link -> isSameSite(rootUrl, link))
                    .filter(link -> !visited.contains(link))
                    .forEach(discovered::add);
            }
        }
    }
    return results;
}
```

### Pattern 3: Sitemap-First Discovery
**What:** Check for sitemap.xml at well-known locations before falling back to link crawling.
**When to use:** Initial page discovery for any source URL.
**Example:**
```java
// Source: sitemaps.org protocol + crawler-commons
public List<String> discoverUrls(String rootUrl) {
    // Try sitemap.xml at standard locations
    for (String sitemapUrl : getSitemapCandidates(rootUrl)) {
        try {
            byte[] content = fetchBytes(sitemapUrl);
            AbstractSiteMap sitemap = new SiteMapParser().parseSiteMap(content, new URL(sitemapUrl));
            if (sitemap instanceof SiteMap sm) {
                return sm.getSiteMapUrls().stream()
                    .map(SiteMapURL::getUrl)
                    .map(URL::toString)
                    .toList();
            } else if (sitemap instanceof SiteMapIndex idx) {
                // Recursively fetch sub-sitemaps
                return idx.getSitemaps().stream()
                    .flatMap(sub -> parseSingleSitemap(sub).stream())
                    .toList();
            }
        } catch (Exception e) {
            // Sitemap not found or unparseable, try next
        }
    }
    return List.of(); // Fall back to link crawling
}

private List<String> getSitemapCandidates(String rootUrl) {
    String base = normalizeBaseUrl(rootUrl);
    return List.of(
        base + "/sitemap.xml",
        base + "/sitemap_index.xml"
    );
}
```

### Anti-Patterns to Avoid
- **Sending deep_crawl_strategy via REST API:** The Docker server does not reliably serialize/deserialize deep crawl strategies. Handle URL discovery on the Java side.
- **Synchronous crawling of many pages in a single HTTP call:** Crawl4AI's `POST /crawl` with many URLs may timeout. Crawl pages individually or in small batches.
- **Ignoring the fit_markdown field:** Always prefer `fit_markdown` (boilerplate-removed) over `raw_markdown` for doc ingestion.
- **Not normalizing URLs:** Documentation sites have trailing slashes, fragments, query params that create duplicates. Normalize before dedup.
- **Storing raw HTML instead of Markdown:** The pipeline should store clean Markdown. Crawl4AI handles the conversion.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML-to-Markdown conversion | Custom Jsoup-based converter | Crawl4AI's built-in Markdown generator | Handles edge cases: code blocks with language tags, nested tables, escaped HTML entities, image alt text |
| JavaScript page rendering | Selenium/Playwright integration | Crawl4AI's Chromium headless browser | Already running in sidecar; handles lazy loading, dynamic content, SPA routing |
| Boilerplate removal | Custom heuristics | Crawl4AI's PruningContentFilter | Analyzes text density, link density, HTML structure, known nav/footer patterns |
| Sitemap.xml parsing | JAXB/DOM manual parsing | crawler-commons SiteMapParser | Handles sitemap indexes, gzip compression, malformed XML, all sitemap extensions |
| URL normalization | String manipulation | java.net.URI normalization | Handles encoding, port normalization, trailing slashes, fragment removal |

**Key insight:** Crawl4AI exists specifically to solve the hard problems of web crawling (JS rendering, content extraction, boilerplate removal). The Java side should focus on orchestration, not reimplementing these capabilities.

## Common Pitfalls

### Pitfall 1: Crawl4AI Sidecar Startup Time
**What goes wrong:** Crawl4AI with Chromium takes 15-30 seconds to start. Tests or app startup fail if they hit the API before it's ready.
**Why it happens:** Chromium browser pool initialization is slow; the container reports healthy only after the browser pool is warmed up.
**How to avoid:** Use the `/health` endpoint with appropriate wait/retry logic. In Docker Compose, the `healthcheck` with `start_period: 30s` is already configured. In Testcontainers, use `Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60))`.
**Warning signs:** `Connection refused` or timeout errors on first crawl request.

### Pitfall 2: Crawl4AI REST API Timeouts for Large Pages
**What goes wrong:** JavaScript-heavy pages (React/Vue doc sites) can take 10-30 seconds to render. Default HTTP client timeouts cause failures.
**Why it happens:** Crawl4AI must wait for `domcontentloaded` + JS execution + lazy-load scrolling before extracting content.
**How to avoid:** Set RestClient read timeout to at least 60 seconds. Crawl4AI's default `page_timeout` is 60000ms. Match or exceed this on the client side.
**Warning signs:** Intermittent `SocketTimeoutException` on specific pages while others work fine.

### Pitfall 3: URL Deduplication Failures
**What goes wrong:** The same page gets crawled multiple times because URLs differ in trailing slashes, fragments, or query parameters.
**Why it happens:** `https://docs.example.com/guide` vs `https://docs.example.com/guide/` vs `https://docs.example.com/guide#section` vs `https://docs.example.com/guide?v=2` are all different strings but the same content.
**How to avoid:** Normalize all URLs before adding to the visited/discovered sets: remove fragments, remove tracking query params, normalize trailing slashes, lowercase the host.
**Warning signs:** Duplicate pages in crawl results; unexpectedly high page counts.

### Pitfall 4: Crawl4AI Memory Under Load
**What goes wrong:** Crawl4AI sidecar OOMs or becomes unresponsive when crawling many pages concurrently.
**Why it happens:** Each page requires a Chromium tab. Crawl4AI defaults to 40 concurrent pages, but the default Docker Compose config has no memory limit on the sidecar.
**How to avoid:** Add `shm_size: 1g` and memory limits to the crawl4ai service in docker-compose.yml. Crawl pages sequentially or in small batches (2-5 concurrent) from the Java side. The config.yml defaults `max_concurrent_pages: 40` which is too high for a sidecar.
**Warning signs:** Crawl4AI container restart loops; "out of memory" in container logs.

### Pitfall 5: Nested/Complex Crawl4AI Request JSON Structure
**What goes wrong:** The `crawler_config` and `browser_config` use a `{"type": "ClassName", "params": {...}}` format, not flat key-value pairs. Incorrect nesting causes silent failures or default config being used.
**Why it happens:** Crawl4AI's `CrawlerRunConfig.load()` expects a specific serialization format with type discriminators.
**How to avoid:** Match the exact `{"type": ..., "params": {...}}` structure. Test with Crawl4AI's `/playground` endpoint first to validate config. Write integration tests that verify Markdown output quality.
**Warning signs:** Crawl succeeds but `fit_markdown` is empty or same as `raw_markdown`; content filter not applied.

### Pitfall 6: Missing `shm_size` in Docker Compose
**What goes wrong:** Chromium crashes inside the Crawl4AI container with "session deleted because of page crash" errors.
**Why it happens:** Chromium uses `/dev/shm` for shared memory. Docker defaults this to 64MB which is insufficient for rendering pages.
**How to avoid:** Add `shm_size: 1g` to the crawl4ai service in docker-compose.yml.
**Warning signs:** Intermittent crawl failures with browser crash errors; works for simple pages but fails on complex ones.

## Code Examples

### Crawl4AI REST API Request/Response DTOs
```java
// Source: Crawl4AI deploy/docker/schemas.py + api.py
public record Crawl4AiRequest(
    List<String> urls,
    Map<String, Object> browser_config,
    Map<String, Object> crawler_config
) {}

// Response from POST /crawl
public record Crawl4AiResponse(
    boolean success,
    List<Crawl4AiPageResult> results
) {}

public record Crawl4AiPageResult(
    String url,
    boolean success,
    String status_code,
    Crawl4AiMarkdown markdown,
    Map<String, List<Crawl4AiLink>> links,
    String error_message
) {
    public List<String> internalLinkHrefs() {
        return links.getOrDefault("internal", List.of()).stream()
                .map(Crawl4AiLink::href)
                .toList();
    }
}

public record Crawl4AiMarkdown(
    String raw_markdown,
    String fit_markdown,
    String fit_html,
    String markdown_with_citations,
    String references_markdown
) {}

public record Crawl4AiLink(
    String href,
    String text,
    String title
) {}
```

### RestClient Configuration
```java
// Source: Spring Framework RestClient docs
@Configuration
@ConfigurationProperties(prefix = "alexandria.crawl4ai")
public class Crawl4AiConfig {

    private String baseUrl = "http://localhost:11235";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 120000; // 2 minutes for JS-heavy pages

    @Bean
    public RestClient crawl4AiRestClient(RestClient.Builder builder) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // getters/setters
}
```

### Sitemap Parsing with crawler-commons
```java
// Source: crawler-commons GitHub + sitemaps.org protocol
import crawlercommons.sitemaps.*;

public class SitemapParser {

    private final RestClient httpClient;

    public List<String> discoverFromSitemap(String rootUrl) {
        String baseUrl = normalizeToBase(rootUrl);
        List<String> candidates = List.of(
            baseUrl + "/sitemap.xml",
            baseUrl + "/sitemap_index.xml"
        );

        for (String sitemapUrl : candidates) {
            try {
                byte[] content = fetchBytes(sitemapUrl);
                if (content == null || content.length == 0) continue;

                SiteMapParser parser = new SiteMapParser(false); // strict = false
                AbstractSiteMap result = parser.parseSiteMap(content, new URL(sitemapUrl));

                if (result instanceof SiteMapIndex index) {
                    return index.getSitemaps().stream()
                        .map(AbstractSiteMap::getUrl)
                        .map(URL::toString)
                        .flatMap(url -> parseSingleSitemap(url).stream())
                        .toList();
                } else if (result instanceof SiteMap siteMap) {
                    return siteMap.getSiteMapUrls().stream()
                        .map(SiteMapURL::getUrl)
                        .map(URL::toString)
                        .toList();
                }
            } catch (Exception e) {
                // Sitemap not available or unparseable, try next candidate
            }
        }
        return List.of(); // No sitemap found
    }
}
```

### Testcontainers Setup for Crawl4AI
```java
// Source: Testcontainers docs
@Testcontainers
class CrawlServiceIT extends BaseIntegrationTest {

    @Container
    static GenericContainer<?> crawl4ai = new GenericContainer<>(
            DockerImageName.parse("unclecode/crawl4ai:0.8.0"))
        .withExposedPorts(11235)
        .withCreateContainerCmdModifier(cmd ->
            cmd.getHostConfig().withShmSize(1073741824L)) // 1GB shm
        .waitingFor(Wait.forHttp("/health")
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofSeconds(90)));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("alexandria.crawl4ai.base-url", () ->
            "http://" + crawl4ai.getHost() + ":" + crawl4ai.getMappedPort(11235));
    }
}
```

### Application Properties
```yaml
# application.yml additions
alexandria:
  crawl4ai:
    base-url: http://${CRAWL4AI_HOST:localhost}:${CRAWL4AI_PORT:11235}
    connect-timeout-ms: 5000
    read-timeout-ms: 120000
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RestTemplate | RestClient | Spring Framework 6.1 (2023) | RestTemplate deprecated in Spring 7.0; RestClient is the recommended sync HTTP client |
| Jsoup for HTML-to-Markdown | Crawl4AI Markdown generator | Crawl4AI 0.7+ (2024) | Crawl4AI's DefaultMarkdownGenerator with PruningContentFilter handles boilerplate removal automatically |
| Selenium for JS rendering | Crawl4AI headless Chromium | Crawl4AI architecture | No need for separate browser driver management; Crawl4AI bundles Chromium |
| DOM-based sitemap parsing | SAX-based (crawler-commons 1.0+) | crawler-commons 1.0 (2023) | SAX parser is more robust to malformed documents than DOM |

**Deprecated/outdated:**
- RestTemplate: Deprecated in Spring Framework 7.0, replaced by RestClient
- Crawl4AI `markdown_v2` field: Deprecated in favor of `result.markdown.raw_markdown` / `result.markdown.fit_markdown`

## Open Questions

1. **Deep crawl via REST API reliability**
   - What we know: `deep_crawl_strategy` field exists in `CrawlerRunConfig` but is missing from `to_dict()` and has no explicit handling in docker server's `api.py`. The `CrawlerRunConfig.load()` method attempts generic deserialization which may or may not work.
   - What's unclear: Whether passing `deep_crawl_strategy` in `crawler_config` params actually works in practice through the REST API.
   - Recommendation: Use Java-side URL discovery (proven reliable pattern). If deep crawl via REST is confirmed working later, it could be an optimization but not a dependency.

2. **Crawl4AI 0.8.0 specific behavior vs latest**
   - What we know: The project pins `unclecode/crawl4ai:0.8.0`. Crawl4AI is actively developed with frequent releases.
   - What's unclear: Whether all features documented (PruningContentFilter, fit_markdown, links categorization) are present in 0.8.0 specifically vs only in latest.
   - Recommendation: Test all features against the pinned 0.8.0 image in integration tests. This is LOW risk since 0.8.0 is the "current stable" listed on GitHub.

3. **Optimal PruningContentFilter threshold for documentation sites**
   - What we know: Default threshold is ~0.48 (range 0-1). Higher = more aggressive pruning.
   - What's unclear: What threshold works best for documentation sites specifically (vs news/blog sites).
   - Recommendation: Start with default 0.48, validate against 2-3 real doc sites (Spring Boot docs, Python docs, React docs) in integration tests. Make threshold configurable.

4. **Crawl4AI container resource requirements**
   - What we know: Chromium is memory-hungry. `shm_size: 1g` is recommended. Docker Compose currently has no memory limit on crawl4ai service.
   - What's unclear: Actual memory usage under load for documentation crawling (which tends to have simpler pages than e-commerce/social media).
   - Recommendation: Add `shm_size: 1g` and `deploy.resources.limits.memory: 2g` to crawl4ai service. Monitor in practice.

## Sources

### Primary (HIGH confidence)
- Crawl4AI GitHub repository (https://github.com/unclecode/crawl4ai) - API schemas, models, server code
- Crawl4AI deploy/docker/schemas.py - REST API request/response schemas (CrawlRequest, MarkdownRequest)
- Crawl4AI deploy/docker/api.py - REST endpoint implementations (/crawl, /crawl/job, /crawl/stream)
- Crawl4AI crawl4ai/models.py - CrawlResult model with markdown and links fields
- Crawl4AI crawl4ai/async_configs.py - CrawlerRunConfig.load() implementation confirming deep_crawl_strategy gap
- Spring Framework REST Clients reference (https://docs.spring.io/spring-framework/reference/integration/rest-clients.html) - RestClient recommendation
- sitemaps.org protocol (https://www.sitemaps.org/protocol.html) - Sitemap XML specification
- crawler-commons GitHub (https://github.com/crawler-commons/crawler-commons) - Version 1.6, Java 11+, SAX-based parsing

### Secondary (MEDIUM confidence)
- Crawl4AI official docs (https://docs.crawl4ai.com/) - Deep crawling strategies, content filtering, markdown generation
- Crawl4AI deploy/docker/config.yml - Default settings (40 concurrent pages, port 11235, headless mode)
- Testcontainers docs (https://java.testcontainers.org/) - GenericContainer, wait strategies

### Tertiary (LOW confidence)
- Deep crawl via REST API feasibility - based on code analysis showing serialization gap, but not empirically tested
- Specific PruningContentFilter threshold recommendations for doc sites - defaults documented but not validated for this use case

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Crawl4AI already in docker-compose.yml, RestClient is Spring's recommended sync client, crawler-commons is the standard Java sitemap library
- Architecture: MEDIUM-HIGH - Java-side URL discovery is a proven pattern; Crawl4AI REST API format is well-documented in source code; deep crawl REST limitation is based on code analysis
- Pitfalls: MEDIUM - Timeouts, shm_size, and startup timing are well-known Chromium/Docker issues; URL dedup and config nesting issues are inferred from the API structure

**Research date:** 2026-02-15
**Valid until:** 2026-03-15 (30 days - Crawl4AI releases frequently but 0.8.0 is pinned)
