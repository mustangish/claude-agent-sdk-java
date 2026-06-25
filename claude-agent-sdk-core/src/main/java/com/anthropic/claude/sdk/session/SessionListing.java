package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.internal.JacksonSupport;
import com.anthropic.claude.sdk.types.SessionKey;
import com.anthropic.claude.sdk.types.SessionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Session listing API — list/get/parse session metadata and messages.
 *
 * <p>Mirrors Python SDK's {@code _internal/sessions.py} (1925 LOC). Operates on local-disk
 * JSONL transcripts under {@code ~/.claude/projects/<project_key>/} by default, or on a
 * {@link SessionStore} for the {@code _from_store} variants.
 *
 * <p>Each local session file has two parts:
 * <ul>
 *   <li><b>Main JSONL</b>: {@code <sessionId>.jsonl} — full transcript entries.</li>
 *   <li><b>Lite file</b>: {@code <sessionId>.jsonl.lite} — cached metadata (title, summary,
 *       git branch, cwd, etc.) for fast listing without parsing the full transcript.</li>
 * </ul>
 */
public final class SessionListing {

    private static final ObjectMapper mapper = JacksonSupport.mapper();

    private SessionListing() {}

    // ─── Project layout ──────────────────────────────────────────────────────

    /** Root for all session data: {@code ~/.claude}. */
    public static Path getClaudeConfigHome() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".claude");
    }

    /** {@code ~/.claude/projects/<projectKey>}. */
    public static Path getProjectDir(String projectKey) {
        return getClaudeConfigHome().resolve("projects").resolve(projectKey);
    }

    /** {@code <projectDir>/<sessionId>.jsonl}. */
    public static Path sessionFilePath(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve(sessionId + ".jsonl");
    }

    /** {@code <projectDir>/<sessionId>.jsonl.lite} — cached metadata. */
    public static Path liteFilePath(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve(sessionId + ".jsonl.lite");
    }

    /** {@code <projectDir>/<sessionId>/subagents/} — subagent transcripts. */
    public static Path subagentsDir(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve(sessionId).resolve("subagents");
    }

    // ─── List ───────────────────────────────────────────────────────────────

    /** List sessions in the project, sorted by mtime descending. */
    public static List<SDKSessionInfo> listSessions(String projectKey) {
        return listSessions(projectKey, null, 0);
    }

    /**
     * List sessions with optional limit and offset.
     *
     * @param projectKey project key (usually {@link Sessions#projectKeyForDirectory(String)})
     * @param limit max number to return (null = all)
     * @param offset skip first N entries
     */
    public static List<SDKSessionInfo> listSessions(String projectKey, Integer limit, int offset) {
        Path dir = getProjectDir(projectKey);
        if (!Files.isDirectory(dir)) return List.of();
        List<SDKSessionInfo> sessions = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!file.getFileName().toString().endsWith(".jsonl")) continue;
                String sessionId = file.getFileName().toString().replace(".jsonl", "");
                if (sessionId.endsWith(".lite")) continue;  // skip .jsonl.lite files
                SDKSessionInfo info = readSessionInfo(projectKey, sessionId);
                if (info != null) sessions.add(info);
            }
        } catch (IOException e) {
            return List.of();
        }
        sessions.sort(Comparator.comparingLong(SDKSessionInfo::lastModified).reversed());
        int start = Math.min(offset, sessions.size());
        int end = limit != null ? Math.min(start + limit, sessions.size()) : sessions.size();
        return sessions.subList(start, end);
    }

    // ─── Get one ────────────────────────────────────────────────────────────

    /**
     * Read a single session's metadata. Reads the lite file if present, otherwise parses the
     * first few lines of the JSONL to extract title/summary.
     */
    public static SDKSessionInfo readSessionInfo(String projectKey, String sessionId) {
        Path lite = liteFilePath(projectKey, sessionId);
        Path main = sessionFilePath(projectKey, sessionId);
        try {
            long mtime = Files.getLastModifiedTime(main).toMillis();
            long fileSize = Files.size(main);
            if (Files.exists(lite)) {
                SDKSessionInfo fromLite = parseLite(Files.readString(lite), mtime, fileSize);
                if (fromLite != null) return fromLite;
            }
            // Fallback: peek first 20 lines of JSONL
            return peekSessionInfo(main, mtime, fileSize);
        } catch (IOException e) {
            return null;
        }
    }

    private static SDKSessionInfo parseLite(String json, long mtime, long fileSize) {
        try {
            JsonNode node = mapper.readTree(json);
            return new SDKSessionInfo(
                node.path("sessionId").asText(),
                node.path("summary").asText(""),
                mtime,
                fileSize,
                node.path("customTitle").asText(null),
                node.path("firstPrompt").asText(null),
                node.path("gitBranch").asText(null),
                node.path("cwd").asText(null),
                node.path("tag").asText(null),
                node.path("createdAt").asLong(0)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static SDKSessionInfo peekSessionInfo(Path file, long mtime, long fileSize) {
        try (var lines = Files.lines(file).limit(20)) {
            String firstPrompt = null;
            String gitBranch = null;
            String cwd = null;
            String summary = null;
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                try {
                    JsonNode node = mapper.readTree(line);
                    if (firstPrompt == null && node.has("message")) {
                        JsonNode content = node.path("message").path("content");
                        if (content.isTextual()) firstPrompt = content.asText();
                        else if (content.isArray() && content.size() > 0)
                            firstPrompt = content.get(0).path("text").asText(null);
                    }
                    if (gitBranch == null) gitBranch = node.path("gitBranch").asText(null);
                    if (cwd == null) cwd = node.path("cwd").asText(null);
                    if (summary == null && node.has("summary"))
                        summary = node.path("summary").asText(null);
                } catch (Exception ignored) {}
            }
            String sessionId = file.getFileName().toString().replace(".jsonl", "");
            return new SDKSessionInfo(
                sessionId, summary != null ? summary : "",
                mtime, fileSize,
                null, firstPrompt, gitBranch, cwd, null, 0L);
        } catch (IOException e) {
            return null;
        }
    }

    // ─── Messages ───────────────────────────────────────────────────────────

    /** Read all messages from a session's JSONL transcript. */
    public static List<SessionMessage> getSessionMessages(String projectKey, String sessionId) {
        Path file = sessionFilePath(projectKey, sessionId);
        if (!Files.exists(file)) return List.of();
        return readTranscript(file, sessionId);
    }

    private static List<SessionMessage> readTranscript(Path file, String sessionId) {
        List<SessionMessage> result = new ArrayList<>();
        try (var lines = Files.lines(file)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                try {
                    JsonNode node = mapper.readTree(line);
                    String type = node.path("type").asText();
                    if (!"user".equals(type) && !"assistant".equals(type)) continue;
                    if (node.has("parent_tool_use_id") && !node.path("parent_tool_use_id").isNull()) continue;
                    Object message = node.has("message") ? node.get("message") : null;
                    result.add(new SessionMessage(
                        type,
                        node.path("uuid").asText(null),
                        sessionId,
                        message,
                        null
                    ));
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            return List.of();
        }
        return result;
    }

    // ─── Subagents ──────────────────────────────────────────────────────────

    /** List subagents for a session (by reading subagents/*.jsonl file names). */
    public static List<String> listSubagents(String projectKey, String sessionId) {
        Path dir = subagentsDir(projectKey, sessionId);
        if (!Files.isDirectory(dir)) return List.of();
        List<String> agents = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (name.endsWith(".jsonl")) {
                    agents.add(name.replace(".jsonl", ""));
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        agents.sort(Comparator.naturalOrder());
        return agents;
    }

    /** Read messages from a subagent's JSONL transcript. */
    public static List<SessionMessage> getSubagentMessages(
        String projectKey, String sessionId, String agentId
    ) {
        Path file = subagentsDir(projectKey, sessionId).resolve(agentId + ".jsonl");
        if (!Files.exists(file)) return List.of();
        return readTranscript(file, sessionId + ":" + agentId);
    }

    // ─── Store-backed variants ───────────────────────────────────────────────

    /** List sessions via {@link SessionStore#listSessions(String)}. */
    public static CompletionStage<List<SDKSessionInfo>> listSessionsFromStore(
        SessionStore store, String projectKey
    ) {
        return store.listSessions(projectKey).thenApply(entries ->
            entries.stream()
                .map(e -> new SDKSessionInfo(
                    e.sessionId(), "", e.mtime(), null,
                    null, null, null, null, null, e.mtime()))
                .sorted(Comparator.comparingLong(SDKSessionInfo::lastModified).reversed())
                .toList());
    }

    /** Get session info via {@link SessionStore#load(SessionKey)}. */
    public static CompletionStage<SDKSessionInfo> getSessionInfoFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        var key = new SessionKey(projectKey, sessionId, null);
        return store.load(key).thenApply(entries ->
            entries == null ? null : new SDKSessionInfo(
                sessionId, "", System.currentTimeMillis(), null,
                null, null, null, null, null, 0L));
    }

    /** Get session messages via {@link SessionStore#load(SessionKey)} and project to {@link SessionMessage}. */
    public static CompletionStage<List<SessionMessage>> getSessionMessagesFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return Sessions.getSessionMessages(store, projectKey, sessionId,
            entries -> Sessions.extractMessages(sessionId, entries));
    }

    /** List subagents via {@link SessionStore#listSubkeys(SessionStore.SessionListSubkeysKey)}. */
    public static CompletionStage<List<String>> listSubagentsFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        var key = new SessionStore.SessionListSubkeysKey(projectKey, sessionId);
        return store.listSubkeys(key);
    }

    /** Get subagent messages via {@link SessionStore#load(SessionKey)} with subpath. */
    public static CompletionStage<List<SessionMessage>> getSubagentMessagesFromStore(
        SessionStore store, String projectKey, String sessionId, String agentId
    ) {
        var key = new SessionKey(projectKey, sessionId, "subagents/" + agentId);
        return store.load(key).thenApply(entries ->
            entries == null ? List.of() : Sessions.extractMessages(sessionId, entries));
    }

    // ─── Wrappers for CompletableFuture convenience ────────────────────────

    /** Sync wrapper for {@link #listSessions(String)} (used internally by tests). */
    public static List<SDKSessionInfo> listSessionsSync(String projectKey) {
        return listSessions(projectKey);
    }

    /** Async wrapper for {@link #listSessionsFromStore}. */
    public static CompletableFuture<List<SDKSessionInfo>> listSessionsFuture(
        SessionStore store, String projectKey
    ) {
        return listSessionsFromStore(store, projectKey).toCompletableFuture();
    }
}
