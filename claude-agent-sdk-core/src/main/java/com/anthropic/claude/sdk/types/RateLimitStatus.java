package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Rate limit status reported by the CLI. */
public enum RateLimitStatus {
    ALLOWED("allowed"),
    ALLOWED_WARNING("allowed_warning"),
    REJECTED("rejected");

    private final String wireValue;

    RateLimitStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static RateLimitStatus fromWire(String value) {
        for (RateLimitStatus s : values()) {
            if (s.wireValue.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown RateLimitStatus: " + value);
    }
}
