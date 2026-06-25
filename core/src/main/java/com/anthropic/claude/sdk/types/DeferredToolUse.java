package com.anthropic.claude.sdk.types;

import java.util.Map;

/**
 * Tool use that was deferred by a PreToolUse hook returning {@code "defer"}.
 */
public record DeferredToolUse(String id, String name, Map<String, Object> input) {}
