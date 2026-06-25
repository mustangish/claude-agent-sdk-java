package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Status reported in a {@code task_notification} message. */
public enum TaskNotificationStatus {
    COMPLETED("completed"),
    FAILED("failed"),
    STOPPED("stopped");

    private final String wireValue;

    TaskNotificationStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static TaskNotificationStatus fromWire(String value) {
        for (TaskNotificationStatus s : values()) {
            if (s.wireValue.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown TaskNotificationStatus: " + value);
    }
}
