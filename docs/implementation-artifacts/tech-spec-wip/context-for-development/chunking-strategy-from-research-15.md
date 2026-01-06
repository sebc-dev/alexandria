# Chunking Strategy (from research #15)

**Paramètres de base:**
- **Taille**: 450-500 tokens
- **Overlap**: 50-75 tokens (10-15%)
- **Stratégie**: Markdown-aware hierarchical (headers + recursive splitting)
- **maxOversizedChunk**: 1500 tokens (pour code blocks volumineux)

**Unités atomiques (jamais split):**

| Élément | Comportement | Fallback si > 1500 tokens |
|---------|--------------|---------------------------|
| Code blocks ` ``` ` | Préservation stricte | Split aux boundaries fonction/classe |
| Tables markdown | Préservation stricte | Reformater en key-value par ligne |
| YAML front matter | Extraire → metadata | N/A (typiquement < 100 tokens) |

**Priorité de split** (du plus au moins prioritaire):
1. **Code blocks** - Toujours préserver
2. **Tables** - Toujours préserver
3. **Headers** - Primary split boundary
4. **Lists** - Split entre items, préserver contexte intro
5. **Paragraphs** - Secondary split boundary
6. **Sentences** - Tertiary (pour paragraphes oversized)

**Breadcrumbs:**
- Depth: H1 > H2 > H3 (éviter H4+ = bruit)
- Format: String délimitée `"Configuration > Database > PostgreSQL"`
- Storage: Metadata `breadcrumbs` sur chaque chunk

```java
package dev.alexandria.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.splitter.DocumentSplitter;
import dev.langchain4j.model.Tokenizer;

/**
 * Splitter Markdown-aware avec préservation des unités atomiques.
 * Langchain4j n'a pas de splitter markdown dédié (PR #2418 toujours draft).
 */
public class AlexandriaMarkdownSplitter implements DocumentSplitter {

    private static final int MAX_CHUNK_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 75;
    private static final int MAX_OVERSIZED_TOKENS = 1500;
    private static final int BREADCRUMB_DEPTH = 3;

    private final Tokenizer tokenizer;

    /**
     * @param tokenizer Obtenu via LangchainConfig bean
     * @see LangchainConfig#tokenizer()
     */
    public AlexandriaMarkdownSplitter(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String content = document.text();

        // 1. Extraire YAML front matter → metadata
        var frontMatterResult = extractFrontMatter(content);
        Map<String, Object> docMetadata = frontMatterResult.metadata();
        content = frontMatterResult.content();

        // 2. Identifier et protéger les unités atomiques
        var protectedContent = protectAtomicUnits(content);

        // 3. Split par headers puis récursivement
        List<TextSegment> segments = splitByHeaders(protectedContent, docMetadata);

        // 4. Restaurer les unités protégées et ajouter breadcrumbs
        return segments.stream()
            .map(this::restoreAtomicUnits)
            .map(s -> addBreadcrumbMetadata(s, docMetadata))
            .toList();
    }

    /**
     * Extrait YAML front matter (--- ... ---) en metadata.
     */
    private FrontMatterResult extractFrontMatter(String markdown) {
        if (!markdown.startsWith("---")) {
            return new FrontMatterResult(markdown, Map.of());
        }

        int endIndex = markdown.indexOf("---", 3);
        if (endIndex == -1) {
            return new FrontMatterResult(markdown, Map.of());
        }

        String yamlBlock = markdown.substring(3, endIndex).trim();
        String remaining = markdown.substring(endIndex + 3).trim();

        // Parse YAML basique (ou utiliser SnakeYAML)
        Map<String, Object> metadata = parseSimpleYaml(yamlBlock);
        return new FrontMatterResult(remaining, metadata);
    }

    /**
     * Remplace code blocks et tables par placeholders.
     */
    private ProtectedContent protectAtomicUnits(String content) {
        Map<String, String> placeholders = new HashMap<>();
        String result = content;

        // Protéger code blocks
        Pattern codePattern = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);
        Matcher codeMatcher = codePattern.matcher(result);
        int codeIndex = 0;
        while (codeMatcher.find()) {
            String placeholder = "{{CODE_BLOCK_" + (codeIndex++) + "}}";
            placeholders.put(placeholder, codeMatcher.group());
            result = result.replace(codeMatcher.group(), placeholder);
        }

        // Protéger tables
        Pattern tablePattern = Pattern.compile("^\\|.*\\|$[\\s\\S]*?(?=\\n\\n|$)", Pattern.MULTILINE);
        Matcher tableMatcher = tablePattern.matcher(result);
        int tableIndex = 0;
        while (tableMatcher.find()) {
            String placeholder = "{{TABLE_" + (tableIndex++) + "}}";
            placeholders.put(placeholder, tableMatcher.group());
            result = result.replace(tableMatcher.group(), placeholder);
        }

        return new ProtectedContent(result, placeholders);
    }

    record FrontMatterResult(String content, Map<String, Object> metadata) {}
    record ProtectedContent(String content, Map<String, String> placeholders) {}
}
```

**Configuration recommandée:**

```yaml
# application.yml
alexandria:
  chunking:
    max-tokens: 500
    overlap-tokens: 75
    max-oversized-tokens: 1500
    breadcrumb-depth: 3
    preserve-code-blocks: true
    preserve-tables: true
    extract-front-matter: true
```

**LangchainConfig - Tokenizer et Splitter beans:**

```java
package dev.alexandria.config;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.alexandria.core.AlexandriaMarkdownSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangchainConfig {

    /**
     * Tokenizer pour le chunking markdown-aware.
     * Utilise tiktoken (cl100k_base) comme approximation pour BGE-M3.
     * Alternative: HuggingFaceTokenizer si précision critique.
     */
    @Bean
    public Tokenizer tokenizer() {
        return new OpenAiTokenizer("gpt-4o-mini");
    }

    @Bean
    public AlexandriaMarkdownSplitter markdownSplitter(Tokenizer tokenizer) {
        return new AlexandriaMarkdownSplitter(tokenizer);
    }

    // Autres beans Langchain4j (EmbeddingModel, EmbeddingStore) ...
}
```

**ChunkMetadata record (from research #10 + #12):**

```java
/**
 * Métadonnées par chunk - 8 champs pour tracking complet.
 * Contrainte Langchain4j: types primitifs (String, int, long, double, boolean).
 */
public record ChunkMetadata(
    String sourceUri,        // URI logique (pas filesystem path) - identifiant document
    String documentHash,     // SHA-256 du document complet - détection changements
    int chunkIndex,          // Position dans le document
    String breadcrumbs,      // "H1 > H2 > H3" (string délimitée)
    String documentTitle,    // Titre du document (du front matter ou H1)
    String contentHash,      // SHA-256 du chunk - déduplication fine
    long createdAt,          // Epoch millis
    String documentType      // "markdown", "llmstxt", "text"
) {
    /**
     * Convertit path filesystem en URI logique sécurisé.
     * Ne jamais exposer /home/user/... à Claude.
     */
    public static String toLogicalUri(Path filePath, Path basePath) {
        Path relative = basePath.relativize(filePath);
        return "docs://" + relative.toString().replace("\\", "/");
    }

    /**
     * Calcule le hash SHA-256 normalisé d'un contenu.
     * Normalisation: NFKC + espaces + fins de ligne.
     */
    public static String computeHash(String content) {
        // Normalisation Unicode NFKC
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        // Normaliser espaces et fins de ligne
        normalized = normalized.replaceAll("[\\u00A0\\u2000-\\u200B\\u3000\\t]+", " ")
                               .replace("\r\n", "\n").replace("\r", "\n")
                               .replaceAll(" +", " ").replaceAll("\n+", "\n")
                               .strip();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

**Budget tokens metadata:** 50-80 tokens par chunk (10-15% du budget).

**Champs clés pour update strategy:**
- `sourceUri` = identifiant stable du document (chemin logique)
- `documentHash` = détection de changements (si différent → re-ingestion)
