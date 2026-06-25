package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Discriminated union of all messages emitted by the CLI.
 *
 * <p>Discriminator: {@code "type"} field with values {@code "user"}, {@code "assistant"},
 * {@code "system"}, {@code "result"}, {@code "stream_event"}, {@code "rate_limit_event"}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = SystemMessage.class, name = "system"),
    @JsonSubTypes.Type(value = ResultMessage.class, name = "result"),
    @JsonSubTypes.Type(value = StreamEvent.class, name = "stream_event"),
    @JsonSubTypes.Type(value = RateLimitEvent.class, name = "rate_limit_event")
})
public sealed interface Message
    permits UserMessage, AssistantMessage, SystemMessage,
            ResultMessage, StreamEvent, RateLimitEvent {}
