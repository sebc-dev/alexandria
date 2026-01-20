package fr.kalifazzia.alexandria.infra.persistence;

import fr.kalifazzia.alexandria.core.ingestion.DocumentIngestedEvent;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for document ingestion events and creates graph structures in Apache AGE.
 *
 * <p>Uses {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to ensure graph
 * operations only execute after PostgreSQL transactions have successfully committed.
 * This prevents data inconsistency where graph vertices exist without corresponding
 * PostgreSQL records.
 *
 * <p>If graph operations fail after PostgreSQL commit, the document remains in PostgreSQL
 * and can be recovered by re-ingesting (the content hash check will detect no change,
 * but a manual re-index can be triggered).
 */
@Component
public class GraphOperationsEventListener {

    private static final Logger log = LoggerFactory.getLogger(GraphOperationsEventListener.class);

    private final GraphRepository graphRepository;

    public GraphOperationsEventListener(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    /**
     * Creates graph vertices and edges after PostgreSQL transaction commits.
     *
     * @param event Contains document and chunk information for graph creation
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentIngested(DocumentIngestedEvent event) {
        log.debug("Processing graph operations for document: {}", event.documentId());

        try {
            // Create document vertex
            graphRepository.createDocumentVertex(event.documentId(), event.documentPath());

            // Create chunk vertices and parent-child edges
            for (DocumentIngestedEvent.ChunkGraphOperation op : event.chunkOperations()) {
                graphRepository.createChunkVertex(op.chunkId(), op.chunkType(), op.documentId());

                // Create HAS_CHILD edge if this is a child chunk
                if (op.parentChunkId() != null) {
                    graphRepository.createParentChildEdge(op.parentChunkId(), op.chunkId());
                }
            }

            // Create REFERENCES edges
            for (DocumentIngestedEvent.ReferenceEdgeOperation ref : event.referenceOperations()) {
                graphRepository.createReferenceEdge(ref.sourceDocId(), ref.targetDocId(), ref.linkText());
            }

            log.debug("Completed graph operations for document: {} ({} chunks, {} references)",
                    event.documentId(), event.chunkOperations().size(), event.referenceOperations().size());

        } catch (Exception e) {
            // Log error but don't rethrow - PostgreSQL data is already committed
            // Document can be recovered by re-ingesting with force flag
            log.error("Failed to create graph structures for document: {}. " +
                    "PostgreSQL data is intact. Re-ingest to recover graph data.",
                    event.documentId(), e);
        }
    }
}
