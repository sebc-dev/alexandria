package dev.alexandria.ingestion.prechunked;

import dev.alexandria.BaseIntegrationTest;
import dev.alexandria.ingestion.chunking.ContentType;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreChunkedImporterIT extends BaseIntegrationTest {

    @Autowired
    PreChunkedImporter importer;

    @Autowired
    SearchService searchService;

    @Autowired
    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void cleanStore() {
        embeddingStore.removeAll();
    }

    @Test
    void import_valid_chunks_makes_them_searchable() {
        PreChunkedChunk proseChunk = new PreChunkedChunk(
                "Spring Boot simplifies application development with auto-configuration.",
                "https://docs.spring.io/boot",
                "getting-started/overview",
                ContentType.PROSE,
                "2026-02-18T10:00:00Z",
                null
        );
        PreChunkedChunk codeChunk = new PreChunkedChunk(
                "@SpringBootApplication\npublic class MyApp {}",
                "https://docs.spring.io/boot",
                "getting-started/overview",
                ContentType.CODE,
                "2026-02-18T10:00:00Z",
                "java"
        );
        PreChunkedRequest request = new PreChunkedRequest(
                "https://docs.spring.io/boot",
                List.of(proseChunk, codeChunk)
        );

        int count = importer.importChunks(request);
        assertThat(count).isEqualTo(2);

        List<SearchResult> results = searchService.search(
                new SearchRequest("Spring Boot auto-configuration")
        );
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r ->
                "https://docs.spring.io/boot".equals(r.sourceUrl()));
    }

    @Test
    void import_replaces_existing_chunks_for_same_source_url() {
        String sourceUrl = "https://example.com/docs";

        // Import original chunks
        PreChunkedChunk originalChunk = new PreChunkedChunk(
                "Original content about Kubernetes container orchestration and pod scheduling.",
                sourceUrl,
                "kubernetes/pods",
                ContentType.PROSE,
                "2026-02-18T10:00:00Z",
                null
        );
        importer.importChunks(new PreChunkedRequest(sourceUrl, List.of(originalChunk)));

        // Import replacement chunks with SAME source_url
        PreChunkedChunk replacementChunk = new PreChunkedChunk(
                "Replacement content about Docker containerization and image building.",
                sourceUrl,
                "docker/containers",
                ContentType.PROSE,
                "2026-02-18T11:00:00Z",
                null
        );
        importer.importChunks(new PreChunkedRequest(sourceUrl, List.of(replacementChunk)));

        // Old content should NOT appear
        List<SearchResult> oldResults = searchService.search(
                new SearchRequest("Kubernetes pod scheduling")
        );
        boolean oldContentFound = oldResults.stream()
                .anyMatch(r -> r.text().contains("Kubernetes container orchestration"));
        assertThat(oldContentFound).as("Old content should be replaced").isFalse();

        // New content should appear
        List<SearchResult> newResults = searchService.search(
                new SearchRequest("Docker containerization")
        );
        assertThat(newResults).anyMatch(r ->
                r.text().contains("Docker containerization"));
    }

    @Test
    void import_rejects_invalid_chunks_entirely() {
        PreChunkedChunk validChunk = new PreChunkedChunk(
                "Valid content about microservices architecture patterns.",
                "https://example.com/valid",
                "architecture/microservices",
                ContentType.PROSE,
                "2026-02-18T10:00:00Z",
                null
        );
        PreChunkedChunk invalidChunk = new PreChunkedChunk(
                "",  // blank text -- violates @NotBlank
                "https://example.com/valid",
                "architecture/invalid",
                ContentType.PROSE,
                "2026-02-18T10:00:00Z",
                null
        );
        PreChunkedRequest request = new PreChunkedRequest(
                "https://example.com/valid",
                List.of(validChunk, invalidChunk)
        );

        assertThatThrownBy(() -> importer.importChunks(request))
                .isInstanceOf(IllegalArgumentException.class);

        // Verify NO chunks were stored (all-or-nothing)
        List<SearchResult> results = searchService.search(
                new SearchRequest("microservices architecture patterns")
        );
        boolean validChunkStored = results.stream()
                .anyMatch(r -> r.text().contains("microservices architecture"));
        assertThat(validChunkStored).as("Valid chunk should NOT be stored when batch is invalid").isFalse();
    }

    // import_rejects_invalid_content_type test removed: ContentType enum now
    // enforces valid values at compile time. Invalid JSON values ("invalid") are
    // rejected by Jackson deserialization before reaching the importer.
}
