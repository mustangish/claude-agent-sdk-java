package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Controls when transcript-mirror entries are flushed to a {@code SessionStore}. */
public enum SessionStoreFlushMode {
    BATCHED("batched"),
    EAGER("eager");

    private final String wireValue;

    SessionStoreFlushMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static SessionStoreFlushMode fromWire(String value) {
        for (SessionStoreFlushMode m : values()) {
            if (m.wireValue.equals(value)) return m;
        }
        throw new IllegalArgumentException("Unknown SessionStoreFlushMode: " + value);
    }
}
