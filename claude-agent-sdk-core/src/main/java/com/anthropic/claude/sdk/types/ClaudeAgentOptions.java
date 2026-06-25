package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for a Claude Code SDK session.
 *
 * <p>Built via {@link #builder()}. Mirrors the Python SDK's {@code ClaudeAgentOptions} dataclass.
 *
 * <p>Fields marked with {@code @JsonInclude(NON_NULL)} are omitted from the serialized form
 * when null. Tools can be either a list of tool names or a {@link ToolsPreset}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ClaudeAgentOptions {

    // Tools control
    private Object tools;                          // List<String> | ToolsPreset | null
    private List<String> allowedTools = new ArrayList<>();
    private SystemPrompt systemPrompt;
    private Map<String, McpServerConfig> mcpServers = new LinkedHashMap<>();
    private Object mcpServersPath;                 // String | Path (alternative to map)
    private boolean strictMcpConfig;
    private PermissionMode permissionMode;
    private boolean continueConversation;
    private String resume;
    private String sessionId;
    private Integer maxTurns;
    private Double maxBudgetUsd;
    private List<String> disallowedTools = new ArrayList<>();
    private String model;
    private String fallbackModel;
    private List<SdkBeta> betas = new ArrayList<>();
    private String permissionPromptToolName;
    private Path cwd;
    private Path cliPath;
    private String settings;
    private List<Path> addDirs = new ArrayList<>();
    private Map<String, String> env = new HashMap<>();
    private Map<String, Object> extraArgs = new HashMap<>();
    private Integer maxBufferSize;
    private Consumer<String> stderr;
    private CanUseTool canUseTool;
    private Map<HookEvent, List<HookMatcher>> hooks;
    private String user;
    private boolean includePartialMessages;
    private boolean includeHookEvents;
    private boolean forkSession;
    private Map<String, AgentDefinition> agents;
    private List<SettingSource> settingSources;
    private List<String> skills;
    private SandboxSettings sandbox;
    private List<SdkPluginConfig> plugins = new ArrayList<>();
    private Integer maxThinkingTokens;
    private ThinkingConfig thinking;
    private EffortLevel effort;
    private Map<String, Object> outputFormat;
    private boolean enableFileCheckpointing;
    private SessionStore sessionStore;
    private SessionStoreFlushMode sessionStoreFlush = SessionStoreFlushMode.BATCHED;
    private int loadTimeoutMs = 60_000;
    private TaskBudget taskBudget;

    private ClaudeAgentOptions() {}

    public static Builder builder() {
        return new Builder();
    }

    // ─── Accessors ─────────────────────────────────────────────────────────

    public Object tools() { return tools; }
    public List<String> allowedTools() { return Collections.unmodifiableList(allowedTools); }
    public SystemPrompt systemPrompt() { return systemPrompt; }
    public Map<String, McpServerConfig> mcpServers() { return Collections.unmodifiableMap(mcpServers); }
    public Object mcpServersPath() { return mcpServersPath; }
    public boolean strictMcpConfig() { return strictMcpConfig; }
    public PermissionMode permissionMode() { return permissionMode; }
    public boolean continueConversation() { return continueConversation; }
    public String resume() { return resume; }
    public String sessionId() { return sessionId; }
    public Integer maxTurns() { return maxTurns; }
    public Double maxBudgetUsd() { return maxBudgetUsd; }
    public List<String> disallowedTools() { return Collections.unmodifiableList(disallowedTools); }
    public String model() { return model; }
    public String fallbackModel() { return fallbackModel; }
    public List<SdkBeta> betas() { return Collections.unmodifiableList(betas); }
    public String permissionPromptToolName() { return permissionPromptToolName; }
    public Path cwd() { return cwd; }
    public Path cliPath() { return cliPath; }
    public String settings() { return settings; }
    public List<Path> addDirs() { return Collections.unmodifiableList(addDirs); }
    public Map<String, String> env() { return Collections.unmodifiableMap(env); }
    public Map<String, Object> extraArgs() { return Collections.unmodifiableMap(extraArgs); }
    public Integer maxBufferSize() { return maxBufferSize; }
    public Consumer<String> stderr() { return stderr; }
    public CanUseTool canUseTool() { return canUseTool; }
    public Map<HookEvent, List<HookMatcher>> hooks() { return hooks; }
    public String user() { return user; }
    public boolean includePartialMessages() { return includePartialMessages; }
    public boolean includeHookEvents() { return includeHookEvents; }
    public boolean forkSession() { return forkSession; }
    public Map<String, AgentDefinition> agents() { return agents; }
    public List<SettingSource> settingSources() { return settingSources; }
    public List<String> skills() { return skills; }
    public SandboxSettings sandbox() { return sandbox; }
    public List<SdkPluginConfig> plugins() { return Collections.unmodifiableList(plugins); }
    public Integer maxThinkingTokens() { return maxThinkingTokens; }
    public ThinkingConfig thinking() { return thinking; }
    public EffortLevel effort() { return effort; }
    public Map<String, Object> outputFormat() { return outputFormat; }
    public boolean enableFileCheckpointing() { return enableFileCheckpointing; }
    public SessionStore sessionStore() { return sessionStore; }
    public SessionStoreFlushMode sessionStoreFlush() { return sessionStoreFlush; }
    public int loadTimeoutMs() { return loadTimeoutMs; }
    public TaskBudget taskBudget() { return taskBudget; }

    // ─── Builder ───────────────────────────────────────────────────────────

    public static final class Builder {
        private final ClaudeAgentOptions opts = new ClaudeAgentOptions();

        public Builder tools(List<String> tools) {
            opts.tools = new ArrayList<>(tools);
            return this;
        }
        public Builder toolsPreset(ToolsPreset preset) {
            opts.tools = preset;
            return this;
        }
        public Builder allowedTools(List<String> allowed) { opts.allowedTools = new ArrayList<>(allowed); return this; }
        public Builder allowedTools(String... allowed) { opts.allowedTools = new ArrayList<>(List.of(allowed)); return this; }
        public Builder systemPrompt(String prompt) { opts.systemPrompt = SystemPrompt.of(prompt); return this; }
        public Builder systemPrompt(SystemPromptPreset preset) { opts.systemPrompt = SystemPrompt.of(preset); return this; }
        public Builder systemPrompt(SystemPromptFile file) { opts.systemPrompt = SystemPrompt.of(file); return this; }
        public Builder mcpServers(Map<String, McpServerConfig> servers) { opts.mcpServers = new LinkedHashMap<>(servers); return this; }
        public Builder strictMcpConfig(boolean strict) { opts.strictMcpConfig = strict; return this; }
        public Builder permissionMode(PermissionMode mode) { opts.permissionMode = mode; return this; }
        public Builder continueConversation(boolean c) { opts.continueConversation = c; return this; }
        public Builder resume(String sessionId) { opts.resume = sessionId; return this; }
        public Builder sessionId(String id) { opts.sessionId = id; return this; }
        public Builder maxTurns(int n) { opts.maxTurns = n; return this; }
        public Builder maxBudgetUsd(double usd) { opts.maxBudgetUsd = usd; return this; }
        public Builder disallowedTools(List<String> tools) { opts.disallowedTools = new ArrayList<>(tools); return this; }
        public Builder model(String model) { opts.model = model; return this; }
        public Builder fallbackModel(String model) { opts.fallbackModel = model; return this; }
        public Builder betas(List<SdkBeta> betas) { opts.betas = new ArrayList<>(betas); return this; }
        public Builder permissionPromptToolName(String name) { opts.permissionPromptToolName = name; return this; }
        public Builder cwd(Path cwd) { opts.cwd = cwd; return this; }
        public Builder cwd(String cwd) { opts.cwd = cwd == null ? null : Path.of(cwd); return this; }
        public Builder cliPath(Path path) { opts.cliPath = path; return this; }
        public Builder settings(String settings) { opts.settings = settings; return this; }
        public Builder addDirs(List<Path> dirs) { opts.addDirs = new ArrayList<>(dirs); return this; }
        public Builder env(Map<String, String> env) { opts.env = new HashMap<>(env); return this; }
        public Builder extraArgs(Map<String, Object> args) { opts.extraArgs = new HashMap<>(args); return this; }
        public Builder maxBufferSize(Integer n) { opts.maxBufferSize = n; return this; }
        public Builder stderr(Consumer<String> cb) { opts.stderr = cb; return this; }
        public Builder canUseTool(CanUseTool cb) { opts.canUseTool = cb; return this; }
        public Builder hooks(Map<HookEvent, List<HookMatcher>> hooks) { opts.hooks = hooks; return this; }
        public Builder user(String user) { opts.user = user; return this; }
        public Builder includePartialMessages(boolean b) { opts.includePartialMessages = b; return this; }
        public Builder includeHookEvents(boolean b) { opts.includeHookEvents = b; return this; }
        public Builder forkSession(boolean b) { opts.forkSession = b; return this; }
        public Builder agents(Map<String, AgentDefinition> agents) { opts.agents = agents; return this; }
        public Builder settingSources(List<SettingSource> src) { opts.settingSources = src; return this; }
        public Builder skills(List<String> skills) { opts.skills = skills; return this; }
        public Builder sandbox(SandboxSettings sandbox) { opts.sandbox = sandbox; return this; }
        public Builder plugins(List<SdkPluginConfig> plugins) { opts.plugins = new ArrayList<>(plugins); return this; }
        public Builder maxThinkingTokens(Integer n) { opts.maxThinkingTokens = n; return this; }
        public Builder thinking(ThinkingConfig c) { opts.thinking = c; return this; }
        public Builder effort(EffortLevel e) { opts.effort = e; return this; }
        public Builder outputFormat(Map<String, Object> format) { opts.outputFormat = format; return this; }
        public Builder enableFileCheckpointing(boolean b) { opts.enableFileCheckpointing = b; return this; }
        public Builder sessionStore(SessionStore s) { opts.sessionStore = s; return this; }
        public Builder sessionStoreFlush(SessionStoreFlushMode mode) { opts.sessionStoreFlush = mode; return this; }
        public Builder loadTimeoutMs(int ms) { opts.loadTimeoutMs = ms; return this; }
        public Builder taskBudget(TaskBudget budget) { opts.taskBudget = budget; return this; }

        public ClaudeAgentOptions build() {
            Objects.requireNonNull(opts, "opts");
            return opts;
        }
    }

    // ─── SystemPrompt sealed wrapper ───────────────────────────────────────

    /** Polymorphic system prompt: plain string, {@link SystemPromptPreset}, or {@link SystemPromptFile}. */
    public sealed interface SystemPrompt permits SystemPrompt.StringPrompt, SystemPrompt.PresetPrompt, SystemPrompt.FilePrompt {
        record StringPrompt(String text) implements SystemPrompt {}
        record PresetPrompt(SystemPromptPreset preset) implements SystemPrompt {}
        record FilePrompt(SystemPromptFile file) implements SystemPrompt {}

        static SystemPrompt of(String text) { return new StringPrompt(text); }
        static SystemPrompt of(SystemPromptPreset preset) { return new PresetPrompt(preset); }
        static SystemPrompt of(SystemPromptFile file) { return new FilePrompt(file); }
    }
}
