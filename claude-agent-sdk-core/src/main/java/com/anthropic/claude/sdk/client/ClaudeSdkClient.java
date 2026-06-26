package com.anthropic.claude.sdk.client;

import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.anthropic.claude.sdk.internal.InternalQuery;
import com.anthropic.claude.sdk.internal.JacksonSupport;
import com.anthropic.claude.sdk.internal.message.MessageParser;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.transport.subprocess.SubprocessCliTransport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.HookEvent;
import com.anthropic.claude.sdk.types.HookMatcher;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.PermissionMode;
import com.anthropic.claude.sdk.types.ResultMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Claude Code 的交互式、双向客户端。
 *
 * <p>支持多轮对话，CLI 子进程在消息之间保持打开状态。
 * 使用 {@link InternalQuery} 处理控制协议（hooks、can_use_tool、
 * interrupt、set_permission_mode、set_model）。
 */
public final class ClaudeSdkClient implements AutoCloseable {

    private final ClaudeAgentOptions options;
    private final Transport customTransport;
    private final ObjectMapper mapper = JacksonSupport.mapper();
    private final MessageParser parser;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Transport transport;
    private InternalQuery query;

    public ClaudeSdkClient(ClaudeAgentOptions options) {
        this(options, null);
    }

    public ClaudeSdkClient(ClaudeAgentOptions options, Transport customTransport) {
        this.options = options != null ? options : ClaudeAgentOptions.builder().build();
        this.customTransport = customTransport;
        this.parser = new MessageParser(mapper);
    }

    /**
     * 打开 CLI 子进程并运行 initialize 握手。
     * 必须先调用此方法才能发送查询或接收消息。
     */
    public void connect() {
        if (connected.get()) return;
        if (closed.get()) throw new IllegalStateException("Client is closed");

        // P0 fix #2: validate session_store options before spawning subprocess.
        validateSessionStoreOptions(options);

        // P0 fix #3: materialize resume state if resume/continue set + session_store wired.
        this.materialized = materializeIfNeeded(options);

        // P0 fix #1: apply materialized options (overrides cwd/env/CLI config dir).
        ClaudeAgentOptions effectiveOptions = materialized != null
            ? applyMaterializedOptions(options, materialized)
            : options;

        if (customTransport != null) {
            this.transport = customTransport;
        } else {
            this.transport = new SubprocessCliTransport(effectiveOptions);
        }
        this.transport.connect();

        Map<HookEvent, List<HookMatcher>> hooks = effectiveOptions.hooks();
        this.query = new InternalQuery(
            transport, effectiveOptions, hooks, effectiveOptions.canUseTool(), 60.0);
        this.query.start();

        // P0 fix #1: wire session_store to TranscriptMirrorBatcher (only for store-backed variants).
        if (effectiveOptions.sessionStore() != null) {
            final com.anthropic.claude.sdk.session.TranscriptMirrorBatcher batcher =
                new com.anthropic.claude.sdk.session.TranscriptMirrorBatcher(
                    effectiveOptions.sessionStore(),
                    new com.anthropic.claude.sdk.types.SessionKey(
                        projectKey(effectiveOptions), effectiveOptions.sessionId() != null
                            ? effectiveOptions.sessionId() : "default", null),
                    effectiveOptions.sessionStoreFlush());
            this.query.setTranscriptMirrorBatcher(batcher);
        }

        try {
            this.query.initialize();
        } catch (Exception e) {
            this.query.close();
            this.transport.close();
            throw new CliConnectionError("Initialize handshake failed", e);
        }
        connected.set(true);
    }

    private com.anthropic.claude.sdk.session.SessionResume.MaterializedResume materialized;

    private static void validateSessionStoreOptions(ClaudeAgentOptions options) {
        // session_store requires either fork_session, resume, or continue_conversation.
        if (options.sessionStore() != null) {
            boolean ok = options.resume() != null
                || options.continueConversation()
                || options.forkSession();
            if (!ok) {
                throw new IllegalArgumentException(
                    "sessionStore requires one of: resume, continueConversation, or forkSession");
            }
        }
    }

    private static String projectKey(ClaudeAgentOptions options) {
        String cwd = options.cwd() != null ? options.cwd().toString()
            : System.getProperty("user.dir");
        return com.anthropic.claude.sdk.ClaudeAgentSdk.projectKeyForDirectory(cwd);
    }

    private static com.anthropic.claude.sdk.session.SessionResume.MaterializedResume materializeIfNeeded(
        ClaudeAgentOptions options
    ) {
        if (options.sessionStore() == null) return null;
        if (options.continueConversation() || options.resume() != null) {
            String sessionId = options.resume() != null ? options.resume() : "default";
            try {
                return com.anthropic.claude.sdk.session.SessionResume
                    .materializeFromStore(options, options.sessionStore(),
                        projectKey(options), sessionId)
                    .toCompletableFuture().get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to materialize resume: " + e.getMessage(), e);
            }
        }
        return null;
    }

    private static ClaudeAgentOptions applyMaterializedOptions(
        ClaudeAgentOptions original,
        com.anthropic.claude.sdk.session.SessionResume.MaterializedResume materialized
    ) {
        // Delegate to SessionResume.applyMaterializedOptions (which preserves all fields)
        // then add CLAUDE_CONFIG_DIR env var so the CLI subprocess finds the resumed
        // session at $CLAUDE_CONFIG_DIR/projects/<projectKey>/<sessionId>.jsonl.
        var b = ClaudeAgentOptions.builder();
        copyAllOptions(original, b);
        b.resume(materialized.overriddenOptions().resume() != null
            ? materialized.overriddenOptions().resume()
            : original.resume());
        if (materialized.configDir() != null) {
            java.util.Map<String, String> env = new java.util.LinkedHashMap<>(
                original.env() != null ? original.env() : java.util.Map.of());
            env.put("CLAUDE_CONFIG_DIR", materialized.configDir().toAbsolutePath().toString());
            b.env(env);
        }
        return b.build();
    }

    private static void copyAllOptions(ClaudeAgentOptions src, ClaudeAgentOptions.Builder b) {
        if (src.tools() != null) {
            if (src.tools() instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<String> toolsList = (java.util.List<String>) src.tools();
                b.tools(toolsList);
            }
        }
        b.allowedTools(src.allowedTools());
        if (src.systemPrompt() != null) {
            ClaudeAgentOptions.SystemPrompt sp = src.systemPrompt();
            if (sp instanceof ClaudeAgentOptions.SystemPrompt.StringPrompt sps) {
                b.systemPrompt(sps.text());
            } else if (sp instanceof ClaudeAgentOptions.SystemPrompt.PresetPrompt spp) {
                b.systemPrompt(spp.preset());
            } else if (sp instanceof ClaudeAgentOptions.SystemPrompt.FilePrompt spf) {
                b.systemPrompt(spf.file());
            }
        }
        if (src.mcpServers() != null) b.mcpServers(src.mcpServers());
        b.strictMcpConfig(src.strictMcpConfig());
        if (src.permissionMode() != null) b.permissionMode(src.permissionMode());
        b.continueConversation(src.continueConversation());
        if (src.sessionId() != null) b.sessionId(src.sessionId());
        if (src.maxTurns() != null) b.maxTurns(src.maxTurns());
        if (src.maxBudgetUsd() != null) b.maxBudgetUsd(src.maxBudgetUsd());
        b.disallowedTools(src.disallowedTools());
        if (src.model() != null) b.model(src.model());
        if (src.fallbackModel() != null) b.fallbackModel(src.fallbackModel());
        b.betas(src.betas());
        if (src.permissionPromptToolName() != null) b.permissionPromptToolName(src.permissionPromptToolName());
        if (src.cwd() != null) b.cwd(src.cwd());
        if (src.cliPath() != null) b.cliPath(src.cliPath());
        if (src.settings() != null) b.settings(src.settings());
        b.addDirs(src.addDirs());
        if (src.extraArgs() != null) b.extraArgs(src.extraArgs());
        if (src.maxBufferSize() != null) b.maxBufferSize(src.maxBufferSize());
        if (src.stderr() != null) b.stderr(src.stderr());
        if (src.canUseTool() != null) b.canUseTool(src.canUseTool());
        if (src.hooks() != null) b.hooks(src.hooks());
        if (src.user() != null) b.user(src.user());
        b.includePartialMessages(src.includePartialMessages());
        b.includeHookEvents(src.includeHookEvents());
        b.forkSession(src.forkSession());
        if (src.agents() != null) b.agents(src.agents());
        if (src.settingSources() != null) b.settingSources(src.settingSources());
        if (src.skills() != null) b.skills(src.skills());
        if (src.sandbox() != null) b.sandbox(src.sandbox());
        b.plugins(src.plugins());
        if (src.maxThinkingTokens() != null) b.maxThinkingTokens(src.maxThinkingTokens());
        if (src.thinking() != null) b.thinking(src.thinking());
        if (src.effort() != null) b.effort(src.effort());
        if (src.outputFormat() != null) b.outputFormat(src.outputFormat());
        b.enableFileCheckpointing(src.enableFileCheckpointing());
        if (src.sessionStore() != null) b.sessionStore(src.sessionStore());
        if (src.sessionStoreFlush() != null) b.sessionStoreFlush(src.sessionStoreFlush());
        b.loadTimeoutMs(src.loadTimeoutMs());
        if (src.taskBudget() != null) b.taskBudget(src.taskBudget());
    }

    /**
     * 向 Claude 发送一个用户提示。
     *
     * @param prompt  发送给 Claude 的提示词
     */
    public void query(String prompt) {
        ensureConnected();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "user");
        message.put("message", Map.of("role", "user", "content", prompt));
        message.put("parent_tool_use_id", null);
        message.put("session_id", options.sessionId() != null ? options.sessionId() : "default");
        try {
            transport.write(mapper.writeValueAsString(message) + "\n");
        } catch (JsonProcessingException e) {
            throw new CliConnectionError("Failed to serialize prompt", e);
        }
    }

    /**
     * 从 CLI 流式接收消息。
     *
     * @return 消息迭代器——持续拉取消息直到 CLI 子进程关闭
     */
    public Iterator<Message> receiveMessages() {
        ensureConnected();
        Iterator<JsonNode> raw = query.receiveMessages();
        return new Iterator<>() {
            Message next;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (raw.hasNext()) {
                    Message parsed = parser.parse(raw.next());
                    if (parsed != null) {
                        next = parsed;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Message next() {
                if (!hasNext()) throw new NoSuchElementException();
                Message m = next;
                next = null;
                return m;
            }
        };
    }

    /**
     * 流式接收消息，直到遇到 {@link ResultMessage}（回合结束标记）后停止。
     *
     * @return 一个迭代器，会产生回合中的所有消息并在 {@link ResultMessage}
     *         之后终止
     */
    public Iterator<Message> receiveResponse() {
        return new Iterator<>() {
            final Iterator<Message> source = receiveMessages();
            Message next;
            boolean done = false;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                if (done) return false;
                while (source.hasNext()) {
                    Message m = source.next();
                    if (m instanceof ResultMessage) {
                        next = m;
                        done = true;
                        return true;
                    }
                    next = m;
                    return true;
                }
                done = true;
                return false;
            }

            @Override
            public Message next() {
                if (!hasNext()) throw new NoSuchElementException();
                Message m = next;
                next = null;
                return m;
            }
        };
    }

    /**
     * 发送中断信号，停止正在运行的回合。
     * 当前回合会被中止；后续回合可以从这一点继续。
     */
    public void interrupt() {
        ensureConnected();
        try {
            query.interrupt();
        } catch (Exception e) {
            throw new CliConnectionError("Interrupt failed", e);
        }
    }

    /**
     * 在会话中途切换权限模式。
     *
     * @param mode  新的权限模式
     */
    public void setPermissionMode(PermissionMode mode) {
        ensureConnected();
        try {
            query.setPermissionMode(mode);
        } catch (Exception e) {
            throw new CliConnectionError("set_permission_mode failed", e);
        }
    }

    /**
     * 在会话中途切换模型。
     *
     * @param model  新的模型 ID（例如 {@code "claude-opus-4-1"}）
     */
    public void setModel(String model) {
        ensureConnected();
        try {
            query.setModel(model);
        } catch (Exception e) {
            throw new CliConnectionError("set_model failed", e);
        }
    }

    /**
     * 将文件回退到指定用户消息时刻的状态。
     *
     * @param userMessageId  作为回退点的用户消息 UUID
     */
    public void rewindFiles(String userMessageId) {
        ensureConnected();
        try {
            query.rewindFiles(userMessageId);
        } catch (Exception e) {
            throw new CliConnectionError("rewind_files failed", e);
        }
    }

    /**
     * 重新连接一个断开的 MCP 服务器。
     *
     * @param serverName  要重新连接的 MCP 服务器名称
     */
    public void reconnectMcpServer(String serverName) {
        ensureConnected();
        try {
            query.reconnectMcpServer(serverName);
        } catch (Exception e) {
            throw new CliConnectionError("mcp_reconnect failed", e);
        }
    }

    /**
     * 启用或禁用一个 MCP 服务器。
     *
     * @param serverName  MCP 服务器名称
     * @param enabled  {@code true} 启用，{@code false} 禁用
     */
    public void toggleMcpServer(String serverName, boolean enabled) {
        ensureConnected();
        try {
            query.toggleMcpServer(serverName, enabled);
        } catch (Exception e) {
            throw new CliConnectionError("mcp_toggle failed", e);
        }
    }

    /**
     * 停止一个正在运行的任务。
     *
     * @param taskId  要停止的任务 ID
     */
    public void stopTask(String taskId) {
        ensureConnected();
        try {
            query.stopTask(taskId);
        } catch (Exception e) {
            throw new CliConnectionError("stop_task failed", e);
        }
    }

    /**
     * 查询 MCP 服务器的连接状态。
     *
     * @return 所有 MCP 服务器及其状态
     */
    public com.anthropic.claude.sdk.types.McpStatusResponse getMcpStatus() {
        ensureConnected();
        try {
            return query.getMcpStatus();
        } catch (Exception e) {
            throw new CliConnectionError("mcp_status failed", e);
        }
    }

    /**
     * 查询上下文窗口使用情况的细分。
     *
     * @return 各类上下文使用量的细分
     */
    public com.anthropic.claude.sdk.types.ContextUsageResponse getContextUsage() {
        ensureConnected();
        try {
            return query.getContextUsage();
        } catch (Exception e) {
            throw new CliConnectionError("get_context_usage failed", e);
        }
    }

    /**
     * 从 CLI 获取服务器初始化信息（命令、输出样式、能力）。
     *
     * @return 包含 commands、output styles、capabilities 等字段的 map
     */
    public java.util.Map<String, Object> getServerInfo() {
        ensureConnected();
        JsonNode result = query.initializationResult();
        if (result == null || result.isNull()) return null;
        return mapper.convertValue(result, java.util.Map.class);
    }

    /**
     * 断开与 CLI 的连接。可多次调用（幂等）。
     * 等同于 {@link #close()}。
     */
    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            try {
                if (query != null) query.close();
            } finally {
                try {
                    if (transport != null) transport.close();
                } finally {
                    // P0 fix #5: cleanup materialized resume temp files.
                    if (materialized != null) {
                        com.anthropic.claude.sdk.session.SessionResume.cleanup(materialized);
                        materialized = null;
                    }
                    transport = null;
                    query = null;
                    closed.set(true);
                }
            }
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    private void ensureConnected() {
        if (!connected.get()) throw new CliConnectionError("Not connected. Call connect() first.");
    }
}
