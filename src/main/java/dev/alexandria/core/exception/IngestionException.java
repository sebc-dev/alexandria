package dev.alexandria.core.exception;

import org.springframework.lang.NonNull;

/** Thrown when document ingestion fails. */
public class IngestionException extends AlexandriaException {

  public IngestionException(@NonNull String message) {
    super(ErrorCategory.INGESTION_FAILED, message);
  }

  public IngestionException(@NonNull String message, @NonNull Throwable cause) {
    super(ErrorCategory.INGESTION_FAILED, message, cause);
  }
}
