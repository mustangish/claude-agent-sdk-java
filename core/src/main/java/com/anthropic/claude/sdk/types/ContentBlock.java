package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Discriminated union: text | thinking | tool_use | tool_result | server_tool_use | server_tool_result. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
    @JsonSubTypes.Type(value = ThinkingBlock.class, name = "thinking"),
    @JsonSubTypes.Type(value = ToolUseBlock.class, name = "tool_use"),
    @JsonSubTypes.Type(value = ToolResultBlock.class, name = "tool_result"),
    @JsonSubTypes.Type(value = ServerToolUseBlock.class, name = "server_tool_use"),
    @JsonSubTypes.Type(value = ServerToolResultBlock.class, name = "server_tool_result")
})
public sealed interface ContentBlock
    permits TextBlock, ThinkingBlock, ToolUseBlock, ToolResultBlock,
            ServerToolUseBlock, ServerToolResultBlock {}
