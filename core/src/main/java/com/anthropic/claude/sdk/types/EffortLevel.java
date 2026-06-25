package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Effort levels for Claude's reasoning depth. */
public enum EffortLevel {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    private final String wireValue;

    EffortLevel(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static EffortLevel fromWire(String value) {
        for (EffortLevel e : values()) {
            if (e.wireValue.equals(value)) return e;
        }
        throw new IllegalArgumentException("Unknown EffortLevel: " + value);
    }
}
