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
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid content type: " + value);
    }
}
