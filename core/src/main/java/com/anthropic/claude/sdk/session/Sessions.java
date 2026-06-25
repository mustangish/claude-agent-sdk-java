package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Listing helpers for sessions persisted via a {@link SessionStore}.
 */
public final class Sessions {

    private Sessions() {}

    /**
     * Project-key strategy: hash the cwd to a stable per-directory key. Mirrors Python's
     * {@code project_key_for_directory}.
     */
    public static String projectKeyForDirectory(String cwd) {
        if (cwd == null || cwd.isEmpty()) return "default";
        if (cwd.length() > 200) {
            long hash = djb2(cwd);
            return cwd.substring(0, 200) + "-" + Long.toHexString(hash);
        }
        return cwd;
    }

    private static long djb2(String s) {
        long hash = 5381;
        for (int i = 0; i < s.length(); i++) {
            hash = ((hash << 5) + hash) + s.charAt(i);
        }
        return hash;
    }

    /** List sessions for the given project_key, sorted by mtime descending. */
    public static CompletionStage<List<SessionStore.SessionStoreListEntry>> listSessions(
        SessionStore store, String projectKey
    ) {
        return store.listSessions(projectKey);
    }

    /** Adapter: convert raw entry list into a list of {@link SessionMessage}. */
    public static List<SessionMessage> extractMessages(
        String sessionId, List<SessionStore.SessionStoreEntry> entries
    ) {
        if (entries == null) return List.of();
        return entries.stream()
            .filter(e -> "user".equals(e.type()) || "assistant".equals(e.type()))
            .filter(e -> e.parentToolUseId() == null)
            .map(e -> {
                Object messageObj = e.data() != null ? e.data().get("message") : null;
                return new SessionMessage(e.type(), e.uuid(), sessionId, messageObj, null);
            })
            .<SessionMessage>map(msg -> msg)
            .toList();
    }

    /** Functional helper: load entries via the async SessionStore and project to messages. */
    public static CompletionStage<List<SessionMessage>> getSessionMessages(
        SessionStore store,
        String projectKey,
        String sessionId,
        Function<List<SessionStore.SessionStoreEntry>, List<SessionMessage>> mapper
    ) {
        var key = new SessionKey(projectKey, sessionId, null);
        return store.load(key).thenApply(entries ->
            entries == null ? List.of() : mapper.apply(entries));
    }
}
