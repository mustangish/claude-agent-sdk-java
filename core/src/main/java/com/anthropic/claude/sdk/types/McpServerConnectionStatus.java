package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Connection status values for an MCP server. */
public enum McpServerConnectionStatus {
    CONNECTED("connected"),
    FAILED("failed"),
    NEEDS_AUTH("needs-auth"),
    PENDING("pending"),
    DISABLED("disabled");

    private final String wireValue;

    McpServerConnectionStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static McpServerConnectionStatus fromWire(String value) {
        for (McpServerConnectionStatus s : values()) {
            if (s.wireValue.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown McpServerConnectionStatus: " + value);
    }
}
