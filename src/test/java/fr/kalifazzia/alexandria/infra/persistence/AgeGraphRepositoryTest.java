package fr.kalifazzia.alexandria.infra.persistence;

import fr.kalifazzia.alexandria.core.model.ChunkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgeGraphRepository.
 * Mocks JdbcTemplate since Apache AGE extension is not available in test container.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgeGraphRepository Unit Tests")
class AgeGraphRepositoryTest {

    @Mock
    private JdbcOperations jdbcOperations;

    private AgeGraphRepository graphRepository;

    @BeforeEach
    void setUp() {
        graphRepository = new AgeGraphRepository(jdbcOperations);
    }

    // ========== createDocumentVertex() tests ==========

    @Test
    @DisplayName("createDocumentVertex() executes Cypher query")
    void createDocumentVertex_executesCypherQuery() {
        // Given
        UUID documentId = UUID.randomUUID();
        String path = "/path/to/doc.md";

        // When
        graphRepository.createDocumentVertex(documentId, path);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("cypher('alexandria'");
        assertThat(sql).contains("CREATE (:Document");
        assertThat(sql).contains("document_id: '" + documentId + "'");
        assertThat(sql).contains("path: '" + path + "'");
    }

    // ========== createChunkVertex() tests ==========

    @Test
    @DisplayName("createChunkVertex() executes Cypher query")
    void createChunkVertex_executesCypherQuery() {
        // Given
        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        // When
        graphRepository.createChunkVertex(chunkId, ChunkType.PARENT, documentId);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("cypher('alexandria'");
        assertThat(sql).contains("CREATE (:Chunk");
        assertThat(sql).contains("chunk_id: '" + chunkId + "'");
        assertThat(sql).contains("type: 'parent'");
        assertThat(sql).contains("document_id: '" + documentId + "'");
    }

    // ========== createParentChildEdge() tests ==========

    @Test
    @DisplayName("createParentChildEdge() executes Cypher query")
    void createParentChildEdge_executesCypherQuery() {
        // Given
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        // When
        graphRepository.createParentChildEdge(parentId, childId);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("cypher('alexandria'");
        assertThat(sql).contains("MATCH (p:Chunk");
        assertThat(sql).contains("chunk_id: '" + parentId + "'");
        assertThat(sql).contains("chunk_id: '" + childId + "'");
        assertThat(sql).contains("CREATE (p)-[:HAS_CHILD]->(c)");
    }

    // ========== createReferenceEdge() tests ==========

    @Test
    @DisplayName("createReferenceEdge() with null link text uses empty string")
    void createReferenceEdge_nullLinkText_usesEmptyString() {
        // Given
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        // When
        graphRepository.createReferenceEdge(sourceId, targetId, null);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("CREATE (s)-[:REFERENCES {link_text: ''}]->(t)");
    }

    @Test
    @DisplayName("createReferenceEdge() executes Cypher query with link text")
    void createReferenceEdge_withLinkText_executesCypherQuery() {
        // Given
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        // When
        graphRepository.createReferenceEdge(sourceId, targetId, "see also");

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("REFERENCES {link_text: 'see also'}");
    }

    // ========== deleteChunksByDocumentId() tests ==========

    @Test
    @DisplayName("deleteChunksByDocumentId() executes Cypher query")
    void deleteChunksByDocumentId_executesCypherQuery() {
        // Given
        UUID documentId = UUID.randomUUID();

        // When
        graphRepository.deleteChunksByDocumentId(documentId);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("MATCH (c:Chunk {document_id: '" + documentId + "'})");
        assertThat(sql).contains("DETACH DELETE c");
    }

    // ========== deleteDocumentGraph() tests ==========

    @Test
    @DisplayName("deleteDocumentGraph() executes Cypher query")
    void deleteDocumentGraph_executesCypherQuery() {
        // Given
        UUID documentId = UUID.randomUUID();

        // When
        graphRepository.deleteDocumentGraph(documentId);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("MATCH (d:Document {document_id: '" + documentId + "'})");
        assertThat(sql).contains("DETACH DELETE d");
    }

    // ========== findRelatedDocuments() tests ==========

    @Test
    @DisplayName("findRelatedDocuments() with maxHops < 1 throws exception")
    void findRelatedDocuments_maxHopsZero_throwsException() {
        // Given
        UUID documentId = UUID.randomUUID();

        // When/Then
        assertThatThrownBy(() -> graphRepository.findRelatedDocuments(documentId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxHops must be between 1 and 10");
    }

    @Test
    @DisplayName("findRelatedDocuments() with maxHops > 10 throws exception")
    void findRelatedDocuments_maxHopsEleven_throwsException() {
        // Given
        UUID documentId = UUID.randomUUID();

        // When/Then
        assertThatThrownBy(() -> graphRepository.findRelatedDocuments(documentId, 11))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxHops must be between 1 and 10");
    }

    @Test
    @DisplayName("findRelatedDocuments() with valid hops executes query")
    void findRelatedDocuments_validHops_executesQuery() {
        // Given
        UUID documentId = UUID.randomUUID();

        // When
        List<UUID> results = graphRepository.findRelatedDocuments(documentId, 3);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(RowCallbackHandler.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("cypher('alexandria'");
        assertThat(sql).contains("MATCH (d:Document {document_id: '" + documentId + "'})");
        assertThat(sql).contains("[:REFERENCES*1..3]");
        assertThat(sql).contains("RETURN DISTINCT related.document_id");
        assertThat(results).isEmpty(); // No results since we didn't mock any
    }

    // ========== clearAll() tests ==========

    @Test
    @DisplayName("clearAll() deletes chunks then documents")
    void clearAll_deletesChunksThenDocuments() {
        // When
        graphRepository.clearAll();

        // Then - verify two execute calls in order
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations, times(2)).execute(sqlCaptor.capture());

        List<String> sqls = sqlCaptor.getAllValues();
        assertThat(sqls.get(0)).contains("MATCH (c:Chunk) DETACH DELETE c");
        assertThat(sqls.get(1)).contains("MATCH (d:Document) DETACH DELETE d");
    }

    // ========== escapeCypher() tests (via public methods) ==========

    @Test
    @DisplayName("escapeCypher() handles null path safely")
    void escapeCypher_null_returnsNull() {
        // Given
        UUID documentId = UUID.randomUUID();

        // When - passing null path
        graphRepository.createDocumentVertex(documentId, null);

        // Then - should not throw, null is handled
        verify(jdbcOperations).execute(anyString());
    }

    @Test
    @DisplayName("escapeCypher() escapes special characters")
    void escapeCypher_specialChars_escapes() {
        // Given
        UUID documentId = UUID.randomUUID();
        String pathWithSpecialChars = "/path/with'quote\\backslash";

        // When
        graphRepository.createDocumentVertex(documentId, pathWithSpecialChars);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        // Single quotes should be doubled, backslashes escaped
        assertThat(sql).contains("with''quote"); // ' -> ''
        assertThat(sql).contains("\\\\backslash"); // \ -> \\
    }

    @Test
    @DisplayName("escapeCypher() escapes dollar-quote delimiter")
    void escapeCypher_dollarQuote_escapes() {
        // Given
        UUID documentId = UUID.randomUUID();
        String pathWithDollarQuote = "/path/$cypher$injection";

        // When
        graphRepository.createDocumentVertex(documentId, pathWithDollarQuote);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        // $cypher$ should be escaped to prevent SQL injection
        assertThat(sql).contains("$cypher$$injection");
    }
}
