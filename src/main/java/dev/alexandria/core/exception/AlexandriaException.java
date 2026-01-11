package dev.alexandria.core.exception;

import java.util.Objects;

import org.springframework.lang.NonNull;

/**
 * Base exception for all Alexandria domain errors.
 */
public class AlexandriaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCategory category;

    public AlexandriaException(@NonNull ErrorCategory category, @NonNull String message) {
        super(message);
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    public AlexandriaException(
            @NonNull ErrorCategory category, @NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    public ErrorCategory getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlexandriaException that = (AlexandriaException) o;
        return category == that.category && Objects.equals(getMessage(), that.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, getMessage());
    }
}
