package dev.alexandria.ingestion;

import dev.alexandria.crawl.CrawlResult;
import dev.alexandria.ingestion.chunking.DocumentChunkData;
import dev.alexandria.ingestion.chunking.MarkdownChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

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

    private final MarkdownChunker chunker;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public IngestionService(MarkdownChunker chunker,
                            EmbeddingStore<TextSegment> embeddingStore,
                            EmbeddingModel embeddingModel) {
        this.chunker = chunker;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
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
