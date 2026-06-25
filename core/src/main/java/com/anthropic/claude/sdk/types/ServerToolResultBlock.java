package com.anthropic.claude.sdk.types;

import java.util.Map;

/** Result block returned for a server-side tool call. */
public record ServerToolResultBlock(String toolUseId, Map<String, Object> content) implements ContentBlock {}
