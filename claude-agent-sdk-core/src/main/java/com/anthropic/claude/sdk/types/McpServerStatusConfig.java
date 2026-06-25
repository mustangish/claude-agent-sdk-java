package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/** Broader union of MCP server configs including the output-only {@code claudeai-proxy} type. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = McpServerConfig.McpStdioServerConfig.class, name = "stdio"),
    @JsonSubTypes.Type(value = McpServerConfig.McpSSEServerConfig.class, name = "sse"),
    @JsonSubTypes.Type(value = McpServerConfig.McpHttpServerConfig.class, name = "http"),
    @JsonSubTypes.Type(value = McpSdkServerConfigStatus.class, name = "sdk"),
    @JsonSubTypes.Type(value = McpClaudeAIProxyServerConfig.class, name = "claudeai-proxy")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface McpServerStatusConfig {}
