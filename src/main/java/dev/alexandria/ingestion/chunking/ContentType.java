package dev.alexandria.ingestion.chunking;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Content type of a document chunk: either prose text or a code block.
 */
public enum ContentType {
    PROSE("prose"),
    CODE("code");

    private final String value;

    ContentType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ContentType fromValue(String value) {
        for (ContentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid content type: " + value);
    }

    /**
     * Parses a search filter value into a ContentType, treating "mixed" as null
     * (meaning no content_type filter). Case-insensitive.
     *
     * @param value the filter value (e.g. "prose", "CODE", "mixed", or null)
     * @return the matching ContentType, or null if value is null or "mixed"
     */
    public static ContentType parseSearchFilter(String value) {
        if (value == null || "mixed".equalsIgnoreCase(value)) {
            return null;
        }
        return fromValue(value);
    }
}
