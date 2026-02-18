package dev.alexandria.ingestion;

import dev.alexandria.crawl.CrawlResult;
import dev.alexandria.crawl.CrawlSiteResult;
import dev.alexandria.ingestion.chunking.DocumentChunkData;
import dev.alexandria.ingestion.chunking.MarkdownChunker;
import dev.langchain4j.data.document.Metadata;
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
 * <p>Takes crawled pages from Phase 3's CrawlSiteResult, chunks each page's Markdown
 * via MarkdownChunker (Plan 01), embeds the chunks, and stores them in the EmbeddingStore
 * where they become searchable by Phase 2's SearchService.
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
     * Ingests all successful pages from a crawl result into the EmbeddingStore.
     *
     * @param crawlResult the crawl site result containing successful pages
     * @return total number of chunks stored across all pages
     */
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

    private void storeChunks(List<DocumentChunkData> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        List<TextSegment> segments = chunks.stream()
                .map(cd -> TextSegment.from(cd.text(), buildMetadata(cd)))
                .toList();

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

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
}
