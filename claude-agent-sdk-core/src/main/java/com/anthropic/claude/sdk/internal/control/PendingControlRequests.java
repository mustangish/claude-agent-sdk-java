package com.anthropic.claude.sdk.internal.control;

import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Correlates control responses with their pending requests by {@code request_id}.
 *
 * <p>Simpler than the Python SDK's dict-based approach (which uses anyio.Event).
 */
public final class PendingControlRequests {

    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    /** Allocate a new request ID and register a placeholder future. Returns the ID. */
    public String register() {
        String id = UUID.randomUUID().toString();
        pending.put(id, new CompletableFuture<>());
        return id;
    }

    /** Register with a pre-chosen ID (useful for hook callbacks whose ID comes from CLI). */
    public void register(String id) {
        pending.put(id, new CompletableFuture<>());
    }

    /** Mark a request as successfully completed with the given response payload. */
    public void complete(String id, JsonNode response) {
        CompletableFuture<JsonNode> f = pending.get(id);
        if (f != null) {
            f.complete(response);
            // Don't remove: let await() do the removal in its finally block.
        }
    }

    /** Mark a request as failed with the given error message. */
    public void completeError(String id, String error) {
        CompletableFuture<JsonNode> f = pending.get(id);
        if (f != null) {
            f.completeExceptionally(new CliConnectionError(error));
        }
    }

    /** Block until the request completes or timeout elapses. */
    public JsonNode await(String id, long timeoutMs) throws Exception {
        CompletableFuture<JsonNode> f = pending.get(id);
        if (f == null) throw new IllegalStateException("Unknown request id: " + id);
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            pending.remove(id);
        }
    }

    /** Same as {@link #await} but throws unchecked on error. */
    public JsonNode awaitUnchecked(String id, long timeoutMs) {
        try {
            return await(id, timeoutMs);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Cancel all pending requests (typically on shutdown). */
    public void cancelAll() {
        for (Map.Entry<String, CompletableFuture<JsonNode>> entry : pending.entrySet()) {
            entry.getValue().completeExceptionally(
                new CliConnectionError("Control request cancelled: " + entry.getKey()));
        }
        // Don't clear: let await()'s finally block clean up.
    }

    public int pendingCount() {
        return pending.size();
    }
}
