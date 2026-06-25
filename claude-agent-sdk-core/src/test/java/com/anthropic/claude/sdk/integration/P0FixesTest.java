package com.anthropic.claude.sdk.integration;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.session.InMemorySessionStore;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;
import com.anthropic.claude.sdk.types.ThinkingConfig;
import com.anthropic.claude.sdk.types.ThinkingDisplay;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end tests verifying Phase 19 P0 fixes. */
class P0FixesTest {

    private static Path fakeCli() {
        try {
            return Paths.get(P0FixesTest.class.getResource("/fake-claude.sh").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static int drain(Iterator<Message> iter) {
        int n = 0;
        while (iter.hasNext()) { iter.next(); n++; }
        return n;
    }

    @Test
    void sessionStoreRequiresResumeOrContinueOrFork() {
        InMemorySessionStore store = new InMemorySessionStore();
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli())
            .sessionStore(store)
            .build();
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts)) {
            assertThatThrownBy(client::connect)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionStore requires");
        }
    }

    @Test
    void sessionStoreWithContinueConnectes() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli())
            .sessionStore(store)
            .continueConversation(true)
            .build();
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts)) {
            client.connect();
            client.query("Hi");
            assertThat(drain(client.receiveResponse())).isEqualTo(2);
        }
    }

    @Test
    void sessionStoreWithResumeConnectes() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new SessionKey("proj", "old", null);
        store.append(key, List.of(
            new SessionStore.SessionStoreEntry("user", "u-1", "ts", null,
                Map.of("type", "user", "message", Map.of("role", "user", "content", "old")))
        )).toCompletableFuture().get();

        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli())
            .sessionStore(store)
            .resume("old")
            .build();
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts)) {
            client.connect();
            client.query("Continue");
            assertThat(drain(client.receiveResponse())).isEqualTo(2);
        }
    }

    @Test
    void thinkingDisplayEnabledIsAccepted() throws Exception {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli())
            .thinking(new ThinkingConfig.Enabled(8192, ThinkingDisplay.SUMMARIZED))
            .build();
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts)) {
            client.connect();
            client.query("Test");
            assertThat(drain(client.receiveResponse())).isEqualTo(2);
        }
    }

    @Test
    void thinkingDisplayAdaptiveIsAccepted() throws Exception {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli())
            .thinking(new ThinkingConfig.Adaptive(ThinkingDisplay.OMITTED))
            .build();
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts)) {
            client.connect();
            client.query("Test");
            assertThat(drain(client.receiveResponse())).isEqualTo(2);
        }
    }

    @Test
    void sessionResumeCleanupDeletesTempFile() throws Exception {
        var tempFile = Files.createTempFile("claude-resume-test-", ".jsonl");
        Files.writeString(tempFile, "{\"type\":\"user\"}\n");
        assertThat(Files.exists(tempFile)).isTrue();
        var mat = new com.anthropic.claude.sdk.session.SessionResume.MaterializedResume(
            ClaudeAgentOptions.builder().build(), tempFile, null);
        com.anthropic.claude.sdk.session.SessionResume.cleanup(mat);
        assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    void cleanupNullSafe() {
        com.anthropic.claude.sdk.session.SessionResume.cleanup(null);
        var matNoPath = new com.anthropic.claude.sdk.session.SessionResume.MaterializedResume(
            ClaudeAgentOptions.builder().build(), null, null);
        com.anthropic.claude.sdk.session.SessionResume.cleanup(matNoPath);
    }

    @Test
    void resumeForMissingSessionDoesNotThrow() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli())
            .sessionStore(store)
            .resume("never-existed")
            .build();
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts)) {
            client.connect();
            client.query("Test");
            assertThat(drain(client.receiveResponse())).isEqualTo(2);
        }
    }
}
