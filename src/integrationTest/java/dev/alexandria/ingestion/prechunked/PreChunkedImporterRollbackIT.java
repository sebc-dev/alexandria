package dev.alexandria.ingestion.prechunked;

import dev.alexandria.BaseIntegrationTest;
import dev.alexandria.ingestion.chunking.ContentType;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

/**
 * Verifies that PreChunkedImporter preserves existing chunks when embedAll() fails.
 * Embeddings are computed before any store mutation, so an embedding failure
 * leaves the store unchanged.
 */
class PreChunkedImporterRollbackIT extends BaseIntegrationTest {

    @Autowired
    PreChunkedImporter importer;

    @Autowired
    SearchService searchService;

    @MockitoSpyBean
    EmbeddingModel embeddingModel;

    @Test
    void existing_chunks_preserved_when_embedAll_fails() {
        String sourceUrl = "https://example.com/rollback-test";

        // 1. Import initial chunks successfully (real embedding model behavior)
        PreChunkedChunk originalChunk = new PreChunkedChunk(
                "PostgreSQL supports advanced indexing strategies including B-tree and GIN indexes.",
                sourceUrl,
                "database/indexing",
                ContentType.PROSE,
                "2026-02-18T10:00:00Z",
                null
        );
        importer.importChunks(new PreChunkedRequest(sourceUrl, List.of(originalChunk)));

        // Verify initial chunks are stored
        List<SearchResult> initialResults = searchService.search(
                new SearchRequest("PostgreSQL indexing strategies")
        );
        assertThat(initialResults)
                .anyMatch(r -> r.text().contains("PostgreSQL supports advanced indexing"));

        // 2. Make embedAll() throw on the next call (simulating embedding API failure)
        doThrow(new RuntimeException("Embedding API unavailable"))
                .when(embeddingModel).embedAll(anyList());

        // 3. Attempt replacement import — embedding fails before store is mutated
        PreChunkedChunk replacementChunk = new PreChunkedChunk(
                "Redis provides in-memory caching with persistence options.",
                sourceUrl,
                "caching/redis",
                ContentType.PROSE,
                "2026-02-18T11:00:00Z",
                null
        );
        assertThatThrownBy(() ->
                importer.importChunks(new PreChunkedRequest(sourceUrl, List.of(replacementChunk)))
        ).isInstanceOf(RuntimeException.class);

        // 4. Verify old chunks survived — embedAll failed before removeAll was called
        org.mockito.Mockito.reset(embeddingModel);

        List<SearchResult> afterFailure = searchService.search(
                new SearchRequest("PostgreSQL indexing strategies")
        );
        assertThat(afterFailure)
                .as("Original chunks should be untouched since embedding failed before store mutation")
                .anyMatch(r -> r.text().contains("PostgreSQL supports advanced indexing"));
    }
}
