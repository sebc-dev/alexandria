package dev.alexandria.core.exception;

import org.springframework.lang.NonNull;

/**
 * Thrown when a query fails validation.
 */
public class QueryValidationException extends AlexandriaException {

    public QueryValidationException(@NonNull String message) {
        super(ErrorCategory.VALIDATION, message);
    }

    public QueryValidationException(@NonNull String message, @NonNull Throwable cause) {
        super(ErrorCategory.VALIDATION, message, cause);
    }
}
