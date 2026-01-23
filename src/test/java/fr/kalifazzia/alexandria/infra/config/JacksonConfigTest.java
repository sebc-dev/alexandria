package fr.kalifazzia.alexandria.infra.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JacksonConfig.
 * Tests ObjectMapper configuration for Java Time module and ISO-8601 formatting.
 */
class JacksonConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        JacksonConfig config = new JacksonConfig();
        objectMapper = config.objectMapper();
    }

    @Test
    void objectMapper_registersJavaTimeModule_canSerializeInstant() throws JsonProcessingException {
        Instant instant = Instant.parse("2026-01-20T10:30:00Z");

        String json = objectMapper.writeValueAsString(instant);

        assertThat(json).isNotNull();
        assertThat(json).contains("2026-01-20");
    }

    @Test
    void objectMapper_registersJavaTimeModule_canDeserializeInstant() throws JsonProcessingException {
        String json = "\"2026-01-20T10:30:00Z\"";

        Instant instant = objectMapper.readValue(json, Instant.class);

        assertThat(instant).isEqualTo(Instant.parse("2026-01-20T10:30:00Z"));
    }

    @Test
    void objectMapper_writesIso8601Dates_notTimestamps() throws JsonProcessingException {
        Instant instant = Instant.parse("2026-01-20T10:30:00Z");

        String json = objectMapper.writeValueAsString(instant);

        // ISO-8601 format contains 'T' separator, numeric timestamp does not
        assertThat(json).contains("T");
        // ISO-8601 format is a string, not a number
        assertThat(json).startsWith("\"");
        assertThat(json).endsWith("\"");
    }

    @Test
    void objectMapper_writesIso8601Dates_containsTimezoneIndicator() throws JsonProcessingException {
        Instant instant = Instant.parse("2026-01-20T10:30:00Z");

        String json = objectMapper.writeValueAsString(instant);

        // ISO-8601 UTC format ends with 'Z'
        assertThat(json).contains("Z");
    }

    @Test
    void objectMapper_returnsNonNullInstance() {
        assertThat(objectMapper).isNotNull();
    }

    @Test
    void objectMapper_canSerializeAndDeserializeDto() throws JsonProcessingException {
        record TestDto(String name, Instant createdAt) {}
        TestDto original = new TestDto("test", Instant.parse("2026-01-20T10:30:00Z"));

        String json = objectMapper.writeValueAsString(original);
        TestDto deserialized = objectMapper.readValue(json, TestDto.class);

        assertThat(deserialized.name()).isEqualTo(original.name());
        assertThat(deserialized.createdAt()).isEqualTo(original.createdAt());
    }
}
