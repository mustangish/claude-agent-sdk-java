package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A single context usage category (system prompt, tools, messages, etc.). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextUsageCategory(
    String name,
    int tokens,
    String color,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isDeferred
) {}
