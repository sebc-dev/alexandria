# Markdown splitter edge cases for RAG systems

Custom markdown splitting in RAG requires deliberate handling of structural elements that don't fit neatly into fixed chunk sizes. **The consensus across frameworks is clear: preserve semantic integrity over consistent sizing**, with code blocks and tables treated as atomic units, and header breadcrumbs providing essential retrieval context. Langchain4j 1.10.0 lacks a dedicated markdown splitter (PR #2418 remains in draft), so Alexandria will need custom implementation for these behaviors.

## Code blocks should remain intact, even when oversized

**Recommendation:** Never split code blocks mid-content. Treat them as atomic units regardless of size, with fallback strategies for extreme cases.

The industry consensus is unambiguous. Pinecone's documentation warns that standard recursive text chunkers "would give back broken code." Dell Technologies' chunking guide states that "markdown elements should be treated as atomic units that shouldn't be split—code blocks remain intact regardless of size." Breaking code mid-function destroys comprehension and retrieval utility.

**How frameworks handle this varies significantly:**

| Framework | Code Block Behavior |
|-----------|-------------------|
| **Langchain4j** | No special handling—code blocks split like regular text |
| **LlamaIndex MarkdownNodeParser** | Keeps code blocks intact within parent section; ignores headers inside fenced blocks |
| **Unstructured.io** | Converts markdown to HTML first; known issues with fenced code (missing extension) |
| **LangChain Python** | `RecursiveCharacterTextSplitter.from_language()` provides language-aware splitting |

**Recommended implementation for Alexandria:**

```java
// Pseudocode for code block handling
if (isCodeBlock(content) && tokenCount(content) <= MAX_OVERSIZED_THRESHOLD) {
    // Keep intact even if exceeds 500 tokens
    return createChunk(content, preserveMetadata: true);
} else if (isCodeBlock(content) && tokenCount(content) > MAX_OVERSIZED_THRESHOLD) {
    // For extremely large blocks (>2000 tokens), use fallback:
    // Option A: Split at function/class boundaries
    // Option B: Store full block with summary chunk for retrieval
    return splitAtLogicalBoundaries(content);
}
```

**Before/after example:**

```
BEFORE (naive splitting at 500 tokens):
Chunk 1: "```python\ndef calculate_metrics(data):\n    results = []\n    for item in data:\n        processed = transform(item)\n"
Chunk 2: "        validated = validate(processed)\n        results.append(validated)\n    return results\n```"

AFTER (atomic preservation):
Chunk 1: [entire code block, 650 tokens, flagged as oversized but intact]
Metadata: {type: "code", language: "python", oversized: true}
```

**Trade-offs:** Allowing oversized chunks creates embedding model context concerns (most models cap at 512-8192 tokens). NVIDIA research found that chunks exceeding **2048 tokens underperformed** across datasets. For code blocks exceeding this threshold, consider generating a summary chunk for retrieval while storing the full code separately with a reference pointer.

## Large tables should never split between rows

**Recommendation:** Preserve complete table structures. When tables exceed chunk limits, store them as isolated units with generated summaries for retrieval.

Tables contain relational information where splitting rows destroys meaning—a value in column 3 is meaningless without its column header context. All major frameworks isolate tables during chunking.

**Framework approaches:**

- **LlamaIndex MarkdownElementNodeParser**: Extracts tables as dedicated elements with type "table"; validates column consistency; stores as DataFrame or raw text
- **Unstructured.io**: Tables are **never combined** with other elements; oversized tables become `TableChunk` elements with original HTML preserved in `metadata.text_as_html`
- **Langchain4j**: No table awareness—tables can be split mid-row

**Recommended implementation:**

```java
// Table handling strategy
TableChunk handleTable(String tableMarkdown) {
    int tokenCount = countTokens(tableMarkdown);
    
    if (tokenCount <= MAX_CHUNK_SIZE) {
        return new TableChunk(tableMarkdown, isolated: true);
    } else {
        // Generate summary for retrieval
        String summary = llm.summarize("Summarize this table's key data: " + tableMarkdown);
        return new TableChunk(
            content: tableMarkdown,
            summary: summary,  // Embed this for retrieval
            metadata: { fullContent: tableMarkdown, rowCount: countRows(), columnCount: countCols() }
        );
    }
}
```

**For very wide tables** (many columns), consider reformatting to vertical key-value pairs per row:

```
WIDE TABLE (hard to embed meaningfully):
| Name | Q1 Revenue | Q2 Revenue | Q3 Revenue | Q4 Revenue | YoY Growth | Market Share |...

REFORMATTED FOR CHUNKING:
Record 1:
- Name: Acme Corp
- Q1 Revenue: $14M
- Q2 Revenue: $16M
...
```

**Trade-offs:** Table preservation increases storage requirements and can create imbalanced chunk sizes. Microsoft Azure's RAG guidance recommends considering LLM-generated summaries for complex tables, embedding the summary for matching while storing original content for context injection.

## List splitting should occur between items, preserving context

**Recommendation:** Split between list items (never mid-item), and prepend list context to split chunks.

Lists present a middle ground—they have natural boundaries (items) but also hierarchical context (the list's introductory text) that can be lost.

**Current framework behavior:**

- **Langchain4j DocumentByParagraphSplitter**: May split lists at blank lines between items; no semantic list awareness
- **LlamaIndex**: No special list handling—relies on sentence/paragraph boundaries
- **Unstructured.io**: Individual `ListItem` elements created; chunking respects item boundaries

**Recommended approach:**

```java
// List chunking with context preservation
List<Chunk> splitLongList(String listIntro, List<String> items) {
    List<Chunk> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder(listIntro + "\n");
    
    for (String item : items) {
        if (tokenCount(current.toString() + item) > MAX_CHUNK_SIZE) {
            // Save current chunk with intro context
            chunks.add(new Chunk(current.toString()));
            // Start new chunk with list context preserved
            current = new StringBuilder(listIntro + " (continued)\n" + item + "\n");
        } else {
            current.append(item).append("\n");
        }
    }
    chunks.add(new Chunk(current.toString()));
    return chunks;
}
```

**Before/after example:**

```
BEFORE (naive mid-item split):
Chunk 1: "Key features of our platform:\n- Real-time analytics dashboard with customizable wid"
Chunk 2: "gets\n- Automated reporting\n- API access"

AFTER (context-preserved split):
Chunk 1: "Key features of our platform:\n- Real-time analytics dashboard with customizable widgets\n- Automated reporting"
Chunk 2: "Key features of our platform (continued):\n- API access\n- Multi-tenant architecture"
```

## YAML front matter should become metadata, not chunk content

**Recommendation:** Extract YAML front matter during parsing, attach relevant fields as chunk metadata, exclude from main content chunking.

Front matter contains document-level context (author, date, tags, category) that applies to all chunks but shouldn't consume embedding space. This enables metadata filtering during retrieval.

**Current framework support:**

| Framework | Front Matter Handling |
|-----------|----------------------|
| **Langchain4j** | Treated as regular text; proposed PR #2418 would add `YamlFrontMatterConsumer` |
| **LlamaIndex** | No built-in support; requires pre-processing with `python-frontmatter` library |
| **Unstructured.io** | Not extracted as metadata—becomes regular content elements |

**Recommended implementation:**

```java
// Pre-processing step before chunking
DocumentWithMetadata extractFrontMatter(String markdown) {
    if (markdown.startsWith("---")) {
        int endIndex = markdown.indexOf("---", 3);
        String yamlBlock = markdown.substring(3, endIndex);
        String content = markdown.substring(endIndex + 3);
        
        Map<String, Object> metadata = yamlParser.parse(yamlBlock);
        return new DocumentWithMetadata(content.trim(), metadata);
    }
    return new DocumentWithMetadata(markdown, Map.of());
}

// Later, attach to each chunk
for (Chunk chunk : chunks) {
    chunk.metadata.putAll(documentMetadata);  // title, author, date, tags
}
```

**Trade-offs:** Extracting front matter requires an additional parsing step and the YAML parser dependency. Some front matter fields (like `description` or `summary`) might be valuable as chunk content for retrieval—consider selective extraction.

## Links and images require selective handling based on retrieval needs

**Recommendation:** Preserve inline links for context; extract image references to metadata for multi-modal pipelines; count link/image syntax toward token budget.

Links provide semantic context ("learn more about [OAuth2 authentication](/docs/auth)") while images often require separate processing pipelines.

**Framework approaches:**

- **LlamaIndex**: Links and images kept as markdown text within chunks; `LlamaParseJsonNodeParser` can extract images separately
- **Unstructured.io**: Extracts `link_urls` and `link_texts` into metadata; images become separate `Image` elements

**Recommended strategy:**

```java
ChunkWithAssets processLinksAndImages(String markdownChunk) {
    List<String> imageRefs = extractImages(markdownChunk);  // ![alt](url)
    List<LinkInfo> links = extractLinks(markdownChunk);     // [text](url)
    
    // Keep link text inline for semantic context
    // Store URLs in metadata for citation/source tracking
    Metadata metadata = new Metadata();
    metadata.put("links", links.stream().map(l -> l.url).toList());
    metadata.put("images", imageRefs);
    
    // Option: Replace long URLs with placeholders to save tokens
    String optimized = shortenUrls(markdownChunk);  // [OAuth2][1] ... [1]: /docs/auth
    
    return new ChunkWithAssets(optimized, metadata);
}
```

**Token counting consideration:** Markdown link syntax `[text](https://very-long-url.com/path/to/resource)` consumes significant tokens. For documents with many links, consider URL shortening or reference-style links during pre-processing.

## Header breadcrumbs should include H1 through H3, with optional H4

**Recommendation:** Default breadcrumb depth of H1 > H2 > H3; include H4+ only when documents use deep nesting meaningfully.

Header hierarchy provides essential retrieval context—Anthropic's Contextual Retrieval research showed that adding context reduced retrieval failures by **35-67%**. However, excessive depth creates noise.

**Framework implementations:**

- **LlamaIndex MarkdownNodeParser**: Tracks full hierarchy via `header_stack`; stores path in `header_path` metadata (e.g., "/Introduction/Getting Started/")
- **Proposed Langchain4j MarkdownSectionSplitter**: Would add `md-section-header`, `md-parent-header`, `md-section-level` metadata
- **Unstructured.io**: Tracks `category_depth` (0 for H1, 1 for H2, etc.) and `parent_id`

**Recommended implementation:**

```java
class HeaderTracker {
    private Stack<Header> hierarchy = new Stack<>();
    private static final int MAX_BREADCRUMB_DEPTH = 3;  // H1, H2, H3
    
    void encounterHeader(int level, String text) {
        // Pop headers at same or deeper level
        while (!hierarchy.isEmpty() && hierarchy.peek().level >= level) {
            hierarchy.pop();
        }
        hierarchy.push(new Header(level, text));
    }
    
    String getBreadcrumb() {
        return hierarchy.stream()
            .filter(h -> h.level <= MAX_BREADCRUMB_DEPTH)
            .map(h -> h.text)
            .collect(Collectors.joining(" > "));
    }
}

// Each chunk gets breadcrumb context prepended or in metadata
chunk.metadata.put("breadcrumb", "API Reference > Authentication > OAuth2");
// Or prepend: "[Context: API Reference > Authentication > OAuth2]\n" + chunk.content
```

**Trade-offs of depth levels:**

| Depth | Pros | Cons |
|-------|------|------|
| H1 only | Minimal overhead | Often too generic for retrieval |
| H1-H2 | Good balance for most docs | May miss important subsection context |
| H1-H3 | **Recommended default** | Slight token overhead |
| H1-H4+ | Maximum context | Often adds noise; H4+ headers tend to be implementation details |

## Practical configuration for Alexandria's target parameters

Based on research findings, here are specific recommendations for the **450-500 token chunks with 50-75 token overlap** target:

**Optimal parameter tuning:**

```java
// Alexandria markdown splitter configuration
MarkdownSplitterConfig config = MarkdownSplitterConfig.builder()
    .maxChunkSizeTokens(500)           // NVIDIA sweet spot: 512-1024
    .minChunkSizeTokens(100)           // Avoid tiny orphan chunks
    .overlapTokens(75)                 // 15% overlap (NVIDIA optimal)
    .breadcrumbDepth(3)                // H1 > H2 > H3
    .preserveCodeBlocks(true)          // Atomic code units
    .preserveTables(true)              // Atomic table units
    .maxOversizedChunk(1500)           // Allow up to 3x for code/tables
    .splitListsBetweenItems(true)
    .extractFrontMatter(true)
    .build();
```

**Handling priority order** when multiple elements compete:

1. **Code blocks**: Always preserve (highest priority)
2. **Tables**: Always preserve
3. **Headers**: Use as primary split boundaries
4. **Lists**: Split between items with context
5. **Paragraphs**: Secondary split boundary
6. **Sentences**: Tertiary split boundary (for oversized paragraphs)

**Langchain4j workaround until markdown splitter is available:**

```java
// Current best approach in Langchain4j 1.10.0
// Pre-process markdown to protect structural elements
String preprocessed = protectCodeBlocks(markdown);  // Replace with placeholders
String preprocessed = protectTables(preprocessed);

// Use recursive splitter with paragraph awareness
DocumentSplitter splitter = DocumentSplitters.recursive(
    500,    // ~500 tokens
    75,     // 15% overlap
    new OpenAiTokenCountEstimator("gpt-4o-mini")
);

List<TextSegment> segments = splitter.split(document);

// Post-process to restore protected elements and add metadata
segments.forEach(s -> {
    restoreProtectedElements(s);
    addBreadcrumbMetadata(s, headerTracker);
});
```

The research strongly supports **structural-first, size-second** chunking: split at semantic boundaries (headers, code blocks, paragraphs) first, then apply token limits only to oversized sections. NVIDIA's empirical study found page-level (structural) chunking achieved the highest accuracy (0.648) with lowest variance—validating that respecting document structure outperforms pure token-based approaches.