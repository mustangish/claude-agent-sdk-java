package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Hook matcher configuration: pattern + list of callbacks + optional timeout.
 *
 * <p>The {@code matcher} is a tool-name pattern (e.g. {@code "Bash"} or {@code "Read|Edit"}).
 */
public record HookMatcher(
    @JsonInclude(JsonInclude.Include.NON_NULL) String matcher,
    List<HookCallback> hooks,
    @JsonInclude(JsonInclude.Include.NON_NULL) Double timeout
) {
    public HookMatcher(String matcher, List<HookCallback> hooks) {
        this(matcher, hooks, null);
    }

    public HookMatcher(String matcher, List<HookCallback> hooks, Double timeout) {
        this.matcher = matcher;
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
        this.timeout = timeout;
    }
}
