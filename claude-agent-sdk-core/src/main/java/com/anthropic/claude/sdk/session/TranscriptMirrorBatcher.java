package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionStore;
import com.anthropic.claude.sdk.types.SessionStoreFlushMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Buffers {@code transcript_mirror} frames and flushes them to a {@link SessionStore}.
 *
 * <p>Mirrors Python SDK's {@code TranscriptMirrorBatcher} (219 LOC). Supports batched and eager
 * flush modes; on append failure, retries up to 3 times with short backoff before surfacing
 * as a {@code MirrorErrorMessage} (non-fatal).
 */
public final class TranscriptMirrorBatcher {

    private static final int MAX_BATCH_SIZE = 500;
    private static final long MAX_BATCH_BYTES = 1024 * 1024;  // 1 MiB
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 50;

    private final SessionStore store;
    private final com.anthropic.claude.sdk.types.SessionKey key;
    private final SessionStoreFlushMode flushMode;
    private final LinkedBlockingDeque<SessionStore.SessionStoreEntry> pending = new LinkedBlockingDeque<>();
    private volatile long pendingBytes = 0;
    private volatile boolean closed = false;

    public TranscriptMirrorBatcher(SessionStore store, com.anthropic.claude.sdk.types.SessionKey key,
                                  SessionStoreFlushMode flushMode) {
        this.store = store;
        this.key = key;
        this.flushMode = flushMode != null ? flushMode : SessionStoreFlushMode.BATCHED;
    }

    /** Enqueue a frame for mirroring. Returns immediately; flushing is async. */
    public void enqueue(SessionStore.SessionStoreEntry entry) {
        if (closed || entry == null) return;
        pending.add(entry);
        pendingBytes += estimateSize(entry);
        if (flushMode == SessionStoreFlushMode.EAGER) flushNow();
        else if (pending.size() >= MAX_BATCH_SIZE || pendingBytes >= MAX_BATCH_BYTES) flushNow();
    }

    /** Flush pending entries to the store with retry. */
    public CompletionStage<Void> flushNow() {
        if (pending.isEmpty()) return CompletableFuture.completedFuture(null);
        List<SessionStore.SessionStoreEntry> batch = new ArrayList<>();
        SessionStore.SessionStoreEntry e;
        while ((e = pending.poll()) != null) {
            batch.add(e);
        }
        pendingBytes = 0;
        return appendWithRetry(batch, MAX_RETRIES);
    }

    private CompletionStage<Void> appendWithRetry(List<SessionStore.SessionStoreEntry> batch, int retriesLeft) {
        CompletionStage<Void> attempt = store.append(key, batch);
        if (retriesLeft <= 0) return attempt;
        return attempt.handle((v, ex) -> {
            if (ex == null) return CompletableFuture.<Void>completedFuture(null);
            try { Thread.sleep(RETRY_BACKOFF_MS); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return appendWithRetry(batch, retriesLeft - 1);
        }).thenCompose(f -> f);
    }

    /** Flush any pending entries and stop accepting new ones. */
    public CompletionStage<Void> close() {
        closed = true;
        return flushNow();
    }

    public int pendingCount() {
        return pending.size();
    }

    private static long estimateSize(SessionStore.SessionStoreEntry entry) {
        if (entry.data() == null) return 0;
        // Rough estimate: 100 bytes per entry (good enough for batch-size heuristic)
        return 100;
    }
}
