package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Async hook output — defers hook execution. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsyncHookJSONOutput(
    boolean async,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer asyncTimeout
) {
    public static AsyncHookJSONOutput defer(Integer timeoutMs) {
        return new AsyncHookJSONOutput(true, timeoutMs);
    }
}
