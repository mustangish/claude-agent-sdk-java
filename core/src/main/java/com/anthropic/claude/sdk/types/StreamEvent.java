package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** Stream event for partial message updates during streaming. */
public record StreamEvent(
    String uuid,
    String sessionId,
    JsonNode event,
    @JsonInclude(JsonInclude.Include.NON_NULL) String parentToolUseId
) implements Message {}
