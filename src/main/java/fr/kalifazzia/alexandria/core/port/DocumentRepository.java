package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.model.Document;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for document persistence.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 */
public interface DocumentRepository {

    /**
     * Saves a new document to the repository.
     *
     * @param document Document to save (id may be null for new documents)
     * @return Saved document with generated id and timestamps
     */
    Document save(Document document);

    /**
     * Finds a document by its unique file path.
     *
     * @param path Path to the document file
     * @return Optional containing the document if found
     */
    Optional<Document> findByPath(String path);

    /**
     * Deletes a document by its id.
     *
     * @param id Document id to delete
     */
    void delete(UUID id);

    /**
     * Deletes a document by its file path.
     * Useful for upsert pattern when re-indexing a file.
     *
     * @param path Path to the document file
     */
    void deleteByPath(String path);

    /**
     * Finds documents by a collection of IDs.
     * Used for batch loading document metadata for graph-related results.
     *
     * @param ids Collection of document IDs to find
     * @return List of documents found (may be fewer than requested if some IDs don't exist)
     */
    List<Document> findByIds(Collection<UUID> ids);

    /**
     * Finds a document by its unique ID.
     *
     * @param id Document UUID
     * @return Optional containing the document if found
     */
    Optional<Document> findById(UUID id);

    /**
     * Returns all distinct categories from indexed documents.
     * Used by list_categories MCP tool.
     *
     * @return List of unique category names (excluding null values)
     */
    List<String> findDistinctCategories();

    /**
     * Counts total number of indexed documents.
     * Used by CLI status command.
     *
     * @return Number of documents in the repository
     */
    long count();

    /**
     * Finds the most recent update timestamp across all documents.
     * Used by CLI status command to show last indexation time.
     *
     * @return Optional containing the most recent updated_at, or empty if no documents
     */
    Optional<Instant> findLastUpdated();

    /**
     * Deletes all documents from the repository.
     * Used by CLI clear command for full re-indexation.
     */
    void deleteAll();
}
