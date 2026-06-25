package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Identifies a session transcript or subagent transcript in a store.
 */
public record SessionKey(
    String projectKey,
    String sessionId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String subpath
) {}
