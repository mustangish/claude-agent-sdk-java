package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * A user message from the conversation transcript.
 *
 * <p>{@code content} is either a plain string or a list of {@link ContentBlock}.
 */
public record UserMessage(
    Object content,
    @JsonInclude(JsonInclude.Include.NON_NULL) String uuid,
    @JsonInclude(JsonInclude.Include.NON_NULL) String parentToolUseId,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> toolUseResult
) implements Message {
    public static UserMessage text(String content) {
        return new UserMessage(content, null, null, null);
    }

    public static UserMessage blocks(List<ContentBlock> blocks) {
        return new UserMessage(blocks, null, null, null);
    }
}
