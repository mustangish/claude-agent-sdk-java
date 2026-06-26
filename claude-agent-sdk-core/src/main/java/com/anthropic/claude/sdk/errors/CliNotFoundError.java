package com.anthropic.claude.sdk.errors;

/**
 * 当找不到或未安装 Claude Code 时抛出。
 *
 * <p>对应 Python 的 {@code CLINotFoundError}；消息中会附带
 * {@code cliPath}（如果已知）以便调试。
 */
public class CliNotFoundError extends CliConnectionError {
    private final String cliPath;

    public CliNotFoundError(String message) {
        this(message, null);
    }

    public CliNotFoundError(String message, String cliPath) {
        super(formatMessage(message, cliPath));
        this.cliPath = cliPath;
    }

    private static String formatMessage(String message, String cliPath) {
        return cliPath == null ? message : message + ": " + cliPath;
    }

    public String cliPath() {
        return cliPath;
    }
}
