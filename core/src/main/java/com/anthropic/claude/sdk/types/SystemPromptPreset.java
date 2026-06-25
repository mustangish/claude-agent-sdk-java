package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * System prompt preset configuration.
 *
 * <p>{@code excludeDynamicSections}: when true, strips per-user dynamic sections (working
 * directory, auto-memory, git status) from the system prompt so it stays static and cacheable
 * across users. The stripped content is re-injected into the first user message.
 */
public record SystemPromptPreset(
    String type,
    String preset,
    @JsonInclude(JsonInclude.Include.NON_NULL) String append,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean excludeDynamicSections
) {
    public static SystemPromptPreset claudeCode() {
        return new SystemPromptPreset("preset", "claude_code", null, null);
    }

    public static SystemPromptPreset claudeCode(String append) {
        return new SystemPromptPreset("preset", "claude_code", append, null);
    }

    public static SystemPromptPreset claudeCodeWithoutDynamicSections() {
        return new SystemPromptPreset("preset", "claude_code", null, true);
    }
}
