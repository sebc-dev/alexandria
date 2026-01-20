package fr.kalifazzia.alexandria.infra.ingestion;

import fr.kalifazzia.alexandria.core.ingestion.HierarchicalChunker;
import fr.kalifazzia.alexandria.core.ingestion.IngestionService;
import fr.kalifazzia.alexandria.core.ingestion.MarkdownParser;
import fr.kalifazzia.alexandria.core.model.Chunk;
import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.ChunkRepository;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete ingestion pipeline.
 * Requires Docker for PostgreSQL with pgvector.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Ingestion Integration Tests")
class IngestionIT {

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
    private IngestionService ingestionService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE chunks CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE documents CASCADE");
    }

    @Test
    @DisplayName("should ingest markdown file and create document with chunks")
    void ingestsMarkdownFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path file = tempDir.resolve("test.md");
        Files.writeString(file, """
                ---
                title: Test Document
                category: testing
                tags:
                  - integration
                  - test
                ---

                # Test Document

                This is test content for the ingestion pipeline.
                It contains enough text to generate at least one chunk.

                ## Section One

                More content in section one. The chunker needs sufficient content
                to create meaningful chunks for embedding and search.

                ## Section Two

                Additional content in section two to ensure proper chunking behavior.
                """);

        // When
        ingestionService.ingestFile(file);

        // Then
        Optional<Document> document = documentRepository.findByPath(file.toAbsolutePath().toString());
        assertThat(document).isPresent();
        assertThat(document.get().title()).isEqualTo("Test Document");
        assertThat(document.get().category()).isEqualTo("testing");
        assertThat(document.get().tags()).containsExactly("integration", "test");

        List<Chunk> chunks = chunkRepository.findByDocumentId(document.get().id());
        assertThat(chunks).isNotEmpty();

        // Verify parent chunks exist
        List<Chunk> parents = chunks.stream()
                .filter(c -> c.type() == ChunkType.PARENT)
                .toList();
        assertThat(parents).isNotEmpty();

        // Verify child chunks reference their parents
        List<Chunk> children = chunks.stream()
                .filter(c -> c.type() == ChunkType.CHILD)
                .toList();
        for (Chunk child : children) {
            assertThat(child.parentChunkId()).isNotNull();
        }
    }

    @Test
    @DisplayName("should skip re-indexing when content unchanged")
    void skipsUnchangedContent(@TempDir Path tempDir) throws IOException {
        // Given
        Path file = tempDir.resolve("test.md");
        String content = """
                ---
                title: Stable Document
                ---

                # Stable Document

                This content will not change between ingestion runs.
                """;
        Files.writeString(file, content);

        // First ingestion
        ingestionService.ingestFile(file);
        Optional<Document> firstDoc = documentRepository.findByPath(file.toAbsolutePath().toString());
        assertThat(firstDoc).isPresent();

        // When - second ingestion with same content
        ingestionService.ingestFile(file);

        // Then - document unchanged (same ID)
        Optional<Document> secondDoc = documentRepository.findByPath(file.toAbsolutePath().toString());
        assertThat(secondDoc).isPresent();
        assertThat(secondDoc.get().id()).isEqualTo(firstDoc.get().id());
    }

    @Test
    @DisplayName("should delete old chunks when content changes")
    void deletesOldChunksOnChange(@TempDir Path tempDir) throws IOException {
        // Given
        Path file = tempDir.resolve("test.md");
        String originalContent = """
                ---
                title: Original
                ---

                # Original Content

                This is the original content that will be replaced.
                """;
        Files.writeString(file, originalContent);

        // First ingestion
        ingestionService.ingestFile(file);
        Optional<Document> firstDoc = documentRepository.findByPath(file.toAbsolutePath().toString());
        assertThat(firstDoc).isPresent();
        List<Chunk> originalChunks = chunkRepository.findByDocumentId(firstDoc.get().id());

        // When - update content
        String updatedContent = """
                ---
                title: Updated
                ---

                # Updated Content

                This is completely new content that replaces the original.
                """;
        Files.writeString(file, updatedContent);
        ingestionService.ingestFile(file);

        // Then - new document created, old one deleted
        Optional<Document> updatedDoc = documentRepository.findByPath(file.toAbsolutePath().toString());
        assertThat(updatedDoc).isPresent();
        assertThat(updatedDoc.get().id()).isNotEqualTo(firstDoc.get().id());
        assertThat(updatedDoc.get().title()).isEqualTo("Updated");

        // Old chunks no longer exist
        List<Chunk> oldChunks = chunkRepository.findByDocumentId(firstDoc.get().id());
        assertThat(oldChunks).isEmpty();

        // New chunks exist
        List<Chunk> newChunks = chunkRepository.findByDocumentId(updatedDoc.get().id());
        assertThat(newChunks).isNotEmpty();
    }

    @Test
    @DisplayName("should ingest all markdown files in directory")
    void ingestsDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        Files.writeString(tempDir.resolve("doc1.md"), """
                ---
                title: Document One
                ---
                # Document One
                Content for document one.
                """);
        Files.writeString(tempDir.resolve("doc2.md"), """
                ---
                title: Document Two
                ---
                # Document Two
                Content for document two.
                """);
        Files.writeString(tempDir.resolve("readme.txt"), "Not a markdown file");

        // When
        ingestionService.ingestDirectory(tempDir);

        // Then - only markdown files ingested
        Long docCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        assertThat(docCount).isEqualTo(2);
    }
}
