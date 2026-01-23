package fr.kalifazzia.alexandria.infra.search;

import fr.kalifazzia.alexandria.core.search.SearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import fr.kalifazzia.alexandria.core.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SearchIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("alexandria_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-test-db.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SearchService searchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID documentId;
    private UUID parentChunkId;

    @BeforeEach
    void setUp() {
        // Clean up
        jdbcTemplate.execute("DELETE FROM chunks");
        jdbcTemplate.execute("DELETE FROM documents");

        // Insert test document
        documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, path, title, category, tags, content_hash)
                VALUES (?, '/docs/spring-config.md', 'Spring Configuration Guide', 'java', ARRAY['spring', 'configuration'], 'hash123')
                """, documentId);

        // Insert parent chunk
        parentChunkId = UUID.randomUUID();
        float[] parentEmbedding = new float[384];
        jdbcTemplate.update("""
                INSERT INTO chunks (id, document_id, chunk_type, content, embedding, position)
                VALUES (?, ?, 'parent', 'Spring Boot configuration including application.yml and profiles. Advanced topics like externalized configuration.', ?::vector, 0)
                """, parentChunkId, documentId, arrayToString(parentEmbedding));

        // Insert child chunks with embeddings similar to "spring configuration"
        // Child 1: Very relevant
        float[] childEmbedding1 = createEmbedding(0.9f);
        jdbcTemplate.update("""
                INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type, content, embedding, position)
                VALUES (?, ?, ?, 'child', 'Configure Spring Boot with application.yml', ?::vector, 0)
                """, UUID.randomUUID(), documentId, parentChunkId, arrayToString(childEmbedding1));

        // Child 2: Somewhat relevant
        float[] childEmbedding2 = createEmbedding(0.7f);
        jdbcTemplate.update("""
                INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type, content, embedding, position)
                VALUES (?, ?, ?, 'child', 'Spring profiles for environment-specific config', ?::vector, 1)
                """, UUID.randomUUID(), documentId, parentChunkId, arrayToString(childEmbedding2));
    }

    @Test
    void search_shouldReturnResultsWithParentContext() {
        // When
        List<SearchResult> results = searchService.search("spring configuration", 10);

        // Then
        assertThat(results).isNotEmpty();
        SearchResult first = results.get(0);
        assertThat(first.parentContext()).isNotNull().isNotBlank();
        assertThat(first.childContent()).isNotNull().isNotBlank();
        assertThat(first.documentTitle()).isEqualTo("Spring Configuration Guide");
        assertThat(first.category()).isEqualTo("java");
        assertThat(first.tags()).contains("spring", "configuration");
    }

    @Test
    void search_shouldFilterByCategory() {
        // Given - Insert another document with different category
        UUID otherDocId = UUID.randomUUID();
        UUID otherParentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, path, title, category, tags, content_hash)
                VALUES (?, '/docs/python.md', 'Python Guide', 'python', ARRAY['python'], 'hash456')
                """, otherDocId);
        jdbcTemplate.update("""
                INSERT INTO chunks (id, document_id, chunk_type, content, embedding, position)
                VALUES (?, ?, 'parent', 'Python programming basics', ?::vector, 0)
                """, otherParentId, otherDocId, arrayToString(new float[384]));
        jdbcTemplate.update("""
                INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type, content, embedding, position)
                VALUES (?, ?, ?, 'child', 'Python configuration with config files', ?::vector, 0)
                """, UUID.randomUUID(), otherDocId, otherParentId, arrayToString(createEmbedding(0.8f)));

        // When - Filter by java category
        SearchFilters filters = new SearchFilters(10, null, "java", null);
        List<SearchResult> results = searchService.search("configuration", filters);

        // Then - Only java results
        assertThat(results).allMatch(r -> "java".equals(r.category()));
    }

    @Test
    void search_shouldFilterByTags() {
        // When
        SearchFilters filters = new SearchFilters(10, null, null, List.of("spring"));
        List<SearchResult> results = searchService.search("configuration", filters);

        // Then
        assertThat(results).allMatch(r -> r.tags().contains("spring"));
    }

    @Test
    void search_shouldFilterByMinSimilarity() {
        // When
        SearchFilters filters = new SearchFilters(10, 0.5, null, null);
        List<SearchResult> results = searchService.search("spring configuration", filters);

        // Then
        assertThat(results).allMatch(r -> r.similarity() >= 0.5);
    }

    // Helper to create embeddings with a seed value for testing
    private float[] createEmbedding(float seed) {
        float[] embedding = new float[384];
        for (int i = 0; i < 384; i++) {
            embedding[i] = seed * (i % 10) / 10f;
        }
        return embedding;
    }

    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
