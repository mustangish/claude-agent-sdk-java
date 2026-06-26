package com.anthropic.claude.sdk.errors;

import java.util.Objects;

/**
 * 当 CLI 进程失败时抛出。
 *
 * <p>携带非零退出码（如果可用）和 stderr 片段用于诊断。
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
