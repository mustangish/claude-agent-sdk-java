package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Controls whether thinking text is returned summarized or omitted. */
public enum ThinkingDisplay {
    SUMMARIZED("summarized"),
    OMITTED("omitted");

    private final String wireValue;

    ThinkingDisplay(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ThinkingDisplay fromWire(String value) {
        for (ThinkingDisplay d : values()) {
            if (d.wireValue.equals(value)) return d;
        }
        throw new IllegalArgumentException("Unknown ThinkingDisplay: " + value);
    }
}
