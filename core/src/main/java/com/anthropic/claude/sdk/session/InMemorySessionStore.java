package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link SessionStore} for testing and ephemeral use cases.
 *
 * <p>Mirrors the Python SDK's {@code InMemorySessionStore} (194 LOC). Thread-safe via
 * {@link ConcurrentHashMap}; sessions are sorted by mtime descending when listed.
 */
public final class InMemorySessionStore implements SessionStore {

    /** projectKey → (sessionId → entries) */
    private final ConcurrentMap<String, ConcurrentMap<String, List<SessionStore.SessionStoreEntry>>> store = new ConcurrentHashMap<>();
    /** sessionId → mtime (epoch millis) */
    private final ConcurrentMap<String, Long> mtimes = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Void> append(SessionKey key, List<SessionStore.SessionStoreEntry> entries) {
        if (entries == null || entries.isEmpty()) return CompletableFuture.completedFuture(null);
        ConcurrentMap<String, List<SessionStore.SessionStoreEntry>> projectMap =
            store.computeIfAbsent(key.projectKey(), k -> new ConcurrentHashMap<>());
        projectMap.compute(key.sessionId(), (sid, existing) -> {
            List<SessionStore.SessionStoreEntry> list = existing != null ? existing : new ArrayList<>();
            list.addAll(entries);
            return list;
        });
        mtimes.put(key.sessionId(), Instant.now().toEpochMilli());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<SessionStore.SessionStoreEntry>> load(SessionKey key) {
        Map<String, List<SessionStore.SessionStoreEntry>> projectMap = store.get(key.projectKey());
        if (projectMap == null) return CompletableFuture.completedFuture(null);
        List<SessionStore.SessionStoreEntry> entries = projectMap.get(key.sessionId());
        return CompletableFuture.completedFuture(entries == null ? null : List.copyOf(entries));
    }

    @Override
    public CompletionStage<List<SessionStore.SessionStoreListEntry>> listSessions(String projectKey) {
        Map<String, List<SessionStore.SessionStoreEntry>> projectMap = store.get(projectKey);
        if (projectMap == null) return CompletableFuture.completedFuture(List.of());
        List<SessionStore.SessionStoreListEntry> result = new ArrayList<>();
        projectMap.forEach((sid, entries) -> {
            if (entries != null && !entries.isEmpty()) {
                result.add(new SessionStore.SessionStoreListEntry(sid, mtimes.getOrDefault(sid, 0L)));
            }
        });
        result.sort(Comparator.comparingLong(SessionStore.SessionStoreListEntry::mtime).reversed());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletionStage<Void> delete(SessionKey key) {
        Map<String, List<SessionStore.SessionStoreEntry>> projectMap = store.get(key.projectKey());
        if (projectMap != null) {
            projectMap.remove(key.sessionId());
            mtimes.remove(key.sessionId());
        }
        return CompletableFuture.completedFuture(null);
    }

    // ─── Inspection helpers (not part of SessionStore interface) ───────────

    public int sessionCount() {
        return (int) store.values().stream().mapToLong(Map::size).sum();
    }

    public int entryCount(String projectKey, String sessionId) {
        Map<String, List<SessionStore.SessionStoreEntry>> projectMap = store.get(projectKey);
        if (projectMap == null) return 0;
        List<SessionStore.SessionStoreEntry> entries = projectMap.get(sessionId);
        return entries == null ? 0 : entries.size();
    }

    public void clear() {
        store.clear();
        mtimes.clear();
    }
}
