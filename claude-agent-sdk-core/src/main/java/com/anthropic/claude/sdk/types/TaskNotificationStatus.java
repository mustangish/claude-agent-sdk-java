package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;

/**
 * Status reported in a {@code task_notification} message.
 *
 * <p>The set {@link #TERMINAL_TASK_STATUSES} mirrors the Python SDK's
 * {@code TERMINAL_TASK_STATUSES} frozenset. Use {@link #isTerminal()} to
 * test whether a notification represents a finished task.
 */
public enum TaskNotificationStatus {
    COMPLETED("completed"),
    FAILED("failed"),
    STOPPED("stopped"),
    KILLED("killed");

    /**
     * The four wire values that mark a task as finished (matching
     * Python's {@code TERMINAL_TASK_STATUSES = frozenset({"completed",
     * "failed", "stopped", "killed"})}).
     */
    public static final Set<TaskNotificationStatus> TERMINAL_TASK_STATUSES =
        Set.of(COMPLETED, FAILED, STOPPED, KILLED);

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

    /** True if this status represents a finished task. */
    public boolean isTerminal() {
        return TERMINAL_TASK_STATUSES.contains(this);
    }
}
