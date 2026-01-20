package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.model.Document;

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
}
