package dev.alexandria.core.exception;

import org.springframework.lang.NonNull;

/** Thrown when document ingestion fails. */
public class IngestionException extends AlexandriaException {

  /**
   * Creates an ingestion exception with a message.
   *
   * @param message the error message
   */
  public IngestionException(@NonNull String message) {
    super(ErrorCategory.INGESTION_FAILED, message);
  }

  /**
   * Creates an ingestion exception with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public IngestionException(@NonNull String message, @NonNull Throwable cause) {
    super(ErrorCategory.INGESTION_FAILED, message, cause);
  }
}
