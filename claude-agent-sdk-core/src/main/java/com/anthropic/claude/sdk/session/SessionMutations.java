package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.types.SessionStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Session mutations: rename, tag, delete, fork.
 *
 * <p>Mirrors Python SDK's {@code _internal/session_mutations.py}. Both local-disk and store-backed
 * variants are exposed as static methods. Local-disk variants operate on the JSONL files under
 * {@code ~/.claude/projects/<projectKey>/}.
 */
public final class SessionMutations {

    private SessionMutations() {}

    // ─── Local-disk variants ────────────────────────────────────────────────

    /** Rename a session by moving its JSONL file. */
    public static CompletionStage<Void> renameSession(String sessionId, String newTitle, String projectKey) {
        return renameSession(sessionId, newTitle, projectKey, null);
    }

    public static CompletionStage<Void> renameSession(String sessionId, String newTitle,
                                                     String projectKey, String cwdOverride) {
        return CompletableFuture.runAsync(() -> {
            Path src = sessionFileFor(projectKey, sessionId);
            Path dst = sessionFileFor(projectKey, sanitizeForFilename(newTitle));
            try {
                Files.move(src, dst);
            } catch (IOException e) {
                throw new RuntimeException("Failed to rename session " + sessionId, e);
            }
        });
    }

    /** Tag a session by writing/rewriting its lite file. */
    public static CompletionStage<Void> tagSession(String sessionId, String tag, String projectKey) {
        return CompletableFuture.runAsync(() -> {
            Path lite = SessionListing.liteFilePath(projectKey, sessionId);
            try {
                String existing = Files.exists(lite) ? Files.readString(lite) : "{}";
                var node = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(existing);
                ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("tag", tag);
                Files.writeString(lite, node.toString());
            } catch (IOException e) {
                throw new RuntimeException("Failed to tag session " + sessionId, e);
            }
        });
    }

    /** Delete a session's transcript file. */
    public static CompletionStage<Void> deleteSession(String sessionId, String projectKey) {
        return CompletableFuture.runAsync(() -> {
            Path file = sessionFileFor(projectKey, sessionId);
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(SessionListing.liteFilePath(projectKey, sessionId));
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete session " + sessionId, e);
            }
        });
    }

    /** Fork a session by copying the transcript to a new session_id. */
    public static CompletionStage<ForkSessionResult> forkSession(String sessionId, String newSessionId,
                                                                  String projectKey) {
        return forkSession(sessionId, newSessionId, projectKey, null, null);
    }

    /**
     * Fork a session with optional truncation and title.
     *
     * <p>Mirrors Python's {@code fork_session(session_id, directory=None,
     * up_to_message_id=None, title=None)}. If {@code upToMessageId} is
     * non-null, only entries up to and including that message are copied.
     * If {@code title} is non-null, the destination file is renamed to a
     * sanitized version of the title.
     */
    public static CompletionStage<ForkSessionResult> forkSession(String sessionId, String newSessionId,
                                                                  String projectKey,
                                                                  String upToMessageId,
                                                                  String title) {
        return CompletableFuture.supplyAsync(() -> {
            Path src = sessionFileFor(projectKey, sessionId);
            Path dst = sessionFileFor(projectKey, newSessionId);
            try {
                if (upToMessageId == null) {
                    Files.copy(src, dst);
                } else {
                    truncateCopy(src, dst, upToMessageId);
                }
                if (title != null) {
                    Path renamed = sessionFileFor(projectKey, sanitizeForFilename(title));
                    Files.move(dst, renamed, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return new ForkSessionResult(title != null
                    ? sanitizeForFilename(title) : newSessionId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fork session " + sessionId, e);
            }
        });
    }

    /**
     * Copy entries from {@code src} to {@code dst}, stopping at the JSONL
     * line whose {@code uuid} matches {@code upToMessageId} (inclusive).
     */
    private static void truncateCopy(Path src, Path dst, String upToMessageId) throws IOException {
        try (var reader = Files.newBufferedReader(src);
             var writer = Files.newBufferedWriter(dst)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                if (line.contains("\"uuid\":\"" + upToMessageId + "\"")) {
                    break;
                }
            }
        }
    }

    // ─── Store-backed variants (existing) ──────────────────────────────────

    /** Local disk root for session transcripts (mirrors Python's CLAUDE_CONFIG_DIR/sessions). */
    public static Path defaultSessionsRoot() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".claude", "sessions");
    }

    /** Rename a session by moving its JSONL file. */
    public static CompletionStage<Void> rename(String sessionId, String newTitle) {
        return CompletableFuture.runAsync(() -> {
            Path src = defaultSessionsRoot().resolve(sessionId + ".jsonl");
            Path dst = defaultSessionsRoot().resolve(newTitle + ".jsonl");
            try {
                Files.move(src, dst);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to rename session " + sessionId, e);
            }
        });
    }

    /** Tag a session by writing a tag file alongside it. */
    public static CompletionStage<Void> tag(String sessionId, String tag) {
        return CompletableFuture.runAsync(() -> {
            Path tagFile = defaultSessionsRoot().resolve(sessionId + ".tag");
            try {
                Files.writeString(tagFile, tag);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to tag session " + sessionId, e);
            }
        });
    }

    /** Delete a session's transcript file. */
    public static CompletionStage<Void> delete(String sessionId) {
        return CompletableFuture.runAsync(() -> {
            Path file = defaultSessionsRoot().resolve(sessionId + ".jsonl");
            try {
                Files.deleteIfExists(file);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to delete session " + sessionId, e);
            }
        });
    }

    /**
     * Fork a session by copying the transcript to a new session_id.
     * Returns the new session_id.
     */
    public static CompletionStage<ForkSessionResult> fork(String sessionId, String newSessionId) {
        return CompletableFuture.supplyAsync(() -> {
            Path src = defaultSessionsRoot().resolve(sessionId + ".jsonl");
            Path dst = defaultSessionsRoot().resolve(newSessionId + ".jsonl");
            try {
                Files.copy(src, dst);
                return new ForkSessionResult(newSessionId);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to fork session " + sessionId, e);
            }
        });
    }

    // ─── Store-backed variants ───────────────────────────────────────────────

    /** Delete via SessionStore (delegates to {@link SessionStore#delete}). */
    public static CompletionStage<Void> deleteSessionViaStore(SessionStore store,
                                                            String projectKey,
                                                            String sessionId) {
        var key = new com.anthropic.claude.sdk.types.SessionKey(projectKey, sessionId, null);
        return store.delete(key);
    }

    /** Fork via SessionStore — copy entries to a new key. */
    public static CompletionStage<ForkSessionResult> forkSessionViaStore(SessionStore store,
                                                                          String projectKey,
                                                                          String oldSessionId,
                                                                          String newSessionId) {
        return forkSessionViaStore(store, projectKey, oldSessionId, newSessionId, null, null);
    }

    /**
     * Fork via SessionStore with optional truncation and title.
     *
     * <p>Mirrors Python's {@code fork_session_via_store}. If
     * {@code upToMessageId} is non-null, only entries up to and including
     * that message are copied. The {@code title} parameter is currently
     * accepted for API parity; store-backed metadata-title support is
     * tracked separately.
     */
    public static CompletionStage<ForkSessionResult> forkSessionViaStore(SessionStore store,
                                                                          String projectKey,
                                                                          String oldSessionId,
                                                                          String newSessionId,
                                                                          String upToMessageId,
                                                                          String title) {
        var oldKey = new com.anthropic.claude.sdk.types.SessionKey(projectKey, oldSessionId, null);
        return store.load(oldKey).thenCompose(entries -> {
            if (entries == null || entries.isEmpty()) {
                return CompletableFuture.completedFuture(new ForkSessionResult(newSessionId));
            }
            List<com.anthropic.claude.sdk.types.SessionStore.SessionStoreEntry> toCopy = entries;
            if (upToMessageId != null) {
                toCopy = new ArrayList<>();
                for (var e : entries) {
                    toCopy.add(e);
                    if (upToMessageId.equals(e.uuid())) break;
                }
            }
            var newKey = new com.anthropic.claude.sdk.types.SessionKey(projectKey, newSessionId, null);
            return store.append(newKey, toCopy)
                .thenApply(v -> new ForkSessionResult(newSessionId));
        });
    }

    /**
     * Rename a session via SessionStore by appending a metadata title entry.
     *
     * <p>Mirrors Python's {@code rename_session_via_store}: writes a
     * {@code rename} entry to the store under the same key, leaving the
     * session id unchanged. For the copy-and-delete variant, see
     * {@link #cloneSessionViaStore}.
     */
    public static CompletionStage<Void> renameSessionViaStore(SessionStore store,
                                                              String projectKey,
                                                              String sessionId,
                                                              String newTitle) {
        var key = new com.anthropic.claude.sdk.types.SessionKey(projectKey, sessionId, null);
        var entry = new com.anthropic.claude.sdk.types.SessionStore.SessionStoreEntry(
            "rename", null, java.time.Instant.now().toString(), null,
            java.util.Map.of("new_title", newTitle == null ? "" : newTitle));
        return store.append(key, java.util.List.of(entry));
    }

    /**
     * Clone a session via SessionStore by copying entries to a new key and
     * deleting the source.
     *
     * <p>This is the old {@code renameSessionViaStore} behavior (copy +
     * delete). Renamed for clarity — it does not rename, it clones. Use
     * {@link #renameSessionViaStore} for a metadata-only rename.
     */
    public static CompletionStage<String> cloneSessionViaStore(SessionStore store,
                                                                String projectKey,
                                                                String oldSessionId,
                                                                String newSessionId) {
        return forkSessionViaStore(store, projectKey, oldSessionId, newSessionId)
            .thenCompose(v -> deleteSessionViaStore(store, projectKey, oldSessionId))
            .thenApply(v -> newSessionId);
    }

    /**
     * Tag a session via SessionStore by appending a metadata tag entry.
     *
     * <p>Mirrors Python's {@code tag_session_via_store}: writes a {@code tag}
     * entry to the store under the same key.
     */
    public static CompletionStage<Void> tagSessionViaStore(SessionStore store,
                                                          String projectKey,
                                                          String sessionId,
                                                          String tag) {
        var key = new com.anthropic.claude.sdk.types.SessionKey(projectKey, sessionId, null);
        var entry = new com.anthropic.claude.sdk.types.SessionStore.SessionStoreEntry(
            "tag", null, java.time.Instant.now().toString(), null,
            java.util.Map.of("tag", tag == null ? "" : tag));
        return store.append(key, java.util.List.of(entry));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private static Path sessionFileFor(String projectKey, String sessionId) {
        return SessionListing.sessionFilePath(projectKey, sessionId);
    }

    private static String sanitizeForFilename(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }
}
