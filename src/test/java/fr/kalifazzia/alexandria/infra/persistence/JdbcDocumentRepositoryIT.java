package fr.kalifazzia.alexandria.infra.persistence;

import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JdbcDocumentRepository.
 * Tests all CRUD operations with a real PostgreSQL database.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("JdbcDocumentRepository Integration Tests")
class JdbcDocumentRepositoryIT {

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
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE chunks CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE documents CASCADE");
    }

    // ========== save() tests ==========

    @Test
    @DisplayName("save() generates UUID and timestamps for new document")
    void save_newDocument_generatesUUIDAndTimestamps() {
        // Given
        Document doc = Document.create(
                "/path/to/doc.md",
                "Test Title",
                "testing",
                List.of("tag1", "tag2"),
                "hash123",
                Map.of("key", "value")
        );

        // When
        Instant before = Instant.now();
        Document saved = documentRepository.save(doc);
        Instant after = Instant.now();

        // Then
        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isNotNull();
        assertThat(saved.createdAt()).isBetween(before, after.plusMillis(100));
        assertThat(saved.updatedAt()).isBetween(before, after.plusMillis(100));
    }

    @Test
    @DisplayName("save() persists tags as TEXT[] array")
    void save_withTags_persistsTextArray() {
        // Given
        Document doc = Document.create(
                "/path/to/doc.md",
                "Test Title",
                "testing",
                List.of("java", "spring", "testing"),
                "hash123",
                Map.of()
        );

        // When
        Document saved = documentRepository.save(doc);
        Optional<Document> retrieved = documentRepository.findById(saved.id());

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().tags()).containsExactly("java", "spring", "testing");
    }

    @Test
    @DisplayName("save() persists frontmatter as JSONB")
    void save_withFrontmatter_persistsJsonb() {
        // Given
        Map<String, Object> frontmatter = Map.of(
                "author", "John Doe",
                "version", 2,
                "published", true
        );
        Document doc = Document.create(
                "/path/to/doc.md",
                "Test Title",
                "testing",
                List.of(),
                "hash123",
                frontmatter
        );

        // When
        Document saved = documentRepository.save(doc);
        Optional<Document> retrieved = documentRepository.findById(saved.id());

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().frontmatter())
                .containsEntry("author", "John Doe")
                .containsEntry("version", 2)
                .containsEntry("published", true);
    }

    @Test
    @DisplayName("save() with null frontmatter saves empty JSON")
    void save_nullFrontmatter_savesEmptyJson() {
        // Given
        Document doc = new Document(
                null,
                "/path/to/doc.md",
                "Test Title",
                "testing",
                List.of(),
                "hash123",
                null, // null frontmatter
                null,
                null
        );

        // When
        Document saved = documentRepository.save(doc);
        Optional<Document> retrieved = documentRepository.findById(saved.id());

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().frontmatter()).isEmpty();
    }

    // ========== findByPath() tests ==========

    @Test
    @DisplayName("findByPath() returns document when it exists")
    void findByPath_existing_returnsDocument() {
        // Given
        Document doc = Document.create(
                "/unique/path/doc.md",
                "Test Title",
                "testing",
                List.of("tag1"),
                "hash123",
                Map.of()
        );
        documentRepository.save(doc);

        // When
        Optional<Document> found = documentRepository.findByPath("/unique/path/doc.md");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Test Title");
        assertThat(found.get().category()).isEqualTo("testing");
    }

    @Test
    @DisplayName("findByPath() returns empty when document does not exist")
    void findByPath_notFound_returnsEmpty() {
        // When
        Optional<Document> found = documentRepository.findByPath("/nonexistent/path.md");

        // Then
        assertThat(found).isEmpty();
    }

    // ========== findById() tests ==========

    @Test
    @DisplayName("findById() returns document when it exists")
    void findById_existing_returnsDocument() {
        // Given
        Document doc = Document.create(
                "/path/to/doc.md",
                "Test Title",
                "testing",
                List.of(),
                "hash123",
                Map.of()
        );
        Document saved = documentRepository.save(doc);

        // When
        Optional<Document> found = documentRepository.findById(saved.id());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.id());
        assertThat(found.get().title()).isEqualTo("Test Title");
    }

    @Test
    @DisplayName("findById() returns empty when document does not exist")
    void findById_notFound_returnsEmpty() {
        // When
        Optional<Document> found = documentRepository.findById(UUID.randomUUID());

        // Then
        assertThat(found).isEmpty();
    }

    // ========== findByIds() tests ==========

    @Test
    @DisplayName("findByIds() returns all documents for multiple IDs")
    void findByIds_multipleIds_returnsAll() {
        // Given
        Document doc1 = documentRepository.save(Document.create(
                "/path/doc1.md", "Doc 1", "cat1", List.of(), "hash1", Map.of()));
        Document doc2 = documentRepository.save(Document.create(
                "/path/doc2.md", "Doc 2", "cat2", List.of(), "hash2", Map.of()));
        Document doc3 = documentRepository.save(Document.create(
                "/path/doc3.md", "Doc 3", "cat3", List.of(), "hash3", Map.of()));

        // When
        List<Document> found = documentRepository.findByIds(List.of(doc1.id(), doc3.id()));

        // Then
        assertThat(found).hasSize(2);
        assertThat(found).extracting(Document::id).containsExactlyInAnyOrder(doc1.id(), doc3.id());
    }

    @Test
    @DisplayName("findByIds() returns empty list for empty collection")
    void findByIds_emptyCollection_returnsEmpty() {
        // When
        List<Document> found = documentRepository.findByIds(List.of());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByIds() returns empty list for null collection")
    void findByIds_nullCollection_returnsEmpty() {
        // When
        List<Document> found = documentRepository.findByIds(null);

        // Then
        assertThat(found).isEmpty();
    }

    // ========== delete() tests ==========

    @Test
    @DisplayName("delete() removes existing document")
    void delete_existing_removesDocument() {
        // Given
        Document doc = documentRepository.save(Document.create(
                "/path/to/doc.md", "Test Title", "testing", List.of(), "hash123", Map.of()));
        assertThat(documentRepository.findById(doc.id())).isPresent();

        // When
        documentRepository.delete(doc.id());

        // Then
        assertThat(documentRepository.findById(doc.id())).isEmpty();
    }

    // ========== deleteByPath() tests ==========

    @Test
    @DisplayName("deleteByPath() removes document by path")
    void deleteByPath_existing_removesDocument() {
        // Given
        Document doc = documentRepository.save(Document.create(
                "/path/to/delete.md", "Test Title", "testing", List.of(), "hash123", Map.of()));
        assertThat(documentRepository.findByPath("/path/to/delete.md")).isPresent();

        // When
        documentRepository.deleteByPath("/path/to/delete.md");

        // Then
        assertThat(documentRepository.findByPath("/path/to/delete.md")).isEmpty();
    }

    // ========== findDistinctCategories() tests ==========

    @Test
    @DisplayName("findDistinctCategories() returns distinct sorted categories")
    void findDistinctCategories_returnsDistinctSorted() {
        // Given
        documentRepository.save(Document.create(
                "/path/doc1.md", "Doc 1", "zebra", List.of(), "hash1", Map.of()));
        documentRepository.save(Document.create(
                "/path/doc2.md", "Doc 2", "alpha", List.of(), "hash2", Map.of()));
        documentRepository.save(Document.create(
                "/path/doc3.md", "Doc 3", "zebra", List.of(), "hash3", Map.of())); // duplicate category
        documentRepository.save(Document.create(
                "/path/doc4.md", "Doc 4", "beta", List.of(), "hash4", Map.of()));

        // When
        List<String> categories = documentRepository.findDistinctCategories();

        // Then
        assertThat(categories).containsExactly("alpha", "beta", "zebra");
    }

    // ========== count() tests ==========

    @Test
    @DisplayName("count() returns zero for empty table")
    void count_emptyTable_returnsZero() {
        // When
        long count = documentRepository.count();

        // Then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("count() returns correct count with documents")
    void count_withDocuments_returnsCorrectCount() {
        // Given
        documentRepository.save(Document.create(
                "/path/doc1.md", "Doc 1", "cat", List.of(), "hash1", Map.of()));
        documentRepository.save(Document.create(
                "/path/doc2.md", "Doc 2", "cat", List.of(), "hash2", Map.of()));
        documentRepository.save(Document.create(
                "/path/doc3.md", "Doc 3", "cat", List.of(), "hash3", Map.of()));

        // When
        long count = documentRepository.count();

        // Then
        assertThat(count).isEqualTo(3);
    }

    // ========== findLastUpdated() tests ==========

    @Test
    @DisplayName("findLastUpdated() returns empty for empty table")
    void findLastUpdated_emptyTable_returnsEmpty() {
        // When
        Optional<Instant> lastUpdated = documentRepository.findLastUpdated();

        // Then
        assertThat(lastUpdated).isEmpty();
    }

    @Test
    @DisplayName("findLastUpdated() returns latest timestamp")
    void findLastUpdated_withDocuments_returnsLatest() throws InterruptedException {
        // Given
        documentRepository.save(Document.create(
                "/path/doc1.md", "Doc 1", "cat", List.of(), "hash1", Map.of()));
        Thread.sleep(10); // Ensure different timestamps
        Document latest = documentRepository.save(Document.create(
                "/path/doc2.md", "Doc 2", "cat", List.of(), "hash2", Map.of()));

        // When
        Optional<Instant> lastUpdated = documentRepository.findLastUpdated();

        // Then - allow small tolerance for timestamp precision differences
        assertThat(lastUpdated).isPresent();
        assertThat(lastUpdated.get())
                .isCloseTo(latest.updatedAt(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
    }

    // ========== deleteAll() tests ==========

    @Test
    @DisplayName("deleteAll() removes all documents")
    void deleteAll_removesAllDocuments() {
        // Given
        documentRepository.save(Document.create(
                "/path/doc1.md", "Doc 1", "cat", List.of(), "hash1", Map.of()));
        documentRepository.save(Document.create(
                "/path/doc2.md", "Doc 2", "cat", List.of(), "hash2", Map.of()));
        assertThat(documentRepository.count()).isEqualTo(2);

        // When
        documentRepository.deleteAll();

        // Then
        assertThat(documentRepository.count()).isZero();
    }
}
