# Architecture Patterns: RAG Evaluation Toolkit

**Domain:** RAG system evaluation and monitoring
**Researched:** 2026-01-24
**Overall Confidence:** HIGH

## Executive Summary

The RAG Evaluation Toolkit integrates with Alexandria's existing hexagonal architecture through a new `eval` package that follows the established ports-and-adapters pattern. Evaluation components live in `core/eval/` for metrics calculation and `infra/eval/` for external tool integration (Ollama, Python scripts). Docker Compose extends with an optional `eval` profile containing VictoriaMetrics, Grafana, Loki, and Ollama services. Java-Python interop uses a sidecar REST API pattern rather than subprocess calls for reliability and testability.

## Recommended Architecture

```
                                    +------------------+
                                    |   Grafana        |
                                    |   (Dashboard)    |
                                    +--------+---------+
                                             |
              +------------------------------+------------------------------+
              |                              |                              |
    +---------v---------+       +------------v-----------+       +----------v----------+
    | VictoriaMetrics   |       |       Loki             |       |     Ollama          |
    | (Metrics Store)   |       |   (Log Aggregation)    |       | (LLM-as-Judge)      |
    +-------------------+       +------------------------+       +---------------------+
              ^                              ^                              ^
              |                              |                              |
    +---------+---------+       +------------+-----------+       +----------+-----------+
    | Micrometer        |       |     Promtail           |       | LangChain4j          |
    | (Spring Boot)     |       |  (Log Collector)       |       | Ollama Client        |
    +-------------------+       +------------------------+       +----------------------+
              ^                              ^                              ^
              |                              |                              |
+-------------+------------------------------+------------------------------+-------------+
|                                                                                         |
|                              Alexandria Application                                     |
|                                                                                         |
|  +-----------------------------------------------------------------------------------+  |
|  |  api/eval/                                                                        |  |
|  |  +---------------+  +------------------+  +--------------------+                  |  |
|  |  | EvalCommands  |  | EvalController   |  | MetricsEndpoint    |                  |  |
|  |  | (CLI)         |  | (REST optional)  |  | (/actuator/prom)   |                  |  |
|  |  +-------+-------+  +--------+---------+  +---------+----------+                  |  |
|  +----------|---------------------|----------------------|--------------------------+   |
|             |                     |                      |                              |
|  +----------v---------------------v----------------------v--------------------------+   |
|  |  core/eval/                                                                      |   |
|  |  +------------------+  +-------------------+  +------------------+               |   |
|  |  | EvaluationService|  | MetricsCalculator |  | GoldenDataset    |               |   |
|  |  | (orchestrator)   |  | (P@k, MRR, etc.)  |  | (loader/parser)  |               |   |
|  |  +--------+---------+  +---------+---------+  +--------+---------+               |   |
|  |           |                      |                     |                         |   |
|  |  +--------v---------+  +---------v---------+  +--------v---------+               |   |
|  |  | EvaluationResult |  | RetrievalMetrics  |  | QAPair (record)  |               |   |
|  |  | (domain model)   |  | (domain model)    |  | (domain model)   |               |   |
|  |  +------------------+  +-------------------+  +------------------+               |   |
|  |                                                                                  |   |
|  |  +------------------+  +-------------------+  +------------------+               |   |
|  |  | port/            |                                                            |   |
|  |  | EmbeddingQuality |  | LLMJudge          |  | PythonAnalytics  |               |   |
|  |  | Port             |  | Port              |  | Port             |               |   |
|  |  +------------------+  +-------------------+  +------------------+               |   |
|  +----------|---------------------|----------------------|--------------------------+   |
|             |                     |                      |                              |
|  +----------v---------------------v----------------------v--------------------------+   |
|  |  infra/eval/                                                                     |   |
|  |  +------------------+  +-------------------+  +------------------+               |   |
|  |  | SmileEmbedding   |  | OllamaJudge       |  | PythonSidecar    |               |   |
|  |  | Analyzer         |  | Adapter           |  | Client           |               |   |
|  |  +------------------+  +-------------------+  +------------------+               |   |
|  +----------------------------------------------------------------------------------+   |
|                                                                                         |
+-----------------------------------------------------------------------------------------+
```

## Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|----------------|-------------------|
| `core/eval/EvaluationService` | Orchestrates evaluation runs, coordinates metrics | SearchService, GoldenDataset, all ports |
| `core/eval/MetricsCalculator` | Pure Java retrieval metrics (P@k, Recall@k, MRR, NDCG) | None (stateless) |
| `core/eval/GoldenDataset` | Loads/parses JSONL dataset files | File system |
| `core/eval/port/LLMJudgePort` | Interface for LLM-based evaluation (faithfulness) | Implemented by infra |
| `core/eval/port/EmbeddingQualityPort` | Interface for embedding analysis (silhouette) | Implemented by infra |
| `core/eval/port/PythonAnalyticsPort` | Interface for Python-based analysis (UMAP, NetworkX) | Implemented by infra |
| `infra/eval/OllamaJudgeAdapter` | LangChain4j client for Ollama LLM-as-judge | Ollama service (HTTP) |
| `infra/eval/SmileEmbeddingAnalyzer` | SMILE library for clustering metrics | pgvector (JDBC) |
| `infra/eval/PythonSidecarClient` | REST client for Python analytics service | Python sidecar (HTTP) |
| `api/eval/EvalCommands` | Spring Shell commands for running evaluations | EvaluationService |

### Layer Responsibility Summary

**api/eval/**: Entry points for evaluation operations
- CLI commands: `eval run`, `eval report`, `eval benchmark`
- Optional REST endpoints for programmatic access
- Micrometer metrics exposure via Actuator

**core/eval/**: Business logic and domain models
- Pure Java retrieval metrics (no external dependencies)
- Evaluation orchestration and result aggregation
- Golden dataset parsing and validation
- Port interfaces for external integrations

**infra/eval/**: External integrations
- SMILE library for embedding quality analysis
- LangChain4j Ollama client for LLM-as-judge
- HTTP client for Python sidecar (UMAP, NetworkX)
- Micrometer registry configuration for VictoriaMetrics

## Data Flow

### 1. Retrieval Evaluation Flow (Pure Java)

```
CLI Command                Golden Dataset          SearchService         MetricsCalculator
    |                           |                        |                       |
    | eval run --retrieval      |                        |                       |
    +-------------------------->|                        |                       |
    |                           | load QAPairs           |                       |
    |                           +----------------------->|                       |
    |                           |                        | search(question)      |
    |                           |                        +---------------------->|
    |                           |                        |                       |
    |                           |                        | SearchResults         |
    |                           |                        |<----------------------+
    |                           |                        |                       |
    |                           | compare(expected,      |                       |
    |                           |         actual)        |                       |
    |                           +------------------------------------------------>|
    |                           |                        |                       |
    |                           |                        | RetrievalMetrics      |
    |                           |                        | (P@k, Recall@k, MRR)  |
    |<----------------------------------------------------------------------|
```

### 2. LLM-as-Judge Flow (Ollama)

```
EvaluationService         OllamaJudgeAdapter         Ollama Service
        |                         |                        |
        | evaluate(question,      |                        |
        |          context,       |                        |
        |          answer)        |                        |
        +------------------------>|                        |
        |                         | POST /api/chat         |
        |                         | (faithfulness prompt)  |
        |                         +----------------------->|
        |                         |                        |
        |                         | JSON response          |
        |                         |<-----------------------+
        |                         |                        |
        | FaithfulnessScore       |                        |
        |<------------------------+                        |
```

### 3. Embedding Quality Flow (SMILE)

```
EvaluationService       SmileEmbeddingAnalyzer       pgvector (JDBC)
        |                         |                        |
        | analyzeEmbeddings()     |                        |
        +------------------------>|                        |
        |                         | SELECT embedding       |
        |                         | FROM chunks            |
        |                         | LIMIT 10000            |
        |                         +----------------------->|
        |                         |                        |
        |                         | float[][]              |
        |                         |<-----------------------+
        |                         |                        |
        |                         | KMeans.fit()           |
        |                         | silhouetteScore()      |
        |                         |                        |
        | EmbeddingQualityMetrics |                        |
        |<------------------------+                        |
```

### 4. Python Analytics Flow (Sidecar)

```
EvaluationService       PythonSidecarClient       Python Sidecar
        |                         |                   |
        | analyzeGraph()          |                   |
        +------------------------>|                   |
        |                         | POST /analyze/wcc |
        |                         | (nodes, edges)    |
        |                         +------------------>|
        |                         |                   | NetworkX.wcc()
        |                         |                   |
        |                         | JSON response     |
        |                         |<------------------+
        | GraphMetrics            |                   |
        |<------------------------+                   |
```

### 5. Metrics Publishing Flow (Micrometer)

```
EvaluationService              Micrometer               VictoriaMetrics
        |                          |                           |
        | Timer.record()           |                           |
        | Gauge.set()              |                           |
        +------------------------->|                           |
        |                          | scrape /actuator/prometheus
        |                          |<--------------------------+
        |                          |                           |
        |                          | Prometheus text format    |
        |                          +-------------------------->|
        |                          |                           | store time series
```

## Patterns to Follow

### Pattern 1: Port Interface for External Tools

All external integrations go through port interfaces defined in core.

**What:** Define interfaces in `core/eval/port/` for any external tool
**When:** Integrating Ollama, SMILE, Python sidecar, or any future tool
**Why:** Enables testing with mocks, follows established Alexandria pattern

```java
// core/eval/port/LLMJudgePort.java
public interface LLMJudgePort {
    FaithfulnessResult evaluateFaithfulness(
        String question,
        List<String> contexts,
        String answer
    );

    RelevancyResult evaluateRelevancy(
        String question,
        String answer
    );
}

// infra/eval/OllamaJudgeAdapter.java
@Component
@ConditionalOnProperty(name = "alexandria.eval.ollama.enabled", havingValue = "true")
public class OllamaJudgeAdapter implements LLMJudgePort {
    private final ChatLanguageModel chatModel;

    public OllamaJudgeAdapter(
        @Value("${alexandria.eval.ollama.base-url}") String baseUrl,
        @Value("${alexandria.eval.ollama.model}") String modelName
    ) {
        this.chatModel = OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build();
    }
    // ...
}
```

### Pattern 2: Metrics via Micrometer Instrumentation

**What:** Instrument evaluation operations with Micrometer timers and gauges
**When:** Any operation that should be tracked in Grafana dashboards
**Why:** Automatic Prometheus-compatible export, consistent with Spring Boot patterns

```java
// core/eval/EvaluationService.java
@Service
public class EvaluationService {
    private final Timer evaluationTimer;
    private final Counter evaluationCounter;

    public EvaluationService(MeterRegistry registry) {
        this.evaluationTimer = Timer.builder("alexandria.eval.duration")
            .description("Evaluation run duration")
            .tag("type", "retrieval")
            .publishPercentileHistogram()
            .register(registry);

        this.evaluationCounter = Counter.builder("alexandria.eval.runs")
            .description("Total evaluation runs")
            .register(registry);
    }

    public EvaluationResult runEvaluation(EvaluationConfig config) {
        return evaluationTimer.record(() -> {
            evaluationCounter.increment();
            // ... evaluation logic
        });
    }
}
```

### Pattern 3: Docker Compose Profile for Optional Services

**What:** Use Docker Compose profiles to make eval stack optional
**When:** User wants evaluation features without always running Ollama/Grafana
**Why:** Minimal resource usage in production, full stack in dev/eval

```yaml
# docker-compose.yml additions
services:
  victoriametrics:
    image: victoriametrics/victoria-metrics:v1.105.0
    profiles: ["eval"]
    # ...

  ollama:
    image: ollama/ollama:latest
    profiles: ["eval"]
    # ...
```

Usage: `docker compose --profile eval up -d`

### Pattern 4: Golden Dataset as Test Resource

**What:** Store golden dataset in `src/test/resources/golden-dataset/`
**When:** Running evaluation tests, CI/CD pipelines
**Why:** Versioned with code, accessible to tests, separate from production data

```
src/test/resources/
  golden-dataset/
    v1.0/
      qa-pairs.jsonl
      metadata.json
      CHANGELOG.md
    current -> v1.0
```

```java
// test/java/.../eval/GoldenDatasetIT.java
@SpringBootTest
@ActiveProfiles("test")
class GoldenDatasetIT {
    @Autowired
    GoldenDataset goldenDataset;

    @Test
    void shouldLoadGoldenDataset() {
        var pairs = goldenDataset.load("classpath:golden-dataset/current/qa-pairs.jsonl");
        assertThat(pairs).hasSizeGreaterThan(100);
    }
}
```

### Pattern 5: Python Sidecar for Complex Analytics

**What:** Dedicated Python container exposing REST API for NetworkX/UMAP
**When:** Need Python-only libraries (NetworkX WCC, UMAP visualization)
**Why:** Cleaner than subprocess, testable, horizontally scalable

```python
# python-sidecar/main.py
from fastapi import FastAPI
import networkx as nx
import json

app = FastAPI()

@app.post("/analyze/wcc")
async def analyze_weakly_connected(data: GraphData):
    G = nx.DiGraph()
    G.add_nodes_from(data.nodes)
    G.add_edges_from(data.edges)

    components = list(nx.weakly_connected_components(G))
    return {
        "component_count": len(components),
        "largest_component_size": max(len(c) for c in components),
        "orphan_count": sum(1 for c in components if len(c) == 1)
    }

@app.post("/visualize/umap")
async def umap_projection(data: EmbeddingData):
    # ... UMAP projection logic
```

```yaml
# docker-compose.yml
  python-sidecar:
    build: ./python-sidecar
    profiles: ["eval"]
    ports:
      - "8000:8000"
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Subprocess Calls to Python

**What:** Using `ProcessBuilder` to run Python scripts
**Why bad:** Hard to test, fragile error handling, environment issues
**Instead:** Use Python sidecar REST API

```java
// BAD - Don't do this
ProcessBuilder pb = new ProcessBuilder("python", "analyze.py", "--input", file);
Process p = pb.start();

// GOOD - Use REST client
pythonSidecarClient.post("/analyze/wcc", graphData);
```

### Anti-Pattern 2: Eval Components in Core Without Ports

**What:** Direct dependencies on SMILE/Ollama in core package
**Why bad:** Violates hexagonal architecture, untestable
**Instead:** Define port interfaces, implement in infra

### Anti-Pattern 3: Hardcoded Metrics in Application Code

**What:** Pushing metrics directly to VictoriaMetrics HTTP API
**Why bad:** Tight coupling, breaks if metrics backend changes
**Instead:** Use Micrometer abstraction, configure registry

### Anti-Pattern 4: Eval Stack Always Running

**What:** Grafana/Ollama/VictoriaMetrics in default docker-compose profile
**Why bad:** Wastes resources when not evaluating, slow startup
**Instead:** Use `profiles: ["eval"]` to make optional

### Anti-Pattern 5: Mixing Evaluation Code with Production Code

**What:** Putting evaluation-only code in main src/
**Why bad:** Bloats production JAR, potential security surface
**Instead:** Use Maven profile with separate source set, or keep in test scope

## Docker Compose Orchestration

### Extended docker-compose.yml Structure

```yaml
services:
  # Existing services
  postgres:
    # ... existing config

  app:
    # ... existing config
    environment:
      # Add metrics endpoint
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health,prometheus

  # Evaluation stack (optional profile)
  victoriametrics:
    image: victoriametrics/victoria-metrics:v1.105.0
    profiles: ["eval"]
    container_name: alexandria-vm
    networks:
      - internal
    ports:
      - "8428:8428"
    volumes:
      - vm-data:/victoria-metrics-data
    command:
      - "-storageDataPath=/victoria-metrics-data"
      - "-retentionPeriod=30d"
      - "-httpListenAddr=:8428"
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.0.0
    profiles: ["eval"]
    container_name: alexandria-grafana
    networks:
      - internal
    ports:
      - "3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
      GF_INSTALL_PLUGINS: victoriametrics-metrics-datasource
    depends_on:
      - victoriametrics
    restart: unless-stopped

  loki:
    image: grafana/loki:3.0.0
    profiles: ["eval"]
    container_name: alexandria-loki
    networks:
      - internal
    ports:
      - "3100:3100"
    volumes:
      - loki-data:/loki
      - ./loki/config.yaml:/etc/loki/config.yaml:ro
    command: -config.file=/etc/loki/config.yaml
    restart: unless-stopped

  promtail:
    image: grafana/promtail:3.0.0
    profiles: ["eval"]
    container_name: alexandria-promtail
    networks:
      - internal
    volumes:
      - ./promtail/config.yaml:/etc/promtail/config.yaml:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
    command: -config.file=/etc/promtail/config.yaml
    depends_on:
      - loki
    restart: unless-stopped

  ollama:
    image: ollama/ollama:latest
    profiles: ["eval"]
    container_name: alexandria-ollama
    networks:
      - internal
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    restart: unless-stopped

  python-sidecar:
    build:
      context: ./python-sidecar
      dockerfile: Dockerfile
    profiles: ["eval"]
    container_name: alexandria-python
    networks:
      - internal
    ports:
      - "8000:8000"
    restart: unless-stopped

volumes:
  vm-data:
  grafana-data:
  loki-data:
  ollama-data:
```

### Service Dependencies and Startup Order

```
postgres (always) -----> app (always)
                            |
                            v (when eval profile)
                    +-------+-------+
                    |       |       |
                    v       v       v
              grafana  ollama  python-sidecar
                    |
                    v
            victoriametrics
                    |
                    v
                  loki
                    |
                    v
               promtail
```

### Resource Allocation (24 GB Server)

| Service | RAM | Purpose |
|---------|-----|---------|
| PostgreSQL + pgvector | 4 GB | Database + HNSW index |
| Alexandria app | 2 GB | ONNX model + JVM |
| VictoriaMetrics | 1-2 GB | Metrics storage (4x less than Prometheus) |
| Grafana | 300 MB | Dashboard UI |
| Loki | 500 MB | Log aggregation |
| Promtail | 50 MB | Log collection |
| Ollama (Mistral 7B) | 8 GB | LLM-as-judge |
| Python sidecar | 500 MB | NetworkX/UMAP |
| **Total** | **~17 GB** | Leaves 7 GB headroom |

## Scalability Considerations

| Concern | At 100 docs | At 10K docs | At 100K docs |
|---------|-------------|-------------|--------------|
| Golden dataset size | 100-200 QA pairs | 500 QA pairs | 1000+ QA pairs |
| Embedding export | In-memory | Paginated query | Streaming export |
| Silhouette score | Full calculation | Sample 10K | Sample 10K |
| UMAP visualization | Full dataset | Sample 5K | Sample 5K |
| Graph WCC | In-memory NetworkX | In-memory NetworkX | Consider igraph |
| Eval run duration | < 1 min | 5-15 min | 30-60 min |

## Suggested Build Order

Based on dependencies between components:

### Phase 1: Metrics Foundation (No external deps)

1. **Micrometer instrumentation** in existing services
   - Add timers to SearchService, IngestionService
   - Expose `/actuator/prometheus` endpoint

2. **Pure Java retrieval metrics** in `core/eval/`
   - MetricsCalculator with P@k, Recall@k, MRR
   - No external dependencies, easy to unit test

### Phase 2: Docker Compose Eval Stack

3. **VictoriaMetrics + Grafana** in docker-compose
   - Basic dashboard for existing metrics
   - Validates scraping pipeline works

4. **Loki + Promtail** for log aggregation
   - Useful independent of evaluation
   - Debug evaluation failures via logs

### Phase 3: Golden Dataset Infrastructure

5. **GoldenDataset loader** in `core/eval/`
   - Parse JSONL format
   - Validate schema

6. **EvaluationService orchestrator**
   - Coordinates retrieval eval runs
   - Produces EvaluationResult reports

### Phase 4: External Tool Integration

7. **SMILE integration** in `infra/eval/`
   - EmbeddingQualityPort + SmileEmbeddingAnalyzer
   - Silhouette score, clustering

8. **Python sidecar** for NetworkX/UMAP
   - Dockerfile + FastAPI service
   - PythonAnalyticsPort + PythonSidecarClient

### Phase 5: LLM-as-Judge

9. **Ollama service** in docker-compose
   - Model download automation
   - GPU passthrough if available

10. **OllamaJudgeAdapter** in `infra/eval/`
    - LLMJudgePort implementation
    - Faithfulness and relevancy evaluation

### Phase 6: CLI and Dashboards

11. **EvalCommands** in `api/eval/`
    - `eval run`, `eval report`, `eval benchmark`

12. **Grafana dashboards**
    - RAG performance dashboard
    - Evaluation results dashboard
    - Graph quality dashboard

## ArchUnit Extension

The existing ArchUnit tests should be extended to include the eval package:

```java
@ArchTest
static final ArchRule evalLayerDependencies = layeredArchitecture()
    .consideringAllDependencies()
    .layer("API").definedBy("..api..")
    .layer("Core").definedBy("..core..")
    .layer("Infra").definedBy("..infra..")
    // Eval follows same rules as other packages
    .whereLayer("API").mayNotBeAccessedByAnyLayer()
    .whereLayer("Core").mayOnlyBeAccessedByLayers("API", "Infra")
    .whereLayer("Infra").mayOnlyBeAccessedByLayers("Core");

@ArchTest
static final ArchRule evalCoreShouldNotDependOnInfra = noClasses()
    .that().resideInAPackage("..core.eval..")
    .should().dependOnClassesThat().resideInAPackage("..infra.eval..");
```

## Interface Contracts

### Golden Dataset JSON Schema

Based on the research document "Creer un golden dataset RAG hybride":

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "question", "ground_truth", "expected_contexts"],
  "properties": {
    "id": {"type": "string"},
    "question": {
      "type": "object",
      "properties": {
        "text": {"type": "string"},
        "type": {"enum": ["fact_single", "fact_multi", "summary", "reasoning",
                         "comparison", "temporal", "aggregation", "graph_traversal",
                         "multi_hop", "unanswerable"]},
        "difficulty": {"enum": ["easy", "medium", "hard"]},
        "reasoning_hops": {"type": "integer", "minimum": 1, "maximum": 5},
        "requires_kg": {"type": "boolean"}
      }
    },
    "ground_truth": {
      "type": "object",
      "properties": {
        "answer_text": {"type": "string"},
        "answer_type": {"enum": ["extractive", "abstractive", "boolean", "list", "numeric"]},
        "key_facts": {"type": "array", "items": {"type": "string"}}
      }
    },
    "expected_contexts": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "chunk_id": {"type": "string"},
          "content": {"type": "string"},
          "source_document": {"type": "string"},
          "relevance_label": {"type": "integer", "minimum": 0, "maximum": 2}
        }
      }
    }
  }
}
```

### Python Sidecar API Contract

```yaml
openapi: 3.0.0
info:
  title: Alexandria Python Analytics Sidecar
  version: 1.0.0

paths:
  /analyze/wcc:
    post:
      summary: Calculate weakly connected components
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                nodes:
                  type: array
                  items:
                    type: string
                edges:
                  type: array
                  items:
                    type: object
                    properties:
                      source: { type: string }
                      target: { type: string }
      responses:
        '200':
          description: WCC analysis result
          content:
            application/json:
              schema:
                type: object
                properties:
                  component_count: { type: integer }
                  largest_component_size: { type: integer }
                  orphan_count: { type: integer }

  /visualize/umap:
    post:
      summary: Generate UMAP projection
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                embeddings:
                  type: array
                  items:
                    type: array
                    items: { type: number }
                labels:
                  type: array
                  items: { type: string }
      responses:
        '200':
          description: UMAP projection coordinates
          content:
            application/json:
              schema:
                type: object
                properties:
                  x: { type: array, items: { type: number } }
                  y: { type: array, items: { type: number } }
```

## Metrics Naming Convention

Follow Prometheus naming conventions for all Micrometer metrics:

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `alexandria_eval_duration_seconds` | Timer | type, status | Evaluation run duration |
| `alexandria_eval_runs_total` | Counter | type, status | Total evaluation runs |
| `alexandria_eval_precision_at_k` | Gauge | k | Current P@k score |
| `alexandria_eval_recall_at_k` | Gauge | k | Current Recall@k score |
| `alexandria_eval_mrr` | Gauge | - | Current MRR score |
| `alexandria_eval_faithfulness` | Gauge | - | Current faithfulness score |
| `alexandria_embedding_silhouette` | Gauge | - | Current silhouette score |
| `alexandria_graph_orphan_ratio` | Gauge | - | Ratio of orphan nodes |
| `alexandria_search_duration_seconds` | Timer | type | Search latency (existing) |
| `alexandria_ingestion_duration_seconds` | Timer | - | Ingestion latency (existing) |

## Sources

### Official Documentation
- [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) - Micrometer integration
- [Micrometer Timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html) - Timer and histogram concepts
- [VictoriaMetrics Grafana Integration](https://docs.victoriametrics.com/victoriametrics/integrations/grafana/) - Datasource configuration
- [LangChain4j Ollama Integration](https://docs.langchain4j.dev/integrations/language-models/ollama/) - Java client for Ollama
- [Quarkus LangChain4j Testing](https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html) - Evaluation strategies
- [SMILE Machine Learning](https://haifengl.github.io/) - Java ML library
- [Grafana Loki Docker Install](https://grafana.com/docs/loki/latest/setup/install/docker/) - Log aggregation setup

### Community Resources
- [Docker Monitoring with VictoriaMetrics](https://dominikbritz.com/posts/victoria-metrics-grafana/) - Docker Compose patterns
- [Spring Boot Logging with Loki](https://dev.to/luafanti/spring-boot-logging-with-loki-promtail-and-grafana-loki-stack-aep) - Promtail configuration
- [Polyglot Microservices: Java + Python](https://medium.com/@ayushgupta228/polyglot-microservices-system-java-spring-boot-meets-python-d49426df0953) - IPC patterns
- [RAG Evaluation Guide 2025](https://www.getmaxim.ai/articles/complete-guide-to-rag-evaluation-metrics-methods-and-best-practices-for-2025/) - Metrics and best practices
- [Evidently AI RAG Evaluation](https://www.evidentlyai.com/llm-guide/rag-evaluation) - Component-level evaluation

### Confidence Assessment

| Area | Confidence | Reason |
|------|------------|--------|
| Hexagonal architecture integration | HIGH | Follows established Alexandria patterns |
| Micrometer/VictoriaMetrics | HIGH | Official Spring Boot integration, well-documented |
| Docker Compose profiles | HIGH | Standard Docker feature, tested patterns |
| SMILE silhouette score | MEDIUM | Library confirmed, specific silhouette API needs verification |
| Python sidecar pattern | HIGH | Standard microservices pattern |
| Quarkus LangChain4j scorer | LOW | Quarkus-specific, may not integrate directly with Spring Boot |
| LangChain4j Ollama client | HIGH | Official integration, well-documented |
| Golden dataset schema | HIGH | Based on prior research document |

---

*Architecture research for: RAG Evaluation Toolkit integration with Alexandria*
*Researched: 2026-01-24*
