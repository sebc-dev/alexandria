package dev.alexandria.core.exception;

import org.springframework.lang.NonNull;

/** Thrown when the embedding service (Infinity API) is unavailable. */
public class EmbeddingServiceException extends AlexandriaException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an embedding service exception.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public EmbeddingServiceException(@NonNull final String message, @NonNull final Throwable cause) {
    super(ErrorCategory.SERVICE_UNAVAILABLE, message, cause);
  }
}
