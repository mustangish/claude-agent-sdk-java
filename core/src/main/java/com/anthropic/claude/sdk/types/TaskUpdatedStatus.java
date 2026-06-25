package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Status reported in a {@code task_updated} message patch. */
public enum TaskUpdatedStatus {
    PENDING("pending"),
    RUNNING("running"),
    PAUSED("paused"),
    COMPLETED("completed"),
    FAILED("failed"),
    KILLED("killed");

    private final String wireValue;

    TaskUpdatedStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static TaskUpdatedStatus fromWire(String value) {
        for (TaskUpdatedStatus s : values()) {
            if (s.wireValue.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown TaskUpdatedStatus: " + value);
    }
}
