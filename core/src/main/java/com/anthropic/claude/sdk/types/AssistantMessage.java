package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** An assistant message containing one or more {@link ContentBlock}s. */
public record AssistantMessage(
    List<ContentBlock> content,
    String model,
    @JsonInclude(JsonInclude.Include.NON_NULL) String parentToolUseId,
    @JsonInclude(JsonInclude.Include.NON_NULL) AssistantMessageError error,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> usage,
    @JsonInclude(JsonInclude.Include.NON_NULL) String messageId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String stopReason,
    @JsonInclude(JsonInclude.Include.NON_NULL) String sessionId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String uuid
) implements Message {}
