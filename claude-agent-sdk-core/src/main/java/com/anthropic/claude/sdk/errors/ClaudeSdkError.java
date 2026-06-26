package com.anthropic.claude.sdk.errors;

/**
 * 所有 Claude SDK 错误的基异常。
 *
 * <p>所有 SDK 异常都是非受检的 {@link RuntimeException}（与 Python
 * SDK 的 {@code ClaudeSDKError(Exception)} 语义一致——Python 异常
 * 默认都是运行时异常）。
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
