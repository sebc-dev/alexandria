# Phase 4: Ingestion Pipeline - Research

**Researched:** 2026-02-17
**Domain:** Markdown parsing, heading-based chunking, code block extraction, metadata enrichment, JSON import, LangChain4j embedding storage
**Confidence:** HIGH

## Summary

Phase 4 transforms crawled Markdown (from Phase 3's CrawlService) into richly-annotated, searchable chunks stored via the LangChain4j EmbeddingStore (from Phase 2). The pipeline has two modes: (1) automatic heading-based chunking that splits at H1/H2/H3 boundaries, extracts fenced code blocks as separate chunks, and enriches all chunks with 5 metadata fields; and (2) pre-chunked JSON import for AI-assisted workflows where external tools produce optimized chunks.

The core Markdown parsing library should be **commonmark-java 0.27.1** -- actively maintained (last release January 2026), zero-dependency core, clean AST with `Heading`, `FencedCodeBlock`, and `Node` tree navigation APIs (`getNext()`, `getFirstChild()`, `getParent()`). The alternative flexmark-java (0.64.8) has not been updated since June 2023 and is heavier. commonmark-java's `TablesExtension` handles GFM tables (needed to avoid splitting mid-table). The chunking algorithm walks the flat AST sibling list, accumulating nodes between heading boundaries and extracting `FencedCodeBlock` nodes into separate code chunks.

Storage uses the existing LangChain4j `EmbeddingStore<TextSegment>` with `TextSegment.from(text, metadata)` where metadata keys are `source_url`, `section_path`, `content_type`, `last_updated`, `language`. For the pre-chunked import replacement mode, `embeddingStore.removeAll(Filter)` with `metadataKey("source_url").isEqualTo(url)` deletes existing chunks before importing. Code language auto-detection for unlabeled code blocks uses a simple keyword/pattern heuristic (no external library needed).

**Primary recommendation:** Use commonmark-java 0.27.1 with TablesExtension for AST-based chunking. Walk the document's child nodes linearly, tracking heading hierarchy. Extract FencedCodeBlock nodes as separate code chunks. Use LangChain4j's `EmbeddingStore.addAll()` and `removeAll(Filter)` for storage and replacement. Implement pre-chunked JSON import with Jackson deserialization and Bean Validation.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Heading-only splitting at H1, H2, and H3 boundaries
- H4+ remains within the parent H3 chunk
- No max size limit -- each heading section = 1 chunk regardless of length
- Short chunks (1-2 sentences under a H3) are kept as-is -- precision over size
- No textual overlap between chunks -- heading path provides context instead
- AI-assisted chunking available as alternative mode (via pre-chunked JSON import)
- Every fenced code block is extracted as a separate chunk with `content_type=code`
- Language tag preserved from the fence; blocks without language tag get `language=unknown` with best-effort auto-detection
- Code chunks inherit the heading path of their parent section (no [code] suffix)
- The prose chunk retains its text minus the extracted code blocks
- 5 metadata fields per chunk: `source_url`, `section_path`, `content_type`, `last_updated`, `language`
- `section_path` uses slash separator: `guide/configuration/routes`
- `content_type` has 2 values: `prose` and `code`
- `language` field populated for code chunks; null for prose chunks
- Metadata keys use snake_case (established in Phase 2)
- Import via JSON file containing chunks with complete metadata
- JSON must include all 5 metadata fields per chunk -- Alexandria stores as-is, no inference
- Validation: reject entire file if any chunk is invalid (all-or-nothing)
- Import mode: replacement -- deletes existing chunks for the source_url before importing new ones

### Claude's Discretion
- Markdown parsing library choice (flexmark-java or alternative)
- Code language auto-detection approach
- JSON schema design for pre-chunked import format
- Chunking implementation details (AST walking vs regex vs streaming)
- Error reporting format for JSON validation failures

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| commonmark-java | 0.27.1 | Markdown parsing to AST | Actively maintained (Jan 2026 release), zero-dependency core, clean Node API with `getNext()`/`getFirstChild()`/`getParent()`, simpler than flexmark-java |
| commonmark-ext-gfm-tables | 0.27.1 | GFM table support in AST | Parses tables as `TableBlock` nodes so chunker can avoid splitting mid-table |
| LangChain4j (existing) | 1.11.0 | TextSegment/Metadata/EmbeddingStore for chunk storage | Already in project; `addAll()` for batch storage, `removeAll(Filter)` for replacement import |
| Jackson Databind (existing) | (managed by Spring Boot 3.5.7) | JSON deserialization for pre-chunked import | Already on classpath via spring-boot-starter-web |
| Jakarta Bean Validation (existing) | (managed by Spring Boot 3.5.7) | Validation of pre-chunked import JSON | `@NotNull`, `@NotBlank` on import DTOs with `@Valid` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-validation | (managed by Spring Boot 3.5.7) | Provides Hibernate Validator for Bean Validation | Need `@Valid` annotation support for pre-chunked import validation |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| commonmark-java | flexmark-java 0.64.8 | Flexmark has richer extensions and source-level position tracking, but last release was June 2023 (unmaintained for 2.5 years); heavier API; 35-50% slower than commonmark-java is misleading -- flexmark is slower |
| commonmark-java | Regex-based splitting | Regex cannot handle nested fenced code blocks, indented headings inside code blocks, or setext-style headings reliably; AST approach is correct by construction |
| Simple keyword heuristic for language detection | External library (enry/linguist port) | No mature Java library exists for programming language detection from snippets; heuristic is sufficient for common languages (java, python, javascript, yaml, xml, sql, bash, go, rust); edge cases get `unknown` |

**Installation (Gradle - libs.versions.toml):**
```toml
# In [versions]
commonmark = "0.27.1"

# In [libraries]
commonmark = { module = "org.commonmark:commonmark", version.ref = "commonmark" }
commonmark-ext-gfm-tables = { module = "org.commonmark:commonmark-ext-gfm-tables", version.ref = "commonmark" }
```

```kotlin
// In build.gradle.kts dependencies
implementation(libs.commonmark)
implementation(libs.commonmark.ext.gfm.tables)
// If not already present for validation:
implementation("org.springframework.boot:spring-boot-starter-validation")
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/dev/alexandria/
├── ingestion/
│   ├── IngestionState.java                 # (existing)
│   ├── IngestionStateRepository.java       # (existing)
│   ├── IngestionService.java               # Orchestrator: crawl -> chunk -> embed -> store
│   ├── chunking/
│   │   ├── MarkdownChunker.java            # AST-based heading splitter, code extractor
│   │   ├── DocumentChunkData.java          # Record: text + metadata (pre-embedding)
│   │   └── LanguageDetector.java           # Simple keyword heuristic for code language
│   └── prechunked/
│       ├── PreChunkedImporter.java         # JSON import with validation + replacement
│       ├── PreChunkedRequest.java          # Outer JSON record: source_url + chunks list
│       └── PreChunkedChunk.java            # Inner JSON record: text + 5 metadata fields
```

### Pattern 1: AST-Based Heading Chunking
**What:** Parse Markdown with commonmark-java, walk the document's direct child nodes (which are blocks: Heading, Paragraph, FencedCodeBlock, BulletList, TableBlock, etc.), accumulate nodes between H1/H2/H3 boundaries into chunks.
**When to use:** All automatic Markdown chunking.

**Key insight:** CommonMark AST represents a document as a flat list of block-level children under the Document node. Headings are siblings of paragraphs and code blocks, not parents. This means chunking is a linear scan of `document.getFirstChild()` then `node.getNext()` siblings, tracking the current heading hierarchy.

**Example:**
```java
// Source: commonmark-java 0.27.1 API
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.tables.TablesExtension;

public class MarkdownChunker {

    private final Parser parser;

    public MarkdownChunker() {
        this.parser = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build();
    }

    public List<DocumentChunkData> chunk(String markdown, String sourceUrl, String lastUpdated) {
        Node document = parser.parse(markdown);
        List<DocumentChunkData> chunks = new ArrayList<>();

        // Track heading hierarchy as a stack: [h1Text, h2Text, h3Text]
        String[] headingPath = new String[3]; // index 0=H1, 1=H2, 2=H3
        List<Node> currentNodes = new ArrayList<>();
        List<FencedCodeBlock> codeBlocks = new ArrayList<>();

        Node child = document.getFirstChild();
        while (child != null) {
            Node next = child.getNext(); // capture before potential unlink

            if (child instanceof Heading heading && heading.getLevel() <= 3) {
                // Flush previous section
                if (!currentNodes.isEmpty() || !codeBlocks.isEmpty()) {
                    emitChunks(chunks, currentNodes, codeBlocks, headingPath, sourceUrl, lastUpdated);
                    currentNodes.clear();
                    codeBlocks.clear();
                }
                // Update heading hierarchy
                int level = heading.getLevel();
                headingPath[level - 1] = extractText(heading);
                // Clear deeper levels
                for (int i = level; i < 3; i++) headingPath[i] = null;

                currentNodes.add(heading);
            } else if (child instanceof FencedCodeBlock codeBlock) {
                codeBlocks.add(codeBlock);
            } else {
                currentNodes.add(child);
            }

            child = next;
        }

        // Flush final section
        if (!currentNodes.isEmpty() || !codeBlocks.isEmpty()) {
            emitChunks(chunks, currentNodes, codeBlocks, headingPath, sourceUrl, lastUpdated);
        }

        return chunks;
    }

    private String buildSectionPath(String[] headingPath) {
        // "guide/configuration/routes" format
        return Arrays.stream(headingPath)
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""))
                .collect(Collectors.joining("/"));
    }
}
```

### Pattern 2: Code Block Extraction
**What:** Every `FencedCodeBlock` in a section becomes a separate chunk with `content_type=code`. The prose chunk gets the section text minus the code blocks.
**When to use:** All sections containing fenced code blocks.

**Example:**
```java
// Source: commonmark-java FencedCodeBlock API
private void emitChunks(List<DocumentChunkData> chunks,
                        List<Node> proseNodes,
                        List<FencedCodeBlock> codeBlocks,
                        String[] headingPath,
                        String sourceUrl,
                        String lastUpdated) {

    String sectionPath = buildSectionPath(headingPath);

    // Emit prose chunk (section text minus code blocks)
    String proseText = renderNodesToText(proseNodes);
    if (!proseText.isBlank()) {
        chunks.add(new DocumentChunkData(
                proseText, sourceUrl, sectionPath, "prose", lastUpdated, null
        ));
    }

    // Emit code chunks
    for (FencedCodeBlock codeBlock : codeBlocks) {
        String code = codeBlock.getLiteral();
        String language = detectLanguage(codeBlock);
        chunks.add(new DocumentChunkData(
                code, sourceUrl, sectionPath, "code", lastUpdated, language
        ));
    }
}

private String detectLanguage(FencedCodeBlock codeBlock) {
    String info = codeBlock.getInfo();
    if (info != null && !info.isBlank()) {
        // Info string may contain space-separated params: "java title=Example"
        return info.split("\\s+")[0].toLowerCase();
    }
    return LanguageDetector.detect(codeBlock.getLiteral());
}
```

### Pattern 3: Embedding Storage via LangChain4j
**What:** Convert `DocumentChunkData` records to `TextSegment` with `Metadata`, generate embeddings, and batch-store via `EmbeddingStore.addAll()`.
**When to use:** After chunking, before content is searchable.

**Example:**
```java
// Source: LangChain4j 1.11.0 API (verified from existing HybridSearchIT.java)
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

public void storeChunks(List<DocumentChunkData> chunkDataList) {
    List<TextSegment> segments = chunkDataList.stream()
            .map(cd -> TextSegment.from(cd.text(), buildMetadata(cd)))
            .toList();

    List<Embedding> embeddings = segments.stream()
            .map(seg -> embeddingModel.embed(seg).content())
            .toList();

    embeddingStore.addAll(embeddings, segments);
}

private Metadata buildMetadata(DocumentChunkData cd) {
    Metadata metadata = Metadata.from("source_url", cd.sourceUrl())
            .put("section_path", cd.sectionPath())
            .put("content_type", cd.contentType())
            .put("last_updated", cd.lastUpdated());
    if (cd.language() != null) {
        metadata.put("language", cd.language());
    }
    return metadata;
}
```

### Pattern 4: Pre-Chunked JSON Import with Replacement
**What:** Parse JSON file, validate all chunks, delete existing chunks for the source_url, then store new chunks.
**When to use:** When user provides AI-assisted pre-chunked content.

**Example:**
```java
// Source: LangChain4j Filter API (verified from docs)
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

public void importPreChunked(PreChunkedRequest request) {
    // Validate all chunks first (all-or-nothing)
    validateAllChunks(request.chunks());

    // Delete existing chunks for this source_url (replacement mode)
    embeddingStore.removeAll(
            metadataKey("source_url").isEqualTo(request.sourceUrl())
    );

    // Store new chunks
    storeChunks(request.chunks().stream()
            .map(c -> new DocumentChunkData(c.text(), c.sourceUrl(),
                    c.sectionPath(), c.contentType(), c.lastUpdated(), c.language()))
            .toList());
}
```

### Pattern 5: IngestionService Orchestrator
**What:** Orchestrates the full pipeline: takes CrawlSiteResult from Phase 3, chunks each page's Markdown, embeds and stores all chunks.
**When to use:** End-to-end ingestion triggered by crawl completion.

**Example:**
```java
@Service
public class IngestionService {
    private final MarkdownChunker chunker;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public int ingest(CrawlSiteResult crawlResult) {
        int totalChunks = 0;
        for (CrawlResult page : crawlResult.successPages()) {
            List<DocumentChunkData> chunks = chunker.chunk(
                    page.markdown(), page.url(), Instant.now().toString()
            );
            storeChunks(chunks);
            totalChunks += chunks.size();
        }
        return totalChunks;
    }
}
```

### Anti-Patterns to Avoid
- **Regex-based Markdown splitting:** Cannot distinguish headings inside fenced code blocks from real headings. A line like `## Heading` inside a ````markdown` block would be falsely treated as a split point. AST parsing handles this correctly.
- **Splitting at H4/H5/H6 boundaries:** User decision: H4+ stays within the parent H3 chunk. Do not add splitting logic for deeper headings.
- **Adding textual overlap between chunks:** User decided against overlap; heading path provides context instead. The ROADMAP's success criterion #2 (configurable overlap) is overridden by the user's explicit decision.
- **Custom Markdown renderer for prose extraction:** Use the original Markdown text from source positions, not an HTML render. This preserves the original formatting.
- **Storing chunks via JPA DocumentChunk entity:** Use LangChain4j's `EmbeddingStore.add()` which handles the embedding vector column, text, and metadata JSONB together. The JPA entity exists for read-only queries, not writes.
- **Inferring metadata for pre-chunked imports:** User decision: all 5 fields required per chunk in JSON. Do not auto-fill missing fields.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Markdown parsing | Custom regex parser | commonmark-java Parser | Handles edge cases: headings in code blocks, setext headings, escaped characters, nested lists; CommonMark spec compliant |
| Table detection | Custom pipe-delimiter parser | commonmark-ext-gfm-tables | Parses as `TableBlock` AST node; handles alignment, multiline cells, edge cases |
| JSON validation | Custom validation logic | Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@NotNull`) | Declarative, standard, generates structured error messages automatically |
| Embedding generation + storage | Custom JDBC inserts | LangChain4j `EmbeddingStore.addAll()` | Handles vector serialization, metadata JSONB, batch inserts, ID generation |
| Metadata filtering for deletion | Custom SQL DELETE with JSONB | LangChain4j `removeAll(Filter)` with `metadataKey()` | PgVectorEmbeddingStore implements Filter-to-SQL translation for JSONB metadata |
| Node text extraction | Custom AST-to-string | commonmark-java `TextContentRenderer` | Renders AST nodes to plain text without HTML tags |

**Key insight:** The ingestion pipeline is pure transformation logic (Markdown AST -> domain chunks -> LangChain4j TextSegments). The Markdown parsing and embedding storage layers already exist -- this phase connects them with chunking logic.

## Common Pitfalls

### Pitfall 1: Headings Inside Fenced Code Blocks
**What goes wrong:** A `## Heading` line inside a fenced code block is treated as a chunk split point, breaking the code block.
**Why it happens:** Regex-based splitting cannot distinguish code content from document structure.
**How to avoid:** Use AST-based parsing. commonmark-java's parser correctly nests content inside `FencedCodeBlock` nodes -- headings inside code blocks are text content, not `Heading` nodes.
**Warning signs:** Code blocks split across chunks; chunks starting with code content that looks like a heading.

### Pitfall 2: Empty Prose Chunks After Code Extraction
**What goes wrong:** A section that contains only code blocks (no prose text) produces an empty prose chunk.
**Why it happens:** After extracting all FencedCodeBlock nodes, the remaining prose nodes may have no meaningful text.
**How to avoid:** Check `proseText.isBlank()` before creating a prose chunk. Only emit prose chunks with actual content.
**Warning signs:** Chunks with empty or whitespace-only text stored in the database.

### Pitfall 3: Section Path Slugification Inconsistency
**What goes wrong:** The same heading produces different section paths due to special characters, unicode, or varying whitespace.
**Why it happens:** Heading text like "Configuration & Routes" needs consistent slug conversion ("configuration-routes" or "configuration-and-routes").
**How to avoid:** Define a single `slugify()` method used everywhere. Strip non-alphanumeric characters, lowercase, replace spaces/special chars with hyphens, collapse consecutive hyphens.
**Warning signs:** Search by section_path returns no results; same section appears with different paths.

### Pitfall 4: FencedCodeBlock.getInfo() Contains More Than Language
**What goes wrong:** Language detection returns "java title=Example" instead of "java".
**Why it happens:** The CommonMark spec allows arbitrary text after the language tag in the info string (e.g., ````java title="Example"`). `FencedCodeBlock.getInfo()` returns the full info string.
**How to avoid:** Split the info string on whitespace and take only the first token: `info.split("\\s+")[0]`.
**Warning signs:** Language field contains spaces or unexpected text; language lookups fail.

### Pitfall 5: Pre-Chunked Import Without Transactional Replacement
**What goes wrong:** Old chunks deleted but new chunks fail to store, leaving the source_url with zero chunks (data loss).
**Why it happens:** Delete and insert are separate operations without transaction coordination.
**How to avoid:** Wrap the delete + insert in a `@Transactional` method. If any step fails, the entire operation rolls back. Note: `EmbeddingStore.removeAll()` uses direct JDBC -- verify it participates in the Spring-managed transaction (it should, since PgVectorEmbeddingStore shares the DataSource/connection pool).
**Warning signs:** Source URLs with zero chunks after a failed import attempt.

### Pitfall 6: Heading Text Extraction From AST
**What goes wrong:** `heading.getFirstChild()` returns a `Text` node, but some headings contain inline formatting (e.g., `## The `Config` class`) which produces `Text` + `Code` + `Text` children.
**Why it happens:** Headings can contain inline elements (emphasis, code, links).
**How to avoid:** Use commonmark-java's `TextContentRenderer` to render a heading node's children to plain text, or walk all children concatenating text content. Do not assume `getFirstChild()` is the only child.
**Warning signs:** Section paths missing parts of heading text; truncated heading names.

### Pitfall 7: Large Documents Producing Many Embeddings
**What goes wrong:** A single page with 50+ code blocks produces 50+ embedding API calls, causing slow ingestion.
**Why it happens:** Each code block becomes a separate chunk, and embedding generation is sequential.
**How to avoid:** Use `embeddingModel.embedAll(segments)` for batch embedding instead of embedding one at a time. LangChain4j's ONNX embedding model supports batch processing.
**Warning signs:** Ingestion of code-heavy pages takes disproportionately long.

### Pitfall 8: Source Text Extraction vs. Rendered Text
**What goes wrong:** Prose chunks contain HTML-rendered text or lose Markdown formatting.
**Why it happens:** Using `HtmlRenderer` instead of extracting original source text, or using `TextContentRenderer` which strips all formatting.
**How to avoid:** For prose chunks, reconstruct the original Markdown from the AST node positions or use a Markdown-rendering approach. commonmark-java's nodes have source spans (since 0.24.0) that can map back to original source positions. Alternatively, since we have the original Markdown string, use character offsets to extract the original text between heading boundaries.
**Warning signs:** Prose chunks contain HTML tags, or lose code formatting, bold, links, etc.

## Code Examples

Verified patterns from official sources:

### commonmark-java Parser Setup with Tables Extension
```java
// Source: commonmark-java 0.27.1 README + TablesExtension docs
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.tables.TablesExtension;

Parser parser = Parser.builder()
        .extensions(List.of(TablesExtension.create()))
        .build();

Node document = parser.parse(markdownText);
```

### Walking the AST Sibling List
```java
// Source: commonmark-java Node API (getFirstChild, getNext)
Node child = document.getFirstChild();
while (child != null) {
    if (child instanceof Heading heading) {
        System.out.println("H" + heading.getLevel() + ": " + extractText(heading));
    } else if (child instanceof FencedCodeBlock code) {
        System.out.println("Code [" + code.getInfo() + "]: " + code.getLiteral().length() + " chars");
    } else if (child instanceof Paragraph) {
        System.out.println("Paragraph");
    }
    child = child.getNext();
}
```

### Extracting Text Content from a Node
```java
// Source: commonmark-java TextContentRenderer
import org.commonmark.renderer.text.TextContentRenderer;

private final TextContentRenderer textRenderer = TextContentRenderer.builder().build();

private String extractText(Node node) {
    // Renders node and all children to plain text
    return textRenderer.render(node).trim();
}
```

### TextSegment with Full Metadata
```java
// Source: verified from existing HybridSearchIT.java in this project
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

Metadata metadata = Metadata.from("source_url", "https://docs.spring.io/guide")
        .put("section_path", "guide/configuration/routes")
        .put("content_type", "prose")
        .put("last_updated", "2026-02-17T10:30:00Z");
// language is null for prose chunks -- do not add to metadata

TextSegment segment = TextSegment.from("Configuration guide text...", metadata);
```

### Metadata-Based Deletion (Replacement Import)
```java
// Source: LangChain4j Filter API docs + PgVectorEmbeddingStore implementation
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

// Delete all existing chunks for a given source URL
embeddingStore.removeAll(
        metadataKey("source_url").isEqualTo("https://docs.spring.io/guide")
);
```

### Batch Embedding Generation
```java
// Source: LangChain4j EmbeddingModel API
List<TextSegment> segments = /* ... */;
// embedAll() is more efficient than calling embed() in a loop
Response<List<Embedding>> response = embeddingModel.embedAll(segments);
List<Embedding> embeddings = response.content();
embeddingStore.addAll(embeddings, segments);
```

### Pre-Chunked JSON Import Format
```json
{
  "source_url": "https://docs.spring.io/spring-boot/reference",
  "chunks": [
    {
      "text": "Spring Boot makes it easy to create stand-alone applications...",
      "source_url": "https://docs.spring.io/spring-boot/reference/getting-started",
      "section_path": "getting-started/introducing-spring-boot",
      "content_type": "prose",
      "last_updated": "2026-02-17T10:30:00Z",
      "language": null
    },
    {
      "text": "@SpringBootApplication\npublic class MyApp {\n    public static void main(String[] args) {\n        SpringApplication.run(MyApp.class, args);\n    }\n}",
      "source_url": "https://docs.spring.io/spring-boot/reference/getting-started",
      "section_path": "getting-started/introducing-spring-boot",
      "content_type": "code",
      "last_updated": "2026-02-17T10:30:00Z",
      "language": "java"
    }
  ]
}
```

### Pre-Chunked Import DTOs with Validation
```java
// Source: Jakarta Bean Validation + Jackson
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PreChunkedRequest(
        @NotBlank @JsonProperty("source_url") String sourceUrl,
        @NotEmpty @Valid List<PreChunkedChunk> chunks
) {}

public record PreChunkedChunk(
        @NotBlank String text,
        @NotBlank @JsonProperty("source_url") String sourceUrl,
        @NotBlank @JsonProperty("section_path") String sectionPath,
        @NotBlank @JsonProperty("content_type")
        @Pattern(regexp = "prose|code") String contentType,
        @NotBlank @JsonProperty("last_updated") String lastUpdated,
        // language is nullable -- null for prose, required for code
        String language
) {}
```

### Simple Language Detection Heuristic
```java
// Source: Custom heuristic based on common language keyword patterns
public class LanguageDetector {

    private static final Map<String, List<String>> LANGUAGE_PATTERNS = Map.ofEntries(
            Map.entry("java", List.of("public class ", "private ", "import java.", "@Override",
                    "System.out.", "public static void main")),
            Map.entry("python", List.of("def ", "import ", "from ", "self.", "if __name__",
                    "print(", "class ", "elif ")),
            Map.entry("javascript", List.of("const ", "let ", "function ", "=>", "console.log",
                    "require(", "module.exports")),
            Map.entry("typescript", List.of("interface ", ": string", ": number", ": boolean",
                    "export ", "import {", "type ")),
            Map.entry("yaml", List.of("apiVersion:", "kind:", "metadata:", "spec:", "---")),
            Map.entry("xml", List.of("<?xml", "<beans", "xmlns:", "<project", "<dependency")),
            Map.entry("sql", List.of("SELECT ", "INSERT INTO", "CREATE TABLE", "ALTER TABLE",
                    "WHERE ", "JOIN ")),
            Map.entry("bash", List.of("#!/bin/bash", "echo ", "export ", "if [", "fi", "done")),
            Map.entry("go", List.of("func ", "package ", "import (", "fmt.", "err != nil")),
            Map.entry("rust", List.of("fn ", "let mut ", "impl ", "pub fn", "use std::", "match "))
    );

    public static String detect(String code) {
        Map<String, Integer> scores = new HashMap<>();
        for (var entry : LANGUAGE_PATTERNS.entrySet()) {
            int score = 0;
            for (String pattern : entry.getValue()) {
                if (code.contains(pattern)) score++;
            }
            if (score > 0) scores.put(entry.getKey(), score);
        }

        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() >= 2)  // Require at least 2 pattern matches
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| flexmark-java (dominant Java MD parser) | commonmark-java actively maintained, flexmark-java stale | flexmark: last release June 2023; commonmark: Jan 2026 | commonmark-java is the safer long-term choice |
| LangChain4j `add()` one at a time | `addAll()` batch with `embedAll()` | LangChain4j 1.x | Significantly faster ingestion for large documents |
| Manual JDBC for metadata filtering | `removeAll(Filter)` with `metadataKey()` | LangChain4j 1.x | Clean metadata-based deletion without custom SQL |
| `result.markdown` string (Crawl4AI) | `result.markdown` object with `rawMarkdown` / `fitMarkdown` | Crawl4AI 0.8.x | Use `fitMarkdown` for chunking (boilerplate already removed) |

**Deprecated/outdated:**
- ROADMAP success criterion #2 ("configurable overlap, default 50-100 tokens"): Overridden by user decision "No textual overlap between chunks". Do not implement overlap.
- flexmark-java: Last release 0.64.8 in June 2023. Not recommended for new projects.

## Open Questions

1. **Source Span vs. Original Text for Prose Chunks**
   - What we know: commonmark-java 0.24.0+ supports `getSourceSpans()` on nodes. The original Markdown string is available. Two approaches for prose text: (A) use source spans to extract original text, (B) use `TextContentRenderer` for plain text.
   - What's unclear: Whether source spans give character-level offsets usable for substring extraction in all cases (e.g., with CRLF line endings, BOM, etc.).
   - Recommendation: Start with approach A (source spans from original text) since it preserves original Markdown formatting. Fall back to TextContentRenderer if source span extraction proves unreliable. Test with real Crawl4AI output early.

2. **EmbeddingStore.removeAll(Filter) Transaction Participation**
   - What we know: PgVectorEmbeddingStore uses the shared DataSource (HikariCP pool) configured in Phase 1. Spring's `@Transactional` annotation manages transactions via the DataSource.
   - What's unclear: Whether `removeAll(Filter)` obtains its JDBC connection through Spring's transaction-aware DataSource proxy, or whether it gets a raw connection that operates outside the transaction boundary.
   - Recommendation: Write an integration test that verifies transactional rollback: start transaction, remove chunks, fail intentionally, verify chunks still exist. If removeAll does not participate in Spring transactions, implement a two-phase approach: store new chunks first with temporary IDs, then delete old and rename.

3. **Crawl4AI fitMarkdown Content Quality for Chunking**
   - What we know: Phase 3's CrawlResult has a `markdown` field containing the `fitMarkdown` output from Crawl4AI's PruningContentFilter. This is the Markdown that should be chunked.
   - What's unclear: Whether fitMarkdown preserves heading structure reliably, or if PruningContentFilter sometimes strips headings along with boilerplate.
   - Recommendation: Test with real crawled content during integration testing. If headings are stripped, fall back to `rawMarkdown` and accept more noise.

4. **DocumentChunkRepository Additions**
   - What we know: The existing `DocumentChunkRepository` (JPA) has no custom methods. Phase 4 needs to query chunks by source_id for the Source entity's `chunk_count` update.
   - What's unclear: Whether to add `countBySourceId()` to JPA or query via EmbeddingStore metadata filtering.
   - Recommendation: Add `long countBySourceId(UUID sourceId)` and `void deleteBySourceId(UUID sourceId)` to `DocumentChunkRepository` for Source management. EmbeddingStore metadata filtering is for the replacement import use case.

## Sources

### Primary (HIGH confidence)
- [commonmark-java GitHub](https://github.com/commonmark/commonmark-java) - Version 0.27.1, release dates, AST API, Visitor pattern, extensions
- [commonmark-java CHANGELOG](https://github.com/commonmark/commonmark-java/blob/main/CHANGELOG.md) - Release dates confirmed: 0.27.1 (2026-01-14)
- [commonmark-java Heading.java](https://github.com/commonmark/commonmark-java/blob/main/commonmark/src/main/java/org/commonmark/node/Heading.java) - `getLevel()`, `setLevel()`, extends Block
- [commonmark-java FencedCodeBlock.java](https://github.com/commonmark/commonmark-java/blob/main/commonmark/src/main/java/org/commonmark/node/FencedCodeBlock.java) - `getInfo()`, `getLiteral()`, `getFenceCharacter()`
- [commonmark-java Node.java](https://github.com/commonmark/commonmark-java/blob/main/commonmark/src/main/java/org/commonmark/node/Node.java) - `getNext()`, `getFirstChild()`, `getParent()`, `accept(Visitor)`
- [LangChain4j EmbeddingStore API](https://docs.langchain4j.dev/apidocs/dev/langchain4j/store/embedding/EmbeddingStore.html) - `add()`, `addAll()`, `removeAll(Filter)`, `search()`
- [LangChain4j RAG Tutorial](https://docs.langchain4j.dev/tutorials/rag/) - TextSegment.from(), Metadata API, Filter API
- [LangChain4j PgVector Docs](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/) - datasourceBuilder(), MetadataStorageConfig, removeAll(Filter) support confirmed
- [LangChain4j PgVectorEmbeddingStore Source](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-pgvector/src/main/java/dev/langchain4j/store/embedding/pgvector/PgVectorEmbeddingStore.java) - removeAll(Filter) implemented, Filter-to-SQL for JSONB metadata
- Existing project codebase: `HybridSearchIT.java`, `SearchService.java`, `EmbeddingConfig.java`, `CrawlResult.java` - Verified metadata key conventions, TextSegment usage patterns

### Secondary (MEDIUM confidence)
- [Maven Central - commonmark](https://central.sonatype.com/artifact/com.vladsch.flexmark/flexmark) - flexmark-java 0.64.8 last published June 2023
- [Maven Central - commonmark-java](https://central.sonatype.com/artifact/org.commonmark/commonmark) - Version 0.27.1 confirmed
- [LangChain4j Filter Examples](https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_05_Advanced_RAG_with_Metadata_Filtering_Examples.java) - metadataKey().isEqualTo() syntax verified

### Tertiary (LOW confidence)
- Source span behavior for extracting original Markdown text from AST nodes -- API exists since 0.24.0 but exact usage for substring extraction needs hands-on validation
- EmbeddingStore.removeAll(Filter) transaction participation with Spring @Transactional -- implementation uses DataSource but transaction proxy behavior needs integration test validation
- Language detection heuristic accuracy -- pattern-based approach handles common cases but edge cases (e.g., similar languages like C/C++, JS/TS) need testing with real crawled content

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - commonmark-java actively maintained, API verified from source code; LangChain4j APIs verified from existing project usage
- Architecture: HIGH - AST-based chunking is well-understood; patterns follow existing project conventions for TextSegment/Metadata
- Pitfalls: HIGH - Code block handling, empty chunk prevention, transaction safety are well-documented concerns in similar systems
- Pre-chunked import: MEDIUM - JSON format and Bean Validation approach are standard, but removeAll(Filter) transaction behavior needs integration test validation
- Language detection: MEDIUM - Heuristic approach is pragmatic but accuracy for edge cases is unverified

**Research date:** 2026-02-17
**Valid until:** 2026-03-17 (30 days - commonmark-java stable, LangChain4j pinned at 1.11.0)
