package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Thinking/reasoning configuration. Discriminated by {@code type}.
 *
 * <p>Field names are mapped to snake_case on the wire via the shared
 * {@code PropertyNamingStrategies.SNAKE_CASE} strategy configured on
 * {@link com.anthropic.claude.sdk.internal.JacksonSupport#mapper()}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ThinkingConfig.Adaptive.class, name = "adaptive"),
    @JsonSubTypes.Type(value = ThinkingConfig.Enabled.class, name = "enabled"),
    @JsonSubTypes.Type(value = ThinkingConfig.Disabled.class, name = "disabled")
})
public sealed interface ThinkingConfig {
    /** Claude decides when and how much to think (Opus 4.6+). */
    record Adaptive(ThinkingDisplay display) implements ThinkingConfig {
        public Adaptive() { this(null); }
    }

    /** Fixed thinking token budget. */
    record Enabled(
        int budgetTokens,
        ThinkingDisplay display
    ) implements ThinkingConfig {}

    /** No extended thinking. */
    record Disabled() implements ThinkingConfig {
        public static final Disabled INSTANCE = new Disabled();
    }
}
