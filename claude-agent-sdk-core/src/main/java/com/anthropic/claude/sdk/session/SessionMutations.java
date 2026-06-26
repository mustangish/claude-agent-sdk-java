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
 * 会话变更操作：重命名、打标签、删除、分叉。
 *
 * <p>对应 Python SDK 的 {@code _internal/session_mutations.py}。本地磁盘
 * 和基于存储的两种变体都通过静态方法暴露。本地磁盘变体操作
 * {@code ~/.claude/projects/<projectKey>/} 下的 JSONL 文件。
 */
public final class SessionMutations {

    private SessionMutations() {}

    // ─── Local-disk variants ────────────────────────────────────────────────

    /**
     * 通过移动项目目录下的 JSONL 文件来重命名会话。
     *
     * @param sessionId  会话 UUID
     * @param newTitle  新标题（用作文件名，文件系统不安全字符会被替换）
     * @param projectKey  项目键
     * @return 一个阶段，文件移动完成时结束
     */
    public static CompletionStage<Void> renameSession(String sessionId, String newTitle, String projectKey) {
        return renameSession(sessionId, newTitle, projectKey, null);
    }

    /**
     * 使用显式工作目录覆盖的重命名。
     *
     * @param sessionId  会话 UUID
     * @param newTitle  新标题
     * @param projectKey  项目键
     * @param cwdOverride  可选的工作目录覆盖（当前未使用，为未来的
     *                    位置重定位功能预留）
     * @return 一个阶段，文件移动完成时结束
     */
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

    /**
     * 通过向 lite 文件写入 {@code tag} 字段来给会话打标签。
     * 会覆盖已存在的标签。
     *
     * @param sessionId  会话 UUID
     * @param tag  标签值（例如 {@code "needs-review"}）
     * @param projectKey  项目键
     * @return 一个阶段，lite 文件重写完成时结束
     */
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

    /**
     * 删除会话的转录文件及其 lite 元数据文件。
     * 此操作不可逆——删除后无法恢复会话。
     *
     * @param sessionId  会话 UUID
     * @param projectKey  项目键
     * @return 一个阶段，两个文件都删除完成时结束
     */
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

    /**
     * 通过将转录文件复制到新会话 ID 来分叉会话。
     *
     * @param sessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID
     * @param projectKey  项目键
     * @return 一个阶段，完成时返回分叉结果
     */
    public static CompletionStage<ForkSessionResult> forkSession(String sessionId, String newSessionId,
                                                                  String projectKey) {
        return forkSession(sessionId, newSessionId, projectKey, null, null);
    }

    /**
     * 分叉会话，支持可选的截断和标题。
     *
     * <p>对应 Python 的 {@code fork_session(session_id, directory=None,
     * up_to_message_id=None, title=None)}。如果 {@code upToMessageId}
     * 非空，则只复制到并包括该消息的条目。如果 {@code title} 非空，
     * 目标文件会被重命名为该标题的合法化版本。
     *
     * @param sessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID（如果 {@code title} 为 null
     *                      则用作文件名）
     * @param projectKey  项目键
     * @param upToMessageId  可选消息 ID——只复制到并包括该消息的条目
     * @param title  可选标题——目标文件会被重命名为该标题的合法化
     *               版本；返回的 {@link ForkSessionResult#sessionId()}
     *               反映重命名后的值
     * @return 一个阶段，完成时返回分叉结果
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
     * 从 {@code src} 复制条目到 {@code dst}，停在 {@code uuid} 与
     * {@code upToMessageId} 匹配的 JSONL 行（含）。
     * 由 {@link #forkSession(String, String, String, String, String)}
     * 使用以实现 {@code up_to_message_id} 参数。
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

    /**
     * 会话转录的本地磁盘根目录（对应 Python 的
     * {@code CLAUDE_CONFIG_DIR/sessions}）。默认为
     * {@code ~/.claude/sessions}。
     *
     * @return 本地会话根目录路径
     */
    public static Path defaultSessionsRoot() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".claude", "sessions");
    }

    /**
     * 旧版重命名：在默认会话根目录中移动会话的 JSONL 文件。
     * 推荐使用 {@link #renameSession(String, String, String)}，
     * 后者将操作限定在某个项目范围内。
     *
     * @param sessionId  会话 UUID
     * @param newTitle  新标题
     * @return 一个阶段，移动完成时结束
     */
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

    /**
     * 旧版打标签：在会话旁边写入一个标签文件。
     * 推荐使用 {@link #tagSession(String, String, String)}，
     * 后者写入 lite 元数据文件。
     *
     * @param sessionId  会话 UUID
     * @param tag  标签值
     * @return 一个阶段，标签文件写入完成时结束
     */
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

    /**
     * 旧版删除：从默认会话根目录中删除会话的 JSONL 文件。
     * 推荐使用 {@link #deleteSession(String, String)}。
     *
     * @param sessionId  会话 UUID
     * @return 一个阶段，文件删除完成时结束
     */
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
     * 旧版分叉：在默认会话根目录中将会话复制到新 ID。
     * 推荐使用 {@link #forkSession(String, String, String)}，
     * 后者将操作限定在某个项目范围内，并支持截断和标题。
     *
     * @param sessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID
     * @return 一个阶段，完成时返回分叉结果
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

    /**
     * 通过 {@link SessionStore} 异步删除会话。委托给
     * {@link SessionStore#delete}。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  要删除的会话 UUID
     * @return 一个阶段，存储完成删除时结束
     */
    public static CompletionStage<Void> deleteSessionViaStore(SessionStore store,
                                                            String projectKey,
                                                            String sessionId) {
        var key = new com.anthropic.claude.sdk.types.SessionKey(projectKey, sessionId, null);
        return store.delete(key);
    }

    /**
     * 通过 {@link SessionStore} 异步分叉会话——将条目复制到新键。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param oldSessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID
     * @return 一个阶段，完成时返回分叉结果
     */
    public static CompletionStage<ForkSessionResult> forkSessionViaStore(SessionStore store,
                                                                          String projectKey,
                                                                          String oldSessionId,
                                                                          String newSessionId) {
        return forkSessionViaStore(store, projectKey, oldSessionId, newSessionId, null, null);
    }

    /**
     * 通过 SessionStore 分叉会话，支持可选的截断和标题。
     *
     * <p>对应 Python 的 {@code fork_session_via_store}。如果
     * {@code upToMessageId} 非空，则只复制到并包括该消息的条目。
     * {@code title} 参数当前为 API 兼容性而接受；基于存储的元数据
     * 标题支持单独跟踪。
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
     * 通过 SessionStore 追加元数据标题条目来重命名会话。
     *
     * <p>对应 Python 的 {@code rename_session_via_store}：在存储中
     * 同一键下写入一个 {@code rename} 条目，会话 ID 不变。如需
     * 复制后删除的变体，请参见 {@link #cloneSessionViaStore}。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  要重命名的会话 UUID
     * @param newTitle  新标题
     * @return 一个阶段，重命名条目追加完成时结束
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
     * 通过 SessionStore 将会话条目复制到新键并删除源键。
     *
     * <p>这是旧版 {@code renameSessionViaStore} 的行为（复制 + 删除）。
     * 为了清晰起见重命名——它不是重命名，而是克隆。如需仅元数据层面
     * 的重命名，请使用 {@link #renameSessionViaStore}。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param oldSessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID
     * @return 一个阶段，完成时返回新会话 ID
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
     * 通过 SessionStore 追加元数据标签条目来给会话打标签。
     *
     * <p>对应 Python 的 {@code tag_session_via_store}：在存储中
     * 同一键下写入一个 {@code tag} 条目。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @param tag  标签值
     * @return 一个阶段，标签条目追加完成时结束
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
