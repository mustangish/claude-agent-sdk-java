package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.internal.JacksonSupport;
import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;
import com.anthropic.claude.sdk.types.SessionStoreFlushMode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Import local-disk JSONL transcripts into a {@link SessionStore}.
 *
 * <p>Mirrors Python SDK's {@code _internal/session_import.py}. Walks a base directory for
 * {@code .jsonl} files and appends each line (after parsing) into the store under the
 * configured project key.
 */
public final class SessionImport {

    private static final ObjectMapper mapper = JacksonSupport.mapper();
    private static final int BATCH_SIZE = 100;

    private SessionImport() {}

    /**
     * Import all {@code .jsonl} files under {@code baseDir} into {@code store}.
     *
     * @return total number of entries imported
     */
    public static CompletionStage<Integer> importSessionToStore(
        Path baseDir,
        SessionStore store,
        String projectKey,
        String sessionId
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isDirectory(baseDir)) return 0;
            int total = 0;
            try {
                List<Path> jsonlFiles = new ArrayList<>();
                try (var stream = Files.walk(baseDir)) {
                    stream.filter(Files::isRegularFile)
                          .filter(p -> p.toString().endsWith(".jsonl"))
                          .forEach(jsonlFiles::add);
                }
                for (Path jsonl : jsonlFiles) {
                    total += appendJsonlFileInBatches(jsonl, store, projectKey, sessionId);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to import sessions from " + baseDir, e);
            }
            return total;
        });
    }

    private static int appendJsonlFileInBatches(Path jsonl, SessionStore store,
                                               String projectKey, String sessionId) {
        List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;
        try (var lines = Files.lines(jsonl)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = mapper.readValue(line, Map.class);
                    batch.add(data);
                } catch (Exception ignored) {}
                if (batch.size() >= BATCH_SIZE) {
                    total += flushBatch(store, projectKey, sessionId, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                total += flushBatch(store, projectKey, sessionId, batch);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + jsonl, e);
        }
        return total;
    }

    private static int flushBatch(SessionStore store, String projectKey, String sessionId,
                                  List<Map<String, Object>> batch) {
        var key = new SessionKey(projectKey, sessionId, null);
        try {
            store.append(key, batch.stream()
                .map(m -> new SessionStore.SessionStoreEntry(
                    (String) m.get("type"),
                    (String) m.get("uuid"),
                    (String) m.get("timestamp"),
                    (String) m.get("parent_tool_use_id"),
                    m))
                .toList())
                .toCompletableFuture().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush batch to store", e);
        }
        return batch.size();
    }

    /**
     * Convenience overload: import a single session file.
     */
    public static CompletionStage<Integer> importSessionFile(
        Path jsonlFile,
        SessionStore store,
        String projectKey,
        String sessionId
    ) {
        return CompletableFuture.supplyAsync(() ->
            appendJsonlFileInBatches(jsonlFile, store, projectKey, sessionId));
    }

    /** No-op stub for callers that want to mark a session as imported. */
    public static void markImported(String sessionId) {
        // Implementation detail: in production this would update a separate tracking file.
        // For MVP, import is fire-and-forget.
    }
}
