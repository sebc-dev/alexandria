# Langchain4j 1.0.1 DocumentSplitter capabilities for Alexandria RAG

**Langchain4j 1.0.x lacks a native markdown-aware DocumentSplitter.** The in-development `MarkdownSectionSplitter` (PR #2418) remains in draft status as of January 2026, meaning Alexandria must either implement a custom splitter or use the recommended `DocumentSplitters.recursive()` with workarounds for code block preservation. Token-based splitting is fully supported via `TokenCountEstimator` implementations, and metadata propagates automatically from Document to TextSegment during splitting.

## Version clarification and available splitters

There is **no version 1.0.1** in Langchain4j's release history—versions progress 1.0.0 → 1.0.0-betaN → 1.1.0 → 1.2.0. The current stable release is 1.2.0 with BOM version alignment. All splitter implementations reside in the `langchain4j` module (not `langchain4j-core`) under `dev.langchain4j.data.document.splitter`:

| Class | Package | Type | Behavior |
|-------|---------|------|----------|
| `DocumentByParagraphSplitter` | `dev.langchain4j.data.document.splitter` | Token/Char | Splits on `\n\n` |
| `DocumentByLineSplitter` | `dev.langchain4j.data.document.splitter` | Token/Char | Splits on `\n` |
| `DocumentBySentenceSplitter` | `dev.langchain4j.data.document.splitter` | Token/Char | Uses OpenNLP |
| `DocumentByWordSplitter` | `dev.langchain4j.data.document.splitter` | Token/Char | Splits on spaces |
| `DocumentByCharacterSplitter` | `dev.langchain4j.data.document.splitter` | Char only | Character-level |
| `DocumentByRegexSplitter` | `dev.langchain4j.data.document.splitter` | Token/Char | Custom regex |
| `HierarchicalDocumentSplitter` | `dev.langchain4j.data.document.splitter` | Abstract base | Chain support |

**No MarkdownDocumentSplitter, MarkdownTextSplitter, or MarkdownSectionSplitter exists** in any released version. GitHub Issue #2417 and Draft PR #2418 target this feature, but the PR was removed from milestones in April 2025 and remains unmerged.

## DocumentSplitter interface and recursive chain

The `DocumentSplitter` interface provides the core abstraction:

```java
public interface DocumentSplitter {
    List<TextSegment> split(Document document);
    default List<TextSegment> splitAll(List<Document> documents);
    default List<TextSegment> splitAll(Document... documents);
}
```

The recommended `DocumentSplitters.recursive()` creates a hierarchical chain: **Paragraph → Line → Sentence → Word → Character**. This ensures graceful handling when individual units exceed `maxSegmentSize`:

```java
// Token-based recursive splitter (RECOMMENDED)
DocumentSplitter splitter = DocumentSplitters.recursive(
    500,    // max tokens per segment
    75,     // overlap tokens
    new OpenAiTokenCountEstimator("gpt-4o-mini")
);

// Internally creates this chain:
new DocumentByParagraphSplitter(maxSize, overlap, tokenEstimator,
    new DocumentByLineSplitter(maxSize, overlap, tokenEstimator,
        new DocumentBySentenceSplitter(maxSize, overlap, tokenEstimator,
            new DocumentByWordSplitter(maxSize, overlap, tokenEstimator))));
```

Each hierarchical splitter constructor accepts either character-based or token-based parameters, plus an optional `subSplitter` for fallback behavior.

## Token-based splitting with TokenCountEstimator

Langchain4j fully supports **token-based chunk sizing** through the `TokenCountEstimator` interface. Available implementations:

| Tokenizer | Module | Use Case |
|-----------|--------|----------|
| `OpenAiTokenCountEstimator` | `langchain4j-open-ai` | OpenAI models (tiktoken) |
| `OpenAiTokenizer` | `langchain4j-open-ai` | Direct tokenizer access |
| `HuggingFaceTokenizer` | `langchain4j-embeddings-*` | HuggingFace/BGE models |
| `GoogleAiGeminiTokenizer` | `langchain4j-google-ai-gemini` | Gemini models |

For **BGE-M3 with 8192 token context**, configure the splitter to leave buffer space for query tokens:

```java
// Configuration for BGE-M3 (450-500 tokens, 50-75 overlap)
DocumentSplitter bgeOptimizedSplitter = DocumentSplitters.recursive(
    475,    // ~500 tokens per chunk (conservative)
    65,     // ~65 token overlap
    new HuggingFaceTokenizer()  // For BGE models
);

// Alternative with OpenAI tokenizer (close approximation)
DocumentSplitter splitter = DocumentSplitters.recursive(
    450, 50, new OpenAiTokenCountEstimator("gpt-4o-mini")
);
```

## Metadata propagation and TextSegment API

**All metadata automatically propagates** from Document to TextSegment during splitting. The splitter adds an `"index"` metadata entry (0, 1, 2...) to each segment.

```java
// Metadata class API (fluent builder pattern)
Metadata metadata = Metadata.metadata("source", "docs/api.md")
    .put("category", "technical")
    .put("page", 5)
    .put("breadcrumb", "API > Authentication > OAuth");

// TextSegment creation with metadata
TextSegment segment = TextSegment.from(chunkText, metadata);

// Retrieving metadata
String breadcrumb = segment.metadata().getString("breadcrumb");
Integer index = segment.metadata().getInteger("index");
```

For **breadcrumb header injection**, use `TextSegmentTransformer` in the ingestion pipeline:

```java
EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
    .documentSplitter(splitter)
    .textSegmentTransformer(segment -> {
        String header = extractCurrentHeader(segment);  // Custom logic
        Metadata enriched = segment.metadata().copy()
            .put("breadcrumb", buildBreadcrumb(header))
            .put("section_header", header);
        return TextSegment.from(segment.text(), enriched);
    })
    .embeddingModel(embeddingModel)
    .embeddingStore(embeddingStore)
    .build();
```

## Code block preservation strategies

Since no native markdown splitter exists, Alexandria needs a custom approach. **The pre-processing strategy** is most reliable:

```java
public class MarkdownAwareSplitter implements DocumentSplitter {
    
    private static final Pattern CODE_BLOCK = Pattern.compile(
        "(?ms)^(```|~~~)([^\\n]*)\\n(.*?)\\n\\1"
    );
    private static final String PLACEHOLDER = "__CODE_BLOCK_%d__";
    private final DocumentSplitter delegate;
    
    public MarkdownAwareSplitter(int maxTokens, int overlap, TokenCountEstimator estimator) {
        this.delegate = DocumentSplitters.recursive(maxTokens, overlap, estimator);
    }
    
    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();
        Map<String, String> codeBlocks = new LinkedHashMap<>();
        
        // Extract code blocks, replace with placeholders
        Matcher matcher = CODE_BLOCK.matcher(text);
        StringBuffer sb = new StringBuffer();
        int idx = 0;
        while (matcher.find()) {
            String placeholder = String.format(PLACEHOLDER, idx++);
            codeBlocks.put(placeholder, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(sb);
        
        // Split sanitized text
        Document sanitized = Document.from(sb.toString(), document.metadata());
        List<TextSegment> segments = delegate.split(sanitized);
        
        // Restore code blocks in each segment
        return segments.stream()
            .map(seg -> restoreCodeBlocks(seg, codeBlocks))
            .collect(Collectors.toList());
    }
    
    private TextSegment restoreCodeBlocks(TextSegment segment, Map<String, String> blocks) {
        String text = segment.text();
        for (var entry : blocks.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return TextSegment.from(text, segment.metadata());
    }
}
```

## Complete Alexandria implementation with breadcrumb metadata

```java
import dev.langchain4j.data.document.*;
import dev.langchain4j.data.document.splitter.*;
import dev.langchain4j.data.segment.TextSegment;
import java.util.*;
import java.util.regex.*;

public class AlexandriaMarkdownSplitter implements DocumentSplitter {
    
    private static final Pattern CODE_BLOCK = Pattern.compile("(?ms)^(```|~~~)([^\\n]*)\\n(.*?)\\n\\1");
    private static final Pattern HEADER = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");
    
    private final int maxTokens;
    private final int overlapTokens;
    private final TokenCountEstimator tokenEstimator;
    
    public AlexandriaMarkdownSplitter(int maxTokens, int overlapTokens, TokenCountEstimator estimator) {
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
        this.tokenEstimator = estimator;
    }
    
    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();
        Map<String, String> codeBlocks = new LinkedHashMap<>();
        
        // Step 1: Extract and protect code blocks
        text = extractCodeBlocks(text, codeBlocks);
        
        // Step 2: Build header breadcrumb map
        Map<Integer, String> headerStack = buildHeaderBreadcrumbs(text);
        
        // Step 3: Split using recursive splitter
        DocumentSplitter delegateSplitter = DocumentSplitters.recursive(
            maxTokens, overlapTokens, tokenEstimator
        );
        Document sanitized = Document.from(text, document.metadata());
        List<TextSegment> segments = delegateSplitter.split(sanitized);
        
        // Step 4: Restore code blocks and inject breadcrumb metadata
        List<TextSegment> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            String restored = restoreCodeBlocks(seg.text(), codeBlocks);
            String breadcrumb = findBreadcrumb(seg.text(), headerStack, text);
            
            Metadata enriched = seg.metadata().copy()
                .put("breadcrumb", breadcrumb)
                .put("contains_code", restored.contains("```") || restored.contains("~~~"));
            
            result.add(TextSegment.from(restored, enriched));
        }
        return result;
    }
    
    private String extractCodeBlocks(String text, Map<String, String> storage) {
        Matcher matcher = CODE_BLOCK.matcher(text);
        StringBuffer sb = new StringBuffer();
        int idx = 0;
        while (matcher.find()) {
            String placeholder = String.format("__CODE_%d__", idx++);
            storage.put(placeholder, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    private String restoreCodeBlocks(String text, Map<String, String> storage) {
        for (var entry : storage.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }
    
    private Map<Integer, String> buildHeaderBreadcrumbs(String text) {
        Map<Integer, String> breadcrumbs = new TreeMap<>();
        String[] headerLevels = new String[7];
        
        Matcher matcher = HEADER.matcher(text);
        while (matcher.find()) {
            int level = matcher.group(1).length();
            String headerText = matcher.group(2).trim();
            headerLevels[level] = headerText;
            
            // Clear lower-level headers
            for (int i = level + 1; i < 7; i++) headerLevels[i] = null;
            
            // Build breadcrumb path
            StringBuilder breadcrumb = new StringBuilder();
            for (int i = 1; i <= level; i++) {
                if (headerLevels[i] != null) {
                    if (breadcrumb.length() > 0) breadcrumb.append(" > ");
                    breadcrumb.append(headerLevels[i]);
                }
            }
            breadcrumbs.put(matcher.start(), breadcrumb.toString());
        }
        return breadcrumbs;
    }
    
    private String findBreadcrumb(String segmentText, Map<Integer, String> headerStack, String fullText) {
        int segmentStart = fullText.indexOf(segmentText.substring(0, Math.min(50, segmentText.length())));
        String breadcrumb = "";
        for (var entry : headerStack.entrySet()) {
            if (entry.getKey() <= segmentStart) {
                breadcrumb = entry.getValue();
            } else break;
        }
        return breadcrumb.isEmpty() ? "Document Root" : breadcrumb;
    }
}
```

## Usage for Alexandria with BGE-M3

```java
// Complete ingestion pipeline for Alexandria
TokenCountEstimator estimator = new HuggingFaceTokenizer();  // or OpenAiTokenCountEstimator

AlexandriaMarkdownSplitter splitter = new AlexandriaMarkdownSplitter(
    475,    // ~450-500 tokens per chunk
    65,     // ~50-75 token overlap
    estimator
);

EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
    .documentTransformer(doc -> {
        doc.metadata()
            .put("project", "Alexandria")
            .put("ingested_at", System.currentTimeMillis());
        return doc;
    })
    .documentSplitter(splitter)
    .embeddingModel(bgeM3EmbeddingModel)  // Your BGE-M3 instance
    .embeddingStore(embeddingStore)
    .build();

// Ingest markdown documents
Document markdownDoc = FileSystemDocumentLoader.loadDocument(
    Path.of("docs/api-guide.md"),
    new TextDocumentParser()
);
markdownDoc.metadata().put("source", "api-guide.md");

IngestionResult result = ingestor.ingest(markdownDoc);
```

## Conclusion

Langchain4j provides robust token-based splitting through `DocumentSplitters.recursive()` and automatic metadata propagation, but **lacks native markdown awareness**. For Alexandria's requirements—code block preservation, breadcrumb headers, and 450-500 token chunks with BGE-M3—the recommended approach is implementing a custom `DocumentSplitter` that wraps the recursive splitter with pre-processing to protect code blocks and post-processing to inject breadcrumb metadata. The draft `MarkdownSectionSplitter` (PR #2418) demonstrates the approach the Langchain4j maintainers plan to use (commonmark-java for AST parsing), which could serve as a reference if you need more sophisticated markdown structure preservation.