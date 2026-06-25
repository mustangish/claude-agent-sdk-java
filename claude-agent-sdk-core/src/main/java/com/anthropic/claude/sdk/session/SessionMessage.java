package com.anthropic.claude.sdk.session;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A user or assistant message from a session transcript.
 *
 * <p>Mirrors the Python SDK's {@code SessionMessage} dataclass.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionMessage(
    String type,            // "user" or "assistant"
    String uuid,
    String sessionId,
    Object message,         // raw Anthropic API message dict
    @JsonInclude(JsonInclude.Include.NON_NULL) String parentToolUseId
) {}
