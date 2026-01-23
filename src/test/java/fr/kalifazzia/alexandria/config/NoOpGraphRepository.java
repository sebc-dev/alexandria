package fr.kalifazzia.alexandria.config;

import fr.kalifazzia.alexandria.core.port.GraphRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * No-op implementation of GraphRepository for tests.
 * Apache AGE is not available in the test container (pgvector/pgvector:pg17).
 */
@Repository
@Profile("test")
@Primary
public class NoOpGraphRepository implements GraphRepository {

    @Override
    public void createDocumentVertex(UUID documentId, String path) {
        // No-op: AGE not available in test container
    }

    @Override
    public void createChunkVertex(UUID chunkId, String chunkType, UUID documentId) {
        // No-op
    }

    @Override
    public void createParentChildEdge(UUID parentChunkId, UUID childChunkId) {
        // No-op
    }

    @Override
    public void deleteChunksByDocumentId(UUID documentId) {
        // No-op
    }

    @Override
    public void deleteDocumentGraph(UUID documentId) {
        // No-op
    }

    @Override
    public void createReferenceEdge(UUID sourceDocId, UUID targetDocId, String linkText) {
        // No-op
    }

    @Override
    public Set<UUID> findRelatedDocuments(UUID documentId, int maxHops) {
        return Set.of();
    }

    @Override
    public void clearAll() {
        // No-op
    }
}
