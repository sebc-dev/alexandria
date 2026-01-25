# Technology Stack: RAG Evaluation Toolkit

**Project:** Alexandria v0.4 — RAG Evaluation Toolkit
**Researched:** 2026-01-24
**Overall Confidence:** MEDIUM (most core recommendations verified, some emerging tools need validation)

## Executive Summary

This stack recommendation builds on the existing Alexandria tech stack (Java 21, Spring Boot 3.4.7, LangChain4j 1.2.0) to add comprehensive RAG evaluation capabilities. The key insight: **stay in the Java ecosystem where possible**, use Python only for unavoidable gaps (graph export analysis, advanced visualization).

Critical finding: The Quarkus LangChain4j Testing extension is **not directly compatible with Spring Boot** — it requires Quarkus runtime. For Spring Boot, build custom evaluation services using LangChain4j core + Micrometer metrics.

## Recommended Stack

### Core Evaluation Framework

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **LangChain4j** | 1.2.0 (current) | LLM integration for evaluations | Already in project, stable API |
| **Micrometer** | 1.14.x (via Spring Boot) | Metrics collection | Native Spring Boot integration, Prometheus-compatible |
| **JUnit 5** | 5.11.x (via Spring Boot) | Test framework for evaluation suites | Standard, already in project |
| **AssertJ** | 3.26.x (via Spring Boot) | Fluent assertions for evaluation thresholds | Cleaner evaluation assertions |

**Maven coordinates:**
```xml
<!-- Already included via Spring Boot starter-test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Add for explicit Micrometer Prometheus registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Machine Learning / Clustering (SMILE)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **SMILE Core** | 3.1.1 | K-Means clustering, distance calculations | Mature Java ML library, LGPL-3.0 |
| **SMILE Math** | 3.1.1 | Linear algebra for silhouette calculation | Required dependency for core |

**CRITICAL NOTE:** SMILE does **NOT** include a built-in silhouette score implementation. You must implement it manually using SMILE's distance calculations and clustering results.

**Maven coordinates:**
```xml
<dependency>
    <groupId>com.github.haifengl</groupId>
    <artifactId>smile-core</artifactId>
    <version>3.1.1</version>
</dependency>
```

**Why version 3.1.1 instead of 5.x:**
- Version 5.x requires native BLAS/LAPACK installation (OpenBLAS, ARPACK)
- Version 3.1.1 is pure Java, simpler deployment, sufficient for evaluation metrics
- Version 5.x is overkill for silhouette score / K-Means on embeddings

**Silhouette score implementation pattern:**
```java
// Manual implementation required using SMILE's distance utilities
import smile.clustering.KMeans;
import smile.math.distance.EuclideanDistance;
import smile.math.distance.CosineDistance;

public class SilhouetteCalculator {

    /**
     * Calculate silhouette score for clustering result.
     * Uses cosine distance for embedding vectors.
     *
     * @param embeddings N x D matrix of embedding vectors
     * @param labels cluster assignment for each point
     * @return silhouette score in range [-1, 1]
     */
    public double silhouetteScore(double[][] embeddings, int[] labels) {
        int n = embeddings.length;
        double[] silhouettes = new double[n];
        CosineDistance distance = new CosineDistance();

        for (int i = 0; i < n; i++) {
            // a(i) = mean intra-cluster distance
            double a = meanIntraClusterDistance(embeddings, labels, i, distance);

            // b(i) = mean nearest-cluster distance
            double b = meanNearestClusterDistance(embeddings, labels, i, distance);

            // s(i) = (b - a) / max(a, b)
            silhouettes[i] = (b - a) / Math.max(a, b);
        }

        // Return mean silhouette
        return Arrays.stream(silhouettes).average().orElse(0.0);
    }

    private double meanIntraClusterDistance(double[][] embeddings, int[] labels,
                                            int pointIndex, CosineDistance distance) {
        int cluster = labels[pointIndex];
        double sum = 0;
        int count = 0;

        for (int j = 0; j < embeddings.length; j++) {
            if (j != pointIndex && labels[j] == cluster) {
                sum += distance.d(embeddings[pointIndex], embeddings[j]);
                count++;
            }
        }

        return count > 0 ? sum / count : 0;
    }

    private double meanNearestClusterDistance(double[][] embeddings, int[] labels,
                                              int pointIndex, CosineDistance distance) {
        int myCluster = labels[pointIndex];
        Map<Integer, List<Double>> clusterDistances = new HashMap<>();

        for (int j = 0; j < embeddings.length; j++) {
            if (labels[j] != myCluster) {
                double d = distance.d(embeddings[pointIndex], embeddings[j]);
                clusterDistances.computeIfAbsent(labels[j], k -> new ArrayList<>()).add(d);
            }
        }

        // Find cluster with minimum mean distance
        return clusterDistances.values().stream()
            .mapToDouble(distances -> distances.stream()
                .mapToDouble(Double::doubleValue).average().orElse(Double.MAX_VALUE))
            .min().orElse(0);
    }
}
```

### Graph Analysis (JGraphT)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **JGraphT Core** | 1.5.2 | Graph algorithms in pure Java | Avoids Python dependency for basic analysis |
| **JGraphT IO** | 1.5.2 | CSV/GraphML export | For NetworkX interop when needed |

**Maven coordinates:**
```xml
<dependency>
    <groupId>org.jgrapht</groupId>
    <artifactId>jgrapht-core</artifactId>
    <version>1.5.2</version>
</dependency>
<dependency>
    <groupId>org.jgrapht</groupId>
    <artifactId>jgrapht-io</artifactId>
    <version>1.5.2</version>
</dependency>
```

**Capabilities vs NetworkX:**

| Algorithm | JGraphT | NetworkX | Recommendation |
|-----------|---------|----------|----------------|
| Connected components | Yes (`ConnectivityInspector`) | Yes | Use JGraphT |
| Shortest path | Yes (`DijkstraShortestPath`) | Yes | Use JGraphT |
| Graph density | Yes (manual calc) | Yes | Use JGraphT |
| PageRank | Yes (`PageRank`) | Yes | Use JGraphT |
| Community detection | Limited | Yes (Louvain) | Export to NetworkX |
| Visualization | No | Matplotlib | Export to Python |

**When to use NetworkX (Python):**
- Community detection (Louvain algorithm)
- Graph visualization (matplotlib/networkx.draw)
- Advanced centrality measures

**Export pattern for NetworkX:**
```java
// Export from JGraphT to CSV for NetworkX
import org.jgrapht.nio.csv.CSVExporter;
import org.jgrapht.nio.csv.CSVFormat;

CSVExporter<String, DefaultEdge> exporter = new CSVExporter<>(CSVFormat.EDGE_LIST);
exporter.exportGraph(graph, new FileWriter("graph_edges.csv"));
```

**JGraphT usage for Knowledge Graph validation:**
```java
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class KnowledgeGraphValidator {

    public GraphMetrics validate(Graph<String, DefaultEdge> graph) {
        ConnectivityInspector<String, DefaultEdge> inspector =
            new ConnectivityInspector<>(graph);

        int componentCount = inspector.connectedSets().size();
        int orphanCount = (int) graph.vertexSet().stream()
            .filter(v -> graph.degreeOf(v) == 0)
            .count();

        double density = (double) graph.edgeSet().size() /
            (graph.vertexSet().size() * (graph.vertexSet().size() - 1));

        return new GraphMetrics(componentCount, orphanCount, density);
    }

    public record GraphMetrics(int connectedComponents, int orphans, double density) {}
}
```

### Monitoring Stack (VictoriaMetrics + Grafana + Loki)

| Technology | Version | Docker Image | Purpose |
|------------|---------|--------------|---------|
| **VictoriaMetrics** | 1.133.0 | `victoriametrics/victoria-metrics:v1.133.0` | Metrics storage (4x less RAM than Prometheus) |
| **Grafana** | 11.5.x | `grafana/grafana:11.5.0` | Dashboards and visualization |
| **Loki** | 3.6.0 | `grafana/loki:3.6.0` | Log aggregation |
| **Grafana Alloy** | 1.7.5 | `grafana/alloy:v1.7.5` | Log collection (replaces Promtail) |

**IMPORTANT:** Promtail is in maintenance mode (EOL 2026). Use Grafana Alloy for new deployments.

**Docker Compose snippet:**
```yaml
services:
  victoriametrics:
    image: victoriametrics/victoria-metrics:v1.133.0
    container_name: victoriametrics
    ports:
      - "8428:8428"
    volumes:
      - ./vm-data:/victoria-metrics-data
    command:
      - "--storageDataPath=/victoria-metrics-data"
      - "--httpListenAddr=:8428"
      - "--retentionPeriod=90d"
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.5.0
    container_name: grafana
    ports:
      - "3000:3000"
    volumes:
      - ./grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD:-admin}
    restart: unless-stopped

  loki:
    image: grafana/loki:3.6.0
    container_name: loki
    ports:
      - "3100:3100"
    volumes:
      - ./loki-data:/loki
      - ./loki-config.yaml:/etc/loki/local-config.yaml
    command: -config.file=/etc/loki/local-config.yaml
    restart: unless-stopped

  alloy:
    image: grafana/alloy:v1.7.5
    container_name: alloy
    volumes:
      - ./alloy-config.alloy:/etc/alloy/config.alloy
      - /var/run/docker.sock:/var/run/docker.sock:ro
    command: run --server.http.listen-addr=0.0.0.0:12345 /etc/alloy/config.alloy
    restart: unless-stopped
```

**RAM requirements:**
| Component | Typical RAM | Notes |
|-----------|-------------|-------|
| VictoriaMetrics | 1-2 GB | 4x less than Prometheus |
| Grafana | 300 MB | Lightweight |
| Loki | 500 MB | Index-only, not full-text |
| Alloy | 200 MB | Replaces Promtail |
| **Total** | ~2.5-3 GB | Fits 24 GB server easily |

### LLM-as-Judge (Ollama)

| Model | Parameters | VRAM | Use Case | Recommendation |
|-------|------------|------|----------|----------------|
| **Llama 3.3 70B** | 70B | 48 GB+ | Best quality judgments | If hardware available |
| **Llama 3.1 8B** | 8B | 8 GB | Good quality, fast | **RECOMMENDED for solo dev** |
| **Mistral 7B** | 7B | 6 GB | Fast, slightly lower quality | Budget option |
| **Qwen 2.5 7B** | 7B | 6 GB | Good reasoning | Alternative to Mistral |

**Recommendation: Llama 3.1 8B** for Alexandria v0.4

**Rationale:**
- MMLU score 73% vs Mistral 68% (better reasoning)
- 8 GB VRAM fits consumer GPUs (RTX 3060 12GB, RTX 4070)
- Sufficient for faithfulness/relevance judgments
- Faster than 70B models, acceptable quality tradeoff

**Ollama setup:**
```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull recommended model
ollama pull llama3.1:8b

# Verify
ollama run llama3.1:8b "Rate this answer for faithfulness: ..."
```

**LangChain4j integration:**
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
</dependency>
```

```java
import dev.langchain4j.model.ollama.OllamaChatModel;

ChatLanguageModel judge = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.1:8b")
    .temperature(0.0)  // Deterministic for judging
    .build();
```

### RAG Evaluation Metrics (Custom Implementation)

**No dedicated Spring Boot evaluation library exists.** Build custom evaluation services.

| Metric | Type | Implementation |
|--------|------|----------------|
| Precision@k | Retrieval | Pure Java, no LLM needed |
| Recall@k | Retrieval | Pure Java, no LLM needed |
| MRR (Mean Reciprocal Rank) | Retrieval | Pure Java, no LLM needed |
| NDCG | Retrieval | Pure Java, no LLM needed |
| Faithfulness | Generation | LLM-as-Judge (Ollama) |
| Answer Relevancy | Generation | LLM-as-Judge (Ollama) |
| Context Precision | Retrieval + LLM | LLM-as-Judge (Ollama) |

**Implementation approach:**
```java
@Service
public class RetrievalMetricsService {

    public double precisionAtK(List<String> retrieved, Set<String> relevant, int k) {
        long hits = retrieved.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / k;
    }

    public double recallAtK(List<String> retrieved, Set<String> relevant, int k) {
        long hits = retrieved.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / relevant.size();
    }

    public double mrr(List<String> retrieved, Set<String> relevant) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    public double ndcg(List<String> retrieved, Map<String, Integer> relevanceScores, int k) {
        double dcg = 0.0;
        double idcg = 0.0;

        // Calculate DCG
        for (int i = 0; i < Math.min(k, retrieved.size()); i++) {
            int relevance = relevanceScores.getOrDefault(retrieved.get(i), 0);
            dcg += (Math.pow(2, relevance) - 1) / (Math.log(i + 2) / Math.log(2));
        }

        // Calculate ideal DCG
        List<Integer> idealScores = relevanceScores.values().stream()
            .sorted(Comparator.reverseOrder())
            .limit(k)
            .toList();

        for (int i = 0; i < idealScores.size(); i++) {
            idcg += (Math.pow(2, idealScores.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }

        return idcg > 0 ? dcg / idcg : 0.0;
    }
}
```

## Alternatives Considered

| Category | Recommended | Alternative | Why Not Alternative |
|----------|-------------|-------------|---------------------|
| **Clustering** | SMILE 3.1.1 | Weka | Heavier, GPL license, less modern API |
| **Clustering** | SMILE 3.1.1 | Apache Spark MLlib | Overkill for single-node, complex setup |
| **Graph** | JGraphT | NetworkX (Python) | Avoid Python dependency for basic ops |
| **Metrics storage** | VictoriaMetrics | Prometheus | 4x more RAM, less efficient |
| **Logs** | Loki + Alloy | ELK Stack | Much heavier RAM footprint |
| **LLM Judge** | Llama 3.1 8B | Mistral 7B | Lower MMLU score (68% vs 73%) |
| **LLM Judge** | Llama 3.1 8B | Llama 3.3 70B | Requires 48GB+ VRAM |
| **Eval Framework** | Custom Java | Quarkus LangChain4j Testing | Requires Quarkus, not Spring Boot |
| **Eval Framework** | Custom Java | RAGAS (Python) | Adds Python dependency |

## What NOT to Use

### Quarkus LangChain4j Testing (Incompatible)

**Do NOT use** for Spring Boot projects:
```xml
<!-- DO NOT ADD - Quarkus only -->
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-testing-evaluation-junit5</artifactId>
</dependency>
```

**Why:** Requires Quarkus runtime (`@QuarkusTest`), CDI injection, Quarkus configuration system. Not portable to Spring Boot.

**Alternative:** Build custom evaluation services with LangChain4j core + JUnit 5.

### SMILE 5.x (Complex Dependencies)

**Avoid** unless you need deep learning features:
```xml
<!-- Avoid for simple evaluation use cases -->
<dependency>
    <groupId>com.github.haifengl</groupId>
    <artifactId>smile-core</artifactId>
    <version>5.1.0</version> <!-- Requires native BLAS -->
</dependency>
```

**Why:** Requires OpenBLAS/ARPACK native installation, complicates Docker builds.

### Promtail (Deprecated)

**Do NOT use** for new deployments:
```yaml
# DEPRECATED - EOL 2026
promtail:
  image: grafana/promtail:3.6.0
```

**Why:** Maintenance mode, will reach EOL. Use Grafana Alloy instead.

### Models < 7B for LLM-as-Judge

**Avoid** for evaluation judgments:
- Llama 3.2 3B
- Phi-3 mini (3.8B)
- Gemma 2B

**Why:** Produce unstable, inconsistent judgments. JSON parsing errors common.

## Complete Maven Dependencies

Add to `pom.xml` in a new `<profile id="evals">`:

```xml
<profile>
    <id>evals</id>
    <dependencies>
        <!-- SMILE for clustering/silhouette -->
        <dependency>
            <groupId>com.github.haifengl</groupId>
            <artifactId>smile-core</artifactId>
            <version>3.1.1</version>
        </dependency>

        <!-- JGraphT for graph analysis -->
        <dependency>
            <groupId>org.jgrapht</groupId>
            <artifactId>jgrapht-core</artifactId>
            <version>1.5.2</version>
        </dependency>
        <dependency>
            <groupId>org.jgrapht</groupId>
            <artifactId>jgrapht-io</artifactId>
            <version>1.5.2</version>
        </dependency>

        <!-- LangChain4j Ollama for LLM-as-Judge -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-ollama</artifactId>
        </dependency>

        <!-- Micrometer Prometheus for metrics export -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>
</profile>
```

## Docker Compose Extension

Create `docker-compose.monitoring.yml` for optional monitoring stack:

```yaml
# Extends main docker-compose.yml
# Usage: docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d

services:
  victoriametrics:
    image: victoriametrics/victoria-metrics:v1.133.0
    container_name: victoriametrics
    networks:
      - internal
    ports:
      - "8428:8428"
    volumes:
      - ./monitoring/vm-data:/victoria-metrics-data
    command:
      - "--storageDataPath=/victoria-metrics-data"
      - "--httpListenAddr=:8428"
      - "--retentionPeriod=90d"
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.5.0
    container_name: grafana
    networks:
      - internal
    ports:
      - "3000:3000"
    volumes:
      - ./monitoring/grafana-data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD:-admin}
    restart: unless-stopped

  loki:
    image: grafana/loki:3.6.0
    container_name: loki
    networks:
      - internal
    ports:
      - "3100:3100"
    volumes:
      - ./monitoring/loki-data:/loki
    restart: unless-stopped
```

## Python Integration (When Necessary)

For features unavailable in Java (community detection, visualization), use Python via subprocess:

**Export + Python pattern:**
```java
@Service
public class PythonAnalysisService {

    public CommunityDetectionResult detectCommunities(Path graphExportPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "python3", "scripts/community_detection.py",
            graphExportPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Python analysis failed: " + output);
        }

        return objectMapper.readValue(output, CommunityDetectionResult.class);
    }
}
```

**Python script (scripts/community_detection.py):**
```python
#!/usr/bin/env python3
import sys
import json
import networkx as nx
from networkx.algorithms.community import louvain_communities

def main():
    graph_path = sys.argv[1]

    # Load graph from CSV edge list
    G = nx.read_edgelist(graph_path, delimiter=',')

    # Detect communities
    communities = louvain_communities(G)

    result = {
        "community_count": len(communities),
        "communities": [list(c) for c in communities],
        "modularity": nx.algorithms.community.modularity(G, communities)
    }

    print(json.dumps(result))

if __name__ == "__main__":
    main()
```

## Sources

### HIGH Confidence (Official Documentation)
- [JGraphT Official Site](https://jgrapht.org/) - Maven coordinates, version 1.5.2
- [VictoriaMetrics Quick Start](https://docs.victoriametrics.com/victoriametrics/quick-start/) - Docker image v1.133.0
- [Grafana Loki Docker Install](https://grafana.com/docs/loki/latest/setup/install/docker/) - Version 3.6.0, Alloy migration
- [LangChain4j Spring Boot Integration](https://docs.langchain4j.dev/tutorials/spring-boot-integration/) - Starter naming convention
- [Quarkus LangChain4j Testing](https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html) - Requires Quarkus runtime
- [Micrometer Timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html) - Histogram configuration

### MEDIUM Confidence (Verified with Multiple Sources)
- [SMILE GitHub](https://github.com/haifengl/smile) - Version 3.1.1 vs 5.x tradeoffs
- [SMILE Clustering Documentation](https://haifengl.github.io/clustering.html) - No built-in silhouette score
- [Ollama Llama 3.3](https://ollama.com/library/llama3.3) - Model capabilities
- [Llama 3.1 vs Mistral benchmarks](https://www.practicalwebtools.com/blog/local-llm-benchmarks-consumer-hardware-guide-2025) - MMLU scores

### LOW Confidence (Needs Validation)
- Grafana version 11.5.0 - Inferred from recent release patterns, verify on Docker Hub
- Llama 3.1 8B vs Mistral 7B quality for LLM-as-Judge - Based on MMLU benchmarks, not judge-specific tests
- SMILE 3.1.1 API stability - Older version, may need to verify current Maven Central availability

## Gaps to Address in Implementation

1. **Silhouette score implementation** - SMILE lacks built-in method, manual implementation provided above
2. **Evaluation test framework** - No Spring Boot equivalent to Quarkus Testing, build custom services
3. **Golden dataset schema** - Define JSON schema matching research recommendations
4. **LLM-as-Judge prompts** - Design prompts for faithfulness/relevance scoring (see research docs)
5. **Grafana dashboards** - Create RAG-specific dashboard templates
6. **Python script packaging** - Decide on containerization vs local execution for Python analysis

## Roadmap Implications

Based on this stack research:

1. **Phase 1: Metrics Infrastructure** - Add Micrometer + VictoriaMetrics/Grafana
2. **Phase 2: Retrieval Metrics** - Pure Java implementation (Precision@k, Recall@k, MRR, NDCG)
3. **Phase 3: Embedding Evaluation** - SMILE clustering + custom silhouette score
4. **Phase 4: Graph Validation** - JGraphT for basic analysis, Python export for advanced
5. **Phase 5: Golden Dataset** - JSON schema + manual curation tooling
6. **Phase 6: LLM-as-Judge** - Ollama + LangChain4j integration for faithfulness/relevance
