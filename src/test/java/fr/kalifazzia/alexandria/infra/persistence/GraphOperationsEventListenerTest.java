package fr.kalifazzia.alexandria.infra.persistence;

import fr.kalifazzia.alexandria.core.ingestion.DocumentIngestedEvent;
import fr.kalifazzia.alexandria.core.ingestion.DocumentIngestedEvent.ChunkGraphOperation;
import fr.kalifazzia.alexandria.core.ingestion.DocumentIngestedEvent.ReferenceEdgeOperation;
import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for GraphOperationsEventListener.
 * Tests the event-driven graph creation after PostgreSQL commits.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GraphOperationsEventListener Unit Tests")
class GraphOperationsEventListenerTest {

    @Mock
    private GraphRepository graphRepository;

    private GraphOperationsEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new GraphOperationsEventListener(graphRepository);
    }

    @Test
    @DisplayName("handleDocumentIngested() creates document vertex")
    void handleDocumentIngested_createsDocumentVertex() {
        // Given
        UUID documentId = UUID.randomUUID();
        String documentPath = "/path/to/doc.md";
        DocumentIngestedEvent event = new DocumentIngestedEvent(
                documentId,
                documentPath,
                List.of(),
                List.of()
        );

        // When
        listener.handleDocumentIngested(event);

        // Then
        verify(graphRepository).createDocumentVertex(documentId, documentPath);
    }

    @Test
    @DisplayName("handleDocumentIngested() creates chunk vertices")
    void handleDocumentIngested_createsChunkVertices() {
        // Given
        UUID documentId = UUID.randomUUID();
        UUID parentChunkId = UUID.randomUUID();
        UUID childChunkId = UUID.randomUUID();

        List<ChunkGraphOperation> chunkOps = List.of(
                new ChunkGraphOperation(parentChunkId, ChunkType.PARENT, documentId, null),
                new ChunkGraphOperation(childChunkId, ChunkType.CHILD, documentId, parentChunkId)
        );

        DocumentIngestedEvent event = new DocumentIngestedEvent(
                documentId,
                "/path/to/doc.md",
                chunkOps,
                List.of()
        );

        // When
        listener.handleDocumentIngested(event);

        // Then
        verify(graphRepository).createChunkVertex(parentChunkId, ChunkType.PARENT, documentId);
        verify(graphRepository).createChunkVertex(childChunkId, ChunkType.CHILD, documentId);
    }

    @Test
    @DisplayName("handleDocumentIngested() parent chunk creates no edge")
    void handleDocumentIngested_parentChunk_noEdgeCreated() {
        // Given
        UUID documentId = UUID.randomUUID();
        UUID parentChunkId = UUID.randomUUID();

        List<ChunkGraphOperation> chunkOps = List.of(
                new ChunkGraphOperation(parentChunkId, ChunkType.PARENT, documentId, null)
        );

        DocumentIngestedEvent event = new DocumentIngestedEvent(
                documentId,
                "/path/to/doc.md",
                chunkOps,
                List.of()
        );

        // When
        listener.handleDocumentIngested(event);

        // Then
        verify(graphRepository, never()).createParentChildEdge(any(), any());
    }

    @Test
    @DisplayName("handleDocumentIngested() child chunk creates HAS_CHILD edge")
    void handleDocumentIngested_childChunk_createsHasChildEdge() {
        // Given
        UUID documentId = UUID.randomUUID();
        UUID parentChunkId = UUID.randomUUID();
        UUID childChunkId = UUID.randomUUID();

        List<ChunkGraphOperation> chunkOps = List.of(
                new ChunkGraphOperation(parentChunkId, ChunkType.PARENT, documentId, null),
                new ChunkGraphOperation(childChunkId, ChunkType.CHILD, documentId, parentChunkId)
        );

        DocumentIngestedEvent event = new DocumentIngestedEvent(
                documentId,
                "/path/to/doc.md",
                chunkOps,
                List.of()
        );

        // When
        listener.handleDocumentIngested(event);

        // Then
        verify(graphRepository).createParentChildEdge(parentChunkId, childChunkId);
    }

    @Test
    @DisplayName("handleDocumentIngested() with references creates REFERENCES edges")
    void handleDocumentIngested_withReferences_createsReferenceEdges() {
        // Given
        UUID documentId = UUID.randomUUID();
        UUID targetDocId1 = UUID.randomUUID();
        UUID targetDocId2 = UUID.randomUUID();

        List<ReferenceEdgeOperation> refOps = List.of(
                new ReferenceEdgeOperation(documentId, targetDocId1, "see also"),
                new ReferenceEdgeOperation(documentId, targetDocId2, "related")
        );

        DocumentIngestedEvent event = new DocumentIngestedEvent(
                documentId,
                "/path/to/doc.md",
                List.of(),
                refOps
        );

        // When
        listener.handleDocumentIngested(event);

        // Then
        verify(graphRepository).createReferenceEdge(documentId, targetDocId1, "see also");
        verify(graphRepository).createReferenceEdge(documentId, targetDocId2, "related");
    }

    @Test
    @DisplayName("handleDocumentIngested() graph error logs but does not rethrow")
    void handleDocumentIngested_graphError_logsButDoesNotRethrow() {
        // Given
        UUID documentId = UUID.randomUUID();
        DocumentIngestedEvent event = new DocumentIngestedEvent(
                documentId,
                "/path/to/doc.md",
                List.of(),
                List.of()
        );

        // Make graph operation fail
        doThrow(new RuntimeException("Graph connection failed"))
                .when(graphRepository).createDocumentVertex(any(), any());

        // When - should not throw
        listener.handleDocumentIngested(event);

        // Then - method completed without throwing
        verify(graphRepository).createDocumentVertex(documentId, "/path/to/doc.md");
    }

    @Test
    @DisplayName("handleDocumentIngested() creates operations in correct order")
    void handleDocumentIngested_createsOperationsInOrder() {
        // Given
        UUID documentId = UUID.randomUUID();
        UUID parentChunkId = UUID.randomUUID();
        UUID childChunkId = UUID.randomUUID();
        UUID targetDocId = UUID.randomUUID();

        List<ChunkGraphOperation> chunkOps = List.of(
                new ChunkGraphOperation(parentChunkId, ChunkType.PARENT, documentId, null),
                new ChunkGraphOperation(childChunkId, ChunkType.CHILD, documentId, parentChunkId)
        );
        List<ReferenceEdgeOperation> refOps = List.of(
                new ReferenceEdgeOperation(documentId, targetDocId, "link")
        );

        DocumentIngestedEvent event = new DocumentIngestedEvent(
                documentId,
                "/path/to/doc.md",
                chunkOps,
                refOps
        );

        // When
        listener.handleDocumentIngested(event);

        // Then - verify order: document vertex, chunk vertices, parent-child edges, reference edges
        InOrder inOrder = inOrder(graphRepository);
        inOrder.verify(graphRepository).createDocumentVertex(documentId, "/path/to/doc.md");
        inOrder.verify(graphRepository).createChunkVertex(parentChunkId, ChunkType.PARENT, documentId);
        inOrder.verify(graphRepository).createChunkVertex(childChunkId, ChunkType.CHILD, documentId);
        inOrder.verify(graphRepository).createParentChildEdge(parentChunkId, childChunkId);
        inOrder.verify(graphRepository).createReferenceEdge(documentId, targetDocId, "link");
    }
}
