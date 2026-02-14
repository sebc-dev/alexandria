package dev.alexandria;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlexandriaApplicationTest {
    @Test
    void applicationClassExists() {
        assertThat(AlexandriaApplication.class).isNotNull();
    }
}
