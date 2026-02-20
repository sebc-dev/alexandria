package dev.alexandria.ingestion.prechunked;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.alexandria.BaseIntegrationTest;
import dev.alexandria.ingestion.chunking.ContentType;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PreChunkedImporterIT extends BaseIntegrationTest {

  @Autowired PreChunkedImporter importer;

  @Autowired SearchService searchService;

  @Test
  void importValidChunksMakesThemSearchable() {
    PreChunkedChunk proseChunk =
        new PreChunkedChunk(
            "Spring Boot simplifies application development with auto-configuration.",
            "https://docs.spring.io/boot",
            "getting-started/overview",
            ContentType.PROSE,
            "2026-02-18T10:00:00Z",
            null);
    PreChunkedChunk codeChunk =
        new PreChunkedChunk(
            "@SpringBootApplication\npublic class MyApp {}",
            "https://docs.spring.io/boot",
            "getting-started/overview",
            ContentType.CODE,
            "2026-02-18T10:00:00Z",
            "java");
    PreChunkedRequest request =
        new PreChunkedRequest("https://docs.spring.io/boot", List.of(proseChunk, codeChunk));

    int count = importer.importChunks(request);
    assertThat(count).isEqualTo(2);

    List<SearchResult> results =
        searchService.search(new SearchRequest("Spring Boot auto-configuration"));
    assertThat(results).isNotEmpty();
    assertThat(results).anyMatch(r -> "https://docs.spring.io/boot".equals(r.sourceUrl()));
  }

  @Test
  void importReplacesExistingChunksForSameSourceUrl() {
    String sourceUrl = "https://example.com/docs";

    // Import original chunks
    PreChunkedChunk originalChunk =
        new PreChunkedChunk(
            "Original content about Kubernetes container orchestration and pod scheduling.",
            sourceUrl,
            "kubernetes/pods",
            ContentType.PROSE,
            "2026-02-18T10:00:00Z",
            null);
    importer.importChunks(new PreChunkedRequest(sourceUrl, List.of(originalChunk)));

    // Import replacement chunks with SAME source_url
    PreChunkedChunk replacementChunk =
        new PreChunkedChunk(
            "Replacement content about Docker containerization and image building.",
            sourceUrl,
            "docker/containers",
            ContentType.PROSE,
            "2026-02-18T11:00:00Z",
            null);
    importer.importChunks(new PreChunkedRequest(sourceUrl, List.of(replacementChunk)));

    // Old content should NOT appear
    List<SearchResult> oldResults =
        searchService.search(new SearchRequest("Kubernetes pod scheduling"));
    boolean oldContentFound =
        oldResults.stream().anyMatch(r -> r.text().contains("Kubernetes container orchestration"));
    assertThat(oldContentFound).as("Old content should be replaced").isFalse();

    // New content should appear
    List<SearchResult> newResults =
        searchService.search(new SearchRequest("Docker containerization"));
    assertThat(newResults).anyMatch(r -> r.text().contains("Docker containerization"));
  }

  @Test
  void importRejectsInvalidChunksEntirely() {
    PreChunkedChunk validChunk =
        new PreChunkedChunk(
            "Valid content about microservices architecture patterns.",
            "https://example.com/valid",
            "architecture/microservices",
            ContentType.PROSE,
            "2026-02-18T10:00:00Z",
            null);
    PreChunkedChunk invalidChunk =
        new PreChunkedChunk(
            "", // blank text -- violates @NotBlank
            "https://example.com/valid",
            "architecture/invalid",
            ContentType.PROSE,
            "2026-02-18T10:00:00Z",
            null);
    PreChunkedRequest request =
        new PreChunkedRequest("https://example.com/valid", List.of(validChunk, invalidChunk));

    assertThatThrownBy(() -> importer.importChunks(request))
        .isInstanceOf(IllegalArgumentException.class);

    // Verify NO chunks were stored (all-or-nothing)
    List<SearchResult> results =
        searchService.search(new SearchRequest("microservices architecture patterns"));
    boolean validChunkStored =
        results.stream().anyMatch(r -> r.text().contains("microservices architecture"));
    assertThat(validChunkStored)
        .as("Valid chunk should NOT be stored when batch is invalid")
        .isFalse();
  }

  @Test
  void importRejectsBlankSourceUrl() {
    PreChunkedChunk chunk =
        new PreChunkedChunk(
            "Some content about API design.",
            "https://example.com/api",
            "api/design",
            ContentType.PROSE,
            "2026-02-18T10:00:00Z",
            null);
    PreChunkedRequest request = new PreChunkedRequest("", List.of(chunk));

    assertThatThrownBy(() -> importer.importChunks(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sourceUrl");
  }

  @Test
  void importRejectsEmptyChunksList() {
    PreChunkedRequest request = new PreChunkedRequest("https://example.com/empty", List.of());

    assertThatThrownBy(() -> importer.importChunks(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("chunks");
  }
}
