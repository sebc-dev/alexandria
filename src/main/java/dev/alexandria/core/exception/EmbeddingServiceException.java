package dev.alexandria.core.exception;

import org.springframework.lang.NonNull;

/**
 * Thrown when the embedding service (Infinity API) is unavailable.
 */
public class EmbeddingServiceException extends AlexandriaException {

    public EmbeddingServiceException(@NonNull String message, @NonNull Throwable cause) {
        super(ErrorCategory.SERVICE_UNAVAILABLE, message, cause);
    }
}
