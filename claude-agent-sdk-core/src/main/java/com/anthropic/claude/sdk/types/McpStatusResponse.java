package com.anthropic.claude.sdk.types;

import java.util.List;

/** Response from {@code ClaudeSdkClient.getMcpStatus()}. */
public record McpStatusResponse(List<McpServerStatus> mcpServers) {}
