package com.anthropic.claude.sdk.session;

/**
 * Result of a fork operation.
 *
 * <p>Mirrors the Python SDK's {@code ForkSessionResult} dataclass. Currently
 * a single-field wrapper around the new session id; the wrapper exists so
 * future fields (e.g. parent lineage, forked-at metadata) can be added
 * without breaking the API.
 */
public record ForkSessionResult(String sessionId) {
    /** Compact constructor that rejects null session ids. */
    public ForkSessionResult {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be null or blank");
        }
    }
}
