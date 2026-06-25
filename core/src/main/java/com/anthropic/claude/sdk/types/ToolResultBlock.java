package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Tool result block — result of a tool invocation.
 *
 * <p>{@code content} is either a plain string or a list of typed content blocks (text/image/resource).
 * {@code isError} is optional and only present when the tool failed.
 */
public record ToolResultBlock(
    String toolUseId,
    @JsonInclude(JsonInclude.Include.NON_NULL) Object content,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isError
) implements ContentBlock {
    public static ToolResultBlock text(String toolUseId, String text) {
        return new ToolResultBlock(toolUseId, text, null);
    }

    public static ToolResultBlock error(String toolUseId, String text) {
        return new ToolResultBlock(toolUseId, text, true);
    }

    public static ToolResultBlock blocks(String toolUseId, List<Map<String, Object>> blocks) {
        return new ToolResultBlock(toolUseId, blocks, null);
    }
}
