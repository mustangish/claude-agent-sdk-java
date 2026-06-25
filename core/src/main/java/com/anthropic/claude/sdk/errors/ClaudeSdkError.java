package com.anthropic.claude.sdk.errors;

/**
 * Base exception for all Claude SDK errors.
 *
 * <p>All SDK exceptions are unchecked {@link RuntimeException}s (matches the Python
 * SDK's {@code ClaudeSDKError(Exception)} semantic — Python exceptions are all
 * runtime by default).
 */
public class ClaudeSdkError extends RuntimeException {
    public ClaudeSdkError() {
        super();
    }

    public ClaudeSdkError(String message) {
        super(message);
    }

    public ClaudeSdkError(String message, Throwable cause) {
        super(message, cause);
    }
}
