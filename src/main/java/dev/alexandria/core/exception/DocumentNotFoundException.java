package dev.alexandria.core.exception;

import java.util.Objects;
import org.springframework.lang.NonNull;

/** Thrown when a requested document cannot be found. */
public class DocumentNotFoundException extends AlexandriaException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a document not found exception.
   *
   * @param documentId the ID of the document that was not found
   */
  public DocumentNotFoundException(@NonNull final String documentId) {
    super(
        ErrorCategory.NOT_FOUND,
        "Document not found: " + Objects.requireNonNull(documentId, "documentId must not be null"));
  }
}
