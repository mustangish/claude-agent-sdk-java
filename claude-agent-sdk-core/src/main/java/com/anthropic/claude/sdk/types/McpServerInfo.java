package com.anthropic.claude.sdk.types;

/** Server info from MCP initialize handshake (available when connected). */
public record McpServerInfo(String name, String version) {}
