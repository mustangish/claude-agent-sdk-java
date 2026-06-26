package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.internal.JacksonSupport;
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
 * 内存版 {@link SessionStore}，用于测试和临时场景。
 *
 * <p>对应 Python SDK 的 {@code InMemorySessionStore}。通过
 * {@link ConcurrentHashMap} 保证线程安全；列出时会话按 mtime 降序排序。
 *
 * <p>这是测试和一致性检查工具使用的参考实现。生产场景中应直接针对
 * 目标后端（S3、Redis、Postgres 等）实现 {@link SessionStore}。
 */
public class InMemorySessionStore implements SessionStore {

    /** projectKey → (sessionId → entries) */
    private final ConcurrentMap<String, ConcurrentMap<String, List<SessionStore.SessionStoreEntry>>> store = new ConcurrentHashMap<>();
    /** sessionId → mtime（epoch 毫秒） */
    private final ConcurrentMap<String, Long> mtimes = new ConcurrentHashMap<>();

    /**
     * 批量追加条目到一个会话。
     *
     * <p>空列表或 {@code null} 视为空操作。会话的 mtime 会被刷新为当前
     * 系统时间。
     *
     * @param key  会话键
     * @param entries  要追加的条目
     * @return 一个阶段，记录完成时结束
     */
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

    /**
     * 加载一个会话的所有条目。
     *
     * @param key  会话键
     * @return 一个阶段，完成时返回条目列表（最新的在末尾），
     *         如果会话从未被写入则返回 {@code null}
     */
    @Override
    public CompletionStage<List<SessionStore.SessionStoreEntry>> load(SessionKey key) {
        Map<String, List<SessionStore.SessionStoreEntry>> projectMap = store.get(key.projectKey());
        if (projectMap == null) return CompletableFuture.completedFuture(null);
        List<SessionStore.SessionStoreEntry> entries = projectMap.get(key.sessionId());
        return CompletableFuture.completedFuture(entries == null ? null : List.copyOf(entries));
    }

    /**
     * 列出项目的会话，按最近活动排序。
     *
     * <p>没有条目的会话会从结果中排除。
     *
     * @param projectKey  项目键
     * @return 一个阶段，完成时返回会话摘要列表（会话 ID + mtime），
     *         最新优先
     */
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

    /**
     * 删除一个会话及其所有条目。
     *
     * @param key  要删除的会话键
     * @return 一个阶段，删除完成时结束
     */
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

    /**
     * 返回当前内存中所有项目的会话总数。供测试和断言使用——不属于
     * {@link SessionStore} 契约的一部分。
     *
     * @return 会话总数
     */
    public int sessionCount() {
        return (int) store.values().stream().mapToLong(Map::size).sum();
    }

    /**
     * 返回单个会话存储的条目数。
     *
     * @param projectKey  项目键
     * @param sessionId  会话 ID
     * @return 条目数；如果会话未知则返回 {@code 0}
     */
    public int entryCount(String projectKey, String sessionId) {
        Map<String, List<SessionStore.SessionStoreEntry>> projectMap = store.get(projectKey);
        if (projectMap == null) return 0;
        List<SessionStore.SessionStoreEntry> entries = projectMap.get(sessionId);
        return entries == null ? 0 : entries.size();
    }

    /**
     * 清空存储中的所有会话和条目。供需要在多次运行间获得干净状态的
     * 测试使用。
     */
    public void clear() {
        store.clear();
        mtimes.clear();
    }
}
