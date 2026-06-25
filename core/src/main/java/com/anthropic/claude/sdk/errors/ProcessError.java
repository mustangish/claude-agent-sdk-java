package com.anthropic.claude.sdk.errors;

import java.util.Objects;

/**
 * Raised when the CLI process fails.
 *
 * <p>Carries the non-zero exit code (when available) and a stderr snippet for diagnostics.
 */
public final class ProcessError extends ClaudeSdkError {
    private final Integer exitCode;
    private final String stderr;

    public ProcessError(String message, Integer exitCode, String stderr) {
        super(formatMessage(message, exitCode, stderr));
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    private static String formatMessage(String message, Integer exitCode, String stderr) {
        Objects.requireNonNull(message, "message");
        StringBuilder sb = new StringBuilder(message);
        if (exitCode != null) {
            sb.append(" (exit code: ").append(exitCode).append(")");
        }
        if (stderr != null && !stderr.isEmpty()) {
            sb.append("\nError output: ").append(stderr);
        }
        return sb.toString();
    }

    public Integer exitCode() {
        return exitCode;
    }

    public String stderr() {
        return stderr;
    }
}
