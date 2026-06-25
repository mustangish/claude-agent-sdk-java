package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Claude.ai proxy MCP server config (output-only). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpClaudeAIProxyServerConfig(String type, String url, String id) {}
