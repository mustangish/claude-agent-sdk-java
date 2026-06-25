package com.anthropic.claude.sdk.errors;

/**
 * Raised when Claude Code is not found or not installed.
 *
 * <p>Mirrors Python {@code CLINotFoundError}; includes the {@code cliPath} (when known)
 * in the message to help debugging.
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
