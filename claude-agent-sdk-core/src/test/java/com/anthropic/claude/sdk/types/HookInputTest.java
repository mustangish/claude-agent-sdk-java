package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookInputTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preToolUseInputFields() throws Exception {
        var input = new HookInput.PreToolUse(
            "sess-1", "/path/to/transcript", "/cwd",
            "Bash", mapper.readTree("{\"command\":\"ls\"}"), "tu-1",
            null, null);
        assertThat(input.toolName()).isEqualTo("Bash");
        assertThat(input.toolUseId()).isEqualTo("tu-1");
    }

    @Test
    void stopInputHasStopHookActive() {
        var input = new HookInput.Stop("sess-1", "/path", "/cwd", true);
        assertThat(input.stopHookActive()).isTrue();
    }

    @Test
    void notificationInputHasTitle() {
        var input = new HookInput.Notification(
            "sess-1", "/path", "/cwd", "Build completed", "CI", "info");
        assertThat(input.message()).isEqualTo("Build completed");
        assertThat(input.notificationType()).isEqualTo("info");
    }

    @Test
    void hookSpecificOutputPreToolUseDeny() throws Exception {
        var out = new HookSpecificOutput.PreToolUse(
            "PreToolUse",
            "deny", "dangerous command",
            null, null);
        JsonNode node = mapper.valueToTree(out);
        assertThat(node.path("hookEventName").asText()).isEqualTo("PreToolUse");
        assertThat(node.path("permissionDecision").asText()).isEqualTo("deny");
    }
}
