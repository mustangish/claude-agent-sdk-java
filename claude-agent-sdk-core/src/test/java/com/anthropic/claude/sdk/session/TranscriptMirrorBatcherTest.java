package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;
import com.anthropic.claude.sdk.types.SessionStoreFlushMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptMirrorBatcherTest {

    private static SessionStore.SessionStoreEntry entry(String type, String uuid) {
        return new SessionStore.SessionStoreEntry(type, uuid, "2026-06-25T00:00:00Z", null, Map.of("type", type));
    }

    @Test
    void enqueueThenFlushSendsToStore() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var batcher = new TranscriptMirrorBatcher(
            store, new SessionKey("p", "s", null), SessionStoreFlushMode.EAGER);
        batcher.enqueue(entry("user", "u-1"));
        batcher.enqueue(entry("assistant", "a-1"));

        // EAGER mode flushes immediately, but flushNow guarantees
        batcher.flushNow().toCompletableFuture().get();

        var loaded = store.load(new SessionKey("p", "s", null)).toCompletableFuture().get();
        assertThat(loaded).hasSize(2);
    }

    @Test
    void pendingCountReflectsQueue() {
        InMemorySessionStore store = new InMemorySessionStore();
        var batcher = new TranscriptMirrorBatcher(
            store, new SessionKey("p", "s", null), SessionStoreFlushMode.BATCHED);
        assertThat(batcher.pendingCount()).isEqualTo(0);
        batcher.enqueue(entry("user", "u-1"));
        batcher.enqueue(entry("user", "u-2"));
        assertThat(batcher.pendingCount()).isEqualTo(2);
    }

    @Test
    void closeFlushesRemaining() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var batcher = new TranscriptMirrorBatcher(
            store, new SessionKey("p", "s", null), SessionStoreFlushMode.BATCHED);
        batcher.enqueue(entry("user", "u-1"));
        batcher.close().toCompletableFuture().get();
        assertThat(batcher.pendingCount()).isEqualTo(0);
    }
}
