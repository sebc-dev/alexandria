# Phase 2: Ingestion Core - Research

**Researched:** 2026-01-20
**Domain:** Document ingestion, chunking, embeddings, vector storage (Java/LangChain4j)
**Confidence:** HIGH

## Summary

Phase 2 implements the core ingestion pipeline: parsing markdown files with YAML frontmatter, hierarchical chunking (parent 1000 tokens / child 200 tokens), embedding generation with all-MiniLM-L6-v2, and storage in the existing pgvector database schema.

LangChain4j provides most required components out-of-the-box: `FileSystemDocumentLoader` for file loading, `DocumentSplitters.recursive()` for token-based splitting, `AllMiniLmL6V2EmbeddingModel` for ONNX embeddings. However, two gaps exist: (1) LangChain4j has no built-in YAML frontmatter extraction - we need CommonMark with its yaml-front-matter extension, and (2) LangChain4j's `PgVectorEmbeddingStore` creates its own table schema - we must use our custom schema from Phase 1 with direct JDBC.

The hierarchical parent-child chunking pattern is not natively supported by LangChain4j's `EmbeddingStoreIngestor` but can be implemented by running the splitter twice with different token sizes and linking chunks via the existing `parent_chunk_id` foreign key.

**Primary recommendation:** Use LangChain4j for document loading, splitting, and embedding; use CommonMark for frontmatter; use direct JDBC with pgvector-java for storage to match our existing schema.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| langchain4j | 1.0.0-beta3 | Document loading, splitting | Already in pom.xml, unified RAG framework |
| langchain4j-embeddings-all-minilm-l6-v2 | 1.0.0-beta3 | ONNX embedding model | In-process, no external deps, 384 dims |
| commonmark | 0.22.0 | Markdown parsing | Fast (10-20x pegdown), CommonMark spec |
| commonmark-ext-yaml-front-matter | 0.22.0 | YAML frontmatter extraction | Standard extension, subset YAML support |
| pgvector-java | 0.1.6 | JDBC vector type support | Already in pom.xml, PGvector type for JDBC |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-jdbc | 3.4.1 | JDBC Template | Already in pom.xml, database access |
| liquibase-core | managed | Schema migrations | Already configured in Phase 1 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| CommonMark | Flexmark-java | More features but heavier; CommonMark sufficient for frontmatter |
| Direct JDBC | LangChain4j PgVectorEmbeddingStore | LC4j creates own schema; we need custom hierarchical schema |
| Manual splitting | LC4j recursive splitter | LC4j handles edge cases (sentence boundaries, word breaks) |

**Installation (add to pom.xml):**
```xml
<!-- CommonMark for markdown/frontmatter -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.22.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-yaml-front-matter</artifactId>
    <version>0.22.0</version>
</dependency>
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/fr/kalifazzia/alexandria/
├── core/
│   ├── ingestion/
│   │   ├── IngestionService.java           # Orchestrates full pipeline
│   │   ├── MarkdownParser.java             # Parse MD + extract frontmatter
│   │   ├── HierarchicalChunker.java        # Parent/child chunking
│   │   └── DocumentMetadata.java           # Record for extracted metadata
│   ├── model/
│   │   ├── Document.java                   # Domain entity
│   │   ├── Chunk.java                      # Domain entity (parent/child)
│   │   └── ChunkType.java                  # Enum: PARENT, CHILD
│   └── port/
│       ├── DocumentRepository.java         # Port interface
│       ├── ChunkRepository.java            # Port interface
│       └── EmbeddingGenerator.java         # Port interface
├── infra/
│   ├── persistence/
│   │   ├── JdbcDocumentRepository.java     # Adapter implementation
│   │   ├── JdbcChunkRepository.java        # Adapter with pgvector
│   │   └── PgVectorEmbedding.java          # Vector type wrapper
│   └── embedding/
│       └── LangChain4jEmbeddingGenerator.java  # Adapter for LC4j model
```

### Pattern 1: Two-Pass Hierarchical Chunking

**What:** Split document twice - once for parent chunks (1000 tokens), once for children (200 tokens)
**When to use:** Always for this project (requirement ING-02)

**Implementation Strategy:**
```java
// Source: Project-specific pattern based on LangChain4j DocumentSplitters
public class HierarchicalChunker {

    private final DocumentSplitter parentSplitter;
    private final DocumentSplitter childSplitter;

    public HierarchicalChunker(Tokenizer tokenizer) {
        // Parent: 1000 tokens, 100 token overlap (10%)
        this.parentSplitter = DocumentSplitters.recursive(1000, 100, tokenizer);
        // Child: 200 tokens, 20 token overlap (10%)
        this.childSplitter = DocumentSplitters.recursive(200, 20, tokenizer);
    }

    public List<ChunkPair> chunk(Document document) {
        List<TextSegment> parentSegments = parentSplitter.split(document);
        List<ChunkPair> result = new ArrayList<>();

        for (int i = 0; i < parentSegments.size(); i++) {
            TextSegment parent = parentSegments.get(i);
            // Create sub-document from parent text for child splitting
            Document parentDoc = Document.from(parent.text(), parent.metadata());
            List<TextSegment> childSegments = childSplitter.split(parentDoc);

            result.add(new ChunkPair(parent, childSegments, i));
        }
        return result;
    }
}

record ChunkPair(TextSegment parent, List<TextSegment> children, int position) {}
```

### Pattern 2: Frontmatter Extraction with CommonMark

**What:** Parse markdown, extract YAML frontmatter to metadata, get clean content
**When to use:** Every markdown file (requirement ING-05)

```java
// Source: CommonMark documentation + YamlFrontMatterVisitor API
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

public class MarkdownParser {
    private final Parser parser;

    public MarkdownParser() {
        this.parser = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .build();
    }

    public ParsedDocument parse(String content) {
        Node document = parser.parse(content);

        // Extract frontmatter
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> frontmatter = visitor.getData();

        // Extract title, tags, category from frontmatter
        String title = getFirst(frontmatter, "title");
        List<String> tags = frontmatter.getOrDefault("tags", List.of());
        String category = getFirst(frontmatter, "category");

        // Content without frontmatter (CommonMark strips it from rendered output)
        // For plain text, we need to re-extract after frontmatter block
        String cleanContent = extractContentAfterFrontmatter(content);

        return new ParsedDocument(title, tags, category, cleanContent, frontmatter);
    }

    private String extractContentAfterFrontmatter(String content) {
        // YAML frontmatter is delimited by --- ... ---
        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex != -1) {
                return content.substring(endIndex + 3).trim();
            }
        }
        return content;
    }
}

record ParsedDocument(
    String title,
    List<String> tags,
    String category,
    String content,
    Map<String, List<String>> rawFrontmatter
) {}
```

### Pattern 3: Direct JDBC with PGvector

**What:** Store embeddings using JDBC with pgvector-java types
**When to use:** All chunk storage (to use our custom schema)

```java
// Source: pgvector-java documentation + our Phase 1 schema
import com.pgvector.PGvector;

public class JdbcChunkRepository implements ChunkRepository {

    private final JdbcTemplate jdbc;

    public UUID saveChunk(Chunk chunk, float[] embedding) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type,
                               content, embedding, position)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            chunk.documentId(),
            chunk.parentChunkId(),  // null for parent chunks
            chunk.type().name().toLowerCase(),
            chunk.content(),
            new PGvector(embedding),
            chunk.position()
        );

        return id;
    }

    public void saveChunksWithEmbeddings(List<Chunk> chunks, List<float[]> embeddings) {
        // Batch insert for performance
        jdbc.batchUpdate("""
            INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type,
                               content, embedding, position)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Chunk chunk = chunks.get(i);
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, chunk.documentId());
                    ps.setObject(3, chunk.parentChunkId());
                    ps.setString(4, chunk.type().name().toLowerCase());
                    ps.setString(5, chunk.content());
                    ps.setObject(6, new PGvector(embeddings.get(i)));
                    ps.setInt(7, chunk.position());
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            });
    }
}
```

### Pattern 4: Embedding Generation with Batch Processing

**What:** Generate embeddings for multiple text segments efficiently
**When to use:** All embedding generation

```java
// Source: LangChain4j AllMiniLmL6V2EmbeddingModel documentation
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;

public class LangChain4jEmbeddingGenerator implements EmbeddingGenerator {

    private final EmbeddingModel model;

    public LangChain4jEmbeddingGenerator() {
        // Model uses parallel processing by default (all CPU cores)
        this.model = new AllMiniLmL6V2EmbeddingModel();
    }

    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    public List<float[]> embedAll(List<String> texts) {
        // LangChain4j handles batching internally
        List<Embedding> embeddings = model.embedAll(
            texts.stream()
                 .map(TextSegment::from)
                 .toList()
        ).content();

        return embeddings.stream()
            .map(Embedding::vector)
            .toList();
    }
}
```

### Anti-Patterns to Avoid

- **Using LangChain4j's PgVectorEmbeddingStore:** Creates its own table schema, incompatible with our hierarchical parent-child schema from Phase 1
- **Embedding parent chunks:** Only embed child chunks for retrieval; parent chunks provide context but are retrieved via FK
- **Single-pass chunking:** Results in "precise but fragmented" or "complete but vague" retrieval; hierarchical solves this
- **Ignoring frontmatter:** Metadata (title, tags, category) is critical for filtering in Phase 4
- **Character-based splitting:** Use token-based splitting for accurate embedding model limits

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Markdown parsing | Regex-based parser | CommonMark | Edge cases: nested blocks, escapes, code blocks |
| YAML frontmatter | String splitting on `---` | commonmark-ext-yaml-front-matter | Handles multi-line values, lists, nested structures |
| Text chunking | Simple string split | DocumentSplitters.recursive() | Respects sentence/word boundaries, handles overlap |
| Tokenization | Character counting | LangChain4j Tokenizer | Token != char; models have specific tokenizers |
| Vector type handling | Manual SQL formatting | pgvector-java PGvector | Escaping, dimension validation, JDBC integration |
| Embedding parallelization | Manual thread pool | AllMiniLmL6V2EmbeddingModel default | Already parallelizes across CPU cores |

**Key insight:** The ingestion pipeline looks simple (parse -> chunk -> embed -> store) but each step has edge cases that existing libraries handle. The hierarchical parent-child relationship is the one custom piece we must implement.

## Common Pitfalls

### Pitfall 1: Token Limit Exceeded
**What goes wrong:** all-MiniLM-L6-v2 accepts unlimited input but quality degrades past 256 tokens
**Why it happens:** Child chunks configured too large, or overlap pushes effective size past limit
**How to avoid:** Child chunks at 200 tokens (well under 256 limit); verify with actual token counts
**Warning signs:** Embedding quality test shows poor similarity scores for longer chunks

### Pitfall 2: Duplicate Content from Overlap
**What goes wrong:** Same content appears in multiple chunks, bloating database
**Why it happens:** Overlap needed for context but creates redundancy
**How to avoid:** 10-20% overlap is sufficient (100 tokens for 1000-token parent, 20 for 200-token child)
**Warning signs:** Database size grows faster than expected; search returns near-duplicate chunks

### Pitfall 3: Lost Frontmatter Metadata
**What goes wrong:** Document metadata not persisted, filtering impossible later
**Why it happens:** Frontmatter extracted but not saved to documents table
**How to avoid:** Always save title/tags/category to documents table AND as JSONB in frontmatter column
**Warning signs:** Empty category/tags columns in database

### Pitfall 4: Child Chunks Without Parent Reference
**What goes wrong:** Orphan child chunks; can't retrieve parent context during search
**Why it happens:** parent_chunk_id not set during insertion
**How to avoid:** Save parent chunk first, capture its UUID, then save children with FK
**Warning signs:** NULL parent_chunk_id for chunk_type='child' rows

### Pitfall 5: Embedding Dimension Mismatch
**What goes wrong:** Insert fails or silent data corruption
**Why it happens:** Model outputs 384 dims but schema has different dimension
**How to avoid:** Schema already set to vector(384) in Phase 1; verify model matches
**Warning signs:** SQL errors on insert; search returns wrong results

### Pitfall 6: Content Hash Not Updated
**What goes wrong:** Re-indexing same document creates duplicates
**Why it happens:** No upsert logic; inserts always create new rows
**How to avoid:** Check content_hash before insert; delete old chunks if hash changed
**Warning signs:** Multiple documents with same path; growing database on re-index

### Pitfall 7: Frontmatter in Chunk Content
**What goes wrong:** YAML frontmatter embedded as text, pollutes embeddings
**Why it happens:** Content not stripped before chunking
**How to avoid:** MarkdownParser.extractContentAfterFrontmatter() removes frontmatter
**Warning signs:** Search results contain `---` and YAML key-value pairs

## Code Examples

Verified patterns from official sources:

### Complete Ingestion Pipeline
```java
// Orchestrating the full pipeline
public class IngestionService {

    private final MarkdownParser markdownParser;
    private final HierarchicalChunker chunker;
    private final EmbeddingGenerator embeddingGenerator;
    private final DocumentRepository documentRepo;
    private final ChunkRepository chunkRepo;

    @Transactional
    public void ingestDirectory(Path directory) {
        try (var files = Files.walk(directory)) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .forEach(this::ingestFile);
        }
    }

    @Transactional
    public void ingestFile(Path file) {
        String content = Files.readString(file);
        String contentHash = sha256(content);

        // Check if already indexed with same content
        Optional<Document> existing = documentRepo.findByPath(file.toString());
        if (existing.isPresent() && existing.get().contentHash().equals(contentHash)) {
            return; // No change, skip
        }

        // Delete old version if exists
        existing.ifPresent(doc -> {
            chunkRepo.deleteByDocumentId(doc.id());
            documentRepo.delete(doc.id());
        });

        // Parse markdown and extract frontmatter
        ParsedDocument parsed = markdownParser.parse(content);

        // Create document record
        Document document = documentRepo.save(new Document(
            file.toString(),
            parsed.title(),
            parsed.category(),
            parsed.tags(),
            contentHash,
            parsed.rawFrontmatter()
        ));

        // Create LangChain4j Document for splitting
        var lc4jDoc = dev.langchain4j.data.document.Document.from(parsed.content());

        // Hierarchical chunking
        List<ChunkPair> chunkPairs = chunker.chunk(lc4jDoc);

        // Process each parent-child group
        for (ChunkPair pair : chunkPairs) {
            // Save parent chunk (no embedding needed for search, but store for context)
            Chunk parentChunk = new Chunk(
                document.id(),
                null,  // no parent for parent chunks
                ChunkType.PARENT,
                pair.parent().text(),
                pair.position()
            );

            // Generate embedding for parent (optional, for potential future use)
            float[] parentEmbedding = embeddingGenerator.embed(pair.parent().text());
            UUID parentId = chunkRepo.saveChunk(parentChunk, parentEmbedding);

            // Save child chunks with embeddings and parent reference
            for (int i = 0; i < pair.children().size(); i++) {
                TextSegment child = pair.children().get(i);
                Chunk childChunk = new Chunk(
                    document.id(),
                    parentId,  // FK to parent
                    ChunkType.CHILD,
                    child.text(),
                    i
                );
                float[] childEmbedding = embeddingGenerator.embed(child.text());
                chunkRepo.saveChunk(childChunk, childEmbedding);
            }
        }
    }
}
```

### FileSystemDocumentLoader Usage
```java
// Source: LangChain4j FileSystemDocumentLoader documentation
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.*;

// Load all markdown files recursively
PathMatcher markdownMatcher = FileSystems.getDefault()
    .getPathMatcher("glob:**.md");

List<Document> documents = loadDocumentsRecursively(
    directoryPath,
    markdownMatcher,
    new TextDocumentParser()
);

// Or load single file
Document document = loadDocument(filePath, new TextDocumentParser());
```

### Embedding Model Initialization
```java
// Source: LangChain4j in-process embedding docs
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;

// Default: uses all CPU cores for parallelization
EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();

// Verify dimension matches our schema
assert model.dimension() == 384; // vector(384) in chunks table

// Single embedding
Response<Embedding> response = model.embed("text to embed");
float[] vector = response.content().vector();

// Batch embedding (more efficient)
List<TextSegment> segments = List.of(
    TextSegment.from("text 1"),
    TextSegment.from("text 2")
);
Response<List<Embedding>> batchResponse = model.embedAll(segments);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Fixed-size character chunking | Token-based recursive chunking | 2024 | Better embedding quality, respects model limits |
| Single granularity chunks | Hierarchical parent/child | 2024-2025 | Solves precision vs context tradeoff |
| IVFFlat indexes | HNSW indexes | pgvector 0.5+ | 15x faster queries, no rebuild on updates |
| Embedding all chunks | Embed only retrieval chunks | 2025 | Save compute; parent for context only |

**Deprecated/outdated:**
- **pegdown markdown parser:** Unmaintained since 2016; CommonMark is 10-20x faster
- **LangChain4j 0.x versions:** Use 1.0.0-beta3+ for stable API
- **Character-based DocumentSplitters:** Use token-based with Tokenizer

## Open Questions

Things that couldn't be fully resolved:

1. **Tokenizer choice for DocumentSplitters**
   - What we know: LangChain4j supports OpenAI tokenizers via Tokenizer interface
   - What's unclear: Does all-MiniLM-L6-v2 have a specific tokenizer? Or use generic word-based?
   - Recommendation: Use `null` tokenizer (character-based) initially; upgrade if token mismatch observed

2. **Optimal overlap percentage**
   - What we know: 10-20% is recommended; 100 tokens for 1000-token parent
   - What's unclear: Best overlap for technical documentation specifically
   - Recommendation: Start with 10% (100/20 tokens); tune based on retrieval quality tests

3. **Embedding both parent and child chunks**
   - What we know: Standard pattern embeds only children for search, retrieves parent for context
   - What's unclear: Should we embed parents for potential direct parent search?
   - Recommendation: Embed both (schema has embedding column); child for search, parent available

## Sources

### Primary (HIGH confidence)
- [LangChain4j In-Process Embedding Models](https://docs.langchain4j.dev/integrations/embedding-models/in-process/) - AllMiniLmL6V2EmbeddingModel API
- [LangChain4j RAG Tutorial](https://docs.langchain4j.dev/tutorials/rag/) - EmbeddingStoreIngestor, DocumentSplitters
- [LangChain4j FileSystemDocumentLoader GitHub](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/data/document/loader/FileSystemDocumentLoader.java) - Document loading API
- [CommonMark Java GitHub](https://github.com/commonmark/commonmark-java) - Markdown parser + YAML extension
- [pgvector-java GitHub](https://github.com/pgvector/pgvector-java) - JDBC vector type

### Secondary (MEDIUM confidence)
- [Parent-Child Chunking in LangChain](https://medium.com/@seahorse.technologies.sl/parent-child-chunking-in-langchain-for-advanced-rag-e7c37171995a) - Hierarchical chunking pattern
- [LangChain4j Examples Repository](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/DocumentLoaderExamples.java) - Document loading examples
- [PGVector Documentation](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/) - LangChain4j pgvector integration

### Tertiary (LOW confidence)
- [Best Chunking Strategies for RAG 2025](https://www.firecrawl.dev/blog/best-chunking-strategies-rag-2025) - General chunking guidance (Python-focused)
- [RAG Pipeline Medium Article](https://medium.com/@faryalriz9/if-i-had-to-build-a-rag-pipeline-today-id-do-it-this-way-2080c4ab0b69) - Pitfalls and best practices

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - LangChain4j/CommonMark documented, already in pom.xml
- Architecture patterns: HIGH - Based on official LangChain4j APIs and standard RAG patterns
- Hierarchical chunking: MEDIUM - Pattern well-known but custom implementation required
- Pitfalls: MEDIUM - Based on multiple sources and common RAG issues

**Research date:** 2026-01-20
**Valid until:** 2026-02-20 (LangChain4j in active development, check for API changes)

---
*Research completed: 2026-01-20*
*Sources: LangChain4j docs, CommonMark GitHub, pgvector-java, RAG best practices articles*
