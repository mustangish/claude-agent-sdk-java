package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Error categories reported in {@code AssistantMessage.error}. */
public enum AssistantMessageError {
    AUTHENTICATION_FAILED("authentication_failed"),
    BILLING_ERROR("billing_error"),
    RATE_LIMIT("rate_limit"),
    INVALID_REQUEST("invalid_request"),
    SERVER_ERROR("server_error"),
    UNKNOWN("unknown");

    private final String wireValue;

    AssistantMessageError(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static AssistantMessageError fromWire(String value) {
        for (AssistantMessageError e : values()) {
            if (e.wireValue.equals(value)) return e;
        }
        throw new IllegalArgumentException("Unknown AssistantMessageError: " + value);
    }
}
