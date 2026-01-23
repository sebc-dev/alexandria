package fr.kalifazzia.alexandria.infra.persistence;

import fr.kalifazzia.alexandria.core.model.Chunk;
import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.ChunkRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for JdbcChunkRepository.
 * Tests chunk persistence with pgvector embeddings.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("JdbcChunkRepository Integration Tests")
class JdbcChunkRepositoryIT {

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
    private ChunkRepository chunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Document testDocument;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE chunks CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE documents CASCADE");

        // Create a test document for chunk tests
        testDocument = documentRepository.save(Document.create(
                "/path/to/test.md",
                "Test Document",
                "testing",
                List.of("test"),
                "hash123",
                Map.of()
        ));
    }

    // ========== saveChunk() tests ==========

    @Test
    @DisplayName("saveChunk() persists parent chunk with null parent ID")
    void saveChunk_parentChunk_persistsWithNullParentId() {
        // Given
        float[] embedding = createTestEmbedding(384);

        // When
        UUID chunkId = chunkRepository.saveChunk(
                testDocument.id(),
                null, // parent chunk has no parent
                ChunkType.PARENT,
                "Parent chunk content",
                embedding,
                0
        );

        // Then
        List<Chunk> chunks = chunkRepository.findByDocumentId(testDocument.id());
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().id()).isEqualTo(chunkId);
        assertThat(chunks.getFirst().type()).isEqualTo(ChunkType.PARENT);
        assertThat(chunks.getFirst().parentChunkId()).isNull();
    }

    @Test
    @DisplayName("saveChunk() persists child chunk with parent reference")
    void saveChunk_childChunk_persistsWithParentReference() {
        // Given
        float[] embedding = createTestEmbedding(384);
        UUID parentId = chunkRepository.saveChunk(
                testDocument.id(),
                null,
                ChunkType.PARENT,
                "Parent chunk content",
                embedding,
                0
        );

        // When
        UUID childId = chunkRepository.saveChunk(
                testDocument.id(),
                parentId,
                ChunkType.CHILD,
                "Child chunk content",
                embedding,
                1
        );

        // Then
        List<Chunk> chunks = chunkRepository.findByDocumentId(testDocument.id());
        Chunk childChunk = chunks.stream()
                .filter(c -> c.id().equals(childId))
                .findFirst()
                .orElseThrow();
        assertThat(childChunk.type()).isEqualTo(ChunkType.CHILD);
        assertThat(childChunk.parentChunkId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("saveChunk() embedding round-trips correctly")
    void saveChunk_embedding_roundTripsCorrectly() {
        // Given
        float[] embedding = new float[384];
        embedding[0] = 0.1f;
        embedding[1] = 0.5f;
        embedding[383] = 0.9f;

        // When
        UUID chunkId = chunkRepository.saveChunk(
                testDocument.id(),
                null,
                ChunkType.PARENT,
                "Chunk with embedding",
                embedding,
                0
        );

        // Then - verify embedding is stored by checking it can be used in vector search
        Float similarity = jdbcTemplate.queryForObject(
                "SELECT 1 - (embedding <=> ?) AS similarity FROM chunks WHERE id = ?",
                Float.class,
                new com.pgvector.PGvector(embedding),
                chunkId
        );
        assertThat(similarity).isCloseTo(1.0f, within(1e-6f)); // Same embedding = similarity ~1.0
    }

    @Test
    @DisplayName("saveChunk() returns generated UUID")
    void saveChunk_returnsGeneratedUUID() {
        // Given
        float[] embedding = createTestEmbedding(384);

        // When
        UUID chunkId = chunkRepository.saveChunk(
                testDocument.id(),
                null,
                ChunkType.PARENT,
                "Chunk content",
                embedding,
                0
        );

        // Then
        assertThat(chunkId).isNotNull();

        // Verify the chunk exists in DB with this ID
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE id = ?",
                Long.class,
                chunkId
        );
        assertThat(count).isEqualTo(1);
    }

    // ========== findByDocumentId() tests ==========

    @Test
    @DisplayName("findByDocumentId() returns chunks ordered by position and type")
    void findByDocumentId_returnsChunksOrderedByPositionAndType() {
        // Given
        float[] embedding = createTestEmbedding(384);

        UUID parent1 = chunkRepository.saveChunk(
                testDocument.id(), null, ChunkType.PARENT, "Parent 1", embedding, 0);
        chunkRepository.saveChunk(
                testDocument.id(), parent1, ChunkType.CHILD, "Child 1a", embedding, 0);
        chunkRepository.saveChunk(
                testDocument.id(), parent1, ChunkType.CHILD, "Child 1b", embedding, 0);

        UUID parent2 = chunkRepository.saveChunk(
                testDocument.id(), null, ChunkType.PARENT, "Parent 2", embedding, 1);
        chunkRepository.saveChunk(
                testDocument.id(), parent2, ChunkType.CHILD, "Child 2a", embedding, 1);

        // When
        List<Chunk> chunks = chunkRepository.findByDocumentId(testDocument.id());

        // Then - ordered by position, then by type
        assertThat(chunks).hasSize(5);
        // Position 0: child chunks come after parent (alphabetical: 'child' < 'parent')
        assertThat(chunks.get(0).position()).isEqualTo(0);
        assertThat(chunks.get(1).position()).isEqualTo(0);
        assertThat(chunks.get(2).position()).isEqualTo(0);
        // Position 1
        assertThat(chunks.get(3).position()).isEqualTo(1);
        assertThat(chunks.get(4).position()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByDocumentId() returns empty for non-existent document")
    void findByDocumentId_notFound_returnsEmpty() {
        // When
        List<Chunk> chunks = chunkRepository.findByDocumentId(UUID.randomUUID());

        // Then
        assertThat(chunks).isEmpty();
    }

    // ========== deleteByDocumentId() tests ==========

    @Test
    @DisplayName("deleteByDocumentId() removes all chunks for document")
    void deleteByDocumentId_removesAllChunks() {
        // Given
        float[] embedding = createTestEmbedding(384);
        chunkRepository.saveChunk(testDocument.id(), null, ChunkType.PARENT, "P1", embedding, 0);
        chunkRepository.saveChunk(testDocument.id(), null, ChunkType.PARENT, "P2", embedding, 1);
        assertThat(chunkRepository.findByDocumentId(testDocument.id())).hasSize(2);

        // When
        chunkRepository.deleteByDocumentId(testDocument.id());

        // Then
        assertThat(chunkRepository.findByDocumentId(testDocument.id())).isEmpty();
    }

    // ========== count() tests ==========

    @Test
    @DisplayName("count() returns zero for empty table")
    void count_emptyTable_returnsZero() {
        // When
        long count = chunkRepository.count();

        // Then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("count() returns correct count with chunks")
    void count_withChunks_returnsCorrectCount() {
        // Given
        float[] embedding = createTestEmbedding(384);
        chunkRepository.saveChunk(testDocument.id(), null, ChunkType.PARENT, "P1", embedding, 0);
        chunkRepository.saveChunk(testDocument.id(), null, ChunkType.PARENT, "P2", embedding, 1);
        chunkRepository.saveChunk(testDocument.id(), null, ChunkType.PARENT, "P3", embedding, 2);

        // When
        long count = chunkRepository.count();

        // Then
        assertThat(count).isEqualTo(3);
    }

    // ========== deleteAll() tests ==========

    @Test
    @DisplayName("deleteAll() removes all chunks")
    void deleteAll_removesAllChunks() {
        // Given
        float[] embedding = createTestEmbedding(384);
        chunkRepository.saveChunk(testDocument.id(), null, ChunkType.PARENT, "P1", embedding, 0);
        chunkRepository.saveChunk(testDocument.id(), null, ChunkType.PARENT, "P2", embedding, 1);
        assertThat(chunkRepository.count()).isEqualTo(2);

        // When
        chunkRepository.deleteAll();

        // Then
        assertThat(chunkRepository.count()).isZero();
    }

    // Helper method to create test embeddings
    private float[] createTestEmbedding(int dimension) {
        float[] embedding = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            embedding[i] = (float) Math.random();
        }
        // Normalize the vector
        float sum = 0;
        for (float v : embedding) {
            sum += v * v;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < dimension; i++) {
            embedding[i] /= norm;
        }
        return embedding;
    }
}
