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
 * 会话列表 API——列表/获取/解析会话元数据和消息。
 *
 * <p>对应 Python SDK 的 {@code _internal/sessions.py}（1925 行）。默认
 * 操作 {@code ~/.claude/projects/<project_key>/} 下的本地磁盘 JSONL
 * 转录文件，对于 {@code _from_store} 变体则使用 {@link SessionStore}。
 *
 * <p>每个本地会话文件由两部分组成：
 * <ul>
 *   <li><b>主 JSONL</b>：{@code <sessionId>.jsonl}——完整的转录条目。</li>
 *   <li><b>Lite 文件</b>：{@code <sessionId>.jsonl.lite}——缓存的元数据
 *       （标题、摘要、git 分支、cwd 等），用于快速列表而无需解析完整
 *       转录。</li>
 * </ul>
 */
public final class SessionListing {

    private static final ObjectMapper mapper = JacksonSupport.mapper();

    private SessionListing() {}

    // ─── Project layout ──────────────────────────────────────────────────────

    /**
     * 所有会话数据的根目录：{@code ~/.claude}。
     *
     * @return Claude 配置主目录
     */
    public static Path getClaudeConfigHome() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".claude");
    }

    /**
     * 项目目录：{@code ~/.claude/projects/<projectKey>}。
     *
     * @param projectKey  项目键
     * @return 项目目录路径（不保证存在）
     */
    public static Path getProjectDir(String projectKey) {
        return getClaudeConfigHome().resolve("projects").resolve(projectKey);
    }

    /**
     * 会话转录文件路径：{@code <projectDir>/<sessionId>.jsonl}。
     *
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @return JSONL 转录文件路径
     */
    public static Path sessionFilePath(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve(sessionId + ".jsonl");
    }

    /**
     * Lite 元数据路径：{@code <projectDir>/<sessionId>.jsonl.lite}——
     * 用于快速列表的缓存元数据。
     *
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @return lite 元数据文件路径
     */
    public static Path liteFilePath(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve(sessionId + ".jsonl.lite");
    }

    /**
     * 子代理转录目录：{@code <projectDir>/<sessionId>/subagents/}。
     *
     * @param projectKey  项目键
     * @param sessionId  父会话 UUID
     * @return 子代理目录路径
     */
    public static Path subagentsDir(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve(sessionId).resolve("subagents");
    }

    // ─── List ───────────────────────────────────────────────────────────────

    /**
     * 列出项目中的会话，按 mtime 降序排序。
     *
     * @param projectKey  项目键
     * @return 会话列表（最新优先）
     */
    public static List<SDKSessionInfo> listSessions(String projectKey) {
        return listSessions(projectKey, null, 0);
    }

    /**
     * List sessions with optional limit and offset.
     * 列出带可选 limit 和 offset 的会话。
     *
     * @param projectKey  项目键（通常通过
     *                    {@link Sessions#projectKeyForDirectory(String)} 获取）
     * @param limit  返回的最大数量（{@code null} 表示全部）
     * @param offset  跳过的条目数
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
     * 读取单个会话的元数据。如果 lite 文件存在则优先读取，否则解析
     * JSONL 的前几行以提取标题和摘要。
     *
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @return 会话信息；如果会话文件不存在则返回 {@code null}
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

    /**
     * 从会话的 JSONL 转录中读取所有用户/助手消息。
     *
     * <p>系统消息和工具内部消息（{@code parent_tool_use_id} 非空的消息）
     * 会被过滤掉。如果会话文件不存在，返回空列表。
     *
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @return 按时间排序的对话消息列表
     */
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

    /**
     * 列出会话的子代理 ID（从 {@code subagents/*.jsonl} 文件名派生）。
     *
     * @param projectKey  项目键
     * @param sessionId  父会话 UUID
     * @return 子代理 ID 列表（按字典序排序）
     */
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

    /**
     * 从子代理的 JSONL 转录中读取用户/助手消息。
     *
     * @param projectKey  项目键
     * @param sessionId  父会话 UUID
     * @param agentId  子代理 ID
     * @return 按时间排序的子代理对话消息列表
     */
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
