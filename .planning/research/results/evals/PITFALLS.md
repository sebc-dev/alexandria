# Domain Pitfalls: RAG Evaluation Toolkit

**Domain:** RAG Evaluation System (Retrieval Metrics, pgvector Benchmarks, LLM-as-Judge, Monitoring)
**Project Context:** Java 21, Spring Boot 3.4.7, PostgreSQL 17, pgvector 0.8.1, Apache AGE, LangChain4j 1.2.0
**Constraints:** Personal use, offline-first (local embeddings, local LLM via Ollama), single container for PostgreSQL+pgvector+AGE
**Researched:** 2026-01-24
**Confidence:** HIGH (verified with official docs and multiple authoritative sources)

---

## Critical Pitfalls

Mistakes that cause incorrect metrics, misleading results, or require significant rework.

---

### Pitfall 1: Wrong Distance Metric for Embeddings (Euclidean instead of Cosine)

**What goes wrong:**
Using Euclidean distance (`<->` operator) instead of cosine distance (`<=>` operator) for `all-MiniLM-L6-v2` embeddings produces semantically incorrect similarity rankings. Documents with longer text appear less similar regardless of semantic content.

**Why it happens:**
pgvector supports multiple distance functions, and developers may default to Euclidean (L2) distance from familiarity with other contexts. The `all-MiniLM-L6-v2` model was specifically trained using cosine similarity loss.

**Consequences:**
- Retrieval Precision@k and Recall@k metrics are artificially low
- Benchmark results are not comparable to published baselines
- Longer documents systematically rank lower regardless of relevance
- Silhouette score calculations are invalid

**Warning signs:**
- Retrieval metrics significantly below published benchmarks for the model
- Long documents consistently rank lower than short documents
- UMAP visualizations show unexpected clustering patterns

**Prevention strategy:**
1. **Verify index creation uses `vector_cosine_ops`:**
   ```sql
   CREATE INDEX ON chunks USING hnsw (embedding vector_cosine_ops);
   ```
2. **Verify queries use `<=>` operator (cosine distance):**
   ```sql
   SELECT * FROM chunks ORDER BY embedding <=> query_vec LIMIT 10;
   ```
3. **Add assertion in retrieval code:**
   ```java
   // Fail-fast if wrong operator detected in query
   assert query.contains("<=>") : "Must use cosine distance for all-MiniLM-L6-v2";
   ```

**Detection:**
- Check index definition: `\d chunks` in psql should show `vector_cosine_ops`
- Review all SQL queries in repository for `<->` operator usage

**Phase to address:** Phase 1 - Retrieval Metrics (verify before any benchmarking)

**Sources:**
- [Pinecone: Vector Similarity Explained](https://www.pinecone.io/learn/vector-similarity/)
- [Weaviate: Distance Metrics in Vector Search](https://weaviate.io/blog/distance-metrics-in-vector-search)
- [Google Cloud: Choose Vector Distance Function](https://cloud.google.com/spanner/docs/choose-vector-distance-function)

---

### Pitfall 2: Mismatched K Values Between Retrieval and Ground Truth

**What goes wrong:**
Calculating Precision@10 when ground truth only has 3 relevant documents makes perfect precision mathematically impossible. Calculating Recall@3 when there are 10 relevant documents caps recall at 30% even with perfect retrieval.

**Why it happens:**
K values are chosen arbitrarily ("use k=10 because it's common") without considering the ground truth distribution. Different test samples have varying numbers of relevant documents.

**Consequences:**
- Precision scores are artificially capped below 1.0
- Recall scores are artificially capped below 1.0
- Metrics become incomparable across datasets
- Aggregate scores are misleading

**Warning signs:**
- Precision or Recall never exceeds certain thresholds regardless of tuning
- Metrics vary wildly across different test samples
- Average Precision@k doesn't improve with better retrieval

**Prevention strategy:**
1. **Analyze ground truth distribution before choosing K:**
   ```java
   // Before setting K, check how many relevant docs per query
   var relevantCounts = groundTruth.stream()
       .mapToInt(gt -> gt.getRelevantDocIds().size())
       .toArray();
   int medianRelevant = calculateMedian(relevantCounts);
   int maxRelevant = Arrays.stream(relevantCounts).max().orElse(0);
   // Set K appropriately: for Recall, K >= maxRelevant
   // For Precision, understand upper bound = min(K, relevant) / K
   ```
2. **Report multiple K values:** Precision@{3,5,10} and Recall@{5,10,20}
3. **Use MRR (Mean Reciprocal Rank)** for K-independent ranking quality
4. **Document ground truth statistics** in golden dataset metadata

**Detection:**
- Check if max(Precision@k) == min(k, max_relevant) / k
- Verify ground truth JSON includes `relevant_count` per sample

**Phase to address:** Phase 1 - Retrieval Metrics (design decision before implementation)

**Sources:**
- [Towards Data Science: Precision@k, Recall@k, and F1@k](https://towardsdatascience.com/how-to-evaluate-retrieval-quality-in-rag-pipelines-precisionk-recallk-and-f1k/)
- [Weaviate: Evaluation Metrics for Search](https://weaviate.io/blog/retrieval-evaluation-metrics)
- [Evidently AI: Precision and Recall at K](https://www.evidentlyai.com/ranking-metrics/precision-recall-at-k)

---

### Pitfall 3: Silhouette Score O(N^2) Complexity Causing OOM

**What goes wrong:**
Running silhouette score calculation on the full embedding corpus (10,000+ documents) causes memory exhaustion or multi-hour computation times. The application crashes or becomes unresponsive.

**Why it happens:**
Silhouette score requires computing pairwise distances between all points, resulting in O(N^2) memory and time complexity. For N=10,000, this means 100 million distance calculations.

**Consequences:**
- JVM OutOfMemoryError
- Evaluation pipeline hangs indefinitely
- CI/CD timeouts
- Developers disable embedding evaluation entirely

**Warning signs:**
- Memory usage spikes during embedding evaluation
- Evaluation phase takes orders of magnitude longer than retrieval
- "java.lang.OutOfMemoryError: Java heap space" errors

**Prevention strategy:**
1. **Sample before computing:**
   ```java
   // Sample max 5000 embeddings for silhouette calculation
   List<float[]> sample = embeddings.size() > 5000
       ? stratifiedSample(embeddings, 5000)
       : embeddings;
   double silhouette = calculateSilhouetteScore(sample);
   ```
2. **Use SMILE library's efficient implementation** (native Java, optimized)
3. **Set memory-based limits:**
   ```java
   int maxSampleSize = (int) Math.sqrt(Runtime.getRuntime().maxMemory() / 1500);
   ```
4. **Document sampling in metrics output:**
   ```json
   {"silhouette_score": 0.32, "sample_size": 5000, "total_documents": 15000}
   ```

**Detection:**
- Add memory monitoring before silhouette calculation
- Set timeout for embedding evaluation phase

**Phase to address:** Phase 2 - Embeddings Evaluation (critical design constraint)

**Sources:**
- [Research document: "Validation d'un systeme RAG hybride" - Section on silhouette score complexity]

---

### Pitfall 4: pgvector Benchmarks on Cold Cache (Non-Representative Results)

**What goes wrong:**
Running pgvector benchmarks immediately after PostgreSQL restart or after clearing caches produces latencies 10-100x higher than production reality. These results incorrectly suggest pgvector is too slow.

**Why it happens:**
HNSW index traversal requires reading graph nodes from disk. First access loads pages into shared_buffers. Benchmark runs before cache warming measure disk I/O, not index efficiency.

**Consequences:**
- Benchmark p50 latency of 50ms when production is 0.5ms
- False conclusions about need for more hardware
- Incorrect parameter tuning decisions
- Non-reproducible results between runs

**Warning signs:**
- First benchmark run is 10-100x slower than subsequent runs
- Huge variance in latency measurements
- Benchmark results don't match query latencies in Grafana

**Prevention strategy:**
1. **Pre-warm index before benchmarking:**
   ```sql
   -- Warm the index pages into shared_buffers
   SELECT pg_prewarm('chunks_embedding_idx');
   -- Also warm the table
   SELECT pg_prewarm('chunks');
   ```
2. **Run warmup queries before measurement:**
   ```java
   // Discard first 100 queries as warmup
   for (int i = 0; i < 100; i++) {
       runQuery(randomVector());
   }
   // Now measure
   for (int i = 0; i < 1000; i++) {
       measureQuery(testVectors.get(i));
   }
   ```
3. **Use different vectors for warmup vs measurement** (prevents query cache effects)
4. **Document cache state in benchmark results:**
   ```json
   {"cache_state": "warm", "prewarm_method": "pg_prewarm", "warmup_queries": 100}
   ```

**Detection:**
- Compare first 10 queries vs last 10 queries latency
- Check `pg_stat_user_tables` for `heap_blks_hit` vs `heap_blks_read` ratio

**Phase to address:** Phase 2 - pgvector Benchmarking (setup procedure)

**Sources:**
- [pgvector GitHub Issue #666: Search slow after cache clean](https://github.com/pgvector/pgvector/issues/666)
- [Crunchy Data: pgvector Performance Tips](https://www.crunchydata.com/blog/pgvector-performance-for-developers)
- [Jonathan Katz: 150x pgvector speedup](https://jkatz05.com/post/postgres/pgvector-performance-150x-speedup/)

---

### Pitfall 5: pgvector Benchmark Without LIMIT Clause (No Index Usage)

**What goes wrong:**
Benchmark queries without `LIMIT k` cause PostgreSQL to perform sequential scan instead of HNSW index traversal. Benchmarks measure full table scan performance, not vector search performance.

**Why it happens:**
HNSW is an approximate nearest neighbor index optimized for top-k queries. Without LIMIT, PostgreSQL correctly determines that scanning all rows is necessary and bypasses the index.

**Consequences:**
- Benchmark shows O(N) performance instead of O(log N)
- False conclusion that index is not working
- Incorrect ef_search tuning (parameter has no effect on seq scan)
- Wasted time investigating "index not being used"

**Warning signs:**
- Query latency scales linearly with table size
- `EXPLAIN ANALYZE` shows "Seq Scan" instead of "Index Scan"
- ef_search parameter changes have no effect on latency

**Prevention strategy:**
1. **Always include ORDER BY and LIMIT in vector queries:**
   ```sql
   -- Correct: uses HNSW index
   SELECT * FROM chunks
   ORDER BY embedding <=> $1
   LIMIT 10;

   -- Wrong: triggers sequential scan
   SELECT * FROM chunks
   WHERE embedding <=> $1 < 0.5;
   ```
2. **Verify index usage with EXPLAIN:**
   ```sql
   EXPLAIN ANALYZE SELECT * FROM chunks ORDER BY embedding <=> $1 LIMIT 10;
   -- Must show: "Index Scan using chunks_embedding_idx"
   ```
3. **Add integration test validating index usage:**
   ```java
   @Test
   void vectorQueryUsesHnswIndex() {
       String plan = jdbcTemplate.queryForObject(
           "EXPLAIN SELECT * FROM chunks ORDER BY embedding <=> ? LIMIT 10",
           String.class, testVector);
       assertThat(plan).contains("Index Scan");
   }
   ```

**Detection:**
- Run `EXPLAIN ANALYZE` on all benchmark queries
- Monitor `pg_stat_user_indexes` for `idx_scan` count on vector index

**Phase to address:** Phase 2 - pgvector Benchmarking (query design)

**Sources:**
- [Research document: "Validation d'un systeme RAG hybride" - pgvector HNSW section]
- [AWS: pgvector 0.8.0 on Aurora](https://aws.amazon.com/blogs/database/supercharging-vector-search-performance-and-relevance-with-pgvector-0-8-0-on-amazon-aurora-postgresql/)

---

### Pitfall 6: Apache AGE Missing Graph Algorithms (Assuming Neo4j Feature Parity)

**What goes wrong:**
Attempting to use `apoc.algo.wcc()`, `gds.pageRank()`, or other Neo4j graph algorithms in Apache AGE queries. These procedures don't exist and queries fail with "function not found" errors.

**Why it happens:**
Developers familiar with Neo4j assume Cypher implementations are equivalent. AGE implements Cypher query language but NOT Neo4j's APOC library or Graph Data Science library.

**Consequences:**
- Blocked development when graph metrics are needed
- Significant rework to implement alternative approach
- Delayed milestone if graph validation is on critical path

**Warning signs:**
- Query errors mentioning undefined functions
- Neo4j examples from tutorials don't work in AGE
- Cypher queries that work in Neo4j sandbox fail in Alexandria

**Prevention strategy:**
1. **Know what AGE does NOT support:**
   - `apoc.*` procedures (APOC library)
   - `gds.*` procedures (Graph Data Science)
   - `algo.*` procedures (legacy algorithms)
   - Weakly Connected Components (WCC)
   - PageRank, Betweenness Centrality, etc.

2. **Export to NetworkX for graph algorithms:**
   ```sql
   -- Export nodes
   COPY (SELECT id, properties FROM cypher('alexandria', $$ MATCH (n) RETURN n $$) AS (n agtype))
   TO '/tmp/nodes.csv' WITH CSV HEADER;

   -- Export edges
   COPY (SELECT start_id, end_id FROM cypher('alexandria', $$ MATCH ()-[r]->() RETURN r $$) AS (r agtype))
   TO '/tmp/edges.csv' WITH CSV HEADER;
   ```

3. **Use Cypher-native queries for what IS supported:**
   ```sql
   -- Orphan detection (works in AGE)
   SELECT * FROM cypher('alexandria', $$
       MATCH (n) WHERE NOT (n)-[]-() RETURN n
   $$) AS (orphan agtype);

   -- Path queries (works in AGE)
   SELECT * FROM cypher('alexandria', $$
       MATCH p = shortestPath((a:Concept)-[*]-(b:Concept))
       WHERE a.name = 'Java' AND b.name = 'Spring'
       RETURN p
   $$) AS (path agtype);
   ```

**Detection:**
- Test all Cypher queries against AGE before implementing validation code
- Maintain a "tested in AGE" status for each planned query

**Phase to address:** Phase 3 - Graph Validation (early spike to verify capabilities)

**Sources:**
- [Research document: "Validation d'un systeme RAG hybride" - Apache AGE section]
- [DEV.to: Apache AGE vs Neo4j](https://dev.to/pawnsapprentice/apache-age-vs-neo4j-battle-of-the-graph-databases-2m4)
- [DEV.to: Comparing Apache AGE and Neo4j](https://dev.to/k1hara/comparing-apache-age-and-neo4j-choosing-the-right-graph-database-for-your-needs-54eh)

---

### Pitfall 7: LLM-as-Judge JSON Parsing Failures with Small Models

**What goes wrong:**
Using Ollama with models smaller than 7B parameters for LLM-as-Judge evaluation produces malformed JSON that breaks automated parsing. Evaluation pipeline fails intermittently.

**Why it happens:**
Smaller models (Phi-2, TinyLlama, etc.) have weaker instruction following and often:
- Add explanatory text outside JSON structure
- Miss closing braces or brackets
- Use single quotes instead of double quotes
- Include markdown code fences around JSON

**Consequences:**
- `JsonParseException` crashes evaluation pipeline
- Inconsistent evaluation results
- Lost evaluation runs requiring manual restart
- Unreliable faithfulness/relevancy scores

**Warning signs:**
- Intermittent `com.fasterxml.jackson.core.JsonParseException`
- LLM output starts with "Here is the JSON:" before actual JSON
- Evaluation scores are null for some samples
- stderr shows JSON parsing errors during eval runs

**Prevention strategy:**
1. **Use minimum 7B parameter models:**
   - Mistral 7B (recommended)
   - Llama 3.1 8B
   - Gemma 2 9B
   - Avoid: Phi-2 (2.7B), TinyLlama (1.1B), Gemma 2B

2. **Implement robust JSON extraction:**
   ```java
   public String extractJson(String llmOutput) {
       // Try direct parse first
       try {
           objectMapper.readTree(llmOutput);
           return llmOutput;
       } catch (JsonProcessingException e) {
           // Fallback: extract JSON from markdown
           var matcher = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```",
               Pattern.DOTALL).matcher(llmOutput);
           if (matcher.find()) {
               return matcher.group(1);
           }
           // Fallback: find first { to last }
           int start = llmOutput.indexOf('{');
           int end = llmOutput.lastIndexOf('}');
           if (start >= 0 && end > start) {
               return llmOutput.substring(start, end + 1);
           }
           throw new JsonExtractionException("No valid JSON found", llmOutput);
       }
   }
   ```

3. **Add retry logic with temperature variation:**
   ```java
   public EvalResult evaluateWithRetry(String prompt, int maxRetries) {
       for (int i = 0; i < maxRetries; i++) {
           try {
               float temp = 0.1f + (i * 0.1f); // Increase temp on retry
               String response = llm.generate(prompt, temp);
               return parseEvalResult(extractJson(response));
           } catch (JsonExtractionException e) {
               log.warn("JSON extraction failed, retry {}/{}", i+1, maxRetries);
           }
       }
       throw new EvaluationException("Failed after " + maxRetries + " retries");
   }
   ```

4. **Use Ollama's structured output feature:**
   ```bash
   # Ollama now supports JSON mode
   curl http://localhost:11434/api/generate -d '{
     "model": "mistral",
     "prompt": "...",
     "format": "json"
   }'
   ```

**Detection:**
- Log all raw LLM responses during development
- Track JSON parse failure rate as a metric
- Alert if failure rate > 5%

**Phase to address:** Phase 4 - LLM-as-Judge (critical for reliability)

**Sources:**
- [Research document: "Validation d'un systeme RAG hybride" - LLM-as-Judge section]
- [Ollama Blog: Structured Outputs](https://ollama.com/blog/structured-outputs)
- [Monte Carlo: LLM-as-Judge Best Practices](https://www.montecarlodata.com/blog-llm-as-judge/)
- [Promptfoo: Evaluate JSON](https://www.promptfoo.dev/docs/guides/evaluate-json/)

---

### Pitfall 8: LLM-as-Judge Self-Enhancement Bias

**What goes wrong:**
Using the same LLM model for both RAG generation and evaluation produces inflated quality scores. The evaluator favors outputs that match its own generation patterns.

**Why it happens:**
LLMs have consistent stylistic patterns and reasoning approaches. When the same model generates and judges, it rates its own preferred style higher than potentially better alternatives.

**Consequences:**
- Faithfulness scores 10-15% higher than human judgment
- Overconfidence in RAG quality
- Missing real quality issues
- Misleading baseline comparisons

**Warning signs:**
- Evaluation scores consistently > 0.9 across all samples
- Poor user feedback despite high automated scores
- Scores don't improve with obvious retrieval improvements

**Prevention strategy:**
1. **Use different models for generation vs evaluation:**
   ```yaml
   # config/eval.yml
   generation:
     model: mistral:7b-instruct
   evaluation:
     model: llama3.1:8b-instruct  # Different model family
   ```

2. **When using same model, use different temperatures:**
   ```java
   // Generation: creative (higher temp)
   var answer = llm.generate(ragPrompt, temperature=0.7);
   // Evaluation: deterministic (low temp)
   var score = llm.generate(evalPrompt, temperature=0.1);
   ```

3. **Calibrate with human judgments:**
   - Sample 20-30 evaluations
   - Have human rate same samples
   - Calculate correlation coefficient
   - Flag if correlation < 0.7

4. **Use ensemble of judges:**
   ```java
   double faithfulness = Stream.of(
       evaluateWith("mistral"),
       evaluateWith("llama3"),
       evaluateWith("gemma")
   ).mapToDouble(Double::doubleValue).average().orElse(0);
   ```

**Detection:**
- Track score distribution (should have variance, not all >0.9)
- Periodically validate against human judgment sample

**Phase to address:** Phase 4 - LLM-as-Judge (evaluation design)

**Sources:**
- [Research document: "Golden dataset RAG hybride" - Anti-biais strategies]
- [Evidently AI: LLM-as-a-Judge Guide](https://www.evidentlyai.com/llm-guide/llm-as-a-judge)
- [Hugging Face Cookbook: LLM Judge](https://huggingface.co/learn/cookbook/en/llm_judge)

---

## Moderate Pitfalls

Mistakes that cause delays, technical debt, or suboptimal results.

---

### Pitfall 9: Prometheus RAM Consumption Spikes (OOM in Constrained Environment)

**What goes wrong:**
Prometheus memory usage spikes from 6GB to 23GB during high cardinality metric ingestion, triggering OOM killer on the 24GB development server. Monitoring goes blind during the incident it should capture.

**Why it happens:**
Prometheus stores all time series in memory. High cardinality labels (e.g., unique request IDs, user sessions) create millions of time series. Each series consumes ~3KB RAM.

**Consequences:**
- OOM kills Prometheus during load testing
- Lost metrics during critical evaluation runs
- System instability affects other containers
- No visibility into what went wrong

**Warning signs:**
- Prometheus memory grows continuously
- Docker stats shows prometheus container at 80%+ memory
- Alerts about Prometheus being down

**Prevention strategy:**
1. **Use VictoriaMetrics instead of Prometheus:**
   ```yaml
   services:
     victoriametrics:
       image: victoriametrics/victoria-metrics:v1.93.0
       command:
         - '-retentionPeriod=30d'
         - '-memory.allowedPercent=60'  # Cap memory usage
       deploy:
         resources:
           limits:
             memory: 2G
   ```

2. **Benefits of VictoriaMetrics:**
   - 4-5x lower RAM consumption (4.3GB stable vs 6-23GB)
   - Compatible with Prometheus scrape configs
   - Compatible with PromQL queries
   - Compatible with Grafana

3. **If staying with Prometheus, limit cardinality:**
   ```yaml
   # prometheus.yml - drop high cardinality labels
   metric_relabel_configs:
     - source_labels: [request_id]
       action: drop
   ```

**Detection:**
- Monitor Prometheus/VictoriaMetrics container memory
- Set memory limits in Docker Compose
- Alert on memory > 70% of limit

**Phase to address:** Phase 5 - Monitoring Stack (architecture decision)

**Sources:**
- [VictoriaMetrics: Comparing Agents](https://victoriametrics.com/blog/comparing-agents-for-scraping/)
- [Last9: Prometheus vs VictoriaMetrics](https://last9.io/blog/prometheus-vs-victoriametrics/)
- [SigNoz: Prometheus High Memory](https://signoz.io/guides/why-does-prometheus-consume-so-much-memory/)

---

### Pitfall 10: Loki Retention Not Enabled by Default

**What goes wrong:**
Loki disk usage grows indefinitely until disk is full. At that point, Loki crashes with "no space left on device" and cannot even serve queries.

**Why it happens:**
By default, `compactor.retention-enabled` is false in Loki. Logs are stored forever. There is no size-based retention in Loki, only time-based.

**Consequences:**
- Disk fills up over weeks/months
- Loki becomes unresponsive
- Cannot query historical logs
- Manual cleanup required

**Warning signs:**
- Loki data directory growing continuously
- No log deletion visible in compactor logs
- Disk usage alerts not triggered because growth is gradual

**Prevention strategy:**
1. **Enable retention explicitly:**
   ```yaml
   # loki-config.yml
   compactor:
     working_directory: /loki/compactor
     retention_enabled: true
     retention_delete_delay: 2h
     delete_request_store: filesystem

   limits_config:
     retention_period: 168h  # 7 days
   ```

2. **Configure compactor as singleton with persistent storage:**
   ```yaml
   services:
     loki:
       volumes:
         - loki-data:/loki  # Persistent for compactor state
   ```

3. **Use 24h index period (required for retention):**
   ```yaml
   schema_config:
     configs:
       - from: 2024-01-01
         index:
           prefix: loki_index_
           period: 24h  # Required for retention
   ```

4. **Monitor disk usage:**
   ```yaml
   # Alert rule
   - alert: LokiDiskUsageHigh
     expr: container_fs_usage_bytes{container="loki"} / container_fs_limit_bytes > 0.8
     for: 5m
   ```

**Detection:**
- Check Loki config for `retention_enabled: true`
- Monitor Loki data directory size
- Verify compactor logs show deletion activity

**Phase to address:** Phase 5 - Monitoring Stack (Loki configuration)

**Sources:**
- [Grafana Loki: Retention Documentation](https://grafana.com/docs/loki/latest/operations/storage/retention/)
- [GitHub Loki Issue #5242: Retention not working](https://github.com/grafana/loki/issues/5242)
- [Grafana Community: Loki disk inflation](https://community.grafana.com/t/loki-disk-space-inflation/109287)

---

### Pitfall 11: Docker Compose depends_on Without Health Check

**What goes wrong:**
Application container starts before PostgreSQL is ready to accept connections. Application fails to connect, crashes, and requires manual restart after PostgreSQL is ready.

**Why it happens:**
`depends_on` only waits for container to start, not for service to be ready. PostgreSQL takes 5-30 seconds to initialize (longer on first run with AGE extension loading).

**Consequences:**
- Application crashes on startup
- Flaky CI/CD pipelines
- Developer frustration during local setup
- Race conditions in integration tests

**Warning signs:**
- "Connection refused" errors in application logs at startup
- Application restarts in Docker Compose logs
- Intermittent test failures in CI

**Prevention strategy:**
1. **Add health check to PostgreSQL:**
   ```yaml
   services:
     postgres:
       image: custom-pg17-age-pgvector
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
         interval: 10s
         timeout: 5s
         retries: 5
         start_period: 60s  # AGE extension loading takes time
   ```

2. **Use `condition: service_healthy`:**
   ```yaml
   services:
     alexandria:
       depends_on:
         postgres:
           condition: service_healthy
           restart: true  # Restart if postgres restarts
   ```

3. **For AGE, ensure extension is loaded:**
   ```yaml
   healthcheck:
     test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER && psql -U $$POSTGRES_USER -d $$POSTGRES_DB -c \"LOAD 'age'; SELECT 1;\""]
   ```

4. **Use environment variable escaping (`$$`)** to prevent Compose interpolation

**Detection:**
- Check docker-compose.yml for `condition: service_healthy`
- Test `docker compose up` from clean state

**Phase to address:** Phase 6 - Docker Orchestration (infrastructure setup)

**Sources:**
- [Docker Docs: Control Startup Order](https://docs.docker.com/compose/how-tos/startup-order/)
- [GitHub: docker-compose-healthcheck](https://github.com/peter-evans/docker-compose-healthcheck)
- [BetterLink: Docker Compose Healthcheck](https://eastondev.com/blog/en/posts/dev/20251217-docker-compose-healthcheck/)

---

### Pitfall 12: Golden Dataset Overfitting

**What goes wrong:**
RAG system is tuned to achieve high scores on golden dataset but performs poorly on real user queries. The evaluation metrics don't reflect actual user satisfaction.

**Why it happens:**
Hyperparameters (chunk size, overlap, ef_search, reranker threshold) are optimized on the entire golden dataset. The dataset doesn't cover edge cases in production.

**Consequences:**
- High test scores, low user satisfaction
- False confidence before deployment
- Wasted optimization effort on wrong direction
- Delayed discovery of real issues

**Warning signs:**
- Scores plateau near perfect despite system changes
- Production queries fail that golden dataset should have caught
- Users report issues not represented in test set

**Prevention strategy:**
1. **Reserve 20% holdout set that is NEVER used for tuning:**
   ```
   golden_dataset/
   ├── train/        # 160 samples - use for development
   └── holdout/      # 40 samples - ONLY for final validation
   ```

2. **Add production queries to dataset monthly:**
   ```java
   // Track queries that resulted in poor user feedback
   if (userFeedback.isNegative()) {
       candidateQueriesForGoldenSet.add(query);
   }
   ```

3. **Compare golden set vs production metrics:**
   ```java
   var goldenRecall = evaluate(goldenSet);
   var productionRecall = sampleAndEvaluate(productionQueries);
   if (Math.abs(goldenRecall - productionRecall) > 0.15) {
       alertDrift("Golden set may not represent production");
   }
   ```

4. **Version the golden dataset:**
   ```
   golden_dataset/
   ├── v1.0/
   │   ├── samples.jsonl
   │   ├── metadata.json
   │   └── CHANGELOG.md
   └── current -> v1.0/
   ```

**Detection:**
- Track correlation between golden set scores and user satisfaction
- Monitor if hyperparameter changes only improve golden set

**Phase to address:** Phase 1 - Retrieval Metrics (methodology design)

**Sources:**
- [Research document: "Golden dataset RAG hybride" - Anti-overfitting section]
- [Pinecone: RAG Evaluation](https://www.pinecone.io/learn/series/vector-databases-in-production-for-busy-engineers/rag-evaluation/)

---

## Minor Pitfalls

Mistakes that cause annoyance but are quickly fixable.

---

### Pitfall 13: HNSW ef_search Not Tuned (Using Defaults)

**What goes wrong:**
Using default `ef_search = 40` when higher recall is needed, or using `ef_search = 200` when latency is critical. Suboptimal tradeoff for the use case.

**Prevention:**
```sql
-- Benchmark multiple values
SET hnsw.ef_search = 40;   -- ~95-98% recall, 1x latency
SET hnsw.ef_search = 100;  -- ~98-99% recall, 2x latency
SET hnsw.ef_search = 200;  -- ~99%+ recall, 4x latency

-- Choose based on requirements
-- High throughput: ef_search = 40
-- Balanced: ef_search = 100
-- Maximum recall: ef_search = 200
```

**Phase to address:** Phase 2 - pgvector Benchmarking

---

### Pitfall 14: Precision@k Without Rank Awareness

**What goes wrong:**
Precision@10 scores two retrieval results equally even if one has all relevant documents at the top and another has them at the bottom.

**Prevention:**
- Use MRR (Mean Reciprocal Rank) for rank-aware evaluation
- Use NDCG (Normalized Discounted Cumulative Gain) for graded relevance
- Report both position-aware and position-agnostic metrics

**Phase to address:** Phase 1 - Retrieval Metrics

---

### Pitfall 15: AGE search_path Not Set Per Connection

**What goes wrong:**
Cypher queries fail with "function ag_catalog.agtype_in does not exist" or similar errors because the `search_path` is not configured.

**Prevention:**
Configure in HikariCP (already done in Alexandria):
```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: "LOAD 'age'; SET search_path = ag_catalog, \"$user\", public"
```

**Phase to address:** Phase 3 - Graph Validation (inherited from existing setup)

---

### Pitfall 16: Ollama Model Not Pre-Pulled

**What goes wrong:**
First evaluation run triggers model download (4-8GB), causing timeout in CI/CD or unexpected delay in local development.

**Prevention:**
```yaml
# docker-compose.yml
services:
  ollama:
    image: ollama/ollama
    volumes:
      - ollama-models:/root/.ollama
    # Pull model on startup
    entrypoint: ["/bin/sh", "-c", "ollama serve & sleep 5 && ollama pull mistral:7b && wait"]
```

Or pre-pull in CI setup:
```yaml
# .github/workflows/eval.yml
- name: Pull Ollama model
  run: docker exec ollama ollama pull mistral:7b
```

**Phase to address:** Phase 4 - LLM-as-Judge (Docker setup)

---

## Phase-Specific Warnings Summary

| Phase | Topic | Critical Pitfall | Mitigation |
|-------|-------|------------------|------------|
| Phase 1 | Retrieval Metrics | Mismatched K values (Pitfall 2) | Analyze ground truth distribution first |
| Phase 1 | Retrieval Metrics | Golden set overfitting (Pitfall 12) | Reserve 20% holdout |
| Phase 2 | Embeddings Evaluation | O(N^2) silhouette (Pitfall 3) | Sample max 5000 docs |
| Phase 2 | Embeddings Evaluation | Wrong distance metric (Pitfall 1) | Verify cosine everywhere |
| Phase 2 | pgvector Benchmark | Cold cache bias (Pitfall 4) | pg_prewarm before benchmark |
| Phase 2 | pgvector Benchmark | Missing LIMIT (Pitfall 5) | Verify EXPLAIN shows Index Scan |
| Phase 3 | Graph Validation | AGE missing algorithms (Pitfall 6) | Plan NetworkX export early |
| Phase 4 | LLM-as-Judge | JSON parsing failures (Pitfall 7) | Use 7B+ models, robust extraction |
| Phase 4 | LLM-as-Judge | Self-enhancement bias (Pitfall 8) | Different models for gen/eval |
| Phase 5 | Monitoring | Prometheus OOM (Pitfall 9) | Use VictoriaMetrics instead |
| Phase 5 | Monitoring | Loki retention (Pitfall 10) | Enable retention, set period |
| Phase 6 | Docker | Startup race (Pitfall 11) | Health checks + condition |

---

## "Looks Done But Isn't" Checklist

Before considering RAG evaluation toolkit complete:

- [ ] **Distance metric verified:** All queries use `<=>` for cosine, index uses `vector_cosine_ops`
- [ ] **K values justified:** K chosen based on ground truth distribution analysis
- [ ] **Sampling implemented:** Silhouette score uses sampling for large corpora
- [ ] **Cache warming documented:** Benchmark procedure includes pg_prewarm step
- [ ] **EXPLAIN verified:** All benchmark queries show "Index Scan" in plan
- [ ] **AGE capabilities tested:** All planned Cypher queries tested against AGE
- [ ] **JSON extraction robust:** LLM-as-Judge handles malformed JSON
- [ ] **Model size adequate:** Using 7B+ parameter models for evaluation
- [ ] **Bias mitigation:** Different models or temps for generation vs evaluation
- [ ] **Memory bounded:** VictoriaMetrics or Prometheus with limits
- [ ] **Retention enabled:** Loki configured with retention period
- [ ] **Health checks present:** All service dependencies use `condition: service_healthy`
- [ ] **Holdout reserved:** 20% of golden dataset untouched during development

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong distance metric | MEDIUM | Recreate index, re-run benchmarks |
| Mismatched K values | LOW | Recalculate metrics with appropriate K |
| Silhouette OOM | LOW | Add sampling, rerun |
| Cold cache benchmarks | LOW | Warm cache, rerun benchmarks |
| Missing LIMIT | LOW | Add LIMIT, verify plan, rerun |
| AGE missing algo | MEDIUM | Implement NetworkX export pipeline |
| JSON parse failures | LOW | Add robust extraction, retry logic |
| Self-enhancement bias | MEDIUM | Configure different evaluator model |
| Prometheus OOM | MEDIUM | Migrate to VictoriaMetrics |
| Loki disk full | HIGH | Manual cleanup, enable retention |
| Startup race | LOW | Add health checks, restart compose |
| Overfitting | HIGH | Create new holdout set, re-validate |

---

## Sources

### Official Documentation
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [Apache AGE Documentation](https://age.apache.org/age-manual/)
- [Grafana Loki Retention](https://grafana.com/docs/loki/latest/operations/storage/retention/)
- [Docker Compose: Control Startup Order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Ollama Structured Outputs](https://ollama.com/blog/structured-outputs)
- [VictoriaMetrics Documentation](https://docs.victoriametrics.com/)

### Research Documents (Project-Specific)
- `.planning/research/results/evals/Validation d'un systeme RAG hybride sans outils payants.md`
- `.planning/research/results/evals/Creer un golden dataset RAG hybride.md`

### Community Resources
- [Crunchy Data: pgvector Performance](https://www.crunchydata.com/blog/pgvector-performance-for-developers)
- [Pinecone: Vector Similarity](https://www.pinecone.io/learn/vector-similarity/)
- [Weaviate: Distance Metrics](https://weaviate.io/blog/distance-metrics-in-vector-search)
- [DEV.to: Apache AGE vs Neo4j](https://dev.to/pawnsapprentice/apache-age-vs-neo4j-battle-of-the-graph-databases-2m4)
- [Evidently AI: LLM-as-a-Judge](https://www.evidentlyai.com/llm-guide/llm-as-a-judge)
- [Towards Data Science: Precision@k, Recall@k](https://towardsdatascience.com/how-to-evaluate-retrieval-quality-in-rag-pipelines-precisionk-recallk-and-f1k/)
- [Last9: Prometheus vs VictoriaMetrics](https://last9.io/blog/prometheus-vs-victoriametrics/)
- [Monte Carlo: LLM-as-Judge Best Practices](https://www.montecarlodata.com/blog-llm-as-judge/)

---

*Pitfalls research for: RAG Evaluation Toolkit*
*Project: Alexandria v0.4*
*Researched: 2026-01-24*
