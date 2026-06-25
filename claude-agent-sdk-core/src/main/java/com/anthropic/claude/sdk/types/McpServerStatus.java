package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Status information for an MCP server connection. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerStatus(
    String name,
    McpServerConnectionStatus status,
    @JsonInclude(JsonInclude.Include.NON_NULL) McpServerInfo serverInfo,
    @JsonInclude(JsonInclude.Include.NON_NULL) String error,
    McpServerStatusConfig config,
    @JsonInclude(JsonInclude.Include.NON_NULL) String scope,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<McpToolInfo> tools
) {}
