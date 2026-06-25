package com.anthropic.claude.sdk.session;

import com.anthropic.claude.sdk.internal.JacksonSupport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.SessionStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Materialized session resume support.
 *
 * <p>When {@code ClaudeAgentOptions.resume} or {@code continueConversation} is set, the SDK
 * needs to load prior transcript entries and present them to the new CLI subprocess so the
 * conversation can continue. {@code SessionResume} handles loading from local disk or a
 * {@link SessionStore}.
 */
public final class SessionResume {

    private SessionResume() {}

    /**
     * A materialized resume: transcript entries staged for the CLI subprocess.
     *
     * <p>{@code transcriptPath} is the JSONL file written into a temp dir; {@code configDir}
     * is the parent dir that should be passed to the subprocess via the
     * {@code CLAUDE_CONFIG_DIR} env var so the CLI can find the session at
     * {@code $CLAUDE_CONFIG_DIR/projects/&lt;projectKey&gt;/&lt;sessionId&gt;.jsonl}.
     */
    public record MaterializedResume(
        ClaudeAgentOptions overriddenOptions,
        Path transcriptPath,
        Path configDir  // parent dir for CLAUDE_CONFIG_DIR (null if no resume)
    ) {}

    /** Load entries for {@code sessionId} from the local disk and stage into a temp file. */
    public static CompletionStage<MaterializedResume> materialize(
        ClaudeAgentOptions options,
        String projectId,
        String sessionId
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path src = SessionMutations.defaultSessionsRoot().resolve(sessionId + ".jsonl");
                if (!Files.exists(src)) {
                    // No prior session — just return options unchanged, no resume.
                    return new MaterializedResume(options, null, null);
                }
                // Stage: create temp config dir + projects/<projectId>/<sessionId>.jsonl
                Path configDir = Files.createTempDirectory("claude-config-");
                Path projectDir = configDir.resolve("projects").resolve(projectId);
                Files.createDirectories(projectDir);
                Path dest = projectDir.resolve(sessionId + ".jsonl");
                Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                ClaudeAgentOptions resolved = ClaudeAgentOptions.builder()
                    .resume(sessionId)
                    .build();
                return new MaterializedResume(resolved, dest, configDir);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to materialize session " + sessionId, e);
            }
        });
    }

    /** Load entries from a {@link SessionStore} and stage them. */
    public static CompletionStage<MaterializedResume> materializeFromStore(
        ClaudeAgentOptions options,
        SessionStore store,
        String projectKey,
        String sessionId
    ) {
        var key = new com.anthropic.claude.sdk.types.SessionKey(projectKey, sessionId, null);
        return store.load(key).thenApply(entries -> {
            if (entries == null || entries.isEmpty()) {
                return new MaterializedResume(options, null, null);
            }
            try {
                // Stage: create temp config dir + projects/<projectKey>/<sessionId>.jsonl
                Path configDir = Files.createTempDirectory("claude-config-");
                Path projectDir = configDir.resolve("projects").resolve(projectKey);
                Files.createDirectories(projectDir);
                Path dest = projectDir.resolve(sessionId + ".jsonl");
                try (var writer = Files.newBufferedWriter(dest)) {
                    for (var entry : entries) {
                        writer.write(toJson(entry));
                        writer.newLine();
                    }
                }
                ClaudeAgentOptions resolved = ClaudeAgentOptions.builder()
                    .resume(sessionId)
                    .build();
                return new MaterializedResume(resolved, dest, configDir);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to materialize session " + sessionId, e);
            }
        });
    }

    private static String toJson(com.anthropic.claude.sdk.types.SessionStore.SessionStoreEntry entry) {
        // Minimal serialization — Python SDK uses json.dumps on data map
        if (entry.data() == null) return "{}";
        try {
            return JacksonSupport.mapper().writeValueAsString(entry.data());
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Apply materialized options to the original options (override resume/env/etc). */
    public static ClaudeAgentOptions applyMaterializedOptions(
        ClaudeAgentOptions original,
        MaterializedResume materialized
    ) {
        // Build a copy of original preserving all fields, with materialized resume override.
        var b = ClaudeAgentOptions.builder();
        if (original.tools() != null) {
            if (original.tools() instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<String> toolsList = (java.util.List<String>) original.tools();
                b.tools(toolsList);
            }
        }
        b.allowedTools(original.allowedTools());
        if (original.systemPrompt() != null) {
            ClaudeAgentOptions.SystemPrompt sp = original.systemPrompt();
            if (sp instanceof ClaudeAgentOptions.SystemPrompt.StringPrompt sps) {
                b.systemPrompt(sps.text());
            } else if (sp instanceof ClaudeAgentOptions.SystemPrompt.PresetPrompt spp) {
                b.systemPrompt(spp.preset());
            } else if (sp instanceof ClaudeAgentOptions.SystemPrompt.FilePrompt spf) {
                b.systemPrompt(spf.file());
            }
        }
        if (original.mcpServers() != null) b.mcpServers(original.mcpServers());
        b.strictMcpConfig(original.strictMcpConfig());
        if (original.permissionMode() != null) b.permissionMode(original.permissionMode());
        b.continueConversation(original.continueConversation());
        b.resume(materialized.overriddenOptions.resume() != null
            ? materialized.overriddenOptions.resume()
            : original.resume());
        if (original.sessionId() != null) b.sessionId(original.sessionId());
        if (original.maxTurns() != null) b.maxTurns(original.maxTurns());
        if (original.maxBudgetUsd() != null) b.maxBudgetUsd(original.maxBudgetUsd());
        b.disallowedTools(original.disallowedTools());
        if (original.model() != null) b.model(original.model());
        if (original.fallbackModel() != null) b.fallbackModel(original.fallbackModel());
        b.betas(original.betas());
        if (original.permissionPromptToolName() != null) b.permissionPromptToolName(original.permissionPromptToolName());
        if (original.cwd() != null) b.cwd(original.cwd());
        if (original.cliPath() != null) b.cliPath(original.cliPath());
        if (original.settings() != null) b.settings(original.settings());
        b.addDirs(original.addDirs());
        if (original.env() != null) b.env(original.env());
        if (original.extraArgs() != null) b.extraArgs(original.extraArgs());
        if (original.maxBufferSize() != null) b.maxBufferSize(original.maxBufferSize());
        if (original.stderr() != null) b.stderr(original.stderr());
        if (original.canUseTool() != null) b.canUseTool(original.canUseTool());
        if (original.hooks() != null) b.hooks(original.hooks());
        if (original.user() != null) b.user(original.user());
        b.includePartialMessages(original.includePartialMessages());
        b.includeHookEvents(original.includeHookEvents());
        b.forkSession(original.forkSession());
        if (original.agents() != null) b.agents(original.agents());
        if (original.settingSources() != null) b.settingSources(original.settingSources());
        if (original.skills() != null) b.skills(original.skills());
        if (original.sandbox() != null) b.sandbox(original.sandbox());
        b.plugins(original.plugins());
        if (original.maxThinkingTokens() != null) b.maxThinkingTokens(original.maxThinkingTokens());
        if (original.thinking() != null) b.thinking(original.thinking());
        if (original.effort() != null) b.effort(original.effort());
        if (original.outputFormat() != null) b.outputFormat(original.outputFormat());
        b.enableFileCheckpointing(original.enableFileCheckpointing());
        if (original.sessionStore() != null) b.sessionStore(original.sessionStore());
        if (original.sessionStoreFlush() != null) b.sessionStoreFlush(original.sessionStoreFlush());
        b.loadTimeoutMs(original.loadTimeoutMs());
        if (original.taskBudget() != null) b.taskBudget(original.taskBudget());
        return b.build();
    }

    /** Cleanup temporary files (config dir tree). */
    public static void cleanup(MaterializedResume materialized) {
        if (materialized == null) return;
        if (materialized.configDir() != null) {
            // Recursive delete of the temp config dir
            try (var walk = Files.walk(materialized.configDir())) {
                walk.sorted((a, b) -> b.compareTo(a))  // reverse order → files before dirs
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (java.io.IOException ignored) {}
                    });
            } catch (java.io.IOException ignored) {}
        }
        // Backwards-compat: if only transcriptPath is set (older MaterializedResume instances), delete it too
        if (materialized.configDir() == null && materialized.transcriptPath() != null) {
            try { Files.deleteIfExists(materialized.transcriptPath()); }
            catch (java.io.IOException ignored) {}
        }
    }
}
