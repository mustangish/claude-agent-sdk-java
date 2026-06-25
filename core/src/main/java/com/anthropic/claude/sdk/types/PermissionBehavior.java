package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Behavior values in a {@code PermissionUpdate}. */
public enum PermissionBehavior {
    ALLOW("allow"),
    DENY("deny"),
    ASK("ask");

    private final String wireValue;

    PermissionBehavior(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static PermissionBehavior fromWire(String value) {
        for (PermissionBehavior b : values()) {
            if (b.wireValue.equals(value)) return b;
        }
        throw new IllegalArgumentException("Unknown PermissionBehavior: " + value);
    }
}
