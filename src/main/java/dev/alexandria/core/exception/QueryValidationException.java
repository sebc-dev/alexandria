package dev.alexandria.core.exception;

import org.springframework.lang.NonNull;

/** Thrown when a query fails validation. */
public class QueryValidationException extends AlexandriaException {

  /**
   * Creates a query validation exception with a message.
   *
   * @param message the error message
   */
  public QueryValidationException(@NonNull String message) {
    super(ErrorCategory.VALIDATION, message);
  }

  /**
   * Creates a query validation exception with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public QueryValidationException(@NonNull String message, @NonNull Throwable cause) {
    super(ErrorCategory.VALIDATION, message, cause);
  }
}
