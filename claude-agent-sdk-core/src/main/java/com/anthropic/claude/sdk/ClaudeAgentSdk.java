package com.anthropic.claude.sdk;

import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.session.ForkSessionResult;
import com.anthropic.claude.sdk.session.SessionImport;
import com.anthropic.claude.sdk.session.SessionListing;
import com.anthropic.claude.sdk.session.SessionMutations;
import com.anthropic.claude.sdk.session.Sessions;
import com.anthropic.claude.sdk.session.SDKSessionInfo;
import com.anthropic.claude.sdk.session.SessionMessage;
import com.anthropic.claude.sdk.session.SessionSummary;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.SessionStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Claude Agent SDK 的公共门面类。对应 Python SDK 中
 * {@code claude_agent_sdk.__init__} 模块通过 {@code __all__} 导出的顶层
 * 函数——同名的函数、同样的语义，翻译为 Java 习惯用法。
 *
 * <p>支持两种使用方式：
 * <ul>
 *   <li><b>一次性查询</b>：通过 {@link #query(String, ClaudeAgentOptions)}
 *       发起无状态请求。适合一次性脚本、批处理、CI/CD 流水线等场景。</li>
 *   <li><b>交互式客户端</b>：通过 {@code ClaudeSdkClient} 进行有状态的
 *       多轮对话——可以发送追问消息、中断正在运行的回合、或在会话中途
 *       切换权限模式。</li>
 * </ul>
 *
 * <p>会话数据默认存储在本地磁盘
 * {@code ~/.claude/projects/<projectKey>/} 目录下。如需自定义后端
 * （S3、Redis、Postgres 等），可通过
 * {@link ClaudeAgentOptions#builder()}.{@code sessionStore(...)}
 * 传入 {@link SessionStore}，并使用本类中以 {@code *FromStore} 结尾的
 * 变体方法。
 */
public final class ClaudeAgentSdk {

    private ClaudeAgentSdk() {}

    // ─── Query ──────────────────────────────────────────────────────────────

    /**
     * 向 Claude Code 发送一个提示词，并返回一个一次性查询会话。
     *
     * <p>在首次迭代时，会话会启动一个 Claude Code CLI 子进程，将提示词
     * 发送给它，并关闭 stdin。子进程将按行分隔的 JSON 转录写入 stdout，
     * 会话会将其解析为 {@link com.anthropic.claude.sdk.types.Message}
     * 实例。当返回的会话被关闭时（通常通过 try-with-resources），
     * 子进程也会被关闭。
     *
     * @param prompt  发送给 Claude 的输入提示词（单轮用户消息）
     * @param options  CLI 子进程的配置——模型、权限模式、系统提示词、
     *                 MCP 服务器、钩子、会话存储等。参见
     *                 {@link ClaudeAgentOptions}。
     * @return 一个会话，迭代时产生 {@link com.anthropic.claude.sdk.types.Message}
     *         实例
     * @throws CliConnectionError 如果找不到 CLI 二进制文件或无法启动
     * @see #query(String)  使用默认选项的便捷方法
     * @see com.anthropic.claude.sdk.client.ClaudeSdkClient  交互式多轮场景
     */
    public static QuerySession query(String prompt, ClaudeAgentOptions options) {
        return new QuerySession(prompt, options, null);
    }

    /**
     * 使用默认选项向 Claude Code 发送一个提示词。
     *
     * <p>等价于 {@code query(prompt, ClaudeAgentOptions.builder().build())}。
     *
     * @param prompt  发送给 Claude 的输入提示词
     * @return 一个会话，迭代时产生消息
     * @see #query(String, ClaudeAgentOptions)
     */
    public static QuerySession query(String prompt) {
        return query(prompt, ClaudeAgentOptions.builder().build());
    }

    /**
     * 使用自定义 {@link Transport} 向 Claude Code 发送一个提示词。
     *
     * <p>当需要非默认的传输方式时使用——例如基于 SSH 的远程 CLI、自定义
     * 守护进程，或用于测试的 mock 传输。传输层负责所有 I/O；SDK 只处理
     * JSON 解析和控制协议。
     *
     * @param prompt  发送给 Claude 的输入提示词
     * @param transport  用于与 CLI 通信的传输实现
     * @return 一个会话，迭代时产生消息
     */
    public static QuerySession queryWith(String prompt, Transport transport) {
        return new QuerySession(prompt, ClaudeAgentOptions.builder().build(), transport);
    }

    // ─── Session listing (local disk) ───────────────────────────────────────

    /**
     * 列出指定项目的会话，按最近活动排序。
     *
     * <p>读取 {@code ~/.claude/projects/<projectKey>/} 下的
     * {@code .jsonl.lite} 元数据文件。不会执行 O(n) 的完整转录扫描——
     * 列表操作仅基于元数据。
     *
     * @param projectKey  项目键（通常通过
     *                    {@code Sessions.projectKeyForDirectory(cwd)} 获取）
     * @return 会话列表，最新的排在最前
     * @see #projectKeyForDirectory(String)
     */
    public static List<SDKSessionInfo> listSessions(String projectKey) {
        return SessionListing.listSessions(projectKey);
    }

    /**
     * {@link #listSessions(String)} 的分页变体。
     *
     * @param projectKey  项目键
     * @param limit  返回的最大会话数；{@code null} 表示无限制
     * @param offset  从排序结果开头跳过的会话数（用于分页）
     * @return 应用 offset 和 limit 后返回的会话列表（最新优先）
     */
    public static List<SDKSessionInfo> listSessions(String projectKey, Integer limit, int offset) {
        return SessionListing.listSessions(projectKey, limit, offset);
    }

    /**
     * 读取单个会话的元数据。
     *
     * @param sessionId  会话 UUID
     * @param projectKey  项目键
     * @return 会话信息；如果会话文件不存在、是侧链会话、或没有可提取的
     *         摘要，则返回 {@code null}
     */
    public static SDKSessionInfo getSessionInfo(String sessionId, String projectKey) {
        return SessionListing.readSessionInfo(projectKey, sessionId);
    }

    /**
     * 从会话的 JSONL 转录文件中读取对话消息。
     *
     * <p>解析完整的 JSONL，通过 {@code parentUuid} 链接构建会话链，
     * 并按时间顺序返回用户/助手消息。
     *
     * @param sessionId  会话 UUID
     * @param projectKey  项目键
     * @return 按时间排序的会话消息列表
     */
    public static List<SessionMessage> getSessionMessages(String sessionId, String projectKey) {
        return SessionListing.getSessionMessages(projectKey, sessionId);
    }

    /**
     * 列出会话中已派生的子代理的 ID 列表。
     *
     * @param sessionId  父会话 UUID
     * @param projectKey  项目键
     * @return 该会话下有转录文件的子代理 ID 列表
     */
    public static List<String> listSubagents(String sessionId, String projectKey) {
        return SessionListing.listSubagents(projectKey, sessionId);
    }

    /**
     * 读取子代理的对话消息。
     *
     * @param sessionId  父会话 UUID
     * @param agentId  子代理 ID
     * @param projectKey  项目键
     * @return 按时间排序的子代理消息列表
     */
    public static List<SessionMessage> getSubagentMessages(
        String sessionId, String agentId, String projectKey
    ) {
        return SessionListing.getSubagentMessages(projectKey, sessionId, agentId);
    }

    /**
     * 从工作目录推导规范的项目键。
     *
     * <p>项目键是从目录的绝对路径派生的稳定标识符——通常是目录名，
     * 或在 git 仓库中时使用 git 远程 URL。所有会话列表和变更方法
     * 都接受项目键而非目录路径。
     *
     * @param cwd  工作目录路径
     * @return 项目键
     */
    public static String projectKeyForDirectory(String cwd) {
        return Sessions.projectKeyForDirectory(cwd);
    }

    // ─── Session listing (via SessionStore) ────────────────────────────────

    /**
     * 异步列出会话，从自定义 {@link SessionStore} 读取元数据。
     *
     * <p>当会话通过 {@code SessionStore} 接口镜像到非磁盘后端（S3、
     * Redis、Postgres 等）时使用此变体。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @return 一个阶段，完成时返回会话列表（最新优先）
     */
    public static CompletionStage<List<SDKSessionInfo>> listSessionsFromStore(
        SessionStore store, String projectKey
    ) {
        return SessionListing.listSessionsFromStore(store, projectKey);
    }

    /**
     * 异步从 {@link SessionStore} 读取单个会话的元数据。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @return 一个阶段，完成时返回会话信息；如果未找到则为 {@code null}
     */
    public static CompletionStage<SDKSessionInfo> getSessionInfoFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionListing.getSessionInfoFromStore(store, projectKey, sessionId);
    }

    /**
     * 异步从 {@link SessionStore} 读取会话的对话消息。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @return 一个阶段，完成时返回按时间排序的会话消息列表
     */
    public static CompletionStage<List<SessionMessage>> getSessionMessagesFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionListing.getSessionMessagesFromStore(store, projectKey, sessionId);
    }

    /**
     * 异步从 {@link SessionStore} 列出子代理 ID。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  父会话 UUID
     * @return 一个阶段，完成时返回子代理 ID 列表
     */
    public static CompletionStage<List<String>> listSubagentsFromStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionListing.listSubagentsFromStore(store, projectKey, sessionId);
    }

    /**
     * 异步从 {@link SessionStore} 读取子代理的消息。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  父会话 UUID
     * @param agentId  子代理 ID
     * @return 一个阶段，完成时返回按时间排序的子代理消息列表
     */
    public static CompletionStage<List<SessionMessage>> getSubagentMessagesFromStore(
        SessionStore store, String projectKey, String sessionId, String agentId
    ) {
        return SessionListing.getSubagentMessagesFromStore(store, projectKey, sessionId, agentId);
    }

    // ─── Session mutations (local disk) ─────────────────────────────────────

    /**
     * 通过向 lite 文件写入元数据标题来重命名会话。会话 ID 不变。
     *
     * @param sessionId  要重命名的会话 UUID
     * @param newTitle  写入元数据的新标题
     * @param projectKey  项目键
     * @return 一个阶段，写入完成时结束
     */
    public static CompletionStage<Void> renameSession(String sessionId, String newTitle, String projectKey) {
        return SessionMutations.renameSession(sessionId, newTitle, projectKey);
    }

    /**
     * 给会话打一个短字符串标签。
     *
     * @param sessionId  要打标签的会话 UUID
     * @param tag  标签值（例如 {@code "needs-review"}）
     * @param projectKey  项目键
     * @return 一个阶段，写入完成时结束
     */
    public static CompletionStage<Void> tagSession(String sessionId, String tag, String projectKey) {
        return SessionMutations.tagSession(sessionId, tag, projectKey);
    }

    /**
     * 删除会话的转录文件和所有子代理转录。
     *
     * <p>此操作不可逆——删除后无法恢复会话。
     *
     * @param sessionId  要删除的会话 UUID
     * @param projectKey  项目键
     * @return 一个阶段，删除完成时结束
     */
    public static CompletionStage<Void> deleteSession(String sessionId, String projectKey) {
        return SessionMutations.deleteSession(sessionId, projectKey);
    }

    /**
     * 通过将转录文件复制到新会话 ID 来分叉会话。
     *
     * <p>复制 JSONL 文件，重新映射每条消息的 UUID，并保留
     * {@code parentUuid} 链。返回的 {@link ForkSessionResult}
     * 包含新会话的 ID。
     *
     * @param sessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID
     * @param projectKey  项目键
     * @return 一个阶段，完成时返回分叉结果（新会话 ID）
     */
    public static CompletionStage<ForkSessionResult> forkSession(String sessionId, String newSessionId, String projectKey) {
        return SessionMutations.forkSession(sessionId, newSessionId, projectKey);
    }

    // ─── Session mutations (via SessionStore) ───────────────────────────────

    /**
     * 通过 {@link SessionStore} 异步删除会话。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  要删除的会话 UUID
     * @return 一个阶段，存储完成删除时结束
     */
    public static CompletionStage<Void> deleteSessionViaStore(
        SessionStore store, String projectKey, String sessionId
    ) {
        return SessionMutations.deleteSessionViaStore(store, projectKey, sessionId);
    }

    /**
     * 通过 {@link SessionStore} 异步给会话打标签。
     *
     * <p>在存储中同一键下追加一个 {@code tag} 条目。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  会话 UUID
     * @param tag  标签值
     * @return 一个阶段，标签条目追加完成时结束
     */
    public static CompletionStage<Void> tagSessionViaStore(
        SessionStore store, String projectKey, String sessionId, String tag
    ) {
        return SessionMutations.tagSessionViaStore(store, projectKey, sessionId, tag);
    }

    /**
     * 通过 {@link SessionStore} 异步重命名会话。
     *
     * <p>在存储中同一键下追加一个仅元数据层面的 {@code rename} 条目——
     * 会话 ID 不变。这与 Python SDK 的元数据重命名语义一致；如需
     * 复制后删除的（已重命名的）变体，请使用
     * {@link #cloneSessionViaStore}。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param sessionId  要重命名的会话 UUID
     * @param newTitle  新标题
     * @return 一个阶段，重命名条目追加完成时结束
     */
    public static CompletionStage<Void> renameSessionViaStore(
        SessionStore store, String projectKey, String sessionId, String newTitle
    ) {
        return SessionMutations.renameSessionViaStore(store, projectKey, sessionId, newTitle);
    }

    /**
     * 通过 {@link SessionStore} 异步分叉会话。
     *
     * <p>将源会话的条目复制到新键。可选地支持在指定消息处截断和分叉
     * 标题，以与 Python SDK 的 API 保持一致；本地磁盘版本在
     * {@link SessionMutations#forkSession} 中有完整的截断 + 重命名
     * 实现。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param oldSessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID
     * @return 一个阶段，完成时返回分叉结果（新会话 ID）
     */
    public static CompletionStage<ForkSessionResult> forkSessionViaStore(
        SessionStore store, String projectKey, String oldSessionId, String newSessionId
    ) {
        return SessionMutations.forkSessionViaStore(store, projectKey, oldSessionId, newSessionId);
    }

    /**
     * 通过 {@link SessionStore} 异步克隆会话。
     *
     * <p>将源会话的条目复制到新键，然后删除源键。这是 Java 独有的便利
     * 方法——对应旧版 {@code renameSessionViaStore} 的复制+删除行为——
     * 单独命名为 {@code cloneSessionViaStore} 以明确语义区别于
     * 仅元数据层面的 {@link #renameSessionViaStore}。
     *
     * @param store  会话存储
     * @param projectKey  项目键
     * @param oldSessionId  源会话 UUID
     * @param newSessionId  目标会话 UUID
     * @return 一个阶段，完成时返回新会话 ID
     */
    public static CompletionStage<String> cloneSessionViaStore(
        SessionStore store, String projectKey, String oldSessionId, String newSessionId
    ) {
        return SessionMutations.cloneSessionViaStore(store, projectKey, oldSessionId, newSessionId);
    }

    // ─── Session import ─────────────────────────────────────────────────────

    /**
     * 将本地磁盘的会话转录导入到 {@link SessionStore}。
     *
     * <p>遍历 {@code baseDir} 下的所有 {@code .jsonl} 文件，解析每行
     * 内容，并将条目追加到存储中配置的 project key 下。用于将本地
     * 文件系统部署的会话迁移到远程存储。
     *
     * @param baseDir  扫描 {@code .jsonl} 文件的根目录
     * @param store  目标存储
     * @param projectKey  要写入的项目键
     * @param sessionId  要导入的会话 ID（{@code null} 表示导入全部）
     * @return 一个阶段，完成时返回已导入的条目数
     */
    public static CompletionStage<Integer> importSessionToStore(
        Path baseDir, SessionStore store, String projectKey, String sessionId
    ) {
        return SessionImport.importSessionToStore(baseDir, store, projectKey, sessionId);
    }

    // ─── Session summary ───────────────────────────────────────────────────

    /**
     * 将一个会话条目折叠进运行中的摘要聚合。
     *
     * <p>对应 Python SDK 的 {@code fold_session_summary}——给定上一次
     * 摘要和新条目，生成下一次摘要。用于在不持有完整转录的情况下
     * 增量构建会话摘要。
     *
     * @param prev  上一次的摘要状态；{@code null} 表示初始状态
     * @param entry  要折叠进去的新会话条目
     * @return 下一次的摘要状态
     */
    public static Map<String, Object> foldSessionSummary(Map<String, Object> prev, Map<String, Object> entry) {
        return SessionSummary.foldSessionSummary(prev, entry);
    }
}
