package com.anthropic.claude.sdk.types;

import java.util.Map;

/** Tool use block — Claude is invoking a tool. */
public record ToolUseBlock(String id, String name, Map<String, Object> input) implements ContentBlock {}
