package com.anthropic.claude.sdk.types;

import java.util.Map;

/** Server-side tool use block (e.g. advisor, web_search, web_fetch). */
public record ServerToolUseBlock(String id, ServerToolName name, Map<String, Object> input) implements ContentBlock {}
