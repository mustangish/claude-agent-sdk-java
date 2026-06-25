package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** SDK beta features. See https://docs.anthropic.com/en/api/beta-headers. */
public enum SdkBeta {
    CONTEXT_1M("context-1m-2025-08-07");

    private final String wireValue;

    SdkBeta(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static SdkBeta fromWire(String value) {
        for (SdkBeta b : values()) {
            if (b.wireValue.equals(value)) return b;
        }
        throw new IllegalArgumentException("Unknown SdkBeta: " + value);
    }
}
