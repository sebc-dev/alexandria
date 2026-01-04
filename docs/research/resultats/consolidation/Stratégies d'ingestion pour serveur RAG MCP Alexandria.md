# Stratégies d'ingestion pour serveur RAG MCP Alexandria

Un serveur RAG mono-utilisateur avec Spring Boot 3.5 et Java 25 bénéficie d'une **architecture hybride CLI + MCP tool**, le CLI gérant l'ingestion bulk initiale tandis qu'un outil MCP léger permet les mises à jour ponctuelles depuis Claude Code. L'option watcher de répertoire s'avère contre-productive pour un usage occasionnel sur hardware limité. Cette recommandation découle d'une analyse approfondie des contraintes MCP (timeout 60s), des capacités de Langchain4j, et du contexte mono-utilisateur avec ressources modestes.

## Analyse comparative des cinq options d'ingestion

### 1. Outil MCP `ingest` via @McpTool Spring AI

L'exposition d'un outil d'ingestion via Spring AI MCP SDK 1.1.x est techniquement simple mais pose des **problèmes fondamentaux de timeout**. Le protocole MCP impose un timeout client de **60 secondes** par défaut (erreur -32001), et bien que les notifications de progression puissent théoriquement le réinitialiser, ce comportement n'est pas garanti par tous les clients.

```java
@McpTool(name = "ingest-document",
         description = "Ingest a markdown document into Alexandria",
         annotations = @McpTool.McpAnnotations(
             readOnlyHint = false,
             idempotentHint = true))
public String ingestDocument(
        McpSyncRequestContext context,
        @McpToolParam(description = "Document path", required = true) String path) {
    
    context.progress(p -> p.progress(0.0).total(1.0).message("Starting"));
    // Processing with periodic progress updates every 10-30 seconds
    return "Successfully ingested: " + path;
}
```

**Verdict**: Approprié uniquement pour **1-5 documents** ou mises à jour ponctuelles. Pour des centaines de fichiers markdown, le pattern "async hand-off" (retourner un jobId immédiatement + outil de polling séparé) devient nécessaire mais alourdit considérablement l'implémentation.

### 2. Endpoint REST séparé (POST /api/ingest)

Spring MVC permet la coexistence parfaite d'endpoints REST traditionnels avec le transport MCP SSE sur le même serveur. Cette approche offre un **contrôle total sur le timeout** et une progression SSE native.

```java
@RestController
@RequestMapping("/api")
public class IngestionController {
    
    @PostMapping("/ingest")
    public ResponseEntity<JobResponse> startIngestion(@RequestBody IngestionRequest request) {
        String jobId = ingestionService.submitJob(request.getPaths());
        return ResponseEntity.accepted().body(new JobResponse(jobId));
    }
    
    @GetMapping(value = "/ingest/{jobId}/progress", 
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable String jobId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        ingestionService.subscribeToProgress(jobId, emitter);
        return emitter;
    }
}
```

**Configuration SSE pour progression temps réel:**

```yaml
spring:
  mvc:
    async:
      request-timeout: -1  # Pas de timeout pour SSE
```

**Verdict**: Excellente option pour une **interface web** ou des scripts d'automatisation, mais ajoute une complexité inutile pour un développeur solo utilisant principalement Claude Code.

### 3. Commande CLI (java -jar alexandria.jar ingest)

L'approche CLI représente le **meilleur rapport simplicité/puissance** pour l'ingestion bulk. Spring Boot supporte nativement les applications dual-mode (serveur + CLI) via les profils.

**Configuration dual-mode:**

```yaml
# application-server.yaml
spring:
  main:
    web-application-type: servlet
  ai:
    mcp:
      server:
        name: alexandria
        sse-endpoint: /sse
        
# application-cli.yaml  
spring:
  main:
    web-application-type: none
```

**Implémentation CLI avec Picocli** (recommandé sur CommandLineRunner natif pour la richesse fonctionnelle):

```java
@Component
@Command(name = "alexandria", mixinStandardHelpOptions = true,
         subcommands = {IngestCommand.class, ServerCommand.class})
public class AlexandriaCli implements Callable<Integer> {
    @Override
    public Integer call() { return 0; }
}

@Component
@Command(name = "ingest", description = "Ingest documents into Alexandria")
public class IngestCommand implements Callable<Integer> {
    
    @Autowired
    private IngestionPipeline pipeline;
    
    @Parameters(description = "Paths to ingest")
    private List<Path> paths;
    
    @Option(names = {"-r", "--recursive"}, description = "Recursive directory scan")
    private boolean recursive;
    
    @Option(names = {"--batch-size"}, defaultValue = "50")
    private int batchSize;
    
    @Override
    public Integer call() {
        List<Document> documents = loadDocuments(paths, recursive);
        int total = documents.size();
        
        Lists.partition(documents, batchSize).forEach(batch -> {
            pipeline.ingest(batch);
            System.out.printf("Ingested %d/%d documents%n", processed.get(), total);
        });
        return 0;
    }
}
```

**Dépendance Picocli:**
```xml
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli-spring-boot-starter</artifactId>
    <version>4.7.7</version>
</dependency>
```

**Verdict**: **Option recommandée pour l'ingestion bulk**. Picocli apporte génération automatique d'aide, parsing POSIX/GNU, et conversion de types sans code supplémentaire.

### 4. Watcher de répertoire (auto-détection)

Java WatchService permet une surveillance native des fichiers, mais présente des **inconvénients significatifs** pour le contexte Alexandria.

```java
public class DocumentWatcher implements AutoCloseable {
    private final WatchService watcher;
    private final Map<WatchKey, Path> watchedDirs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debouncer;
    private final Map<Path, ScheduledFuture<?>> pendingEvents = new ConcurrentHashMap<>();
    
    private static final Duration DEBOUNCE_TIME = Duration.ofMillis(500);
    
    private void registerRecursively(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) 
                    throws IOException {
                WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
                watchedDirs.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private void handleEvent(Path file) {
        if (!file.toString().endsWith(".md")) return;
        
        // Debouncing: annuler événement précédent, replanifier
        ScheduledFuture<?> existing = pendingEvents.get(file);
        if (existing != null) existing.cancel(false);
        
        pendingEvents.put(file, debouncer.schedule(() -> {
            pendingEvents.remove(file);
            ingestionService.ingestSingleDocument(file);
        }, DEBOUNCE_TIME.toMillis(), TimeUnit.MILLISECONDS));
    }
}
```

**Problèmes identifiés:**
- Consommation CPU constante même au repos (polling interne sur certains OS)
- Nécessite debouncing complexe (les éditeurs génèrent multiples événements)
- Limite inotify Linux potentiellement atteinte avec beaucoup de sous-répertoires
- Surcharge injustifiée pour "mises à jour occasionnelles"

**Verdict**: **Non recommandé** pour le cas d'usage Alexandria. La complexité d'implémentation robuste (debouncing, OVERFLOW, récursivité) ne justifie pas le gain pour des updates occasionnelles.

### 5. Architecture hybride recommandée

L'analyse conduit à une architecture **CLI + MCP tool simplifié**:

```
┌─────────────────────────────────────────────────────────────┐
│                    Alexandria Server                         │
├──────────────────────┬──────────────────────────────────────┤
│   CLI (bulk)         │   MCP Tool (updates ponctuels)       │
│   ══════════════     │   ═══════════════════════════════    │
│   • Initial load     │   • 1-5 docs depuis Claude Code      │
│   • Re-indexation    │   • Feedback direct dans chat        │
│   • Maintenance      │   • Timeout-safe (< 60s)             │
├──────────────────────┴──────────────────────────────────────┤
│              Langchain4j IngestionPipeline                   │
│   [FileLoader → Splitter → Embedding → PgVector]            │
└─────────────────────────────────────────────────────────────┘
```

## Pipeline d'ingestion Langchain4j optimisé

### Configuration PgVector avec Langchain4j 1.10

```java
@Configuration
public class EmbeddingConfig {
    
    @Bean
    public EmbeddingModel embeddingModel() {
        // BGE-M3 via RunPod/Infinity (compatible OpenAI API)
        return OpenAiEmbeddingModel.builder()
            .baseUrl("https://your-runpod-endpoint/v1")
            .apiKey(System.getenv("RUNPOD_API_KEY"))
            .modelName("BAAI/bge-m3")
            .timeout(Duration.ofSeconds(120))
            .build();
    }
    
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
            .datasource(dataSource)  // Réutilise HikariCP existant
            .table("alexandria_embeddings")
            .dimension(1024)  // BGE-M3 dimension
            .useIndex(true)
            .indexListSize(100)  // IVFFlat lists
            .createTable(true)
            .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
            .build();
    }
    
    @Bean
    public DocumentSplitter documentSplitter() {
        // 512 tokens optimal pour BGE-M3 malgré context window de 8192
        return DocumentSplitters.recursive(512, 50);
    }
}
```

### Service d'ingestion avec batch et rate limiting

```java
@Service
public class IngestionService {
    
    private final EmbeddingStoreIngestor ingestor;
    private final RateLimiter rateLimiter;
    
    private static final int BATCH_SIZE = 25;  // Adapté à hardware limité
    private static final Duration BATCH_DELAY = Duration.ofMillis(200);
    
    public IngestionService(EmbeddingModel model, 
                           EmbeddingStore<TextSegment> store,
                           DocumentSplitter splitter) {
        
        this.ingestor = EmbeddingStoreIngestor.builder()
            .embeddingModel(model)
            .embeddingStore(store)
            .documentSplitter(splitter)
            .documentTransformer(this::enrichMetadata)
            .textSegmentTransformer(this::prefixWithSource)
            .build();
            
        // Resilience4j rate limiter pour RunPod
        this.rateLimiter = RateLimiter.of("runpod", RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(10)  // 10 req/s vers RunPod
            .timeoutDuration(Duration.ofSeconds(30))
            .build());
    }
    
    public IngestionResult ingestWithProgress(List<Document> documents, 
                                              Consumer<ProgressUpdate> progressCallback) {
        List<List<Document>> batches = Lists.partition(documents, BATCH_SIZE);
        int totalBatches = batches.size();
        int processedChunks = 0;
        
        for (int i = 0; i < totalBatches; i++) {
            List<Document> batch = batches.get(i);
            
            // Rate limiting
            RateLimiter.waitForPermission(rateLimiter);
            
            IngestionResult batchResult = ingestor.ingest(batch);
            processedChunks += batchResult.tokenUsage().totalTokenCount();
            
            // Callback progression
            progressCallback.accept(new ProgressUpdate(
                (i + 1) * 100 / totalBatches,
                String.format("Batch %d/%d", i + 1, totalBatches)
            ));
            
            // Pause entre batches pour éviter surcharge
            Thread.sleep(BATCH_DELAY.toMillis());
        }
        
        return new IngestionResult(documents.size(), processedChunks);
    }
    
    private Document enrichMetadata(Document doc) {
        String fileName = doc.metadata().getString("file_name");
        doc.metadata().put("source_type", "markdown");
        doc.metadata().put("ingested_at", Instant.now().toString());
        doc.metadata().put("doc_hash", DigestUtils.md5Hex(doc.text()));
        return doc;
    }
    
    private TextSegment prefixWithSource(TextSegment segment) {
        String title = segment.metadata().getString("file_name", "unknown");
        return TextSegment.from(
            "Source: " + title + "\n\n" + segment.text(),
            segment.metadata()
        );
    }
}
```

## Patterns asynchrones avec Virtual Threads

Java 25 avec Spring Boot 3.5 permet d'exploiter les **Virtual Threads** (JEP 491) pour une concurrence légère sans la complexité de Project Reactor.

```yaml
# application.yaml
spring:
  threads:
    virtual:
      enabled: true  # Active Virtual Threads pour @Async, Tomcat, etc.
```

```java
@Service
public class AsyncIngestionService {
    
    @Async  // Exécuté sur Virtual Thread automatiquement
    public CompletableFuture<IngestionResult> ingestAsync(List<Path> paths) {
        List<Document> documents = loadMarkdownFiles(paths);
        IngestionResult result = ingestionService.ingestWithProgress(
            documents, 
            progress -> log.info("Progress: {}%", progress.percentage())
        );
        return CompletableFuture.completedFuture(result);
    }
}
```

**Pourquoi Virtual Threads plutôt que Reactor:**
- Modèle de programmation impératif classique (plus simple à débugger)
- Parfait pour I/O-bound (appels API embedding, écriture BDD)
- Aucune surcharge sur **24 GB RAM** pour milliers de threads virtuels
- CompletableFuture suffit pour orchestration simple

## Outil MCP léger pour updates ponctuels

Pour les mises à jour depuis Claude Code (1-5 documents), un outil MCP minimaliste évite le timeout:

```java
@Component
public class AlexandriaTools {
    
    @Autowired
    private IngestionService ingestionService;
    
    @McpTool(name = "alexandria_ingest",
             description = "Ingest 1-5 markdown files into Alexandria knowledge base")
    public String ingestDocuments(
            McpSyncRequestContext context,
            @McpToolParam(description = "Comma-separated file paths", required = true) 
            String pathsStr) {
        
        List<Path> paths = Arrays.stream(pathsStr.split(","))
            .map(String::trim)
            .map(Path::of)
            .filter(p -> p.toString().endsWith(".md"))
            .limit(5)  // Hard limit pour éviter timeout
            .toList();
            
        if (paths.isEmpty()) {
            return "No valid .md files provided";
        }
        
        context.progress(p -> p.progress(0.0).total(1.0)
            .message("Loading " + paths.size() + " files"));
        
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
            paths, new TextDocumentParser());
        
        context.progress(p -> p.progress(0.3).total(1.0).message("Embedding"));
        
        IngestionResult result = ingestionService.ingestWithProgress(
            documents,
            progress -> context.progress(p -> p
                .progress(0.3 + progress.percentage() * 0.7 / 100)
                .total(1.0)
                .message(progress.message()))
        );
        
        return String.format("Ingested %d documents (%d chunks) into Alexandria",
            result.documentCount(), result.chunkCount());
    }
    
    @McpTool(name = "alexandria_status",
             description = "Get Alexandria knowledge base statistics")
    public String getStatus() {
        long docCount = embeddingStore.count();
        return String.format("Alexandria contains %d indexed chunks", docCount);
    }
}
```

## Configuration Spring Boot complète

```yaml
# application.yaml
spring:
  profiles:
    active: server
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:postgresql://localhost:5432/alexandria
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      
  ai:
    mcp:
      server:
        name: alexandria
        version: 1.0.0
        type: SYNC
        sse-endpoint: /sse
        request-timeout: 120s
        keep-alive-interval: 30s
        capabilities:
          tool: true
          resource: true

alexandria:
  embedding:
    base-url: ${RUNPOD_URL}
    api-key: ${RUNPOD_API_KEY}
    batch-size: 25
    rate-limit-per-second: 10
  chunking:
    size: 512
    overlap: 50
  docs-path: ./knowledge-base
```

## Recommandations finales pour workflow développeur solo

**Workflow quotidien optimal:**

1. **Initial load**: `java -jar alexandria.jar ingest --recursive ./docs` (CLI)
2. **Ajout ponctuel**: Depuis Claude Code, utiliser `alexandria_ingest` pour 1-5 fichiers
3. **Re-indexation**: Planifier CLI mensuel si embedding model ou chunking change
4. **Pas de watcher**: Overhead injustifié pour "mises à jour occasionnelles"

**Priorité d'implémentation:**
1. CLI avec Picocli (essentiel pour bulk)
2. MCP tool léger (intégration Claude Code)
3. Endpoint REST optionnel (si interface web souhaitée)
4. Watcher: à éviter sauf changement de requirements

Cette architecture minimise la friction quotidienne: le CLI gère les opérations lourdes hors-session, tandis que l'outil MCP permet les ajustements rapides sans quitter Claude Code. Sur un i5-4570 avec **24 GB RAM**, les Virtual Threads et le batching adapté (25 docs) garantissent une ingestion fluide sans saturation mémoire.