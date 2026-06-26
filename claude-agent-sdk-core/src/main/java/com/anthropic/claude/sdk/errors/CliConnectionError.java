package com.anthropic.claude.sdk.errors;

/**
 * 当无法连接 Claude Code 时抛出。
 */
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
