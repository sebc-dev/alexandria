# Architecture logicielle pour Alexandria : système RAG Java self-hosted

**Alexandria est un projet solo de ~20 classes — l'architecture hybride pragmatique (couches classiques + packages par feature) constitue le sweet spot optimal.** Ce document recommande une architecture en couches Spring Boot idiomatique avec organisation par feature (`ingestion/`, `search/`, `source/`, `mcp/`), des interfaces uniquement aux frontières d'intégration réelles (embedding, vector store, crawler), et une orchestration de pipeline par simple service séquentiel plutôt que par framework dédié. Cette approche maximise la maintenabilité et la testabilité sans créer d'abstractions prématurées. Le principe directeur est celui de Martin Fowler sur YAGNI : ne pas ajouter de couches d'abstraction tant qu'un besoin réel ne les justifie pas, tout en gardant le code suffisamment malléable pour les introduire quand le moment viendra. Les signaux de migration vers une architecture plus structurée sont documentés en fin de document.

**Niveau de confiance global de cette recommandation : Élevé.** Elle converge avec le consensus observé dans les projets LangChain4j open-source, les recommandations de la communauté Spring, et les retours d'expérience de développeurs expérimentés sur l'over-engineering des petits projets.

---

## Matrice de décision : quatre architectures évaluées pour Alexandria

| Critère (pondéré par pertinence projet) | Couches classiques | Clean / Hexagonale | Vertical Slice | **Hybride pragmatique** |
|---|:---:|:---:|:---:|:---:|
| **Maintenabilité** (20 classes, solo) | ★★★★☆ | ★★★☆☆ | ★★★☆☆ | **★★★★★** |
| **Testabilité** du pipeline RAG | ★★★☆☆ | ★★★★★ | ★★★★☆ | **★★★★☆** |
| **Complexité d'implémentation** | ★★★★★ | ★★☆☆☆ | ★★★☆☆ | **★★★★★** |
| **Alignement Spring Boot** | ★★★★★ | ★★☆☆☆ | ★★★☆☆ | **★★★★☆** |
| **Pertinence projet solo ~20 classes** | ★★★★☆ | ★★☆☆☆ | ★★★☆☆ | **★★★★★** |
| **Overhead de mapping/boilerplate** | Faible | Élevé (5 DTOs/entité) | Moyen | **Faible** |
| **Évolutivité vers plus de structure** | Modérée | Déjà maximale | Élevée | **Élevée** |
| **Sweet spot taille projet** | 5-40 classes | 50+ classes | 30+ classes | **15-40 classes** |

**Verdict** : La Clean Architecture / Hexagonale est conçue pour des projets d'entreprise avec équipes multiples et domaines complexes — elle impose un overhead de mapping entre couches (Entity → Domain → DTO → Response) disproportionné pour Alexandria. La Vertical Slice Architecture brille avec des équipes feature-oriented, mais un projet à 3-4 features ne justifie pas l'isolation stricte entre slices. **L'hybride pragmatique combine le meilleur** : la simplicité idiomatique Spring Boot avec une organisation par feature qui prévient le God Service.

[Confiance : **Élevée** — consensus convergent entre Arho Huttunen (arhohuttunen.com), Dimitri Mestdagh (dimitri.codes), et les recommandations de Martin Fowler sur YAGNI.]

---

## Architecture recommandée : arborescence complète et responsabilités

### Structure de packages

```
dev.alexandria/
├── AlexandriaApplication.java              # @SpringBootApplication
│
├── config/                                  # Configuration Spring & beans
│   ├── EmbeddingConfig.java                # Bean EmbeddingModel ONNX, EmbeddingStore pgvector
│   ├── CrawlerConfig.java                  # RestClient pour Crawl4AI, timeouts, retry
│   └── McpServerConfig.java                # ToolCallbackProvider, enregistrement outils MCP
│
├── ingestion/                               # Pipeline d'ingestion (feature)
│   ├── IngestionPipeline.java              # Orchestrateur séquentiel : crawl→parse→chunk→embed→store
│   ├── CrawlerClient.java                  # Client HTTP REST vers Crawl4AI sidecar (port 11235)
│   ├── MarkdownChunker.java                # Chunking markdown-aware via Flexmark
│   └── IngestionResult.java                # Record : résultat d'une ingestion (compteurs, durée)
│
├── search/                                  # Pipeline de recherche (feature)
│   ├── HybridSearchService.java            # Orchestration : embed query → vector search + FTS → RRF merge
│   ├── RrfMerger.java                      # Reciprocal Rank Fusion scoring
│   └── SearchResult.java                   # Record : résultat formaté pour MCP
│
├── source/                                  # Gestion des sources documentaires (feature)
│   ├── Source.java                          # Entité JPA : URL, statut, dates, config crawl
│   ├── SourceRepository.java               # Spring Data JPA
│   └── SourceService.java                  # CRUD, statut, logique de mise à jour incrémentale
│
├── document/                                # Stockage des chunks et embeddings
│   ├── DocumentChunk.java                  # Entité JPA avec colonne pgvector (embedding 384d)
│   ├── DocumentChunkRepository.java        # Requêtes natives pgvector + FTS PostgreSQL
│   └── ChunkMetadata.java                  # Record : métadonnées enrichies (source, position, titre)
│
├── mcp/                                     # Interface MCP (outils exposés à Claude Code)
│   └── AlexandriaTools.java                # @Tool : search_docs, list_sources, get_source_status, ingest_url
│
└── api/                                     # Interface REST admin/management
    ├── AdminController.java                # Endpoints REST : trigger ingestion, CRUD sources, health
    └── dto/                                # Records DTO pour l'API REST uniquement
        ├── IngestionRequest.java
        └── SourceResponse.java
```

**Nombre total : ~20 fichiers Java** — exactement dans la fourchette cible de 15-25 classes.

### Diagramme de dépendances entre modules

```
    ┌─────────┐     ┌──────────────────┐
    │   mcp/  │     │       api/       │
    │  Tools  │     │   Controllers    │
    └────┬────┘     └────────┬─────────┘
         │                   │
         └─────────┬─────────┘
                   │ (injection Spring)
         ┌─────────▼─────────┐
         │   ingestion/      │◄──── config/
         │   search/         │      (beans Spring)
         │   source/         │
         └─────────┬─────────┘
                   │
         ┌─────────▼─────────┐
         │    document/       │
         │   (JPA + pgvector) │
         └─────────┬─────────┘
                   │
              PostgreSQL 17
              + pgvector 0.8
```

**Règle clé** : `mcp/` et `api/` sont des **adaptateurs d'entrée** — ils ne contiennent aucune logique métier, uniquement de la délégation vers les services des features `ingestion/`, `search/`, `source/`. Cette règle suffit à obtenir 80% des bénéfices de l'architecture hexagonale sans en payer le coût.

### Quand utiliser des interfaces (et quand c'est du YAGNI)

| Composant | Interface ? | Justification |
|---|---|---|
| `CrawlerClient` | **Oui** — `ContentCrawler` | Frontière d'intégration externe (Crawl4AI REST). Permet un stub pour les tests. |
| `DocumentChunkRepository` | **Non** (déjà une interface Spring Data) | Spring Data fournit l'abstraction. |
| `MarkdownChunker` | **Non** | Une seule implémentation, logique pure testable directement. |
| `HybridSearchService` | **Non** | Orchestrateur interne, une seule implémentation. |
| `SourceService` | **Non** | CRUD classique, pas de polymorphisme. |
| `EmbeddingModel` | **Oui** (fournie par LangChain4j) | Interface LangChain4j native — pas à créer. |
| `EmbeddingStore` | **Oui** (fournie par LangChain4j) | Interface LangChain4j native — pas à créer. |

**Principe directeur** (Dimitri Mestdagh, dimitri.codes) : « Si vous me demandez si vous devriez utiliser une interface pour vos services, ma réponse serait non. La seule exception est si vous faites de l'inversion de contrôle ou avez plusieurs implémentations. » Depuis Spring 3.2+, CGLIB rend les interfaces inutiles pour le proxying. Mockito mock les classes concrètes sans problème.

[Confiance : **Élevée** — consensus documenté par Dimitri Mestdagh, Baeldung, TheServerSide, et aligné avec le principe YAGNI de Martin Fowler.]

---

## Patterns d'implémentation pour le pipeline RAG

### Le pipeline d'ingestion : orchestration séquentielle simple

Quatre approches ont été évaluées — l'orchestration séquentielle par service est la plus adaptée à Alexandria.

| Approche | Complexité | Testabilité | Adapté à ~20 classes ? |
|---|---|---|---|
| **Orchestration séquentielle** | Très faible | Bonne (mocks par constructeur) | **Oui — recommandé** |
| Pipeline pattern (generics) | Moyenne | Excellente | Borderline over-engineering |
| Chain of Responsibility | Moyenne | Bonne | Non — sémantique inadaptée (chaque étape DOIT s'exécuter) |
| Spring Batch | Élevée | Bonne | Non — overhead infrastructure démesuré |

**Implémentation recommandée :**

```java
@Service
public class IngestionPipeline {
    private final ContentCrawler crawler;        // Interface → CrawlerClient
    private final MarkdownChunker chunker;       // Classe concrète
    private final EmbeddingModel embeddingModel;  // LangChain4j
    private final EmbeddingStore<TextSegment> embeddingStore; // LangChain4j
    private final SourceService sourceService;

    // Injection par constructeur — pas de @Autowired sur champs
    public IngestionPipeline(ContentCrawler crawler, MarkdownChunker chunker,
                             EmbeddingModel embeddingModel,
                             EmbeddingStore<TextSegment> embeddingStore,
                             SourceService sourceService) {
        this.crawler = crawler;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.sourceService = sourceService;
    }

    public IngestionResult ingest(Source source) {
        var markdown = crawler.crawl(source.url());          // HTTP vers Crawl4AI
        var chunks = chunker.chunk(markdown, source);         // Logique pure, Flexmark
        var segments = chunks.stream()
            .map(c -> TextSegment.from(c.text(), c.toMetadata()))
            .toList();

        // LangChain4j EmbeddingStoreIngestor gère embed+store atomiquement
        var ingestor = EmbeddingStoreIngestor.builder()
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();
        ingestor.ingest(segments.stream()
            .map(s -> Document.from(s.text(), s.metadata()))
            .toList());

        sourceService.markIngested(source, chunks.size());
        return new IngestionResult(source.url(), chunks.size(), Instant.now());
    }
}
```

**Pourquoi pas le Pipeline pattern ?** Avec seulement 4-5 étapes fixes et un seul développeur, le Pipeline pattern générique (`Handler<I,O>`) ajoute une infrastructure (interface + classe Pipeline + composition) qui ne résout aucun problème réel. L'orchestration séquentielle se lit de haut en bas, chaque étape est testable via mock du constructeur, et le refactoring vers un pattern plus structuré est trivial si le besoin émerge.

[Confiance : **Élevée** — aligné avec Baeldung, java-design-patterns.com, et l'observation que tous les exemples LangChain4j utilisent cette approche.]

### Retry et résilience ciblés

```java
@Service
public class CrawlerClient implements ContentCrawler {
    private final RestClient restClient;

    @Retryable(maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2),
               retryFor = {IOException.class, RestClientException.class})
    public String crawl(URI url) {
        return restClient.post()
            .uri("/crawl")
            .body(Map.of("urls", List.of(url.toString())))
            .retrieve()
            .body(CrawlResponse.class)
            .markdown();
    }
}
```

Appliquer `@Retryable` uniquement sur les appels réseau (Crawl4AI, pgvector write) — jamais sur la logique pure comme le chunking. Spring Retry suffit ; Resilience4j est surdimensionné sauf si des circuit breakers sont nécessaires.

### Stratégie de test par étage du pipeline

```java
// 1. UNIT TEST — Chunking : logique pure, zéro mock
class MarkdownChunkerTest {
    private final MarkdownChunker chunker = new MarkdownChunker(500, 50);

    @Test
    void preserves_heading_hierarchy() {
        String md = "# Title\n## Section\nContent paragraph...";
        var chunks = chunker.chunk(md, TestSource.SAMPLE);
        assertThat(chunks).allSatisfy(c ->
            assertThat(c.metadata().title()).isNotBlank());
    }
}

// 2. INTEGRATION TEST — Vector search avec Testcontainers pgvector
@SpringBootTest
@Testcontainers
class HybridSearchServiceIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres"));

    @Autowired HybridSearchService searchService;

    @Test
    void hybrid_search_returns_relevant_chunks() {
        // Ingest test data via pipeline
        // Search and verify RRF-merged results
    }
}

// 3. UNIT TEST — Pipeline orchestration : mocks pour chaque étape
@ExtendWith(MockitoExtension.class)
class IngestionPipelineTest {
    @Mock ContentCrawler crawler;
    @Mock MarkdownChunker chunker;
    @Mock EmbeddingModel embeddingModel;
    @Mock EmbeddingStore<TextSegment> embeddingStore;
    @Mock SourceService sourceService;

    @Test
    void ingest_calls_all_stages_in_order() {
        var pipeline = new IngestionPipeline(crawler, chunker,
            embeddingModel, embeddingStore, sourceService);
        // Configure mocks, verify call order with InOrder
    }
}
```

**Stratégie par étage :**

| Étage | Type de test | Outils | Mock / Réel |
|---|---|---|---|
| **Chunking** (Flexmark) | Unit | JUnit 5 seul | Logique pure — aucun mock |
| **CrawlerClient** | Unit | WireMock | Mock HTTP Crawl4AI |
| **Embedding ONNX** | Intégration légère | JUnit 5 + modèle ONNX réel | Réel (bge-small-en-v1.5 tourne en ~50ms in-process) |
| **Vector Store pgvector** | Intégration | Testcontainers `pgvector/pgvector:pg17` | Réel pgvector |
| **Recherche hybride** | Intégration | Testcontainers + modèle ONNX | Tout réel |
| **Pipeline complet** | Unit | Mockito | Mocks de chaque étape |

Pour les tests d'intégration pgvector, utiliser `@ServiceConnection` de Spring Boot 3.1+ qui auto-configure le `DataSource` depuis le conteneur Testcontainers. **Ne pas utiliser H2 comme substitut** — H2 ne supporte pas pgvector ni les opérateurs de recherche vectorielle.

[Confiance : **Élevée** — pattern Testcontainers pgvector officiellement documenté sur testcontainers.com, `@ServiceConnection` documenté dans Spring Boot 3.5.]

---

## Cohabitation MCP stdio et API REST dans Spring Boot

Le projet Alexandria doit exposer **deux interfaces** : un serveur MCP stdio (pour Claude Code) et une API REST admin. La contrainte technique fondamentale est que **le mode STDIO exige `web-application-type=none`**, donc les deux modes ne peuvent pas tourner simultanément dans le même processus.

### Pattern recommandé : un JAR, deux modes de lancement

Utiliser `spring-ai-starter-mcp-server-webmvc` qui supporte nativement le double transport :

**Mode REST + MCP SSE** (développement, admin) :
```bash
java -jar alexandria.jar --spring.profiles.active=web
```

**Mode STDIO** (Claude Code via `.claude/mcp.json`) :
```bash
java -Dspring.ai.mcp.server.stdio=true \
     -Dspring.main.web-application-type=none \
     -Dspring.main.banner-mode=off \
     -Dlogging.pattern.console= \
     -jar alexandria.jar --spring.profiles.active=stdio
```

**Fichiers de configuration :**

```yaml
# application.yml (partagé)
spring:
  ai:
    mcp:
      server:
        name: alexandria
        version: 1.0.0
        type: SYNC

# application-web.yml
server:
  port: 8080
spring.ai.mcp.server.stdio: false

# application-stdio.yml
spring:
  main:
    web-application-type: none
    banner-mode: off
  ai.mcp.server.stdio: true
logging:
  pattern.console:
  file.name: ./logs/alexandria-mcp.log
```

### Partage des services métier entre MCP et REST

Le pattern est celui de l'injection Spring standard — les deux interfaces consomment les mêmes services :

```java
// Services partagés (cœur métier)
@Service public class HybridSearchService { /* ... */ }
@Service public class SourceService { /* ... */ }
@Service public class IngestionPipeline { /* ... */ }

// Interface MCP — délègue aux services
@Service
public class AlexandriaTools {
    private final HybridSearchService search;
    private final SourceService sources;

    @Tool(description = "Search indexed documentation by semantic query")
    public List<SearchResult> searchDocs(String query, @ToolParam int maxResults) {
        return search.hybridSearch(query, maxResults);
    }

    @Tool(description = "List all indexed documentation sources with status")
    public List<Source> listSources() {
        return sources.findAll();
    }
}

// Interface REST — délègue aux mêmes services
@RestController @RequestMapping("/api")
public class AdminController {
    private final IngestionPipeline pipeline;
    private final SourceService sources;

    @PostMapping("/sources/{id}/ingest")
    public IngestionResult triggerIngestion(@PathVariable Long id) {
        var source = sources.findById(id);
        return pipeline.ingest(source);
    }
}

// Enregistrement MCP
@Configuration
public class McpServerConfig {
    @Bean
    ToolCallbackProvider toolCallbackProvider(AlexandriaTools tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build();
    }
}
```

**Aucune logique dupliquée** — `AlexandriaTools` et `AdminController` sont des adaptateurs minces qui délèguent au même graphe de services. C'est la règle architecturale la plus importante du projet.

[Confiance : **Élevée** — pattern confirmé par la documentation Spring AI MCP, les exemples officiels spring-ai-examples, et plusieurs projets communautaires (Vikalp/Medium, mtwn105/GitHub).]

---

## Anti-patterns documentés : liste concrète de « ne pas faire »

### 1. Le God Service d'ingestion
**Symptôme** : `IngestionService` atteint 500+ lignes avec crawling, parsing, chunking, embedding, stockage, gestion des sources, et notifications — tout dans une seule classe.
**Seuil d'alerte** : service > **200 lignes** ou > **5 dépendances injectées**.
**Solution** : découper par responsabilité (`CrawlerClient`, `MarkdownChunker`, `IngestionPipeline` comme orchestrateur mince).

### 2. L'anti-pattern ServiceImpl
**Symptôme** : `SearchService` (interface) + `SearchServiceImpl` (seule implémentation) pour chaque service.
**Coût réel** : double le nombre de fichiers (potentiellement **+8-10 fichiers** inutiles sur ce projet), complique la navigation IDE, zéro bénéfice puisque Mockito mock les classes concrètes.
**Règle** : créer une interface uniquement quand une **deuxième implémentation existe** ou quand on teste via un stub in-memory (cas du `ContentCrawler`). L'IDE peut extraire une interface en un clic quand le besoin émerge.

### 3. L'abstraction prématurée du pipeline
**Symptôme** : construire un `PipelineFramework<I,O>` avec `PipelineStep`, `PipelineContext`, `PipelineResult`, `PipelineErrorHandler` — 6 classes d'infrastructure pour 4 étapes fixes.
**Test décisif** : si les étapes du pipeline ne sont jamais recomposées dynamiquement, l'abstraction ne sert à rien. Préférer l'appel séquentiel.

### 4. Le mapping multicouche
**Symptôme** : `SourceEntity` → `SourceDomain` → `SourceDTO` → `SourceResponse` avec 3 classes `Mapper` pour convertir entre représentations quasi-identiques.
**Solution pour Alexandria** : une **entité JPA** (`Source`) et un **record DTO** (`SourceResponse`) pour la couche API suffisent. Le domaine métier n'est pas assez complexe pour justifier un objet domaine séparé de l'entité.

### 5. L'injection par champ (`@Autowired`)
**Symptôme** : `@Autowired private SourceRepository repo;` au lieu de l'injection par constructeur.
**Problèmes** : rend l'instanciation en test impossible sans Spring, masque les dépendances, permet les dépendances circulaires.
**Solution** : constructeur explicite ou `@RequiredArgsConstructor` de Lombok.

### 6. Ignorer les Java Records
**Symptôme** : classes mutables avec getters/setters/equals/hashCode pour les objets de transit du pipeline.
**Solution** : utiliser `record` pour tout ce qui est immuable et traverse le pipeline :

```java
public record IngestionResult(String sourceUrl, int chunksProcessed, Instant completedAt) {}
public record SearchResult(String content, ChunkMetadata metadata, double score) {}
public record ChunkMetadata(String sourceUrl, String title, int position) {}
```

### 7. Tester uniquement en E2E
**Symptôme** : tous les tests utilisent `@SpringBootTest` avec Testcontainers → suite de tests lente (30s+), feedback loop pénible.
**Règle** : **80% des tests doivent être des tests unitaires** (chunking, RRF scoring, metadata extraction) qui s'exécutent en millisecondes. Réserver `@SpringBootTest` + Testcontainers aux tests d'intégration pgvector et au pipeline E2E.

---

## Observations des projets RAG Java open-source

L'analyse de projets réels sur GitHub (langchain4j-examples, danvega/java-rag, miliariadnane/spring-boot-doc-rag-bot) révèle des patterns convergents. **Tous les projets LangChain4j observés utilisent l'orchestration séquentielle simple** — aucun n'implémente de Pipeline pattern formel ou de Chain of Responsibility. La séparation systématique entre ingestion (souvent déclenchée au démarrage via `CommandLineRunner` ou `@PostConstruct`) et retrieval (au request-time) est universelle. Les configurations RAG sont regroupées dans des `@Configuration` classes qui exposent `EmbeddingStoreIngestor` et `EmbeddingStoreContentRetriever` comme beans Spring.

**Anti-pattern récurrent observé** : la quasi-totalité des projets communautaires n'a aucun test — c'est la lacune la plus flagrante. Les projets les plus matures (customer-support-agent de LangChain4j) utilisent `@SpringBootTest` + `@MockitoBean` et un pattern original de « Judge Model » (un LLM qui évalue la qualité des réponses du système sous test).

**Abstraction LangChain4j à exploiter** : le framework fournit déjà les interfaces clés (`EmbeddingModel`, `EmbeddingStore<TextSegment>`, `ContentRetriever`). Alexandria n'a pas besoin de créer ses propres abstractions pour ces composants — il suffit de consommer les beans LangChain4j via injection Spring. Le `EmbeddingStoreIngestor` encapsule la logique embed+store et accepte un `DocumentSplitter` configurable.

[Confiance : **Élevée** pour les patterns observés, **Moyenne** pour l'extrapolation que ces patterns sont optimaux — les projets communautaires sont souvent des démos, pas des systèmes de production.]

---

## Checklist de validation architecturale

Utiliser cette checklist périodiquement (tous les 5-10 classes ajoutées) pour vérifier que l'architecture reste saine :

**Santé structurelle :**
- [ ] Aucun service ne dépasse **200 lignes** ou **5 dépendances injectées**
- [ ] `AlexandriaTools` et `AdminController` ne contiennent **aucune logique métier** — uniquement de la délégation
- [ ] Toute la logique métier est testable **sans Spring context** (sauf intégration pgvector)
- [ ] Les `record` sont utilisés pour tous les objets de transit immuables
- [ ] Injection par **constructeur** exclusivement (pas de `@Autowired` sur champs)

**Intégrité des interfaces :**
- [ ] Chaque `interface` a **au minimum 2 implémentations** (dont stubs de test) ou représente une frontière d'intégration externe
- [ ] Pas de `XxxServiceImpl` pour une seule implémentation
- [ ] Les abstractions LangChain4j (`EmbeddingModel`, `EmbeddingStore`) sont utilisées directement, pas wrappées dans des interfaces maison

**Testabilité :**
- [ ] Tests unitaires pour le chunking, le RRF scoring, et le parsing de métadonnées → **<100ms**
- [ ] Tests d'intégration pgvector via Testcontainers avec `@ServiceConnection`
- [ ] Le ratio est ~**80% unit / 20% intégration**
- [ ] Le `CrawlerClient` est testable via WireMock (stub HTTP Crawl4AI)

**Signaux de migration vers plus de structure :**
- [ ] ⚠️ Si un service atteint 300+ lignes → extraire des sous-services
- [ ] ⚠️ Si un deuxième type de crawler est nécessaire → l'interface `ContentCrawler` est déjà prête
- [ ] ⚠️ Si le nombre de classes dépasse 40 → considérer des modules Maven séparés (`domain`, `infrastructure`)
- [ ] ⚠️ Si les tests d'intégration dépassent 60s → ajouter des test slices custom avec `@DataJpaTest`

**Indicateurs d'over-engineering :**
- [ ] 🚫 Plus de 2 niveaux de mapping pour la même donnée
- [ ] 🚫 Des packages vides ou avec un seul fichier
- [ ] 🚫 Des classes `Factory`, `Strategy`, `Builder` custom pour un seul cas d'usage
- [ ] 🚫 Un `PipelineFramework` maison quand l'appel séquentiel suffisait

---

## Conclusion : l'architecture doit être méritée, pas installée par défaut

Le piège principal pour Alexandria n'est pas le manque de structure — c'est l'excès. Un développeur solo avec 20 classes n'a pas besoin de ports et adapters formels, de modules Maven séparés, ni d'un framework de pipeline générique. **L'architecture recommandée ici est délibérément simple** : des packages par feature dans un module unique, des interfaces uniquement aux frontières réelles, et un pipeline en appels séquentiels. Cette simplicité n'est pas un compromis — c'est un choix architectural actif, soutenu par le principe que le code le plus maintenable est celui qui n'existe pas.

Les abstractions LangChain4j (`EmbeddingModel`, `EmbeddingStore`, `EmbeddingStoreIngestor`) fournissent déjà les bonnes frontières. Le starter `spring-ai-starter-mcp-server-webmvc` résout élégamment la cohabitation MCP/REST avec un JAR unique et deux profils de lancement. Et Testcontainers avec `pgvector/pgvector:pg17` rend les tests d'intégration pgvector aussi simples qu'un test H2 classique — sans les compromis.

Le seul investissement architectural non-négociable dès le départ : **séparer strictement les adaptateurs d'entrée (MCP, REST) de la logique métier (services)**. Cette unique règle garantit que le code est testable, évolutif, et que le passage à une architecture plus structurée sera un refactoring incrémental plutôt qu'une réécriture.
