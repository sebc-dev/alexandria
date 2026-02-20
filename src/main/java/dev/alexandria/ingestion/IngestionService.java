package dev.alexandria.ingestion;

import dev.alexandria.crawl.ContentHasher;
import dev.alexandria.crawl.CrawlResult;
import dev.alexandria.crawl.UrlNormalizer;
import dev.alexandria.ingestion.chunking.DocumentChunkData;
import dev.alexandria.ingestion.chunking.MarkdownChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Orchestrates the ingestion pipeline: crawl result -> chunk -> embed -> store.
 *
 * <p>Takes crawled pages from Phase 3's CrawlService, chunks each page's Markdown
 * via MarkdownChunker, embeds the chunks, and stores them in the EmbeddingStore
 * where they become searchable by Phase 2's SearchService.
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
     * Ingests all crawled pages into the EmbeddingStore.
     *
     * @param pages the successfully crawled pages from CrawlService
     * @return total number of chunks stored across all pages
     */
    public int ingest(List<CrawlResult> pages) {
        String lastUpdated = Instant.now().toString();
        int totalChunks = 0;
        for (CrawlResult page : pages) {
            List<DocumentChunkData> chunks = chunker.chunk(
                    page.markdown(), page.url(), lastUpdated
            );
            storeChunks(chunks);
            totalChunks += chunks.size();
        }
        return totalChunks;
    }

    /**
     * Convenience method for single-page ingestion.
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
     * Incrementally ingest a single page with content hash-based change detection.
     *
     * <p>Compares the SHA-256 hash of the provided markdown against the stored hash
     * for this source+URL. If unchanged, the page is skipped entirely. If changed
     * (or new), old chunks are deleted and the page is re-chunked and re-embedded.
     *
     * @param sourceId the UUID of the source being crawled
     * @param url      the URL of the page
     * @param markdown the raw Markdown content
     * @return result indicating chunks stored, whether skipped, and whether content changed
     */
    public IngestResult ingestPageIncremental(UUID sourceId, String url, String markdown) {
        String normalizedUrl = UrlNormalizer.normalize(url);
        String newHash = ContentHasher.sha256(markdown);

        Optional<IngestionState> existingState =
                ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, normalizedUrl);

        if (existingState.isPresent() && existingState.get().getContentHash().equals(newHash)) {
            log.debug("Content unchanged for {}, skipping ingestion", normalizedUrl);
            return new IngestResult(0, true, false);
        }

        // Delete old chunks before re-ingesting
        embeddingStore.removeAll(metadataKey("source_url").isEqualTo(normalizedUrl));

        // Chunk and embed
        int chunkCount = ingestPage(markdown, normalizedUrl, Instant.now().toString());

        // Update or create ingestion state
        if (existingState.isPresent()) {
            IngestionState state = existingState.get();
            state.setContentHash(newHash);
            state.setLastIngestedAt(Instant.now());
            ingestionStateRepository.save(state);
        } else {
            ingestionStateRepository.save(new IngestionState(sourceId, normalizedUrl, newHash));
        }

        log.debug("Ingested {} chunks for {} ({})", chunkCount, normalizedUrl,
                existingState.isPresent() ? "updated" : "new");
        return new IngestResult(chunkCount, false, true);
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
