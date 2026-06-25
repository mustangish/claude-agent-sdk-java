package com.anthropic.claude.sdk.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Result returned by an {@link Tool}.
 *
 * <p>Mirrors the Python SDK's MCP tool result shape: a list of typed content blocks
 * (text, image, audio, resource_link, embedded_resource).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(
    List<Map<String, Object>> content,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isError
) {

    public static ToolResult text(String text) {
        return new ToolResult(List.of(Map.of("type", "text", "text", text)), null);
    }

    public static ToolResult error(String message) {
        return new ToolResult(List.of(Map.of("type", "text", "text", message)), true);
    }

    public static ToolResult blocks(List<Map<String, Object>> blocks) {
        return new ToolResult(blocks, null);
    }
}
