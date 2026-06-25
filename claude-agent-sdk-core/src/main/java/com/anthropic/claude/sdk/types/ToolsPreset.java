package com.anthropic.claude.sdk.types;

/** Tools preset configuration — use Claude Code's default toolset. */
public record ToolsPreset(String type, String preset) {
    public static ToolsPreset claudeCode() {
        return new ToolsPreset("preset", "claude_code");
    }
}
