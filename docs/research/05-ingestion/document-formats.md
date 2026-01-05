# Formats de documents pour Alexandria : guide d'implémentation RAG

Les parsers Java pour un serveur RAG technique en 2026 sont matures et bien intégrés à l'écosystème Langchain4j. **CommonMark-java** et l'intégration native Langchain4j couvrent 80% des besoins avec **~7 dépendances Maven**. L'architecture recommandée est un système modulaire basé sur le pattern Strategy avec SPI, permettant d'ajouter des formats sans modifier le code existant. Pour le MVP, concentrez-vous sur Markdown, texte brut et llms.txt qui partagent le même parser.

## Tableau comparatif des 8 formats candidats

| Format | Parser recommandé | Version | Licence | Effort | Priorité | Langchain4j natif |
|--------|------------------|---------|---------|--------|----------|-------------------|
| **Markdown (.md)** | CommonMark-java | 0.27.0 | BSD-2 | Faible | **MVP** | ✅ MarkdownDocumentParser |
| **Texte brut (.txt)** | TextDocumentParser | Core | Apache 2.0 | Faible | **MVP** | ✅ Natif core |
| **llms.txt / llms-full.txt** | CommonMark-java + regex | 0.27.0 | BSD-2 | Faible | **MVP** | Via Markdown |
| **HTML** | JSoup | 1.22.1 | MIT | Faible | **MVP** | ✅ HtmlToTextDocumentTransformer |
| **PDF** | Apache PDFBox | 3.0.6 | Apache 2.0 | Moyen | Futur | ✅ ApachePdfBoxDocumentParser |
| **AsciiDoc (.adoc)** | AsciidoctorJ | 3.0.1 | Apache 2.0 | Moyen | Futur | ❌ Custom requis |
| **Code source (.java)** | JavaParser | 3.27.1 | Apache 2.0 | Moyen | Futur | ❌ Custom requis |
| **reStructuredText (.rst)** | Apache Tika | 3.2.3 | Apache 2.0 | Élevé | Différé | Via Tika uniquement |

## Analyse détaillée des parsers par format

### Markdown : le pilier central avec CommonMark-java

CommonMark-java s'impose comme choix optimal pour le parsing Markdown-aware. Sa dernière version **0.27.0 (octobre 2025)** offre un AST complet avec source spans, essentiel pour le chunking intelligent. La bibliothèque est légère (~100KB), sans dépendances transitives, et supporte les extensions GFM (tables, task lists, strikethrough) via modules séparés.

L'intégration Langchain4j existe via `langchain4j-document-parser-markdown` qui utilise CommonMark en interne. Pour un chunking de **450-500 tokens** avec préservation des code blocks, l'approche recommandée utilise l'AST pour identifier les frontières naturelles (headers, blocs de code) avant de subdiviser les sections textuelles.

**flexmark-java** (version 0.64.8) offre un positionnement caractère par caractère plus précis mais n'a pas été mis à jour depuis mai 2023. Son footprint de ~5MB et sa maintenance incertaine le rendent moins adapté qu'CommonMark pour un projet neuf.

### llms.txt : Markdown structuré pour LLMs

Le standard llms.txt de llmstxt.org est du **Markdown pur avec structure imposée** : titre H1 obligatoire, blockquote de résumé, sections H2 avec listes de liens. Adopté par **844,000+ sites** dont Anthropic, Cloudflare, Stripe et Vercel, ce format offre une documentation pré-curée idéale pour RAG.

La distinction clé : `llms.txt` est un index de navigation (~quelques KB), tandis que `llms-full.txt` contient l'intégralité du contenu (potentiellement 100k+ tokens). Les données Profound montrent que llms-full.txt reçoit **2x plus de trafic AI** que llms.txt, les LLMs préférant ingérer le contenu complet.

Pour le parsing, aucune bibliothèque Java dédiée n'existe. La stratégie recommandée combine CommonMark-java pour le parsing Markdown et une couche regex légère pour extraire les métadonnées structurelles (titre, sections, URLs).

### HTML et JSoup : extraction robuste à faible coût

JSoup **1.22.1** reste la référence pour l'extraction HTML en Java. Compatible HTML5 (spécification WHATWG), la bibliothèque de seulement **~450KB** gère le HTML malformé et expose des sélecteurs CSS/XPath pour cibler précisément le contenu. Langchain4j propose `HtmlToTextDocumentTransformer` via le module `langchain4j-document-transformer-jsoup`.

Pour la documentation technique, JSoup excelle à extraire `title`, headings (`h1-h6`), et contenu principal en ignorant navigation et publicités. La méthode `Element.text()` produit du texte propre adapté à l'embedding vectoriel.

### PDF et Apache PDFBox : texte extractible mais sans structure

Apache PDFBox **3.0.6** (octobre 2025) fonctionne avec Java 11+ et reste compatible Java 25. Langchain4j l'encapsule dans `ApachePdfBoxDocumentParser`. La qualité d'extraction est excellente pour les PDFs textuels, mais les documents scannés nécessitent Tesseract (dépendance externe).

L'alternative Apache Tika utilise PDFBox en interne tout en ajoutant la détection automatique de format et la normalisation des métadonnées. Pour un système multi-format, Tika simplifie l'architecture mais ajoute **~100-150 MB** de dépendances transitives incluant POI, PDFBox, et parsers XML.

### AsciiDoc avec AsciidoctorJ : lourd mais complet

AsciidoctorJ **3.0.1** (novembre 2024) offre un parsing AST complet des fichiers `.adoc`, format répandu dans la documentation Spring et Red Hat. Le prix : **~30MB** de dépendances incluant JRuby embarqué.

L'approche recommandée pour RAG : convertir en HTML via `asciidoctor.convert()` puis extraire le texte avec JSoup ou Tika. Cette conversion préserve la structure (sections, code blocks, admonitions) tout en produisant du contenu parseable avec les outils existants.

### reStructuredText : l'impasse Java

**Aucun parser Java mature n'existe pour .rst**. JRst est abandonné depuis 2013. Les alternatives viables :
- Apache Tika 3.x qui délègue à docutils
- Subprocess Python avec `rst2html`
- Pré-conversion hors pipeline Java

Recommandation : **différer le support RST** sauf si requis explicitement. La complexité ne justifie pas l'effort pour un cas d'usage minoritaire en documentation technique moderne.

### Code source Java avec JavaParser

JavaParser **3.27.1** parse le code Java jusqu'à la version 24 et expose les Javadoc via la classe `JavadocComment`. L'extraction des tags `@param`, `@return`, `@throws` permet de construire une documentation API structurée. Le module core pèse ~2MB sans dépendances lourdes.

Pour le RAG, combinez le parsing AST avec une extraction textuelle des commentaires de classe et méthode. Les métadonnées (nom de classe, signature de méthode) enrichissent le contexte des chunks.

## Dépendances Maven pour le MVP

Les quatre formats prioritaires (Markdown, texte, llms.txt, HTML) nécessitent ces dépendances :

```xml
<!-- Langchain4j Core -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.10.0</version>
</dependency>

<!-- Markdown Parser (CommonMark via Langchain4j) -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-markdown</artifactId>
    <version>1.10.0</version>
</dependency>

<!-- HTML Transformer (JSoup via Langchain4j) -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-transformer-jsoup</artifactId>
    <version>1.10.0</version>
</dependency>

<!-- Extensions GFM pour Markdown avancé -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
    <version>0.27.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-yaml-front-matter</artifactId>
    <version>0.27.0</version>
</dependency>
```

Pour les formats futurs, ajoutez selon les besoins :

```xml
<!-- PDF (Phase 2) -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
    <version>1.10.0</version>
</dependency>

<!-- AsciiDoc (Phase 2) -->
<dependency>
    <groupId>org.asciidoctor</groupId>
    <artifactId>asciidoctorj</artifactId>
    <version>3.0.1</version>
</dependency>

<!-- Java Source Parsing (Phase 2) -->
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
    <version>3.27.1</version>
</dependency>

<!-- Multi-format fallback (si nécessaire) -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-tika</artifactId>
    <version>1.10.0</version>
</dependency>
```

## Architecture recommandée : Strategy pattern avec SPI

L'architecture plugin via **Java SPI (Service Provider Interface)** permet d'ajouter des formats sans recompiler le core. Cette approche est déjà utilisée par Langchain4j pour ses DocumentParsers.

```
alexandria/
├── core/
│   ├── DocumentParser.java          # Interface Strategy
│   ├── DocumentParserRegistry.java  # Factory avec SPI discovery
│   └── ChunkingService.java         # Chunking markdown-aware
├── adapters/
│   ├── markdown/
│   │   └── MarkdownDocumentParser.java
│   ├── html/
│   │   └── HtmlDocumentParser.java
│   └── llmstxt/
│       └── LlmsTxtDocumentParser.java
└── config/
    └── ParserConfig.java             # Spring configuration
```

### Interface DocumentParser personnalisée

```java
public interface AlexandriaDocumentParser {
    
    /**
     * Parse un document en chunks avec métadonnées.
     */
    List<DocumentChunk> parse(InputStream input, DocumentMetadata metadata);
    
    /**
     * Formats MIME supportés par ce parser.
     */
    Set<String> supportedMimeTypes();
    
    /**
     * Extensions de fichier supportées.
     */
    Set<String> supportedExtensions();
}
```

### Implémentation Markdown-aware avec breadcrumbs

```java
@Component
public class MarkdownDocumentParser implements AlexandriaDocumentParser {
    
    private final Parser parser;
    private final int maxTokensPerChunk = 475; // Cible 450-500
    
    public MarkdownDocumentParser() {
        this.parser = Parser.builder()
            .extensions(List.of(
                TablesExtension.create(),
                YamlFrontMatterExtension.create()
            ))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    }
    
    @Override
    public List<DocumentChunk> parse(InputStream input, DocumentMetadata meta) {
        String markdown = new String(input.readAllBytes(), UTF_8);
        Node document = parser.parse(markdown);
        
        List<DocumentChunk> chunks = new ArrayList<>();
        Deque<String> headerBreadcrumb = new ArrayDeque<>();
        
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                updateBreadcrumb(headerBreadcrumb, heading);
                visitChildren(heading);
            }
            
            @Override
            public void visit(FencedCodeBlock codeBlock) {
                // Préserver les code blocks intacts
                chunks.add(DocumentChunk.builder()
                    .content(codeBlock.getLiteral())
                    .type(ChunkType.CODE)
                    .language(codeBlock.getInfo())
                    .breadcrumb(String.join(" > ", headerBreadcrumb))
                    .preserveFormatting(true)
                    .build());
            }
            
            @Override
            public void visit(Paragraph paragraph) {
                String text = extractText(paragraph);
                chunks.add(DocumentChunk.builder()
                    .content(text)
                    .type(ChunkType.TEXT)
                    .breadcrumb(String.join(" > ", headerBreadcrumb))
                    .build());
                visitChildren(paragraph);
            }
        });
        
        return mergeAndSplitChunks(chunks, maxTokensPerChunk);
    }
    
    @Override
    public Set<String> supportedMimeTypes() {
        return Set.of("text/markdown", "text/x-markdown");
    }
    
    @Override
    public Set<String> supportedExtensions() {
        return Set.of("md", "markdown");
    }
}
```

### Registry avec détection automatique

```java
@Component
public class DocumentParserRegistry {
    
    private final Map<String, AlexandriaDocumentParser> parsersByExtension;
    private final Map<String, AlexandriaDocumentParser> parsersByMimeType;
    
    public DocumentParserRegistry(List<AlexandriaDocumentParser> parsers) {
        this.parsersByExtension = new HashMap<>();
        this.parsersByMimeType = new HashMap<>();
        
        parsers.forEach(parser -> {
            parser.supportedExtensions().forEach(ext -> 
                parsersByExtension.put(ext.toLowerCase(), parser));
            parser.supportedMimeTypes().forEach(mime -> 
                parsersByMimeType.put(mime, parser));
        });
    }
    
    public AlexandriaDocumentParser getParser(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        return parsersByExtension.getOrDefault(ext.toLowerCase(), 
            parsersByExtension.get("txt")); // Fallback texte brut
    }
}
```

## Recommandations finales et roadmap

La stratégie d'implémentation en trois phases optimise le ratio effort/valeur :

- **Phase MVP** : Markdown + texte + llms.txt + HTML représentent **95% des besoins** pour documentation technique Claude Code. Effort estimé : 2-3 jours avec les modules Langchain4j existants.

- **Phase 2** : PDF et AsciiDoc ajoutent le support documentation legacy et formats Red Hat/Spring. L'intégration PDFBox est directe ; AsciidoctorJ nécessite une conversion HTML intermédiaire.

- **Phase 3** : JavaParser pour ingestion de code source documenté, Tika comme fallback universel. Évaluer le besoin RST selon l'usage réel.

Le pattern SPI garantit l'extensibilité sans modification du core. Chaque nouveau format s'ajoute comme module Maven indépendant avec auto-discovery Spring. Cette architecture supporte facilement **20+ formats** sans dégradation de performance ni complexité accrue du code principal.