package com.anthropic.claude.sdk.types;

/** Hook event names. */
public enum HookEvent {
    PRE_TOOL_USE,
    POST_TOOL_USE,
    POST_TOOL_USE_FAILURE,
    USER_PROMPT_SUBMIT,
    STOP,
    SUBAGENT_STOP,
    PRE_COMPACT,
    NOTIFICATION,
    SUBAGENT_START,
    PERMISSION_REQUEST
}
