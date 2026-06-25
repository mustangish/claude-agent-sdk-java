package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the store-backed variants of SessionMutations.
 *
 * <p>Local-disk variants are tested indirectly via the {@link SessionListingTest}.
 */
class SessionMutationsTest {

    @Test
    void deleteSessionViaStoreRemovesEntries() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new SessionKey("p", "sess-1", null);
        store.append(key, List.of(
            new SessionStore.SessionStoreEntry("user", "u-1", "ts", null, Map.of("type", "user")))
        ).toCompletableFuture().get();
        assertThat(store.load(key).toCompletableFuture().get()).hasSize(1);

        SessionMutations.deleteSessionViaStore(store, "p", "sess-1")
            .toCompletableFuture().get();
        assertThat(store.load(key).toCompletableFuture().get()).isNull();
    }

    @Test
    void forkSessionViaStoreCopiesEntries() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var srcKey = new SessionKey("p", "old", null);
        var entries = List.of(
            new SessionStore.SessionStoreEntry("user", "u-1", "ts", null, Map.of("type", "user")),
            new SessionStore.SessionStoreEntry("assistant", "a-1", "ts", null, Map.of("type", "assistant"))
        );
        store.append(srcKey, entries).toCompletableFuture().get();

        String result = SessionMutations.forkSessionViaStore(store, "p", "old", "new")
            .toCompletableFuture().get()
            .sessionId();

        assertThat(result).isEqualTo("new");
        var forked = store.load(new SessionKey("p", "new", null)).toCompletableFuture().get();
        assertThat(forked).hasSize(2);
    }

    @Test
    void forkSessionViaStoreHandlesEmptySource() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        String result = SessionMutations.forkSessionViaStore(store, "p", "nonexistent", "new")
            .toCompletableFuture().get()
            .sessionId();
        assertThat(result).isEqualTo("new");
        var forked = store.load(new SessionKey("p", "new", null)).toCompletableFuture().get();
        assertThat(forked).isNull();  // never appended, load returns null
    }

    @Test
    void renameSessionViaStoreWritesMetadataEntry() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var key = new SessionKey("p", "my-session", null);
        store.append(key, List.of(
            new SessionStore.SessionStoreEntry("user", "u-1", "ts", null, Map.of("type", "user", "content", "data"))
        )).toCompletableFuture().get();

        // New behavior: metadata-only rename (no key change, appends a rename entry).
        SessionMutations.renameSessionViaStore(store, "p", "my-session", "new-title")
            .toCompletableFuture().get();

        var loaded = store.load(key).toCompletableFuture().get();
        assertThat(loaded).hasSize(2);
        var rename = loaded.get(1);
        assertThat(rename.type()).isEqualTo("rename");
        assertThat(rename.data()).containsEntry("new_title", "new-title");
    }

    @Test
    void cloneSessionViaStoreForksAndDeletes() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        var srcKey = new SessionKey("p", "old-name", null);
        store.append(srcKey, List.of(
            new SessionStore.SessionStoreEntry("user", "u-1", "ts", null, Map.of("type", "user", "content", "data"))
        )).toCompletableFuture().get();

        String result = SessionMutations.cloneSessionViaStore(store, "p", "old-name", "new-name")
            .toCompletableFuture().get();
        assertThat(result).isEqualTo("new-name");

        // Old is gone, new has the data
        assertThat(store.load(srcKey).toCompletableFuture().get()).isNull();
        var newLoaded = store.load(new SessionKey("p", "new-name", null)).toCompletableFuture().get();
        assertThat(newLoaded).hasSize(1);
    }

    @Test
    void tagSessionViaStoreIsNoop() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        // Tags are local-metadata only — store-backed variant is a no-op.
        SessionMutations.tagSessionViaStore(store, "p", "sess-1", "my-tag")
            .toCompletableFuture().get();
        // Nothing to verify — just shouldn't throw.
    }
}
