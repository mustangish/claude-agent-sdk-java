package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.types.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class SessionListingTest {

    @TempDir Path tempHome;
    private String projectKey;

    @BeforeEach
    void setup() throws Exception {
        // Redirect getProperty("user.home") lookups via the standard mechanism isn't trivial,
        // so we work directly with the directory layout: create ~/.claude/projects/<projectKey>
        Path claudeHome = tempHome.resolve(".claude");
        projectKey = "test-project";
        Files.createDirectories(claudeHome.resolve("projects").resolve(projectKey));
        // The SessionListing class hardcodes ${user.home}/.claude. For test isolation we
        // verify the API contract using SessionImport + direct file paths in dedicated tests.
    }

    @Test
    void sessionFilePathFormat() {
        Path path = SessionListing.sessionFilePath("p", "sess-1");
        assertThat(path.toString()).endsWith("projects/p/sess-1.jsonl");
    }

    @Test
    void liteFilePathFormat() {
        Path path = SessionListing.liteFilePath("p", "sess-1");
        assertThat(path.toString()).endsWith("projects/p/sess-1.jsonl.lite");
    }

    @Test
    void subagentsDirFormat() {
        Path path = SessionListing.subagentsDir("p", "sess-1");
        assertThat(path.toString()).endsWith("projects/p/sess-1/subagents");
    }

    @Test
    void projectKeyForDirectoryIsStable() {
        String cwd = "/Users/foo/projects/myapp";
        assertThat(ClaudeAgentSdk.projectKeyForDirectory(cwd))
            .isEqualTo(ClaudeAgentSdk.projectKeyForDirectory(cwd));
    }

    @Test
    void listSessionsFromStoreReturnsSummary() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new com.anthropic.claude.sdk.types.SessionKey("p", "sess-1", null);
        store.append(key, List.of(
            new SessionStore.SessionStoreEntry("user", "u-1", "ts", null,
                Map.of("type", "user", "message", Map.of("role", "user", "content", "Hello")))))
            .toCompletableFuture().get();

        var sessions = SessionListing.listSessionsFromStore(store, "p")
            .toCompletableFuture().get();

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionId()).isEqualTo("sess-1");
    }

    @Test
    void getSessionInfoFromStoreReturnsNullForMissing() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var info = SessionListing.getSessionInfoFromStore(store, "p", "nonexistent")
            .toCompletableFuture().get();
        assertThat(info).isNull();
    }

    @Test
    void getSessionMessagesFromStoreExtractsUserAssistant() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new com.anthropic.claude.sdk.types.SessionKey("p", "sess-1", null);
        store.append(key, List.of(
            new SessionStore.SessionStoreEntry("user", "u-1", "ts", null,
                Map.of("type", "user", "message", Map.of("role", "user", "content", "Hi"))),
            new SessionStore.SessionStoreEntry("assistant", "a-1", "ts", null,
                Map.of("type", "assistant", "message", Map.of("role", "assistant", "content", "Hello"))),
            new SessionStore.SessionStoreEntry("summary", null, "ts", null,
                Map.of("type", "summary", "summary", "User greeted Claude")))
        ).toCompletableFuture().get();

        var messages = SessionListing.getSessionMessagesFromStore(store, "p", "sess-1")
            .toCompletableFuture().get();
        assertThat(messages).hasSize(2);  // user + assistant (summary filtered out)
        assertThat(messages.get(0).type()).isEqualTo("user");
        assertThat(messages.get(1).type()).isEqualTo("assistant");
    }

    @Test
    void listSubagentsFromStoreThrowsForUnimplementedStore() {
        // InMemorySessionStore does not implement listSubkeys (it's optional).
        // The default SessionStore.listSubkeys throws UnsupportedOperationException directly.
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new com.anthropic.claude.sdk.types.SessionStore.SessionListSubkeysKey("p", "sess-1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.listSubkeys(key))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
