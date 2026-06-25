package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Permission modes for tool execution. */
public enum PermissionMode {
    DEFAULT("default"),
    ACCEPT_EDITS("acceptEdits"),
    PLAN("plan"),
    BYPASS_PERMISSIONS("bypassPermissions"),
    DONT_ASK("dontAsk"),
    AUTO("auto");

    private final String wireValue;

    PermissionMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static PermissionMode fromWire(String value) {
        for (PermissionMode m : values()) {
            if (m.wireValue.equals(value)) return m;
        }
        throw new IllegalArgumentException("Unknown PermissionMode: " + value);
    }
}
