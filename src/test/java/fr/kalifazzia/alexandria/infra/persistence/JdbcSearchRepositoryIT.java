package fr.kalifazzia.alexandria.infra.persistence;

import com.pgvector.PGvector;
import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.SearchRepository;
import fr.kalifazzia.alexandria.core.search.HybridSearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JdbcSearchRepository.
 * Tests vector similarity search and hybrid search with RRF fusion.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("JdbcSearchRepository Integration Tests")
class JdbcSearchRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
    ).withInitScript("init-test-db.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SearchRepository searchRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE chunks CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE documents CASCADE");
    }

    // ========== searchSimilar() tests ==========

    @Test
    @DisplayName("searchSimilar() returns results ordered by similarity")
    void searchSimilar_returnsResultsOrderedBySimilarity() {
        // Given - Create documents with chunks at known embeddings
        float[] queryEmbedding = createDirectionEmbedding(0.0);     // Base direction
        float[] closeEmbedding = createDirectionEmbedding(0.1);    // Very similar
        float[] mediumEmbedding = createDirectionEmbedding(0.5);   // Somewhat similar
        float[] farEmbedding = createDirectionEmbedding(1.0);      // Less similar

        createDocumentWithChunk("close", "Close content", closeEmbedding, "cat", List.of());
        createDocumentWithChunk("medium", "Medium content", mediumEmbedding, "cat", List.of());
        createDocumentWithChunk("far", "Far content", farEmbedding, "cat", List.of());

        // When
        List<SearchResult> results = searchRepository.searchSimilar(
                queryEmbedding,
                SearchFilters.simple(10)
        );

        // Then - results should be ordered by similarity (closest first)
        assertThat(results).hasSize(3);
        assertThat(results.get(0).documentTitle()).isEqualTo("Close content");
        assertThat(results.get(1).documentTitle()).isEqualTo("Medium content");
        assertThat(results.get(2).documentTitle()).isEqualTo("Far content");
        // Verify similarities are in descending order
        assertThat(results.get(0).similarity()).isGreaterThan(results.get(1).similarity());
        assertThat(results.get(1).similarity()).isGreaterThan(results.get(2).similarity());
    }

    @Test
    @DisplayName("searchSimilar() with minSimilarity filters low scores")
    void searchSimilar_withMinSimilarity_filtersLowScores() {
        // Given
        float[] queryEmbedding = createDirectionEmbedding(0.0);
        float[] closeEmbedding = createDirectionEmbedding(0.05);   // Very similar (~0.99)
        float[] farEmbedding = createDirectionEmbedding(1.5);      // Low similarity

        createDocumentWithChunk("close", "Close content", closeEmbedding, "cat", List.of());
        createDocumentWithChunk("far", "Far content", farEmbedding, "cat", List.of());

        // When - filter with high minSimilarity
        List<SearchResult> results = searchRepository.searchSimilar(
                queryEmbedding,
                new SearchFilters(10, 0.9, null, null)
        );

        // Then - only high similarity results pass
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().documentTitle()).isEqualTo("Close content");
    }

    @Test
    @DisplayName("searchSimilar() with category filter")
    void searchSimilar_withCategoryFilter_filtersCorrectly() {
        // Given
        float[] embedding = createDirectionEmbedding(0.0);

        createDocumentWithChunk("java-doc", "Java content", embedding, "java", List.of());
        createDocumentWithChunk("python-doc", "Python content", embedding, "python", List.of());
        createDocumentWithChunk("java-doc2", "More Java", embedding, "java", List.of());

        // When
        List<SearchResult> results = searchRepository.searchSimilar(
                embedding,
                new SearchFilters(10, null, "java", null)
        );

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.category().equals("java"));
    }

    @Test
    @DisplayName("searchSimilar() with tags filter")
    void searchSimilar_withTagsFilter_filtersCorrectly() {
        // Given
        float[] embedding = createDirectionEmbedding(0.0);

        createDocumentWithChunk("doc1", "Content 1", embedding, "cat", List.of("spring", "java"));
        createDocumentWithChunk("doc2", "Content 2", embedding, "cat", List.of("python"));
        createDocumentWithChunk("doc3", "Content 3", embedding, "cat", List.of("spring", "testing"));

        // When - filter by "spring" tag
        List<SearchResult> results = searchRepository.searchSimilar(
                embedding,
                new SearchFilters(10, null, null, List.of("spring"))
        );

        // Then - only documents with "spring" tag
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.tags().contains("spring"));
    }

    @Test
    @DisplayName("searchSimilar() respects maxResults limit")
    void searchSimilar_maxResults_limitsOutput() {
        // Given
        float[] embedding = createDirectionEmbedding(0.0);

        for (int i = 0; i < 10; i++) {
            createDocumentWithChunk("doc" + i, "Content " + i, embedding, "cat", List.of());
        }

        // When
        List<SearchResult> results = searchRepository.searchSimilar(
                embedding,
                SearchFilters.simple(3)
        );

        // Then
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("searchSimilar() returns empty when no matches")
    void searchSimilar_noMatches_returnsEmpty() {
        // Given - no documents exist

        // When
        List<SearchResult> results = searchRepository.searchSimilar(
                createDirectionEmbedding(0.0),
                SearchFilters.simple(10)
        );

        // Then
        assertThat(results).isEmpty();
    }

    // ========== hybridSearch() tests ==========

    @Test
    @DisplayName("hybridSearch() combines RRF scores correctly")
    void hybridSearch_combinedRRFScore_orderedCorrectly() {
        // Given - docs with varying vector and text relevance
        float[] queryEmbedding = createDirectionEmbedding(0.0);

        // Doc with good vector match and good text match
        float[] goodVectorEmb = createDirectionEmbedding(0.05);
        createDocumentWithChunk("best", "java spring framework tutorial", goodVectorEmb, "cat", List.of());

        // Doc with poor vector match but good text match
        float[] poorVectorEmb = createDirectionEmbedding(1.5);
        createDocumentWithChunk("text-only", "java spring boot guide", poorVectorEmb, "cat", List.of());

        // Doc with good vector match but poor text match
        createDocumentWithChunk("vector-only", "unrelated content xyz", goodVectorEmb, "cat", List.of());

        // When
        List<SearchResult> results = searchRepository.hybridSearch(
                queryEmbedding,
                "java spring",
                HybridSearchFilters.defaults(10)
        );

        // Then - combined score should rank "best" first
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().documentTitle()).isEqualTo("java spring framework tutorial");
    }

    @Test
    @DisplayName("hybridSearch() returns result matching only vector")
    void hybridSearch_vectorOnlyMatch_returnsResult() {
        // Given
        float[] queryEmbedding = createDirectionEmbedding(0.0);
        float[] similarEmbedding = createDirectionEmbedding(0.05);

        createDocumentWithChunk("vector-doc", "xyz abc completely different text", similarEmbedding, "cat", List.of());

        // When - search with text that doesn't match
        List<SearchResult> results = searchRepository.hybridSearch(
                queryEmbedding,
                "nomatchingterm",
                HybridSearchFilters.defaults(10)
        );

        // Then - vector match still returns result
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().documentTitle()).isEqualTo("xyz abc completely different text");
    }

    @Test
    @DisplayName("hybridSearch() returns result matching only text")
    void hybridSearch_textOnlyMatch_returnsResult() {
        // Given
        float[] queryEmbedding = createDirectionEmbedding(0.0);
        float[] differentEmbedding = createDirectionEmbedding(Math.PI); // Very different direction

        createDocumentWithChunk("text-doc", "specific keyword searchterm", differentEmbedding, "cat", List.of());

        // When
        List<SearchResult> results = searchRepository.hybridSearch(
                queryEmbedding,
                "searchterm",
                HybridSearchFilters.defaults(10)
        );

        // Then - text match returns result even with poor vector match
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().documentTitle()).isEqualTo("specific keyword searchterm");
    }

    @Test
    @DisplayName("hybridSearch() with minSimilarity filters low RRF scores")
    void hybridSearch_withMinSimilarity_filtersLowRRF() {
        // Given
        float[] queryEmbedding = createDirectionEmbedding(0.0);
        float[] goodEmbedding = createDirectionEmbedding(0.05);
        float[] poorEmbedding = createDirectionEmbedding(Math.PI);

        createDocumentWithChunk("good", "matching text query", goodEmbedding, "cat", List.of());
        createDocumentWithChunk("poor", "unrelated content", poorEmbedding, "cat", List.of());

        // When - with minSimilarity threshold
        // RRF scores are low (max ~0.033 with k=60), so use a lower threshold
        // minScore = minSimilarity * (vectorWeight + textWeight) = 0.015 * 2 = 0.03
        List<SearchResult> results = searchRepository.hybridSearch(
                queryEmbedding,
                "matching text",
                new HybridSearchFilters(10, 0.015, null, null, 1.0, 1.0, 60)
        );

        // Then - only good match passes (appears in both vector and text search)
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().documentTitle()).isEqualTo("matching text query");
    }

    @Test
    @DisplayName("hybridSearch() applies filters correctly")
    void hybridSearch_withFilters_appliesCorrectly() {
        // Given
        float[] embedding = createDirectionEmbedding(0.0);

        createDocumentWithChunk("java-doc", "java content", embedding, "java", List.of("spring"));
        createDocumentWithChunk("python-doc", "python content", embedding, "python", List.of("flask"));
        createDocumentWithChunk("java-doc2", "more java", embedding, "java", List.of("spring"));

        // When - filter by category
        List<SearchResult> results = searchRepository.hybridSearch(
                embedding,
                "content",
                HybridSearchFilters.withFilters(10, "java", null)
        );

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.category().equals("java"));
    }

    // ========== Helper methods ==========

    /**
     * Creates a document with parent and child chunks.
     * Returns the document ID for further verification.
     */
    private UUID createDocumentWithChunk(String path, String title, float[] embedding,
                                         String category, List<String> tags) {
        // Create document
        Document doc = documentRepository.save(Document.create(
                "/path/" + path + ".md",
                title,
                category,
                tags,
                "hash-" + path,
                Map.of()
        ));

        // Create parent chunk
        UUID parentId = UUID.randomUUID();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type, content, embedding, position)
                    VALUES (?, ?, NULL, 'parent', ?, ?, 0)
                    """);
            ps.setObject(1, parentId);
            ps.setObject(2, doc.id());
            ps.setString(3, "Parent context for: " + title);
            ps.setObject(4, new PGvector(embedding));
            return ps;
        });

        // Create child chunk (search targets child chunks)
        UUID childId = UUID.randomUUID();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type, content, embedding, position)
                    VALUES (?, ?, ?, 'child', ?, ?, 0)
                    """);
            ps.setObject(1, childId);
            ps.setObject(2, doc.id());
            ps.setObject(3, parentId);
            ps.setString(4, title); // Use title as child content for text search
            ps.setObject(5, new PGvector(embedding));
            return ps;
        });

        return doc.id();
    }

    /**
     * Creates a normalized 384-dimensional embedding pointing in a specific direction.
     * Different angles produce embeddings with predictable cosine similarities.
     *
     * @param angle Angle in radians (0 = base direction, PI = opposite)
     */
    private float[] createDirectionEmbedding(double angle) {
        float[] embedding = new float[384];

        // Use first two dimensions to control direction
        embedding[0] = (float) Math.cos(angle);
        embedding[1] = (float) Math.sin(angle);

        // Fill rest with small values to make it a valid vector
        for (int i = 2; i < 384; i++) {
            embedding[i] = 0.001f;
        }

        // Normalize
        float sum = 0;
        for (float v : embedding) {
            sum += v * v;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < 384; i++) {
            embedding[i] /= norm;
        }

        return embedding;
    }
}
