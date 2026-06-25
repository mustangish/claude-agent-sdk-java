package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Information about a tool provided by an MCP server. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolInfo(
    String name,
    @JsonInclude(JsonInclude.Include.NON_NULL) String description,
    @JsonInclude(JsonInclude.Include.NON_NULL) McpToolAnnotations annotations
) {}
