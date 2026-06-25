package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionStoreTest {

    private static SessionStore.SessionStoreEntry entry(String type, String uuid, Map<String, Object> data) {
        return new SessionStore.SessionStoreEntry(type, uuid, "2026-06-25T00:00:00Z", null, data);
    }

    @Test
    void appendAndLoadRoundTrip() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new SessionKey("proj-1", "sess-1", null);
        var entries = List.of(
            entry("user", "u-1", Map.of("type", "user", "message", Map.of("role", "user", "content", "Hi"))),
            entry("assistant", "a-1", Map.of("type", "assistant", "message", Map.of("role", "assistant", "content", "Hello!")))
        );
        store.append(key, entries).toCompletableFuture().get();

        var loaded = store.load(key).toCompletableFuture().get();
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).type()).isEqualTo("user");
        assertThat(loaded.get(1).type()).isEqualTo("assistant");
    }

    @Test
    void loadMissingKeyReturnsNull() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var loaded = store.load(new SessionKey("nope", "nope", null)).toCompletableFuture().get();
        assertThat(loaded).isNull();
    }

    @Test
    void listSessionsReturnsSortedByMtimeDescending() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        store.append(new SessionKey("p", "old", null),
            List.of(entry("user", "u-old", Map.of("type", "user")))).toCompletableFuture().get();
        Thread.sleep(5);
        store.append(new SessionKey("p", "new", null),
            List.of(entry("user", "u-new", Map.of("type", "user")))).toCompletableFuture().get();

        var list = store.listSessions("p").toCompletableFuture().get();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).sessionId()).isEqualTo("new");
        assertThat(list.get(1).sessionId()).isEqualTo("old");
    }

    @Test
    void deleteRemovesSession() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new SessionKey("p", "s", null);
        store.append(key, List.of(entry("user", "u", Map.of("type", "user")))).toCompletableFuture().get();
        store.delete(key).toCompletableFuture().get();
        assertThat(store.load(key).toCompletableFuture().get()).isNull();
        assertThat(store.sessionCount()).isEqualTo(0);
    }

    @Test
    void extractMessagesFiltersAndProjects() {
        var entries = List.of(
            entry("user", "u-1", Map.of("type", "user", "message", Map.of("role", "user", "content", "Hi"))),
            entry("assistant", "a-1", Map.of("type", "assistant", "message", Map.of("role", "assistant", "content", "Hello"))),
            new SessionStore.SessionStoreEntry("user", "u-2", "ts", "tool-1", Map.of("type", "user"))
        );
        var messages = Sessions.extractMessages("sess-1", entries);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).type()).isEqualTo("user");
        assertThat(messages.get(0).sessionId()).isEqualTo("sess-1");
        assertThat(messages.get(1).type()).isEqualTo("assistant");
    }

    @Test
    void projectKeyForDirectoryIsStable() {
        String cwd1 = "/Users/foo/projects/myapp";
        String cwd2 = "/Users/foo/projects/myapp";
        assertThat(Sessions.projectKeyForDirectory(cwd1))
            .isEqualTo(Sessions.projectKeyForDirectory(cwd2));

        String longCwd = "/Users/foo/" + "a".repeat(300);
        String truncated = Sessions.projectKeyForDirectory(longCwd);
        assertThat(truncated.length()).isLessThan(longCwd.length());
        assertThat(truncated).contains("-");
    }
}
