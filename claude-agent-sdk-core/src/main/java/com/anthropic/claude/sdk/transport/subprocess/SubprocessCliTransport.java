package com.anthropic.claude.sdk.transport.subprocess;

import com.anthropic.claude.sdk.CliVersion;
import com.anthropic.claude.sdk.Version;
import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.anthropic.claude.sdk.errors.CliJsonDecodeError;
import com.anthropic.claude.sdk.errors.CliNotFoundError;
import com.anthropic.claude.sdk.errors.ProcessError;
import com.anthropic.claude.sdk.internal.JacksonSupport;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.McpServerConfig;
import com.anthropic.claude.sdk.types.SystemPromptFile;
import com.anthropic.claude.sdk.types.SystemPromptPreset;
import com.anthropic.claude.sdk.types.ThinkingConfig;
import com.anthropic.claude.sdk.types.ToolsPreset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Transport that drives the Claude Code CLI as a subprocess.
 *
 * <p>Spawns {@code claude --output-format stream-json --verbose --input-format stream-json ...}
 * and reads/writes line-delimited JSON over stdin/stdout.
 *
 * <p>Mirrors Python SDK's {@code SubprocessCLITransport} (762 LOC). Process lifecycle uses a
 * 5-second graceful close → SIGTERM → 5s → SIGKILL ladder.
 */
public final class SubprocessCliTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(SubprocessCliTransport.class);
    private static final int DEFAULT_MAX_BUFFER_SIZE = 1024 * 1024;  // 1 MB
    private static final String MINIMUM_CLI_VERSION = "2.0.0";
    private static final int SHUTDOWN_GRACE_SECONDS = 5;

    private final ClaudeAgentOptions options;
    private final ObjectMapper mapper = JacksonSupport.mapper();
    private final int maxBufferSize;

    // I/O state
    private Process process;
    private BufferedReader stdoutReader;
    private BufferedReader stderrReader;
    private Writer stdinWriter;

    // Bounded queue matching Python's anyio.create_memory_object_stream(max_buffer_size=100)
    private final BlockingDeque<JsonNode> messageQueue = new LinkedBlockingDeque<>(100);

    // Reader thread + state
    private Thread readerThread;
    private Thread stderrThread;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile ProcessError exitError;

    public SubprocessCliTransport(ClaudeAgentOptions options) {
        this.options = options;
        this.maxBufferSize = options.maxBufferSize() != null
            ? options.maxBufferSize()
            : DEFAULT_MAX_BUFFER_SIZE;
    }

    // ─── Transport contract ─────────────────────────────────────────────────

    @Override
    public void connect() {
        if (process != null) return;

        String cliPath = findCliPath();
        checkCliVersion(cliPath);

        List<String> cmd = buildCommand(cliPath);
        ProcessBuilder pb = new ProcessBuilder(cmd)
            .redirectErrorStream(options.stderr() == null);

        Map<String, String> env = pb.environment();
        // 1. Filter out CLAUDECODE so SDK-spawned subprocesses don't think they're in a parent.
        env.remove("CLAUDECODE");
        // 2. Mark SDK origin.
        env.put("CLAUDE_CODE_ENTRYPOINT", "sdk-java");
        // 3. Stamp version.
        env.put("CLAUDE_AGENT_SDK_VERSION", Version.VERSION);
        // 4. File checkpointing.
        if (options.enableFileCheckpointing()) {
            env.put("CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING", "true");
        }
        // 5. PWD (some tools rely on $PWD rather than cwd).
        if (options.cwd() != null) env.put("PWD", options.cwd().toString());
        // 6. User options.env (can override the above).
        if (options.env() != null) env.putAll(options.env());

        // 7. Working directory.
        if (options.cwd() != null) pb.directory(options.cwd().toFile());

        try {
            this.process = pb.start();
        } catch (IOException e) {
            if (options.cwd() != null && !Files.isDirectory(options.cwd())) {
                throw new CliConnectionError("Working directory does not exist: " + options.cwd(), e);
            }
            throw new CliNotFoundError("Failed to start Claude Code", cliPath);
        }

        ProcessRegistry.register(process);
        this.stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stdinWriter = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);

        // Start background stderr reader only if a callback is registered.
        if (options.stderr() != null) {
            this.stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            this.stderrThread = startVirtualThread("claude-stderr", this::drainStderr);
        }

        // Start stdout reader — populates messageQueue with parsed JSON nodes.
        this.readerThread = startVirtualThread("claude-stdout", this::drainStdout);

        ready.set(true);
    }

    @Override
    public void write(String data) {
        writeLock.lock();
        try {
            if (!ready.get() || stdinWriter == null) {
                throw new CliConnectionError("ProcessTransport is not ready for writing");
            }
            if (process != null && !process.isAlive()) {
                throw new CliConnectionError(
                    "Cannot write to terminated process (exit code: " + process.exitValue() + ")");
            }
            if (exitError != null) {
                throw new CliConnectionError(
                    "Cannot write to process that exited with error: " + exitError.getMessage(), exitError);
            }
            try {
                stdinWriter.write(data);
                stdinWriter.flush();
            } catch (IOException e) {
                ready.set(false);
                exitError = new ProcessError("Failed to write to process stdin", null, e.getMessage());
                throw new CliConnectionError(exitError.getMessage(), e);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Iterator<JsonNode> readMessages() {
        return new Iterator<>() {
            JsonNode next;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                // Surface exit errors eagerly so consumers see ProcessError instead of a silent EOF.
                if (exitError != null && messageQueue.isEmpty()) {
                    throw new CliConnectionError(exitError.getMessage(), exitError);
                }
                if (closed.get() && messageQueue.isEmpty()) return false;
                try {
                    while (next == null) {
                        if (exitError != null && messageQueue.isEmpty()) {
                            throw new CliConnectionError(exitError.getMessage(), exitError);
                        }
                        if (closed.get() && messageQueue.isEmpty()) return false;
                        JsonNode msg = messageQueue.poll(50, TimeUnit.MILLISECONDS);
                        if (msg != null) {
                            next = msg;
                            return true;
                        }
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            @Override
            public JsonNode next() {
                if (!hasNext()) throw new NoSuchElementException();
                JsonNode msg = next;
                next = null;
                return msg;
            }
        };
    }

    @Override
    public void endInput() {
        writeLock.lock();
        try {
            if (stdinWriter != null) {
                try { stdinWriter.close(); } catch (IOException ignored) {}
                stdinWriter = null;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public void close() {
        if (process == null) {
            ready.set(false);
            return;
        }

        // 1. Cancel stderr reader if active.
        if (stderrThread != null) {
            stderrThread.interrupt();
            try { stderrThread.join(1000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // 2. Close stdin under lock — this signals CLI for graceful shutdown.
        writeLock.lock();
        try {
            ready.set(false);
            if (stdinWriter != null) {
                try { stdinWriter.close(); } catch (IOException ignored) {}
                stdinWriter = null;
            }
        } finally {
            writeLock.unlock();
        }

        // 3. Wait for graceful shutdown after EOF on stdin.
        try {
            if (process.isAlive()) {
                if (!process.waitFor(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                    // 4. Timeout → SIGTERM.
                    process.destroy();
                    if (!process.waitFor(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                        // 5. Still alive → SIGKILL.
                        process.destroyForcibly();
                        process.waitFor(2, TimeUnit.SECONDS);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            closed.set(true);
            ProcessRegistry.unregister(process);
            // Wait for reader thread to drain.
            if (readerThread != null) {
                try { readerThread.join(2000); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            // Unblock any pending readers.
            messageQueue.clear();
            process = null;
            exitError = null;
        }
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    /** Drain stdout lines, parse JSON, push to queue. */
    private void drainStdout() {
        StringBuilder buf = new StringBuilder();
        try {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                buf.append(line);
                if (buf.length() > maxBufferSize) {
                    throw new CliJsonDecodeError(buf.toString(),
                        new IllegalStateException("Buffer size " + buf.length() + " exceeds limit " + maxBufferSize));
                }
                try {
                    JsonNode msg = mapper.readTree(buf.toString());
                    buf.setLength(0);
                    messageQueue.put(msg);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    // Incomplete JSON — accumulate more.
                }
            }
        } catch (IOException e) {
            log.debug("stdout reader closed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closed.set(true);
        }

        // After stdout EOF, check exit code.
        if (process != null) {
            try {
                int code = process.waitFor();
                if (code != 0 && exitError == null) {
                    exitError = new ProcessError("Command failed with exit code " + code, code, "Check stderr output");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void drainStderr() {
        try {
            String line;
            while ((line = stderrReader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) continue;
                try {
                    options.stderr().accept(line);
                } catch (Exception e) {
                    log.debug("stderr callback raised; continuing", e);
                }
            }
        } catch (IOException e) {
            log.debug("stderr reader closed", e);
        }
    }

    /** Locate the claude binary. */
    private String findCliPath() {
        if (options.cliPath() != null) return options.cliPath().toString();

        String[] wellKnown = {
            System.getProperty("user.home") + "/.local/bin/claude",
            "/usr/local/bin/claude",
            System.getProperty("user.home") + "/.npm-global/bin/claude",
            System.getProperty("user.home") + "/node_modules/.bin/claude",
            System.getProperty("user.home") + "/.yarn/bin/claude",
            System.getProperty("user.home") + "/.claude/local/claude"
        };
        for (String p : wellKnown) {
            Path path = Paths.get(p);
            if (Files.isRegularFile(path) && Files.isExecutable(path)) return p;
        }
        // PATH lookup.
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                Path candidate = Paths.get(dir, "claude");
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toString();
                }
            }
        }
        throw new CliNotFoundError("Claude Code not found. Install with: npm install -g @anthropic-ai/claude-code");
    }

    /** Run `claude -v` and warn if version is below MINIMUM_CLI_VERSION. */
    private void checkCliVersion(String cliPath) {
        try {
            Process p = new ProcessBuilder(cliPath, "-v").redirectErrorStream(true).start();
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String out = r.readLine();
                if (out == null) return;
                String[] parts = out.trim().split("\\.");
                if (parts.length >= 3) {
                    try {
                        int[] actual = { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
                        int[] minimum = { 2, 0, 0 };
                        for (int i = 0; i < 3; i++) {
                            if (actual[i] > minimum[i]) return;
                            if (actual[i] < minimum[i]) {
                                log.warn("Claude Code version {} is below minimum {} — some features may not work",
                                    out.trim(), MINIMUM_CLI_VERSION);
                                return;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("Version check failed (non-fatal)", e);
        }
    }

    // ─── CLI command construction ───────────────────────────────────────────

    private List<String> buildCommand(String cliPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(cliPath);
        cmd.addAll(List.of("--output-format", "stream-json", "--verbose", "--input-format", "stream-json"));

        // system prompt
        var sp = options.systemPrompt();
        if (sp == null) {
            cmd.addAll(List.of("--system-prompt", ""));
        } else if (sp instanceof ClaudeAgentOptions.SystemPrompt.StringPrompt sps) {
            cmd.addAll(List.of("--system-prompt", sps.text()));
        } else if (sp instanceof ClaudeAgentOptions.SystemPrompt.PresetPrompt spp) {
            SystemPromptPreset preset = spp.preset();
            if (preset.append() != null) {
                cmd.addAll(List.of("--append-system-prompt", preset.append()));
            }
        } else if (sp instanceof ClaudeAgentOptions.SystemPrompt.FilePrompt spf) {
            cmd.addAll(List.of("--system-prompt-file", spf.file().path()));
        }

        // tools
        Object tools = options.tools();
        if (tools != null) {
            if (tools instanceof List<?> list) {
                if (list.isEmpty()) {
                    cmd.addAll(List.of("--tools", ""));
                } else {
                    cmd.addAll(List.of("--tools", String.join(",", castToStrings(list))));
                }
            } else if (tools instanceof ToolsPreset) {
                cmd.addAll(List.of("--tools", "default"));
            }
        }

        if (!options.allowedTools().isEmpty()) {
            cmd.addAll(List.of("--allowedTools", String.join(",", options.allowedTools())));
        }

        // Skills auto-injection (mirrors Python SDK's _apply_skills_defaults).
        if (options.skills() != null) {
            java.util.List<String> effectiveAllowed = new java.util.ArrayList<>(options.allowedTools());
            if ("all".equals(options.skills())) {
                if (!effectiveAllowed.contains("Skill")) effectiveAllowed.add("Skill");
            } else {
                for (String name : options.skills()) {
                    String pattern = "Skill(" + name + ")";
                    if (!effectiveAllowed.contains(pattern)) effectiveAllowed.add(pattern);
                }
            }
            // Remove the previously-added --allowedTools flag and re-add with skills appended
            int idx = cmd.indexOf("--allowedTools");
            if (idx >= 0) {
                cmd.remove(idx); cmd.remove(idx);  // remove flag + value
            }
            cmd.addAll(List.of("--allowedTools", String.join(",", effectiveAllowed)));
        }
        if (options.maxTurns() != null) {
            cmd.addAll(List.of("--max-turns", String.valueOf(options.maxTurns())));
        }
        if (options.maxBudgetUsd() != null) {
            cmd.addAll(List.of("--max-budget-usd", String.valueOf(options.maxBudgetUsd())));
        }
        if (!options.disallowedTools().isEmpty()) {
            cmd.addAll(List.of("--disallowedTools", String.join(",", options.disallowedTools())));
        }
        if (options.taskBudget() != null) {
            cmd.addAll(List.of("--task-budget", String.valueOf(options.taskBudget().total())));
        }
        if (options.model() != null) {
            cmd.addAll(List.of("--model", options.model()));
        }
        if (options.fallbackModel() != null) {
            cmd.addAll(List.of("--fallback-model", options.fallbackModel()));
        }
        if (!options.betas().isEmpty()) {
            cmd.addAll(List.of("--betas",
                options.betas().stream().map(b -> b.wireValue()).reduce((a, b) -> a + "," + b).orElse("")));
        }
        if (options.permissionPromptToolName() != null) {
            cmd.addAll(List.of("--permission-prompt-tool", options.permissionPromptToolName()));
        }
        if (options.permissionMode() != null) {
            cmd.addAll(List.of("--permission-mode", options.permissionMode().wireValue()));
        }
        if (options.continueConversation()) cmd.add("--continue");
        if (options.resume() != null) cmd.addAll(List.of("--resume", options.resume()));
        if (options.sessionId() != null) cmd.addAll(List.of("--session-id", options.sessionId()));

        if (options.settings() != null) {
            cmd.addAll(List.of("--settings", options.settings()));
        }
        for (Path dir : options.addDirs()) {
            cmd.addAll(List.of("--add-dir", dir.toString()));
        }

        // MCP servers
        if (!options.mcpServers().isEmpty()) {
            Map<String, Object> mcpServersWire = new LinkedHashMap<>();
            for (var entry : options.mcpServers().entrySet()) {
                String name = entry.getKey();
                McpServerConfig cfg = entry.getValue();
                if (cfg instanceof McpServerConfig.McpStdioServerConfig stdio) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "stdio");
                    m.put("command", stdio.command());
                    if (stdio.args() != null) m.put("args", stdio.args());
                    if (stdio.env() != null) m.put("env", stdio.env());
                    mcpServersWire.put(name, m);
                } else if (cfg instanceof McpServerConfig.McpSSEServerConfig sse) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "sse");
                    m.put("url", sse.url());
                    if (sse.headers() != null) m.put("headers", sse.headers());
                    mcpServersWire.put(name, m);
                } else if (cfg instanceof McpServerConfig.McpHttpServerConfig http) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "http");
                    m.put("url", http.url());
                    if (http.headers() != null) m.put("headers", http.headers());
                    mcpServersWire.put(name, m);
                }
                // SDK server config not sent as CLI flag — handled via control protocol in Phase 6.
            }
            if (!mcpServersWire.isEmpty()) {
                try {
                    String json = mapper.writeValueAsString(Map.of("mcpServers", mcpServersWire));
                    cmd.addAll(List.of("--mcp-config", json));
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to serialize mcp servers config", e);
                }
            }
        }

        if (options.includePartialMessages()) cmd.add("--include-partial-messages");
        if (options.includeHookEvents()) cmd.add("--include-hook-events");
        if (options.strictMcpConfig()) cmd.add("--strict-mcp-config");
        if (options.forkSession()) cmd.add("--fork-session");
        if (options.sessionStore() != null) cmd.add("--session-mirror");

        if (options.settingSources() != null) {
            cmd.add("--setting-sources=" + String.join(",",
                options.settingSources().stream().map(Enum::name).map(String::toLowerCase).toList()));
        }
        for (var plugin : options.plugins()) {
            cmd.addAll(List.of("--plugin-dir", plugin.path()));
        }

        // thinking / effort
        ThinkingConfig thinking = options.thinking();
        if (thinking != null) {
            if (thinking instanceof ThinkingConfig.Adaptive adp) {
                cmd.addAll(List.of("--thinking", "adaptive"));
                if (adp.display() != null) {
                    cmd.addAll(List.of("--thinking-display", adp.display().wireValue()));
                }
            } else if (thinking instanceof ThinkingConfig.Enabled en) {
                cmd.addAll(List.of("--max-thinking-tokens", String.valueOf(en.budgetTokens())));
                if (en.display() != null) {
                    cmd.addAll(List.of("--thinking-display", en.display().wireValue()));
                }
            } else if (thinking instanceof ThinkingConfig.Disabled) {
                cmd.addAll(List.of("--thinking", "disabled"));
            }
        } else if (options.maxThinkingTokens() != null) {
            cmd.addAll(List.of("--max-thinking-tokens", String.valueOf(options.maxThinkingTokens())));
        }
        if (options.effort() != null) {
            cmd.addAll(List.of("--effort", options.effort().wireValue()));
        }

        // output_format -> --json-schema
        Map<String, Object> outputFormat = options.outputFormat();
        if (outputFormat != null && "json_schema".equals(outputFormat.get("type"))) {
            Object schema = outputFormat.get("schema");
            if (schema != null) {
                try {
                    cmd.addAll(List.of("--json-schema", mapper.writeValueAsString(schema)));
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to serialize output_format schema", e);
                }
            }
        }

        // extra args
        for (var entry : options.extraArgs().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                cmd.add("--" + key);
            } else {
                cmd.addAll(List.of("--" + key, String.valueOf(value)));
            }
        }

        return cmd;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castToStrings(List<?> list) {
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) out.add(String.valueOf(o));
        return out;
    }

    private static Thread startVirtualThread(String name, Runnable r) {
        Thread t = Thread.ofVirtual().name(name).start(r);
        // Thread.ofVirtual() returns a Thread; cast happens implicitly.
        return t;
    }
}
