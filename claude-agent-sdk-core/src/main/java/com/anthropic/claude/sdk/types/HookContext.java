package com.anthropic.claude.sdk.types;

/** Context for hook callbacks. Currently a placeholder for future abort-signal support. */
public record HookContext(Object signal) {
    public static final HookContext EMPTY = new HookContext(null);
}
