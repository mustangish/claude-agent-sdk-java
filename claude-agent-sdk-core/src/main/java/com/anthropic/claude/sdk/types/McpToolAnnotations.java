package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Tool annotations as returned in MCP server status. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolAnnotations(
    Boolean readOnly,
    Boolean destructive,
    Boolean openWorld
) {}
