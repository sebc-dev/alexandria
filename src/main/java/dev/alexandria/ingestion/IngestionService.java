package dev.alexandria.ingestion;

import dev.alexandria.ingestion.chunking.DocumentChunkData;
import dev.alexandria.ingestion.chunking.MarkdownChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Orchestrates the ingestion pipeline: markdown content -> chunk -> embed -> store.
 *
 * <p>Takes markdown content and metadata, chunks it via MarkdownChunker, embeds the
 * chunks, and stores them in the EmbeddingStore where they become searchable by
 * Phase 2's SearchService.
 *
 * <p><strong>Transaction semantics:</strong> this service uses best-effort processing.
 * Each page is chunked, embedded, and stored independently. If a page fails mid-batch,
 * previously stored pages are <em>not</em> rolled back. This is intentional: embedding
 * calls are external API operations that cannot participate in database transactions,
 * and losing N-1 successfully ingested pages because of one failure is undesirable.
 * Callers should handle per-page errors and retry failed pages individually.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final MarkdownChunker chunker;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final IngestionStateRepository ingestionStateRepository;

    public IngestionService(MarkdownChunker chunker,
                            EmbeddingStore<TextSegment> embeddingStore,
                            EmbeddingModel embeddingModel,
                            IngestionStateRepository ingestionStateRepository) {
        this.chunker = chunker;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.ingestionStateRepository = ingestionStateRepository;
    }

    /**
     * Ingest a single page: chunk markdown, embed chunks, and store in embedding store.
     *
     * @param markdown    the raw Markdown text
     * @param sourceUrl   the URL of the page
     * @param lastUpdated ISO-8601 timestamp
     * @return number of chunks stored
     */
    public int ingestPage(String markdown, String sourceUrl, String lastUpdated) {
        List<DocumentChunkData> chunks = chunker.chunk(markdown, sourceUrl, lastUpdated);
        storeChunks(chunks);
        return chunks.size();
    }

    /**
     * Delete all existing chunks for a given URL from the embedding store.
     * Used before re-ingesting changed content to avoid stale data.
     *
     * @param normalizedUrl the URL whose chunks should be removed
     */
    public void deleteChunksForUrl(String normalizedUrl) {
        embeddingStore.removeAll(metadataKey("source_url").isEqualTo(normalizedUrl));
    }

    /**
     * Clear all ingestion state records for a source.
     * Used when triggering a full recrawl to reset change detection.
     *
     * @param sourceId the UUID of the source to clear
     */
    public void clearIngestionState(UUID sourceId) {
        ingestionStateRepository.deleteAllBySourceId(sourceId);
    }

    /**
     * Result of incremental page ingestion.
     *
     * @param chunksStored number of chunks stored (0 if skipped)
     * @param skipped      true if content hash was unchanged
     * @param changed      true if content was new or changed
     */
    public record IngestResult(int chunksStored, boolean skipped, boolean changed) {}

    private static final int EMBED_BATCH_SIZE = 256;

    private void storeChunks(List<DocumentChunkData> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        List<TextSegment> segments = chunks.stream()
                .map(DocumentChunkData::toTextSegment)
                .toList();

        for (int i = 0; i < segments.size(); i += EMBED_BATCH_SIZE) {
            List<TextSegment> batch = segments.subList(i, Math.min(i + EMBED_BATCH_SIZE, segments.size()));
            List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
            embeddingStore.addAll(embeddings, batch);
        }
    }
}
