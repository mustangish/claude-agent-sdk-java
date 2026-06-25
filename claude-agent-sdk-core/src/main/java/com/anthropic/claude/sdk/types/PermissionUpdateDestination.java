package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Destination scope for a {@code PermissionUpdate}. */
public enum PermissionUpdateDestination {
    USER_SETTINGS("userSettings"),
    PROJECT_SETTINGS("projectSettings"),
    LOCAL_SETTINGS("localSettings"),
    SESSION("session");

    private final String wireValue;

    PermissionUpdateDestination(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static PermissionUpdateDestination fromWire(String value) {
        for (PermissionUpdateDestination d : values()) {
            if (d.wireValue.equals(value)) return d;
        }
        throw new IllegalArgumentException("Unknown PermissionUpdateDestination: " + value);
    }
}
