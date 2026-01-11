package dev.alexandria.core.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AlexandriaExceptionTest {

    @Test
    void shouldStoreErrorCategory() {
        var exception = new AlexandriaException(ErrorCategory.VALIDATION, "Invalid query");

        assertThat(exception.getCategory()).isEqualTo(ErrorCategory.VALIDATION);
    }

    @Test
    void shouldStoreMessage() {
        var exception = new AlexandriaException(ErrorCategory.NOT_FOUND, "Document not found");

        assertThat(exception.getMessage()).isEqualTo("Document not found");
    }

    @Test
    void shouldStoreCause() {
        var cause = new RuntimeException("Root cause");
        var exception = new AlexandriaException(ErrorCategory.DATABASE_ERROR, "DB failed", cause);

        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void shouldRejectNullCategory() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AlexandriaException(null, "message"))
                .withMessage("category must not be null");
    }

    @Test
    void shouldRejectNullCategoryWithCause() {
        var cause = new RuntimeException("cause");
        assertThatNullPointerException()
                .isThrownBy(() -> new AlexandriaException(null, "message", cause))
                .withMessage("category must not be null");
    }

    @Test
    void equalsShouldReturnTrueForSameInstance() {
        var ex = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        assertThat(ex).isEqualTo(ex);
    }

    @Test
    void equalsShouldReturnFalseForNull() {
        var ex = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        assertThat(ex).isNotEqualTo(null);
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
        var ex = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        assertThat(ex).isNotEqualTo("not an exception");
    }

    @Test
    void equalsShouldReturnTrueForSameCategoryAndMessage() {
        var ex1 = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        var ex2 = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        assertThat(ex1).isEqualTo(ex2);
    }

    @Test
    void equalsShouldReturnFalseForDifferentCategory() {
        var ex1 = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        var ex2 = new AlexandriaException(ErrorCategory.NOT_FOUND, "msg");
        assertThat(ex1).isNotEqualTo(ex2);
    }

    @Test
    void equalsShouldReturnFalseForDifferentMessage() {
        var ex1 = new AlexandriaException(ErrorCategory.VALIDATION, "msg1");
        var ex2 = new AlexandriaException(ErrorCategory.VALIDATION, "msg2");
        assertThat(ex1).isNotEqualTo(ex2);
    }

    @Test
    void hashCodeShouldBeConsistentWithEquals() {
        var ex1 = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        var ex2 = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        assertThat(ex1.hashCode()).isEqualTo(ex2.hashCode());
    }

    @Test
    void hashCodeShouldDifferForDifferentExceptions() {
        var ex1 = new AlexandriaException(ErrorCategory.VALIDATION, "msg");
        var ex2 = new AlexandriaException(ErrorCategory.NOT_FOUND, "other");
        assertThat(ex1.hashCode()).isNotEqualTo(ex2.hashCode());
    }
}
