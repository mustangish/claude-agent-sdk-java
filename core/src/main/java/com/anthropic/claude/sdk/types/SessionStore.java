package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Adapter for mirroring session transcripts to external storage.
 *
 * <p>The subprocess still writes to local disk; the adapter receives a secondary copy.
 * Only {@link #append} and {@link #load} are required; the remaining methods have default
 * implementations that throw {@link UnsupportedOperationException} — implementors override
 * the ones they support.
 *
 * <p>This mirrors the Python SDK's {@code SessionStore} Protocol with default NotImplementedError.
 */
public interface SessionStore {

    /** Mirror a batch of transcript entries. Called AFTER the subprocess's local write succeeds. */
    default CompletionStage<Void> append(SessionKey key, List<SessionStoreEntry> entries) {
        throw new UnsupportedOperationException("SessionStore.append not implemented");
    }

    /** Load a full session for resume. Return null for a key never written. */
    default CompletionStage<List<SessionStoreEntry>> load(SessionKey key) {
        throw new UnsupportedOperationException("SessionStore.load not implemented");
    }

    /** List session IDs for a project key, sorted by mtime descending. */
    default CompletionStage<List<SessionStoreListEntry>> listSessions(String projectKey) {
        throw new UnsupportedOperationException("SessionStore.listSessions not implemented");
    }

    default CompletionStage<List<SessionSummaryEntry>> listSessionSummaries(String projectKey) {
        throw new UnsupportedOperationException("SessionStore.listSessionSummaries not implemented");
    }

    default CompletionStage<Void> delete(SessionKey key) {
        throw new UnsupportedOperationException("SessionStore.delete not implemented");
    }

    default CompletionStage<List<String>> listSubkeys(SessionListSubkeysKey key) {
        throw new UnsupportedOperationException("SessionStore.listSubkeys not implemented");
    }

    static SessionStore noOp() {
        return new SessionStore() {};
    }

    // ─── Entry types ───────────────────────────────────────────────────────

    /** One JSONL transcript line. The {@code data} map contains all keys from the wire JSON. */
    record SessionStoreEntry(
        String type,
        String uuid,
        String timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) String parentToolUseId,
        Map<String, Object> data
    ) {
        public SessionStoreEntry(Map<String, Object> data) {
            this(
                (String) data.get("type"),
                (String) data.get("uuid"),
                (String) data.get("timestamp"),
                (String) data.get("parent_tool_use_id"),
                data
            );
        }
    }

    record SessionStoreListEntry(String sessionId, long mtime) {}

    record SessionSummaryEntry(String sessionId, long mtime, Map<String, Object> data) {}

    record SessionListSubkeysKey(String projectKey, String sessionId) {}

    static CompletableFuture<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }
}
