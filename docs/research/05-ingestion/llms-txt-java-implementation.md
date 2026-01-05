# Spécification complète llms.txt pour implémentation Java

Le standard llms.txt, créé par **Jeremy Howard (Answer.AI)** en septembre 2024, propose un format Markdown simple permettant aux sites de fournir aux LLMs des informations structurées et optimisées pour le contexte. Ce rapport fournit toutes les spécifications nécessaires pour implémenter `LlmsTxtParser.java` dans Alexandria.

## Anatomie du format llms.txt

Le format utilise un **Markdown minimal** parsable par regex, délibérément simple pour être compris tant par les humains que par les LLMs. La structure impose un ordre strict des sections :

```markdown
# Nom du Projet                          ← H1 OBLIGATOIRE (unique élément requis)

> Résumé concis du projet                ← Blockquote OPTIONNEL

Informations détaillées ici.             ← Contenu libre OPTIONNEL
- Point important 1                         (paragraphes, listes - PAS de headings)
- Point important 2

## Docs                                  ← Sections H2 OPTIONNELLES (0 ou plus)

- [Titre du lien](https://url.md): Description optionnelle
- [Autre lien](https://autre-url.md)

## Optional                              ← Section spéciale (contenu ignorable)

- [Ressource secondaire](https://url.md): Peut être omise si contexte limité
```

La section nommée exactement **`## Optional`** a une signification particulière : les parsers peuvent ignorer son contenu pour économiser des tokens quand le context window est limité.

## Patterns regex validés pour le parsing

L'implémentation Python de référence utilise moins de 20 lignes de code. Voici les patterns regex adaptés pour Java :

| Élément | Pattern Java | Flags |
|---------|--------------|-------|
| H1 Titre | `^#\\s*(?<title>.+?)$` | MULTILINE |
| Blockquote | `^>\\s*(?<summary>.+?)$` | MULTILINE |
| Section H2 | `^##\\s*(?<section>.+?)$` | MULTILINE |
| Lien complet | `-\\s*\\[(?<title>[^\\]]+)\\]\\((?<url>[^\\)]+)\\)(?::\\s*(?<desc>.*))?` | - |

Le pattern de lien capture trois groupes nommés : **title** (obligatoire), **url** (obligatoire), et **desc** (optionnel, peut être null).

## Structure de données Java recommandée

Pour Java 25 avec records, la structure suivante représente fidèlement le format :

```java
/**
 * Représente un document llms.txt parsé.
 * Seul le champ title est garanti non-null.
 */
public record LlmsTxtDocument(
    String title,                              // H1 - OBLIGATOIRE
    String summary,                            // Blockquote - nullable
    String info,                               // Contenu libre - nullable
    Map<String, List<LlmsTxtLink>> sections,   // Sections H2 avec leurs liens
    LocalDateTime parsedAt                     // Métadonnée de parsing
) {
    /**
     * Vérifie si la section Optional est présente.
     */
    public boolean hasOptionalSection() {
        return sections.containsKey("Optional");
    }
    
    /**
     * Retourne les sections sans le contenu optionnel.
     */
    public Map<String, List<LlmsTxtLink>> requiredSections() {
        return sections.entrySet().stream()
            .filter(e -> !e.getKey().equalsIgnoreCase("Optional"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}

/**
 * Représente un lien dans une section.
 */
public record LlmsTxtLink(
    String title,       // Titre du lien - OBLIGATOIRE
    String url,         // URL absolue - OBLIGATOIRE
    String description  // Description après ":" - nullable
) {
    /**
     * Valide que l'URL est absolue et bien formée.
     */
    public boolean isValidUrl() {
        try {
            URI uri = URI.create(url);
            return uri.isAbsolute();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

/**
 * Résultat de validation d'un document llms.txt.
 */
public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings
) {
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }
}
```

## Implémentation du parser LlmsTxtParser.java

```java
public class LlmsTxtParser {
    
    private static final Pattern H1_PATTERN = 
        Pattern.compile("^#\\s*(?<title>.+?)$", Pattern.MULTILINE);
    
    private static final Pattern SUMMARY_PATTERN = 
        Pattern.compile("^>\\s*(?<summary>.+?)$", Pattern.MULTILINE);
    
    private static final Pattern H2_SPLIT_PATTERN = 
        Pattern.compile("^##\\s*(.+?)$", Pattern.MULTILINE);
    
    private static final Pattern LINK_PATTERN = Pattern.compile(
        "-\\s*\\[(?<title>[^\\]]+)\\]\\((?<url>[^\\)]+)\\)(?::\\s*(?<desc>.*))?");

    /**
     * Parse le contenu d'un fichier llms.txt.
     * @throws LlmsTxtParseException si le H1 est absent
     */
    public LlmsTxtDocument parse(String content) throws LlmsTxtParseException {
        Objects.requireNonNull(content, "Content cannot be null");
        
        // Normaliser les fins de ligne
        content = content.replace("\r\n", "\n").replace("\r", "\n");
        
        // 1. Séparer le header des sections H2
        String[] parts = H2_SPLIT_PATTERN.split(content, 2);
        String header = parts[0];
        
        // 2. Extraire le titre H1 (obligatoire)
        String title = extractH1(header);
        if (title == null || title.isBlank()) {
            throw new LlmsTxtParseException("Missing required H1 title");
        }
        
        // 3. Extraire le summary (optionnel)
        String summary = extractSummary(header);
        
        // 4. Extraire le contenu info (optionnel)
        String info = extractInfo(header, title, summary);
        
        // 5. Parser les sections H2
        Map<String, List<LlmsTxtLink>> sections = parseSections(content);
        
        return new LlmsTxtDocument(
            title.trim(), 
            summary != null ? summary.trim() : null,
            info != null && !info.isBlank() ? info.trim() : null,
            sections,
            LocalDateTime.now()
        );
    }
    
    private String extractH1(String header) {
        Matcher matcher = H1_PATTERN.matcher(header);
        return matcher.find() ? matcher.group("title") : null;
    }
    
    private String extractSummary(String header) {
        Matcher matcher = SUMMARY_PATTERN.matcher(header);
        return matcher.find() ? matcher.group("summary") : null;
    }
    
    private String extractInfo(String header, String title, String summary) {
        // Retirer le H1 et le blockquote pour obtenir le contenu info
        String info = header;
        info = info.replaceFirst("^#\\s*.+?$", "").trim();
        if (summary != null) {
            info = info.replaceFirst("^>\\s*.+?$", "").trim();
        }
        return info.isBlank() ? null : info;
    }
    
    private Map<String, List<LlmsTxtLink>> parseSections(String content) {
        Map<String, List<LlmsTxtLink>> sections = new LinkedHashMap<>();
        
        // Trouver toutes les sections H2
        Matcher sectionMatcher = H2_SPLIT_PATTERN.matcher(content);
        List<int[]> sectionPositions = new ArrayList<>();
        List<String> sectionNames = new ArrayList<>();
        
        while (sectionMatcher.find()) {
            sectionPositions.add(new int[]{sectionMatcher.start(), sectionMatcher.end()});
            sectionNames.add(sectionMatcher.group(1).trim());
        }
        
        // Extraire le contenu de chaque section
        for (int i = 0; i < sectionNames.size(); i++) {
            int start = sectionPositions.get(i)[1];
            int end = (i + 1 < sectionPositions.size()) 
                ? sectionPositions.get(i + 1)[0] 
                : content.length();
            
            String sectionContent = content.substring(start, end);
            List<LlmsTxtLink> links = parseLinks(sectionContent);
            sections.put(sectionNames.get(i), links);
        }
        
        return sections;
    }
    
    private List<LlmsTxtLink> parseLinks(String sectionContent) {
        List<LlmsTxtLink> links = new ArrayList<>();
        Matcher linkMatcher = LINK_PATTERN.matcher(sectionContent);
        
        while (linkMatcher.find()) {
            links.add(new LlmsTxtLink(
                linkMatcher.group("title"),
                linkMatcher.group("url"),
                linkMatcher.group("desc")  // Peut être null
            ));
        }
        
        return links;
    }
}
```

## Gestion des URLs : fetch vs métadonnées

Le comportement recommandé suit une **séparation claire des responsabilités** :

| Opération | Comportement | Quand l'utiliser |
|-----------|--------------|------------------|
| **Parsing seul** | Stocker URLs comme métadonnées | Indexation initiale, construction du graphe de navigation |
| **Fetch sélectif** | Récupérer uniquement les URLs demandées | RAG à la demande, requête utilisateur spécifique |
| **Fetch complet** | Récupérer tout sauf `## Optional` | Ingestion batch, construction de base vectorielle |
| **Fetch exhaustif** | Inclure `## Optional` | Contexte maximal disponible |

Pour Alexandria, l'approche recommandée est un **fetcher asynchrone séparé** :

```java
public class LlmsTxtContentFetcher {
    
    private final HttpClient httpClient;
    private final Duration timeout;
    
    public LlmsTxtContentFetcher(Duration timeout) {
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
    
    /**
     * Fetch asynchrone du contenu de toutes les URLs.
     * @param includeOptional inclure la section Optional
     */
    public CompletableFuture<Map<String, FetchResult>> fetchAllAsync(
            LlmsTxtDocument doc, 
            boolean includeOptional) {
        
        Map<String, List<LlmsTxtLink>> sections = includeOptional 
            ? doc.sections() 
            : doc.requiredSections();
        
        List<CompletableFuture<Map.Entry<String, FetchResult>>> futures = 
            sections.values().stream()
                .flatMap(List::stream)
                .map(link -> fetchAsync(link.url())
                    .thenApply(result -> Map.entry(link.url(), result)))
                .toList();
        
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
    
    private CompletableFuture<FetchResult> fetchAsync(String url) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(timeout)
            .GET()
            .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> new FetchResult(
                response.statusCode() == 200,
                response.body(),
                null))
            .exceptionally(ex -> new FetchResult(false, null, ex.getMessage()));
    }
    
    public record FetchResult(boolean success, String content, String error) {}
}
```

## Différence entre llms.txt et llms-full.txt

Ces deux fichiers servent des objectifs complémentaires :

**llms.txt** fonctionne comme une **carte de navigation** : structure légère avec liens vers ressources Markdown. Taille typique inférieure à **10 KB**. C'est le point d'entrée pour les LLMs qui découvrent un site.

**llms-full.txt** contient le **contenu complet inline** : toute la documentation concaténée, souvent avec frontmatter YAML. Peut atteindre **plusieurs MB** (Anthropic : ~481K tokens, Cloudflare : ~3.7M tokens).

Les données de trafic Mintlify révèlent que **llms-full.txt reçoit 2x plus de requêtes** que llms.txt, suggérant que les LLMs préfèrent le contenu intégré au RAG dynamique quand possible.

Pour Alexandria, supporter les deux formats est recommandé :

```java
public enum LlmsTxtType {
    STANDARD,    // llms.txt - liens uniquement
    FULL         // llms-full.txt - contenu complet
}

public record LlmsTxtSource(
    URI location,
    LlmsTxtType type,
    LlmsTxtDocument document,
    String rawContent  // Pour llms-full.txt, garder le contenu brut
) {}
```

## Validation et gestion des erreurs

Le parser doit implémenter une **validation gracieuse** :

```java
public class LlmsTxtValidator {
    
    public ValidationResult validate(LlmsTxtDocument doc) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Règle 1: H1 obligatoire (déjà vérifié au parsing)
        if (doc.title() == null || doc.title().isBlank()) {
            errors.add("Missing required H1 title");
        }
        
        // Règle 2: Valider format des URLs
        doc.sections().values().stream()
            .flatMap(List::stream)
            .forEach(link -> {
                if (!link.isValidUrl()) {
                    errors.add("Invalid URL format: " + link.url());
                }
                if (!link.url().startsWith("https://")) {
                    warnings.add("Non-HTTPS URL: " + link.url());
                }
            });
        
        // Règle 3: Vérifier présence d'au moins une section avec liens
        if (doc.sections().isEmpty()) {
            warnings.add("No H2 sections found - file may be incomplete");
        }
        
        // Règle 4: Vérifier nombre de liens (recommandé: 20-50 max)
        long totalLinks = doc.sections().values().stream()
            .mapToLong(List::size).sum();
        if (totalLinks > 50) {
            warnings.add("Large number of links (" + totalLinks + 
                ") - consider reducing for better curation");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
}
```

Pour les **URLs inaccessibles lors du fetch**, adopter une stratégie de graceful degradation :

```java
public record FetchError(
    String url,
    FetchErrorType type,
    String message,
    int httpStatus  // 0 si erreur réseau
) {}

public enum FetchErrorType {
    TIMEOUT,           // Timeout dépassé
    HTTP_ERROR,        // 4xx ou 5xx
    CONNECTION_ERROR,  // Erreur réseau
    INVALID_CONTENT    // Contenu non-Markdown
}
```

## Exemples réels de fichiers llms.txt

**Cloudflare Developer Docs** (https://developers.cloudflare.com/llms.txt) :

```markdown
# Cloudflare Developer Documentation

Easily build and deploy full-stack applications everywhere,
thanks to integrated compute, storage, and networking.

## Agents

- [Build Agents on Cloudflare](https://developers.cloudflare.com/agents/index.md)
- [Patterns](https://developers.cloudflare.com/agents/patterns/index.md)

## Workers

- [Get started guide](https://developers.cloudflare.com/workers/get-started/guide/index.md)
- [Runtime APIs](https://developers.cloudflare.com/workers/runtime-apis/index.md)
```

**Trigger.dev** (https://trigger.dev/docs/llms.txt) :

```markdown
# Trigger.dev

## Docs

- [API keys](https://trigger.dev/docs/apikeys.md): How to authenticate with Trigger.dev
- [Bulk actions](https://trigger.dev/docs/bulk-actions.md): Perform actions on multiple runs
- [CLI deploy command](https://trigger.dev/docs/cli-deploy-commands.md): Deploy your tasks
```

**Pattern commun observé** : URLs pointant vers fichiers `.md`, descriptions orientées action ("How to...", "Learn..."), sections logiques par fonctionnalité.

## Architecture recommandée pour Alexandria

```
alexandria/
├── parser/
│   ├── LlmsTxtParser.java           # Parsing du format
│   ├── LlmsTxtValidator.java        # Validation
│   └── LlmsTxtParseException.java   # Exception métier
├── model/
│   ├── LlmsTxtDocument.java         # Record principal
│   ├── LlmsTxtLink.java             # Record lien
│   └── LlmsTxtSource.java           # Source avec métadonnées
├── fetcher/
│   ├── LlmsTxtContentFetcher.java   # Fetch async des URLs
│   └── FetchResult.java             # Résultat de fetch
└── indexer/
    └── LlmsTxtIndexer.java          # Intégration Langchain4j
```

L'intégration avec **Langchain4j 1.10.0** pour l'indexation sémantique peut utiliser le `Document` natif :

```java
public class LlmsTxtIndexer {
    
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    
    public void indexDocument(LlmsTxtDocument doc, Map<String, String> fetchedContent) {
        List<Document> documents = new ArrayList<>();
        
        // Indexer les métadonnées du document principal
        documents.add(Document.from(
            "# " + doc.title() + "\n" + 
            (doc.summary() != null ? "> " + doc.summary() : ""),
            Metadata.from("type", "llms-txt-header")
                .add("title", doc.title())
        ));
        
        // Indexer chaque contenu fetché
        fetchedContent.forEach((url, content) -> {
            documents.add(Document.from(
                content,
                Metadata.from("source", url)
                    .add("type", "llms-txt-content")
            ));
        });
        
        // Chunking et embedding via Langchain4j
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> segments = splitter.splitAll(documents);
        
        List<Embedding> embeddings = embeddingModel.embedAll(
            segments.stream().map(TextSegment::text).toList()
        ).content();
        
        embeddingStore.addAll(embeddings, segments);
    }
}
```

## Points clés pour l'implémentation

L'implémentation doit respecter plusieurs principes essentiels. Premièrement, le **parsing et le fetching doivent être séparés** - parser uniquement extrait les métadonnées, le fetch est une opération distincte et asynchrone. Deuxièmement, la **section Optional est sémantique** - elle indique du contenu de moindre priorité, utile pour le graceful degradation quand le contexte est limité.

Pour l'encodage, utiliser **UTF-8** systématiquement. Les URLs dans llms.txt sont toujours **absolues** (pas de chemins relatifs). La convention `.md` suggère d'ajouter cette extension aux URLs HTML pour obtenir la version Markdown.

Le nombre de liens recommandé est de **20-50 maximum** pour une bonne curation. Au-delà, le fichier devient un dump plutôt qu'un guide. Pour les très grandes documentations comme Cloudflare, des fichiers llms.txt segmentés par section (`/workers/llms.txt`, `/pages/llms.txt`) sont une bonne pratique.

Finalement, llms.txt n'est **pas encore un standard officiellement adopté** par les majors (OpenAI, Google) mais gagne en traction avec des implémentations chez Anthropic, Cloudflare, Stripe, et de nombreux frameworks de documentation.