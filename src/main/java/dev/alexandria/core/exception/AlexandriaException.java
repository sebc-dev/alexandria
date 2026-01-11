package dev.alexandria.core.exception;

import java.util.Objects;
import org.springframework.lang.NonNull;

/** Base exception for all Alexandria domain errors. */
public class AlexandriaException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCategory category;

  /**
   * Creates an exception with a category and message.
   *
   * @param category the error category
   * @param message the error message
   */
  public AlexandriaException(@NonNull final ErrorCategory category, @NonNull final String message) {
    super(message);
    this.category = Objects.requireNonNull(category, "category must not be null");
  }

  /**
   * Creates an exception with a category, message, and cause.
   *
   * @param category the error category
   * @param message the error message
   * @param cause the underlying cause
   */
  public AlexandriaException(
      @NonNull final ErrorCategory category,
      @NonNull final String message,
      @NonNull final Throwable cause) {
    super(message, cause);
    this.category = Objects.requireNonNull(category, "category must not be null");
  }

  public ErrorCategory getCategory() {
    return category;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    final AlexandriaException that = (AlexandriaException) other;
    return category == that.category && Objects.equals(getMessage(), that.getMessage());
  }

  @Override
  public int hashCode() {
    return Objects.hash(category, getMessage());
  }
}
