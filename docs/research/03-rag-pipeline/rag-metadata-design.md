# Métadonnées RAG optimales pour le projet Alexandria

L'architecture de métadonnées idéale pour un système RAG mono-utilisateur avec Claude Code repose sur **un équilibre entre richesse contextuelle et économie de tokens**. Langchain4j impose une contrainte critique : seuls 6 types primitifs sont supportés (String, Integer, Long, Float, Double, UUID), sans objets imbriqués ni tableaux. Cette limitation oriente vers un schéma plat mais expressif, où les breadcrumbs hiérarchiques sont stockés en string délimitée et la fraîcheur temporelle en timestamps Long.

## Métadonnées essentielles vs optionnelles pour la production

La recherche sur les systèmes RAG en production révèle une hiérarchie claire de métadonnées selon leur impact sur la qualité du retrieval.

**Tier 1 - Indispensables** (impact direct sur retrieval et reconstruction du contexte) :

| Champ | Type Langchain4j | Justification |
|-------|------------------|---------------|
| `source_uri` | String | Identification logique du document (pas le chemin filesystem) |
| `chunk_index` | Integer | Positionnement pour reconstruction de contexte adjacent |
| `breadcrumbs` | String | Hiérarchie H1 > H2 > H3 pour le contexte sémantique |
| `document_title` | String | Citation et identification rapide par le LLM |
| `content_hash` | String | Déduplication et détection de changements |

**Tier 2 - Recommandées** (amélioration du filtering et ranking) :

| Champ | Type Langchain4j | Justification |
|-------|------------------|---------------|
| `document_type` | String | Filtering par catégorie (api-ref, tutorial, changelog) |
| `modified_at` | Long | Freshness ranking, epoch millis pour compatibilité JSONB |
| `language` | String | Filtering multilingue si applicable |

**Tier 3 - Optionnelles** pour un contexte mono-utilisateur, les métadonnées `author`, `access_level`, et `tenant_id` ont peu de valeur. En revanche, `version` peut être utile pour les changelogs.

## Le schéma Java record proposé

```java
public record ChunkMetadata(
    // Tier 1 - Essentielles
    String sourceUri,      // "/docs/api/authentication" - URI logique, pas filesystem
    Integer chunkIndex,    // 0, 1, 2... position dans le document
    String breadcrumbs,    // "API Reference > Authentication > OAuth2"
    String documentTitle,  // "Guide d'authentification OAuth2"
    String contentHash,    // SHA-256 tronqué pour déduplication
    
    // Tier 2 - Recommandées  
    String documentType,   // "api-ref" | "tutorial" | "changelog" | "concept"
    Long modifiedAt        // System.currentTimeMillis() à l'ingestion
) {
    public Metadata toLangchain4j() {
        return Metadata.from("source_uri", sourceUri)
            .put("chunk_index", chunkIndex)
            .put("breadcrumbs", breadcrumbs)
            .put("document_title", documentTitle)
            .put("content_hash", contentHash)
            .put("document_type", documentType)
            .put("modified_at", modifiedAt);
    }
    
    public static ChunkMetadata fromLangchain4j(Metadata meta) {
        return new ChunkMetadata(
            meta.getString("source_uri"),
            meta.getInteger("chunk_index"),
            meta.getString("breadcrumbs"),
            meta.getString("document_title"),
            meta.getString("content_hash"),
            meta.getString("document_type"),
            meta.getLong("modified_at")
        );
    }
}
```

**Justification de chaque champ** : `sourceUri` remplace le chemin filesystem pour la sécurité et portabilité. `chunkIndex` permet de récupérer les chunks adjacents pour contexte étendu. `breadcrumbs` en string car Langchain4j ne supporte pas les arrays. `contentHash` en SHA-256 tronqué (**8-12 caractères suffisent**) pour détecter les modifications. `documentType` active le filtering pré-retrieval selon l'intention de la requête. `modifiedAt` en Long (epoch millis) car les dates ISO ne sont pas supportées nativement.

## Format recommandé pour les breadcrumbs hiérarchiques

Trois approches existent, mais **la contrainte Langchain4j tranche le débat** : seul le format flat string est viable.

Le format **flat string délimité** (`"H1 > H2 > H3"`) s'impose pour Alexandria :

```java
String breadcrumbs = "API Reference > Authentication > OAuth2 Flow";
```

**Avantages** : Compatible avec les 6 types Langchain4j, recherche `ContainsString` possible sur pgvector, overhead minimal (~20-30 tokens), directement affichable. **Inconvénients** : Filtering par niveau spécifique impossible (mais rarement nécessaire en mono-utilisateur).

Le format **array structuré** (`["H1", "H2", "H3"]`) serait idéal pour le filtering hiérarchique, mais Langchain4j ne supporte pas les types List. Alternative si nécessaire : stocker des champs séparés `h1`, `h2`, `h3` mais cela rigidifie le schéma.

**Pattern d'implémentation dans le splitter existant** :

```java
public class MarkdownSplitter {
    private String buildBreadcrumbs(List<String> headerStack) {
        return String.join(" > ", headerStack);  // ["API", "Auth"] → "API > Auth"
    }
    
    public TextSegment createSegment(String content, List<String> headers, 
                                      String sourceUri, int index) {
        return TextSegment.from(content, 
            Metadata.from("breadcrumbs", buildBreadcrumbs(headers))
                .put("source_uri", sourceUri)
                .put("chunk_index", index)
                .put("document_title", headers.isEmpty() ? "" : headers.get(0)));
    }
}
```

## Chemin fichier source : ne pas exposer le filesystem

La recherche en sécurité RAG est unanime : **ne jamais exposer les chemins filesystem** dans les résultats. Les risques incluent la divulgation de structure interne, le path traversal, et la fuite d'informations sensibles dans les chemins.

**Transformation recommandée** :

```java
// À l'ingestion - mapper filesystem → URI logique
private String toLogicalUri(Path filePath, Path docsRoot) {
    Path relative = docsRoot.relativize(filePath);
    String uri = "/" + relative.toString()
        .replace(File.separatorChar, '/')
        .replaceFirst("\\.md$", "");  // /docs/api/auth.md → /api/auth
    return uri;
}
```

Stocker le mapping dans une table séparée si la reconstruction du chemin est nécessaire pour le debugging :

```sql
CREATE TABLE document_registry (
    logical_uri TEXT PRIMARY KEY,
    filesystem_path TEXT,  -- NON exposé aux clients
    ingested_at TIMESTAMP
);
```

## Scores de relevance : guidance pour l'exposition au LLM

La recherche révèle un insight contre-intuitif : **les LLMs ne pondèrent pas mathématiquement le contexte selon les scores numériques**. Ils traitent le contenu séquentiellement via l'attention, avec un biais positionnel (début et fin plus influents).

**Ce qui fonctionne** :
- **Ordonner par relevance** - placer les chunks les plus pertinents en premier
- **Labels qualitatifs** - "Highly relevant", "Related", "Context" plutôt que 0.94
- **Seuils de coupure** - ne pas inclure de chunks sous un threshold (ex: 0.7)

**Ce qu'il ne faut pas faire** :
- Exposer les scores bruts au LLM (non interprétables)
- Mélanger similarity score et rerank score (échelles incomparables)

**Format recommandé pour la réponse MCP** :

```markdown
## Résultats de recherche

### Très pertinent
**Source:** /api/authentication
**Section:** OAuth2 > Token Refresh

Le token d'accès expire après 3600 secondes. Pour le renouveler...

---

### Pertinent  
**Source:** /guides/security
**Section:** Bonnes pratiques > Sessions

Les sessions utilisent des tokens JWT avec rotation automatique...
```

**Pour le debugging**, exposer les scores dans un champ structuré séparé :

```java
public record SearchResult(
    String content,
    ChunkMetadata metadata,
    String relevanceLevel,     // "high" | "medium" | "low" - pour le LLM
    Double similarityScore,    // 0.0-1.0 - pour debugging uniquement
    Double rerankScore         // 0.0-1.0 - plus fiable si reranking actif
) {}
```

## Optimisation du format de présentation pour Claude Code

Les LLMs sont massivement entraînés sur du Markdown, ce qui en fait le format optimal pour la consommation RAG. Le standard llms.txt confirme cette orientation.

**Structure recommandée pour les réponses RAG** :

```markdown
## {document_title}
> {breadcrumbs}

**Source:** {source_uri} | **Modifié:** {formatted_date}

{chunk_content}

---
```

**Budget tokens pour les métadonnées** : viser **50-80 tokens par chunk** de métadonnées contextuelles. Au-delà de 100 tokens, le ratio signal/bruit se dégrade. Avec des chunks de 450-500 tokens, les métadonnées représentent environ **10-15%** du budget total par chunk.

**Configuration de l'EmbeddingStore pgvector** :

```java
EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
    .host("localhost")
    .port(5432)
    .database("alexandria")
    .table("chunks")
    .dimension(1536)  // OpenAI ada-002 ou ajuster selon modèle
    .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())  // JSONB recommandé
    .createTable(true)
    .useIndex(true)
    .indexListSize(100)
    .build();
```

**Filtering au retrieval avec document_type** :

```java
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

// Routing par intention de requête
Filter filter = switch (queryIntent) {
    case HOW_TO -> metadataKey("document_type").isIn("tutorial", "guide");
    case API_LOOKUP -> metadataKey("document_type").isEqualTo("api-ref");
    case TROUBLESHOOT -> metadataKey("document_type").isIn("troubleshooting", "changelog");
    default -> null;  // Pas de filtre
};

var retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(store)
    .embeddingModel(embeddingModel)
    .maxResults(5)
    .filter(filter)
    .build();
```

## Conclusion : le schéma minimal viable pour Alexandria

Pour un usage mono-utilisateur avec Claude Code, le schéma suivant maximise l'utilité tout en respectant les contraintes Langchain4j :

**Métadonnées à implémenter immédiatement** : `source_uri`, `chunk_index`, `breadcrumbs`, `document_title`, `document_type`. **À ajouter si besoin** : `modified_at` pour freshness, `content_hash` pour déduplication incrémentale.

Le format de présentation Markdown avec labels de relevance qualitatifs optimise la consommation par Claude Code. Les scores numériques restent dans la couche technique pour debugging et tuning, jamais exposés au LLM.

L'insight clé de la recherche Anthropic sur le Contextual Retrieval mérite attention : **prépendre un contexte explicatif humain aux chunks** (50-100 tokens décrivant d'où vient le chunk) réduit les échecs de retrieval de 35%. Cette approche pourrait enrichir le splitter Alexandria : générer automatiquement une phrase de contexte par chunk via le LLM à l'ingestion.