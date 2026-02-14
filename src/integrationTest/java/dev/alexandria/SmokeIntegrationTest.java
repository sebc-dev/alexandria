package dev.alexandria;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Requires PostgreSQL -- re-enabled in Plan 02 with Testcontainers")
@SpringBootTest
class SmokeIntegrationTest {
    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
