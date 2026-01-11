package dev.alexandria.core.exception;

import org.springframework.lang.NonNull;

/** Thrown when document ingestion fails. */
public class IngestionException extends AlexandriaException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an ingestion exception with a message.
   *
   * @param message the error message
   */
  public IngestionException(@NonNull final String message) {
    super(ErrorCategory.INGESTION_FAILED, message);
  }

  /**
   * Creates an ingestion exception with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public IngestionException(@NonNull final String message, @NonNull final Throwable cause) {
    super(ErrorCategory.INGESTION_FAILED, message, cause);
  }
}
