package fr.kalifazzia.alexandria.core.ingestion;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import fr.kalifazzia.alexandria.core.port.ChunkerPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs hierarchical two-pass chunking of documents.
 *
 * <p>Documents are first split into parent chunks (~1000 tokens / 4000 chars) with 10% overlap,
 * then each parent is split into child chunks (~200 tokens / 800 chars) with 10% overlap.
 *
 * <p>This enables precise retrieval (small child chunks for embedding) with context expansion
 * (parent chunks for providing surrounding context), solving the precision vs context tradeoff in RAG.
 *
 * <p>Uses character-based approximation: ~4 chars per token for English text.
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>Parent chunks: 4000 chars (~1000 tokens), 400 char overlap (10%)</li>
 *   <li>Child chunks: 800 chars (~200 tokens), 80 char overlap (10%)</li>
 * </ul>
 */
@Component
public class HierarchicalChunker implements ChunkerPort {

    /**
     * Approximate characters per token for English text.
     */
    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Parent chunk size in tokens.
     */
    private static final int PARENT_TOKENS = 1000;

    /**
     * Child chunk size in tokens.
     */
    private static final int CHILD_TOKENS = 200;

    /**
     * Overlap percentage (10%).
     */
    private static final double OVERLAP_RATIO = 0.1;

    private final DocumentSplitter parentSplitter;
    private final DocumentSplitter childSplitter;

    /**
     * Creates a HierarchicalChunker with default configuration.
     *
     * <p>Parent: 4000 chars (~1000 tokens) with 400 char overlap (10%)
     * <p>Child: 800 chars (~200 tokens) with 80 char overlap (10%)
     */
    public HierarchicalChunker() {
        int parentChars = PARENT_TOKENS * CHARS_PER_TOKEN;
        int parentOverlap = (int) (parentChars * OVERLAP_RATIO);

        int childChars = CHILD_TOKENS * CHARS_PER_TOKEN;
        int childOverlap = (int) (childChars * OVERLAP_RATIO);

        this.parentSplitter = DocumentSplitters.recursive(parentChars, parentOverlap);
        this.childSplitter = DocumentSplitters.recursive(childChars, childOverlap);
    }

    @Override
    public List<ChunkPair> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        Document doc = Document.from(content);
        List<TextSegment> parentSegments = parentSplitter.split(doc);
        List<ChunkPair> result = new ArrayList<>();

        for (int i = 0; i < parentSegments.size(); i++) {
            TextSegment parent = parentSegments.get(i);
            String parentText = parent.text();

            // Split parent into children
            Document parentDoc = Document.from(parentText);
            List<TextSegment> childSegments = childSplitter.split(parentDoc);

            List<String> childTexts = childSegments.stream()
                    .map(TextSegment::text)
                    .toList();

            result.add(new ChunkPair(parentText, childTexts, i));
        }

        return result;
    }
}
