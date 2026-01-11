package dev.alexandria.core.exception;

import java.util.Objects;

import org.springframework.lang.NonNull;

/**
 * Thrown when a requested document cannot be found.
 */
public class DocumentNotFoundException extends AlexandriaException {

    public DocumentNotFoundException(@NonNull String documentId) {
        super(ErrorCategory.NOT_FOUND, "Document not found: " +
                Objects.requireNonNull(documentId, "documentId must not be null"));
    }
}
