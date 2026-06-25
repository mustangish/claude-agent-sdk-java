package com.anthropic.claude.sdk.errors;

/** Raised when unable to decode JSON from CLI output. */
public final class CliJsonDecodeError extends ClaudeSdkError {
    private final String line;
    private final Exception originalError;

    public CliJsonDecodeError(String line, Exception originalError) {
        super("Failed to decode JSON: " + truncate(line, 100) + "...", originalError);
        this.line = line;
        this.originalError = originalError;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    public String line() {
        return line;
    }

    public Exception originalError() {
        return originalError;
    }
}
