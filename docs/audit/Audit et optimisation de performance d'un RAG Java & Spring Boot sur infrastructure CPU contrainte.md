# Audit et optimisation de performance d'un RAG Java/Spring Boot sur infrastructure CPU contrainte

Le **cross-encoder reranking ONNX** et la **contention HikariCP avec les virtual threads** constituent les deux goulots d'étranglement les plus probables de ce pipeline RAG. Sur 4 cores, le reranking de 50 candidats consomme **~370 ms** par requête — soit plus de 70% du budget latence — tandis que HikariCP ignore le cache ThreadLocal avec les virtual threads, provoquant une saturation du pool de connexions sous charge. Le système supporte confortablement **100K-300K chunks** dans l'enveloppe de 14 Go ; au-delà de **500K chunks**, la mémoire pgvector seule dépasse 2 Go et les compromis deviennent critiques. Ce rapport fournit un plan de benchmarking reproductible complet, des configurations validées pour chaque composant, et des seuils d'alerte opérationnels.

---

## 1. Résumé exécutif

### Goulots d'étranglement probables (par ordre de criticité)

Le **reranking cross-encoder** domine la latence : sur CPU, ms-marco-MiniLM-L-6-v2 traite ~12 ms par paire query-document, soit **~370 ms pour 50 candidats** sur 4 cores. Réduire à 20 candidats divise ce temps par 2.5. Le deuxième risque majeur est l'interaction **HikariCP + virtual threads** : le ThreadLocal caching devient inopérant, chaque virtual thread crée une nouvelle réservation de connexion, et les benchmarks montrent **28.76% d'erreurs timeout** à 1000 utilisateurs concurrents avec 20 connexions. Troisièmement, l'**ONNX Runtime thread spinning** consomme 100% CPU même au repos si `allow_spinning` n'est pas désactivé. Quatrièmement, la **mémoire native ONNX** (180-280 Mo off-heap pour les deux modèles) est invisible aux métriques JVM standard. Cinquièmement, le **pg_stat_statements** n'est probablement pas activé, rendant impossible l'identification des requêtes hybrides lentes.

### Top 5 priorités de benchmarking

1. Mesurer la latence du reranking en isolation (JMH) et l'impact de la réduction des candidats de 50 à 20
2. Profiler l'interaction HikariCP/virtual threads sous charge (k6 + Micrometer)
3. Benchmarker pgvector HNSW : latence et recall à différentes valeurs d'ef_search (pgbench)
4. Auditer la mémoire native ONNX (NMT + docker stats delta)
5. Établir la baseline complète du pipeline search end-to-end avec instrumentation Micrometer par étape

### Stack monitoring recommandée

**VictoriaMetrics** single-node (~50-100 Mo RAM) + **Grafana** (~30-80 Mo) + **postgres_exporter** (~15 Mo) + **pg_stat_monitor** (in-process) + **Micrometer** dans Spring Boot (in-process). Overhead total : **~150-250 Mo RAM, <2% CPU**. Alternative minimale : VictoriaMetrics seul avec vmui (**~50-100 Mo**).

---

## 2. Outils de profiling et benchmarking comparés

|Outil|Catégorie|Ce qu'il mesure|Effort setup|Overhead runtime|Compat. stack|Recommandation|Confiance|
|---|---|---|---|---|---|---|---|
|**JMH 1.37**|Microbenchmark|Latence par étape du pipeline, throughput|Modéré (Gradle plugin + classes @Benchmark)|Nul en prod (outil dédié)|✅ Java 21, VT, ONNX|**Essentiel** — benchmark isolé de chaque étape|Haute|
|**JFR** (built-in JDK 21)|Profiling continu|CPU, GC, allocations, VT pinning, lock contention|Minimal (flag JVM)|~1-2% CPU, 10-50 Mo|✅ Natif JDK 21, événements VT dédiés|**Essentiel** — profiling production-safe|Haute|
|**async-profiler**|Profiling CPU/wall|Flamegraphs unifiant Java + native ONNX, I/O wall-clock|Faible (agent natif)|~2-5% CPU, ~20 Mo|✅ Java 21 (carriers), natif ONNX visible|**Essentiel** — seul outil voyant le code natif ONNX|Haute|
|**pgbench** (custom)|Bench PostgreSQL|QPS pgvector, latence HNSW à différents ef_search|Faible (scripts SQL)|Nul (outil externe)|✅ PG 16, pgvector 0.8|**Essentiel** — benchmark HNSW/recall|Haute|
|**k6**|Load testing HTTP|p50/p95/p99, throughput, taux d'erreur, breakpoint|Minimal (binaire Go)|~1-5 Mo/VU, tourne depuis laptop|✅ Externe, cible Spring Boot REST/MCP|**Principal** — load testing primaire|Haute|
|**Gatling Java DSL**|Load testing HTTP|Scénarios complexes, rapports HTML riches|Modéré (Gradle plugin)|~256 Mo+ JVM, tourne depuis laptop|✅ Java-natif, même projet Gradle|**Complémentaire** — rapports supérieurs, feeders Java|Haute|
|**wrk2**|Latence à débit fixe|Latence précise sans coordinated omission|Faible (binaire C)|Négligeable|✅ HTTP externe|**Recommandé** — trouver le QPS max sous SLO|Haute|
|**vegeta**|Regression CI|Débit constant, histogrammes HDR|Minimal (binaire Go)|Négligeable|✅ UNIX pipes, CI-friendly|**Recommandé** — tests de régression automatisés|Haute|
|**hey**|Smoke test rapide|Latence basique, distribution percentiles|Nul (binaire unique)|Négligeable|✅ HTTP simple|**Utile** — validation rapide endpoint|Moyenne|
|**pg_stat_statements**|Analyse requêtes SQL|mean_exec_time, cache hit, appels par requête|Faible (extension PG)|~5 Mo shared memory|✅ PG 16 natif|**Essentiel** — identification requêtes lentes|Haute|
|**auto_explain**|Plans d'exécution auto|EXPLAIN ANALYZE automatique pour requêtes lentes|Faible (paramètre PG)|~2% avec timing activé|✅ PG 16 natif|**Essentiel** — debug plans HNSW/hybrides|Haute|

---

## 3. Outils de monitoring comparés

|Outil|Catégorie|Métriques clés|Overhead mémoire|Overhead CPU|Intégration Spring Boot|Complexité|Recommandation|Confiance|
|---|---|---|---|---|---|---|---|---|
|**Micrometer** (in-JVM)|Métriques applicatives|Timers pipeline, counters, gauges, HikariCP auto|5-15 Mo heap|<1%|✅ Natif Spring Boot 3.5|Faible|**Essentiel** — zéro conteneur additionnel|Haute|
|**VictoriaMetrics** single|TSDB + scraping|Stockage métriques, PromQL, vmui intégré|50-100 Mo|<1%|✅ Compatible Prometheus remote_write|Faible|**Recommandé** — 4× moins de RAM que Prometheus|Haute|
|**Prometheus**|TSDB + scraping|Stockage métriques, PromQL, alerting rules|80-200 Mo|<1%|✅ Via /actuator/prometheus|Faible-Moyen|**Acceptable** — plus lourd que VM|Haute|
|**Grafana**|Dashboards + alerting|Visualisation, alertes, notifications|25-80 Mo|<0.5% idle|✅ Datasources Prometheus/VM|Moyen|**Recommandé** — dashboards ID 19004, 9628|Haute|
|**postgres_exporter**|Métriques PostgreSQL|Cache hit ratio, connections, scans, bloat|10-30 Mo|<0.5%|N/A (cible PG)|Faible|**Recommandé** — conteneur ~15 Mo image|Haute|
|**pg_stat_monitor**|Extension PG|Superset pg_stat_statements, histogrammes, buckets|10-50 Mo shared mem|<1%|N/A (in-process PG)|Faible|**Recommandé** — pas de conteneur séparé|Moyenne|
|**cAdvisor**|Conteneurs Docker|CPU, mémoire, réseau, I/O par conteneur|30-50 Mo|5-6% (optimisé)|N/A (monitoring infra)|Faible|**Optionnel** — docker stats suffisant au départ|Haute|
|**pgwatch v3**|PostgreSQL tout-en-un|All pg_stat_*, bloat, vacuum, custom SQL|200-400 Mo (bundlé)|2-5%|N/A|Moyen|**Non recommandé** — trop lourd pour 14 Go|Moyenne|
|**pgMonitor**|PostgreSQL full stack|Prometheus + Grafana + exporter pré-configuré|300-500 Mo|2-3%|N/A|Élevé|**Non recommandé** — overhead excessif|Moyenne|

---

## 4. Guide de tuning PostgreSQL 16 + pgvector 0.8

### Configuration PostgreSQL optimisée pour le workload RAG

|Paramètre|Défaut PG 16|Valeur recommandée|Impact|Source/Type|
|---|---|---|---|---|
|`shared_buffers`|128 Mo|**2.5 Go**|Cache pages index HNSW + tables. 25-30% du budget 8-10 Go. Ne pas dépasser 40% (laisse de la place au page cache OS).|[CONVENTION] Wiki PostgreSQL|
|`effective_cache_size`|4 Go|**6-7 Go**|Hint planificateur : encourage index scans HNSW vs seq scans. ~70% du RAM total PostgreSQL.|[CONVENTION]|
|`work_mem`|4 Mo|**32-64 Mo**|Mémoire par opération de tri/hash. Les requêtes hybrides trient sur distance vectorielle + ranking FTS. Avec pool 10-15 connexions : conservateur à 32 Mo, max 64 Mo.|[CONVENTION]|
|`maintenance_work_mem`|64 Mo|**1-2 Go**|**Critique pour la construction d'index HNSW.** pgvector avertit quand le graphe ne tient plus dans cette valeur. 1 Go supporte ~600K+ vecteurs 384d pendant le build.|[ESTIMATION basée sur taille vecteurs]|
|`random_page_cost`|4.0|**1.1** (SSD)|Lectures aléatoires quasi-identiques aux séquentielles sur SSD. Encourage les index scans HNSW.|[CONVENTION] PostgreSQL Wiki|
|`max_parallel_workers_per_gather`|2|**2**|Sur 4 cores, garder à 2. Laisse des cores pour les connexions concurrentes.|[CONVENTION]|
|`max_parallel_maintenance_workers`|2|**3**|Pour les builds HNSW parallèles (pgvector 0.6+). 3 workers + leader = 4 threads = 4 cores.|Docs pgvector|
|`wal_buffers`|auto (~3% shared_buffers)|**16 Mo**|Cap standard. Auto donnerait ~80 Mo, excessif.|[CONVENTION]|
|`checkpoint_completion_target`|0.9|**0.9**|Déjà optimal. Étale l'I/O sur l'intervalle de checkpoint.|Défaut PG 16|
|`checkpoint_timeout`|5 min|**15 min**|Réduit la fréquence des checkpoints pendant les builds d'index et l'ingestion.|[CONVENTION]|
|`max_wal_size`|1 Go|**4 Go**|Évite les checkpoints trop fréquents pendant l'ingestion et le build HNSW.|[CONVENTION]|
|`max_connections`|100|**30-50**|Avec HikariCP pool 10-15, pas besoin de 100. Réduit la mémoire par connexion.|[CALCUL] pool + 10-15 admin|
|`jit`|on|**off**|Le JIT ajoute du overhead pour les requêtes vectorielles courtes. Désactiver pour la latence RAG.|Benchmarks communauté pgvector|
|`wal_compression`|off|**lz4**|Réduit le volume WAL, surtout pendant les builds d'index. PG 16 supporte lz4/zstd.|Docs PG 16|
|`huge_pages`|try|**try**|Bénéfice marginal à 8-10 Go (utile à 32 Go+). Pas de downside avec `try`.|[CONVENTION]|

### Paramètres pgvector runtime

|Paramètre|Défaut|Recommandé|Impact|
|---|---|---|---|
|`hnsw.ef_search`|40|**100** (à tuner)|Plus haut = meilleur recall, latence plus élevée. À 384d, ef_search=100 donne ~99% recall. Tester avec protocole recall ci-dessous.|
|`hnsw.iterative_scan`|off|**relaxed_order**|pgvector 0.8.0 : évite le "overfiltering" quand les clauses WHERE filtrent trop de résultats HNSW. Essentiel pour la recherche hybride.|
|`hnsw.max_scan_tuples`|20000|**10000**|Limite l'exploration itérative. À ajuster selon la sélectivité des filtres.|

### Protocole de benchmarking HNSW recall vs latence

**Étape 1 — Ground truth (recherche brute-force) :**

```sql
BEGIN;
SET LOCAL enable_indexscan = off;
SET LOCAL enable_bitmapscan = off;
SELECT id FROM documents ORDER BY embedding <=> $1 LIMIT 50;
COMMIT;
```

**Étape 2 — Mesure recall à chaque ef_search :**

```bash
for ef in 40 100 200 400; do
  echo "=== ef_search=$ef ==="
  psql -c "SET hnsw.ef_search = $ef;" -c "\timing" -f recall_test.sql mydb
  pgbench -f ./vector_bench.sql -c 10 -j 4 -T 60 -P 5 -d mydb
done
```

**Étape 3 — Calcul recall :** `recall@50 = |résultats_HNSW ∩ résultats_bruteforce| / 50`

**Cible :** ef_search où recall ≥ 0.95 avec latence p95 < 50 ms pour la recherche vectorielle seule.

### Extensions à activer

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pg_prewarm;
-- postgresql.conf
shared_preload_libraries = 'pg_stat_statements, auto_explain, pg_prewarm'
pg_stat_statements.track = all
pg_stat_statements.max = 10000
track_io_timing = on
auto_explain.log_min_duration = '200ms'
auto_explain.log_analyze = on
auto_explain.log_buffers = on
auto_explain.log_format = json
pg_prewarm.autoprewarm = true
```

---

## 5. Guide d'optimisation ONNX Runtime CPU (4 cores)

### Configuration globale recommandée (Java)

```java
// Thread pools partagés entre embedding + reranker
OrtEnvironment.ThreadingOptions threadOpts = new OrtEnvironment.ThreadingOptions();
threadOpts.setGlobalIntraOpNumThreads(4);
threadOpts.setGlobalInterOpNumThreads(1);
OrtEnvironment env = OrtEnvironment.getEnvironment(
    OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING, "RAGPipeline", threadOpts);

// Options partagées entre les sessions
OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
opts.disablePerSessionThreads();  // Utilise les pools globaux
opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
opts.setCPUArenaAllocator(true);
opts.setMemoryPatternOptimization(true);
opts.addConfigEntry("session.intra_op.allow_spinning", "0");
```

### Paramètres par paramètre

|Paramètre|Valeur recommandée (4 cores)|Impact|Source/Type|
|---|---|---|---|
|`intra_op_num_threads`|**4** (requête unique) / **2** (concurrent)|Parallélisme intra-opérateur. À 4 : latence minimale pour une requête. À 2 : laisse des cores pour les requêtes concurrentes.|Docs ONNX Runtime threading|
|`inter_op_num_threads`|**1**|Parallélisme inter-opérateurs. Les transformers sont séquentiels (attention → FFN → attention). PARALLEL mode inutile.|Docs ONNX Runtime|
|`ExecutionMode`|**SEQUENTIAL**|PARALLEL ajoute du overhead pour les modèles séquentiels comme MiniLM.|Docs ONNX Runtime|
|`OptimizationLevel`|**ALL_OPT**|Active les fusions BERT-spécifiques (attention fusion, GELU approximation). **Toujours utiliser ALL_OPT** pour les transformers.|Docs ONNX Runtime graph optimizations|
|`allow_spinning`|**"0"** (désactivé)|**Critique.** Défaut = "1" : les threads spin-wait → 100% CPU même au repos. Désactiver économise massivement du CPU (~100× réduction idle CPU) avec seulement ~5-10% de latence additionnelle.|Inworld.ai benchmarks|
|`CPUArenaAllocator`|**true**|Pré-alloue la mémoire pour les inférences répétées. Ajoute ~50-200 Mo de mémoire native par session mais réduit la latence d'allocation.|Docs ONNX Runtime memory|
|`MemoryPatternOptimization`|**true**|Réutilise les patterns d'allocation entre inférences. Réduit les allocations natives.|Docs ONNX Runtime|
|`disablePerSessionThreads()`|**Activé**|Empêche l'explosion de threads quand 2 sessions (embedding + reranker) créent chacune leur pool. Utilise les pools globaux.|Docs ONNX Runtime Java API|
|Batch size (embedding)|**32-64** (au lieu de 256)|256 est optimisé pour GPU. Sur 4 cores, des batches plus petits réduisent la pression mémoire et le cache thrashing L2/L3. Tester 16/32/64.|[ESTIMATION] basée sur architecture CPU|
|Modèle optimisé offline|`opts.setOptimizedModelFilePath(...)`|Sauvegarde le modèle optimisé pour éviter la ré-optimisation à chaque démarrage. Économise ~2-5s au boot.|Docs ONNX Runtime|

### Impact de la réduction des candidats reranking

|Candidats|Latence estimée (4 cores, int8)|Recommandation|
|---|---|---|
|10|~60 ms|✅ Idéal temps réel|
|20|~120 ms|✅ **Recommandé** — bon compromis qualité/latence|
|50 (actuel)|~370 ms|⚠️ Borderline pour interactif|
|100|~740 ms|❌ Trop lent|

[ESTIMATION] Basé sur benchmarks Metarank (JMH, 30 itérations) et Vespa. Les chiffres exacts dépendent du CPU spécifique.

### Alternatives au cross-encoder sur CPU

Le modèle **int8 quantized** de ms-marco-MiniLM-L-6-v2 (~22 Mo) offre ~40-50% de réduction de latence vs float32 avec une qualité quasi-identique. **FlashRank** (Python, mais modèles ONNX réutilisables en Java) propose TinyBERT-L2 (~4 Mo) pour ~100 ms/100 docs. En Java, la meilleure option reste le **ms-marco-MiniLM-L-6-v2 quantized int8** chargé directement via ONNX Runtime.

---

## 6. Projections de scalabilité

### Calculs détaillés pour pgvector 384d float4, HNSW (m=16)

**Formules de base :**

- Stockage vecteur brut : `384 × 4 bytes + 4 bytes header = 1 540 octets/vecteur`
- Index HNSW par vecteur : `~1 862 octets` [ESTIMATION] (element tuple 1 616 + neighbor tuple 196 + page overhead 50)
- Métadonnées JSONB + row overhead : `~328 octets/ligne` [ESTIMATION] (300 bytes JSONB + 28 bytes tuple header)
- Index GIN tsvector : `~300 octets/ligne` [ESTIMATION]
- Index B-tree source_id : `~35 octets/ligne` [ESTIMATION]
- Overhead alignement/fragmentation : **+15%**

### Projections par volume

|Chunks|Vecteurs|HNSW Index|Métadonnées|GIN|B-tree|Overhead 15%|**Total PG**|Mémoire PG nécessaire|Verdict|
|---|---|---|---|---|---|---|---|---|---|
|**50K**|73 Mo|88 Mo|16 Mo|15 Mo|2 Mo|29 Mo|**~223 Mo**|2 Go shared_buffers OK|✅ **OK**|
|**100K**|147 Mo|175 Mo|31 Mo|30 Mo|4 Mo|58 Mo|**~445 Mo**|2.5 Go shared_buffers OK|✅ **OK**|
|**300K**|440 Mo|525 Mo|94 Mo|90 Mo|11 Mo|174 Mo|**~1.3 Go**|2.5 Go shared_buffers, ~4 Go page cache|✅ **OK**|
|**500K**|734 Mo|875 Mo|156 Mo|150 Mo|18 Mo|290 Mo|**~2.2 Go**|2.5 Go sb + 3-4 Go page cache nécessaire|⚠️ **WARNING**|
|**750K**|1 100 Mo|1 310 Mo|234 Mo|225 Mo|26 Mo|435 Mo|**~3.3 Go**|Index HNSW déborde du cache|⚠️ **WARNING**|
|**1M**|1 469 Mo|1 750 Mo|313 Mo|300 Mo|35 Mo|580 Mo|**~4.4 Go**|Nécessite >8 Go PG pour garder l'index en cache|❌ **CRITICAL**|

### Budget mémoire total par composant

|Composant|100K chunks|500K chunks|1M chunks|
|---|---|---|---|
|PostgreSQL (data + cache)|2.5 Go|5-6 Go|8-10 Go|
|Java App (heap + ONNX natif)|2-3 Go|2-3 Go|2-3 Go|
|Crawl4AI (Chromium headless)|2 Go|2 Go|2 Go|
|OS + page cache|1 Go|1 Go|1 Go|
|Monitoring (VM + Grafana)|0.2 Go|0.2 Go|0.2 Go|
|**TOTAL**|**~7.7-8.7 Go** ✅|**~10.2-12.2 Go** ⚠️|**~13.2-16.2 Go** ❌|

### Optimisations pour repousser les limites

**halfvec (pgvector 0.7+) :** Indexer en `halfvec(384)` au lieu de `vector(384)` réduit l'index HNSW de **~50%**. Pour 500K chunks, l'index passe de ~875 Mo à ~440 Mo, économisant ~435 Mo. Le total PG passe de 2.2 Go à **~1.8 Go**. Recall impact : <1-2% de dégradation sur la plupart des datasets.

```sql
CREATE INDEX ON documents USING hnsw ((embedding::halfvec(384)) halfvec_cosine_ops)
  WITH (m = 16, ef_construction = 128);
```

**Avec halfvec**, le **seuil WARNING passe de 500K à ~750K chunks**, et le seuil CRITICAL de 750K à ~1M chunks.

---

## 7. Plan d'implémentation par phases

### Phase 1 — Fondations (< 1 jour)

**Objectif : instrumentation minimale et identification des bottlenecks évidents.**

1. **Activer pg_stat_statements + auto_explain** dans `postgresql.conf` (30 min)
    
    ```ini
    shared_preload_libraries = 'pg_stat_statements, auto_explain'
    pg_stat_statements.track = all
    track_io_timing = on
    auto_explain.log_min_duration = '200ms'
    auto_explain.log_buffers = on
    auto_explain.log_format = json
    ```
    
2. **Ajouter les Timers Micrometer** sur chaque étape du pipeline search (2h)
    
    ```java
    Timer.builder("rag.search.stage_latency")
        .tag("stage", "reranking")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry);
    ```
    
3. **Désactiver le thread spinning ONNX** — gain immédiat CPU (5 min)
    
    ```java
    opts.addConfigEntry("session.intra_op.allow_spinning", "0");
    ```
    
4. **Configurer les pools de threads ONNX globaux** pour éviter l'explosion de threads (15 min)
    
5. **Smoke test avec hey** pour obtenir une baseline brute (15 min)
    
    ```bash
    hey -n 200 -c 10 -m POST -D query.json http://localhost:8080/api/search
    ```
    

### Phase 2 — Profiling approfondi (< 3 jours)

**Objectif : identifier et quantifier chaque bottleneck.**

1. **Configurer JMH** pour le pipeline RAG (4h) — benchmarks isolés par étape : embedding lookup, HNSW query, FTS query, RRF fusion, reranking. Utiliser le Gradle plugin `me.champeau.jmh:0.7.3`.
    
2. **Profiler avec JFR** — enregistrer 2 min de charge réaliste (1h)
    
    ```bash
    java -XX:StartFlightRecording=filename=rag.jfr,duration=120s,settings=profile \
         -XX:+DebugNonSafepoints -jar app.jar
    ```
    
    Analyser dans JMC : événements `VirtualThreadPinned`, `GCPhasePause`, `JavaMonitorEnter`.
    
3. **Flamegraph async-profiler** en mode wall-clock pour capturer l'I/O PostgreSQL et le code natif ONNX (1h)
    
    ```bash
    asprof -e wall -t -i 5ms -d 60 -f rag-flamegraph.html <pid>
    ```
    
4. **Benchmark HNSW recall/latence** avec le protocole ef_search décrit en section 4 (2h)
    
5. **Tester le batch size embedding optimal** : benchmark JMH avec 16, 32, 64, 128, 256 (2h)
    
6. **Auditer la mémoire native ONNX** (1h)
    
    ```bash
    # Lancer avec NMT
    -XX:NativeMemoryTracking=summary
    # Puis comparer
    jcmd <pid> VM.native_memory baseline
    # Après charge
    jcmd <pid> VM.native_memory summary.diff
    # Delta avec docker stats = mémoire ONNX native
    ```
    

### Phase 3 — Load testing et tuning (< 1 semaine)

**Objectif : trouver les limites et optimiser.**

1. **Installer k6** et créer les scénarios de test (4h)
    
    - Smoke test : 1-2 VU, 2 min (validation)
    - Charge moyenne : 10-20 VU, 5 min (baseline)
    - Stress test : rampe de 10 à 100+ VU sur 10 min (point de rupture)
    - Spike test : saut de 10 à 200 VU pendant 30s (résilience)
    - Mixed workload : 80% search + 20% ingestion (contention)
2. **Déployer la stack monitoring** VictoriaMetrics + Grafana + postgres_exporter (3h)
    
    - Importer les dashboards Grafana **19004** (Spring Boot) et **9628** (PostgreSQL)
    - Configurer les alertes selon les seuils de la section suivante
3. **Tuning PostgreSQL** avec les valeurs recommandées section 4 (2h) — appliquer, benchmarker, itérer
    
4. **Optimiser les candidats reranking** : tester avec 20 au lieu de 50, mesurer impact qualité (2h)
    
5. **Tester HikariCP sous charge avec virtual threads** (2h)
    
    - Monitorer `hikaricp.connections.pending` et `hikaricp.connections.timeout.total`
    - Réduire `connection-timeout` à 5-10s pour fail-fast
    - Limiter le pool à 10-15 connexions (`max_connections = (2 × cores) + disk = ~10`)

### Phase 4 — Automatisation et régression (< 2 semaines)

**Objectif : pérenniser les benchmarks.**

1. **Intégrer JMH dans le CI** via GitHub Actions (4h)
    
    ```yaml
    - uses: benchmark-action/github-action-benchmark@v1
      with:
        tool: 'jmh'
        output-file-path: build/reports/jmh/results.json
        alert-threshold: '115%'
        comment-on-alert: true
    ```
    
2. **Créer un dashbord Grafana RAG dédié** avec les métriques custom Micrometer (3h)
    
3. **Configurer les alertes opérationnelles** (2h)
    
    |Métrique|Seuil Warning|Seuil Critical|
    |---|---|---|
    |p95 search latency totale|>500 ms|>2 s|
    |CPU utilisation (5 min avg)|>70%|>90%|
    |PG cache hit ratio|<95%|<90%|
    |JVM GC pause p95|>200 ms|>500 ms|
    |HikariCP pool utilisation|>70%|>90%|
    |JVM heap usage|>75% max|>90% max|
    |Error rate (5xx)|>1%|>5%|
    |Résultats vides|>20%|>40%|
    
4. **Implémenter le pg_prewarm** pour warm-up automatique après restart (30 min)
    
    ```sql
    CREATE EXTENSION pg_prewarm;
    SELECT pg_prewarm('documents_embedding_idx');
    ```
    
5. **Établir le benchmark nightly** avec suite JMH complète + rapport de régression
    
6. **Documenter la baseline de performance** comme référence pour les évolutions futures
    

---

## 8. Pièges et erreurs courantes

### HikariCP + virtual threads : le piège le plus dangereux

HikariCP utilise **ThreadLocal** pour cacher les connexions par thread. Avec les virtual threads — créés et détruits par requête — ce cache est **totalement inopérant**. Chaque requête provoque une nouvelle réservation de connexion au lieu de réutiliser la connexion du thread. Les benchmarks Spring Boot 3.5 + Java 24 montrent **28.76% d'erreurs timeout** à haute concurrence avec un pool de 20 connexions. La solution : limiter strictement le pool HikariCP à **10-15 connexions** (formule : `2 × cores + 1`), réduire le `connection-timeout` à 5-10 secondes pour fail-fast, et monitorer `hikaricp.connections.pending` en permanence.

### ONNX Runtime thread spinning : le voleur silencieux de CPU

Par défaut, `session.intra_op.allow_spinning = "1"` fait que les threads ONNX **spin-wait** en attente de travail. Sur 4 cores, cela consomme 100% CPU même sans requête en cours. Inworld.ai a mesuré une **réduction de consommation CPU de ~100×** simplement en désactivant le spinning, avec seulement 5-10% d'augmentation de latence. Toujours mettre `"0"` sur infrastructure partagée.

### Mémoire native ONNX invisible au JVM

Les modèles ONNX et leurs arena allocators consomment **180-280 Mo de mémoire native** non visible dans les métriques JVM heap (`jvm_memory_used_bytes`). Un développeur qui ne surveille que le heap Java croira avoir 2 Go de marge alors que le RSS réel du conteneur approche la limite. Toujours comparer `docker stats` avec NMT (`-XX:NativeMemoryTracking=summary`) — le delta est la mémoire ONNX native. Fermer explicitement les `OnnxTensor` avec try-with-resources sous peine de fuites natives.

### Batch size 256 : optimisé pour GPU, pas pour 4 cores

Un batch de 256 embeddings sature la bande passante mémoire et provoque du cache thrashing L2/L3 sur 4 cores CPU. Les **batches de 32-64** sont plus efficaces car ils tiennent dans les caches CPU. Le gain de throughput au-delà de 64 est marginal sur CPU tandis que la latence par batch augmente linéairement.

### pgvector HNSW non utilisé : le plan d'exécution silencieux

Si la requête SQL ne contient pas `ORDER BY ... LIMIT K` en ordre **ascendant**, le planificateur PostgreSQL choisit un **seq scan + sort** au lieu de l'index HNSW. Même avec un LIMIT, si le LIMIT est trop grand ou si une clause WHERE très sélective est présente (avant pgvector 0.8.0), l'index peut être ignoré. Toujours vérifier avec `EXPLAIN (ANALYZE, BUFFERS)` que le plan montre "Index Scan using ... hnsw_idx". En cas de doute : `SET enable_seqscan = off` pour forcer.

### JIT PostgreSQL : overhead pour les requêtes courtes

Le JIT PostgreSQL est activé par défaut dans PG 16. Pour les requêtes vectorielles courtes (<100 ms), le temps de compilation JIT **dépasse souvent le gain**. Désactiver avec `SET jit = off` réduit la latence des requêtes simples.

### Mesures de latence corrompues par le coordinated omission

Des outils comme `wrk` (sans le `2`) et les benchmarks naïfs souffrent du **coordinated omission** : quand le serveur ralentit, le client attend et envoie moins de requêtes, masquant la dégradation réelle. Utiliser **wrk2** ou **k6** avec l'executor `constant-arrival-rate` pour maintenir un débit constant et mesurer la vraie latence sous charge.

### ef_search par défaut (40) : probablement insuffisant pour la qualité RAG

Le défaut `hnsw.ef_search = 40` dans pgvector donne ~95% de recall sur 384d. Pour un système RAG où la qualité des résultats impacte directement la réponse LLM, **ef_search = 100** est un meilleur point de départ (recall ~99%) avec un coût de latence acceptable (~1.5-2× baseline). Toujours valider avec le protocole recall vs brute-force.

### JMH + ONNX : warm-up insuffisant

ONNX Runtime nécessite **3-5 inférences de warm-up** avant d'atteindre un état stable (optimisation interne, pré-allocation d'arenas). Le `@Warmup` de JMH chauffe le JIT Java mais pas les optimisations natives ONNX. Ajouter un warm-up explicite ONNX dans `@Setup(Level.Trial)` avec des inférences dummy.

### shared_buffers surdimensionné : double caching

Allouer plus de 40% du RAM à `shared_buffers` est contre-productif : les mêmes pages se retrouvent **en double** dans les buffers PostgreSQL et le page cache OS. Pour 8-10 Go, rester à **2.5 Go** (25-30%) et laisser le reste au page cache Linux, qui gère automatiquement le caching des fichiers d'index HNSW.

### Monitoring trop lourd qui aggrave le problème

Déployer Prometheus + Grafana + pgMonitor + cAdvisor peut consommer **500+ Mo de RAM et 5%+ CPU** — significatif sur une infrastructure à 14 Go. VictoriaMetrics single-node remplace Prometheus avec **4× moins de RAM**. Le combo VictoriaMetrics + Grafana + postgres_exporter tient dans **~150-250 Mo** et <2% CPU.