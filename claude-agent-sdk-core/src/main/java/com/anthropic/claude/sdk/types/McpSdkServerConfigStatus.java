package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Output-only MCP SDK server config (no {@code instance} field, used in status responses). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpSdkServerConfigStatus(String type, String name) {}
