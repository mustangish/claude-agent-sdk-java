package com.anthropic.claude.sdk.types;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D-004: {@code TERMINAL_TASK_STATUSES} + {@code isTerminal()}. */
class TaskNotificationStatusTest {

    @Test
    void terminalStatuses() {
        assertThat(TaskNotificationStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TaskNotificationStatus.FAILED.isTerminal()).isTrue();
        assertThat(TaskNotificationStatus.STOPPED.isTerminal()).isTrue();
        assertThat(TaskNotificationStatus.KILLED.isTerminal()).isTrue();
    }

    @Test
    void terminalSetMatchesPython() {
        // Python: TERMINAL_TASK_STATUSES = frozenset({"completed", "failed", "stopped", "killed"})
        assertThat(TaskNotificationStatus.TERMINAL_TASK_STATUSES)
            .containsExactlyInAnyOrder(
                TaskNotificationStatus.COMPLETED,
                TaskNotificationStatus.FAILED,
                TaskNotificationStatus.STOPPED,
                TaskNotificationStatus.KILLED);
    }

    @Test
    void wireValueRoundTrip() {
        for (TaskNotificationStatus s : TaskNotificationStatus.values()) {
            assertThat(TaskNotificationStatus.fromWire(s.wireValue())).isEqualTo(s);
        }
    }
}
