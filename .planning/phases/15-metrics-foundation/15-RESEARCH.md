# Phase 15: Metrics Foundation - Research

**Researched:** 2026-01-24
**Domain:** Application metrics with Micrometer and Prometheus
**Confidence:** HIGH

## Summary

This phase adds Micrometer instrumentation to expose application metrics via a Prometheus-compatible endpoint. The project already has Spring Boot Actuator and Micrometer Core (1.14.8) as dependencies through `spring-boot-starter-actuator`. The only missing piece is `micrometer-registry-prometheus` to enable the `/actuator/prometheus` endpoint.

Spring Boot 3.4.7 auto-configures Micrometer when the Prometheus registry is on the classpath. The standard approach is to inject `MeterRegistry` into services and create Timers/Counters programmatically. The `@Timed` annotation approach requires additional AOP configuration and is better suited for cross-cutting concerns rather than targeted instrumentation.

**Primary recommendation:** Add `micrometer-registry-prometheus` dependency, configure percentile histograms via application.yml, and instrument `SearchService` and `IngestionService` with programmatic Timers/Counters injected via constructor.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| micrometer-registry-prometheus | 1.14.8 (managed by Spring Boot BOM) | Prometheus format export | Spring Boot auto-configures when on classpath |
| micrometer-core | 1.14.8 | Metrics API (Timer, Counter, Gauge) | Already included via spring-boot-starter-actuator |
| spring-boot-starter-actuator | 3.4.7 | Management endpoints | Already in project |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| micrometer-registry-prometheus-simpleclient | 1.14.x | Legacy Prometheus client | Only if compatibility issues with new client |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Programmatic Timer | @Timed annotation | Requires TimedAspect bean, AOP overhead, less control over tag values |
| Prometheus registry | OpenTelemetry | More complex setup, better for distributed tracing scenarios |

**Installation:**
```xml
<!-- Add to pom.xml - version managed by Spring Boot BOM -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## Architecture Patterns

### Recommended Instrumentation Approach

For this project's hexagonal architecture, instrument at the **core service layer** (SearchService, IngestionService). This provides:
- Consistent measurement regardless of entry point (CLI, MCP, HTTP)
- Natural boundary for timing operations
- Clean separation from infrastructure concerns

```
api (MCP/CLI/REST) → core (instrumented) → infra (repositories)
                         ↑
                    MeterRegistry injected
```

### Pattern 1: Constructor-Injected Timer
**What:** Create Timer in constructor using injected MeterRegistry
**When to use:** For timing service method execution
**Example:**
```java
// Source: https://docs.micrometer.io/micrometer/reference/concepts/timers.html
@Service
public class SearchService {
    private final Timer searchTimer;
    private final EmbeddingGenerator embeddingGenerator;
    // ... other dependencies

    public SearchService(
            MeterRegistry meterRegistry,
            EmbeddingGenerator embeddingGenerator,
            // ... other dependencies
    ) {
        this.searchTimer = Timer.builder("alexandria.search.duration")
            .description("Time spent executing search operations")
            .tag("type", "semantic")
            .register(meterRegistry);
        this.embeddingGenerator = embeddingGenerator;
        // ...
    }

    public List<SearchResult> search(String query, SearchFilters filters) {
        return searchTimer.record(() -> {
            // existing implementation
        });
    }
}
```

### Pattern 2: Counter for Events
**What:** Increment counter on discrete events
**When to use:** For counting documents ingested, errors, etc.
**Example:**
```java
// Source: https://docs.micrometer.io/micrometer/reference/concepts/counters.html
@Service
public class IngestionService {
    private final Counter documentsIngestedCounter;

    public IngestionService(MeterRegistry meterRegistry, /* ... */) {
        this.documentsIngestedCounter = Counter.builder("alexandria.documents.ingested")
            .description("Number of documents successfully ingested")
            .register(meterRegistry);
        // ...
    }

    @Transactional
    public void ingestFile(Path file) {
        // ... existing logic ...
        documentsIngestedCounter.increment();
        log.info("Ingested file: {}", file);
    }
}
```

### Pattern 3: Timer.Sample for Conditional Timing
**What:** Start timer before operation, stop with tags determined by outcome
**When to use:** When tags depend on operation result (success/failure)
**Example:**
```java
// Source: https://docs.micrometer.io/micrometer/reference/concepts/timers.html
public void processWithOutcome(Request request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
        // ... processing
        sample.stop(meterRegistry.timer("operation.duration", "outcome", "success"));
    } catch (Exception e) {
        sample.stop(meterRegistry.timer("operation.duration", "outcome", "failure"));
        throw e;
    }
}
```

### Anti-Patterns to Avoid
- **High cardinality tags:** Never use document IDs, file paths, or query text as tag values - causes memory explosion and OOM
- **Timing infrastructure layer:** Don't add timers to repositories; time at service layer for consistent semantics
- **Using @Timed without TimedAspect:** The annotation does nothing without the AOP aspect bean
- **Creating timers inside methods:** Creates new timer per invocation; create once in constructor

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Percentile calculation | Custom histogram code | `publishPercentileHistogram()` + Prometheus query | Micrometer generates optimal buckets; Prometheus calculates accurately |
| Endpoint exposure | Custom controller | spring-boot-starter-actuator | Auto-configures `/actuator/prometheus` endpoint |
| Registry setup | Manual PrometheusMeterRegistry | Spring Boot auto-configuration | Just add dependency; Spring does the rest |
| Tag normalization | Custom URI path cleaning | Spring's default HTTP metrics | Already normalizes `/users/{id}` patterns |

**Key insight:** Spring Boot's auto-configuration handles 90% of the setup. Adding the prometheus registry dependency automatically enables the endpoint and wires everything together.

## Common Pitfalls

### Pitfall 1: High Cardinality Tags Leading to OOM
**What goes wrong:** Using unique values (IDs, paths, queries) as tag values causes unbounded metric creation
**Why it happens:** Each unique tag combination creates a new time series stored in memory
**How to avoid:**
- Only use bounded, known tag values (e.g., "semantic", "hybrid", "success", "failure")
- Never use document IDs, file paths, or user input as tags
- Use MeterFilter#maximumAllowableTags if needed
**Warning signs:** Memory usage grows over time, eventual OOM, slow prometheus scrapes

### Pitfall 2: Client-Side Percentiles Not Aggregatable
**What goes wrong:** Using `.percentiles(0.5, 0.95, 0.99)` produces metrics that can't be combined across instances
**Why it happens:** Client-side percentiles are pre-computed approximations per instance
**How to avoid:** Use `.publishPercentileHistogram()` instead; Prometheus calculates accurate percentiles from buckets
**Warning signs:** Inconsistent percentile values in Grafana dashboards with multiple instances

### Pitfall 3: @Timed Annotation Not Working
**What goes wrong:** Adding @Timed to methods has no effect
**Why it happens:** Missing TimedAspect bean in Spring context
**How to avoid:** Either add TimedAspect bean OR use programmatic Timer.record() approach (recommended for this project)
**Warning signs:** No timer metrics appear despite annotation being present

### Pitfall 4: Missing Endpoint Exposure
**What goes wrong:** `/actuator/prometheus` returns 404
**Why it happens:** Actuator endpoints must be explicitly exposed in Spring Boot
**How to avoid:** Add `management.endpoints.web.exposure.include=prometheus` (or `health,prometheus`)
**Warning signs:** 404 on prometheus endpoint, empty response

### Pitfall 5: Timing Already-Timed Methods
**What goes wrong:** HTTP controller methods are double-timed (once by Spring Boot, once by custom code)
**Why it happens:** Spring Boot auto-instruments HTTP endpoints
**How to avoid:** Only add custom timers to service layer; let Spring handle HTTP metrics
**Warning signs:** Duplicate metrics with slightly different names

## Code Examples

Verified patterns from official sources:

### Creating Timer with Percentile Histogram
```java
// Source: https://docs.micrometer.io/micrometer/reference/concepts/timers.html
Timer timer = Timer.builder("alexandria.search.duration")
    .description("Search operation duration")
    .tag("method", "semantic")
    .publishPercentileHistogram()  // Enable histogram for Prometheus percentile calculation
    .register(meterRegistry);
```

### Wrapping Existing Code with Timer.record()
```java
// Source: https://docs.micrometer.io/micrometer/reference/concepts/timers.html
public List<SearchResult> search(String query, SearchFilters filters) {
    return searchTimer.record(() -> {
        // Original implementation unchanged
        float[] queryEmbedding = embeddingGenerator.embed(query);
        List<SearchResult> results = searchRepository.searchSimilar(queryEmbedding, filters);
        return results;
    });
}
```

### Counter Increment Pattern
```java
// Source: https://docs.micrometer.io/micrometer/reference/concepts/counters.html
Counter counter = Counter.builder("alexandria.documents.ingested")
    .description("Total documents ingested")
    .register(meterRegistry);

// In method:
counter.increment();
```

### Application Configuration for Percentiles
```yaml
# Source: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    distribution:
      percentiles-histogram:
        alexandria.search.duration: true
        alexandria.embedding.duration: true
```

### Unit Testing with SimpleMeterRegistry
```java
// Source: https://www.baeldung.com/testing-micrometer-metrics
class SearchServiceTest {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    SearchService searchService; // inject meterRegistry via constructor

    @Test
    void search_recordsTimer() {
        searchService.search("test query", 10);

        Timer timer = meterRegistry.get("alexandria.search.duration").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| micrometer-registry-prometheus-simpleclient | micrometer-registry-prometheus | Micrometer 1.12+ | New Prometheus client (1.x) is default |
| Dropwizard Metrics | Micrometer | Spring Boot 2.0 | Micrometer is the standard for Spring Boot |
| Custom HTTP endpoints | Spring Boot Actuator | Spring Boot 1.x | Auto-configured management endpoints |
| Manual percentile calculation | publishPercentileHistogram() | Micrometer 1.0 | Server-side percentile calculation preferred |

**Deprecated/outdated:**
- `micrometer-registry-prometheus-simpleclient`: Deprecated in favor of new client-based module
- `.percentiles()` for Prometheus: Client-side percentiles not recommended; use histogram

## Open Questions

Things that couldn't be fully resolved:

1. **Embedding timing granularity**
   - What we know: `EmbeddingGenerator.embed()` is called multiple times per ingestion (per chunk)
   - What's unclear: Should we time individual embed calls or aggregate per document?
   - Recommendation: Time individual calls; use tags to distinguish search vs ingestion context

2. **Virtual threads impact on Timer accuracy**
   - What we know: Project uses Java 21 virtual threads
   - What's unclear: Whether Timer.record() handles virtual thread parking correctly
   - Recommendation: Proceed with standard Timer.record(); Micrometer 1.14 supports virtual threads

## Sources

### Primary (HIGH confidence)
- [Micrometer Timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html) - Timer API, @Timed, Timer.Sample
- [Micrometer Counters](https://docs.micrometer.io/micrometer/reference/concepts/counters.html) - Counter API
- [Micrometer Histograms and Percentiles](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html) - Client vs server-side percentiles
- [Micrometer Prometheus Registry](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html) - Prometheus setup
- [Spring Boot Metrics Documentation](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) - Auto-configuration, properties

### Secondary (MEDIUM confidence)
- [Maven Repository: micrometer-registry-prometheus](https://mvnrepository.com/artifact/io.micrometer/micrometer-registry-prometheus) - Version info
- [Baeldung: @Timed Annotation](https://www.baeldung.com/timed-metrics-aspectj) - TimedAspect configuration
- [Baeldung: Testing Micrometer](https://www.baeldung.com/testing-micrometer-metrics) - SimpleMeterRegistry testing

### Tertiary (LOW confidence)
- [Medium: Tracking Metrics with Micrometer](https://medium.com/@AlexanderObregon/tracking-metrics-in-spring-boot-with-micrometer-and-prometheus-d61b97520477) - General patterns
- [GitHub Issue #3038](https://github.com/micrometer-metrics/micrometer/issues/3038) - High cardinality OOM issues

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Spring Boot BOM manages versions, auto-configuration is well-documented
- Architecture: HIGH - Micrometer patterns are stable and well-established
- Pitfalls: HIGH - High cardinality and percentile issues are well-documented

**Research date:** 2026-01-24
**Valid until:** 2026-03-24 (60 days - Micrometer is stable)
