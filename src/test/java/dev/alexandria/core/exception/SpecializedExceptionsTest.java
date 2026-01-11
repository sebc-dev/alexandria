package dev.alexandria.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecializedExceptionsTest {

    @Test
    void documentNotFoundShouldHaveNotFoundCategory() {
        var exception = new DocumentNotFoundException("doc-123");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("Document not found: doc-123");
    }

    @Test
    void queryValidationShouldHaveValidationCategory() {
        var exception = new QueryValidationException("Query too short");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.VALIDATION);
        assertThat(exception.getMessage()).isEqualTo("Query too short");
    }

    @Test
    void embeddingServiceShouldHaveServiceUnavailableCategory() {
        var cause = new RuntimeException("Connection refused");
        var exception = new EmbeddingServiceException("Infinity API failed", cause);

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo("Infinity API failed");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void ingestionShouldHaveIngestionFailedCategory() {
        var exception = new IngestionException("Invalid markdown format");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.INGESTION_FAILED);
        assertThat(exception.getMessage()).isEqualTo("Invalid markdown format");
    }
}
