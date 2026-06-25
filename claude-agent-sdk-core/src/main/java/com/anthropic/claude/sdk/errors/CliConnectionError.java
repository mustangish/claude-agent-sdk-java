package com.anthropic.claude.sdk.errors;

/** Raised when unable to connect to Claude Code. */
public class CliConnectionError extends ClaudeSdkError {
    public CliConnectionError() {
        super();
    }

    public CliConnectionError(String message) {
        super(message);
    }

    public CliConnectionError(String message, Throwable cause) {
        super(message, cause);
    }
}
