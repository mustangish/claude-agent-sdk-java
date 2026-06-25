package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Thinking/reasoning configuration. Discriminated by {@code type}. */
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
        @JsonProperty("budget_tokens") int budgetTokens,
        ThinkingDisplay display
    ) implements ThinkingConfig {}

    /** No extended thinking. */
    record Disabled() implements ThinkingConfig {
        public static final Disabled INSTANCE = new Disabled();
    }
}
