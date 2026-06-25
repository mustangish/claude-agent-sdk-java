package com.anthropic.claude.sdk.types;

/** System prompt loaded from a file path. */
public record SystemPromptFile(String type, String path) {
    public static SystemPromptFile of(String path) {
        return new SystemPromptFile("file", path);
    }
}
