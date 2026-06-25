package com.anthropic.claude.sdk;

import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;

/**
 * Public facade for the Claude Agent SDK.
 *
 * <p>Provides static factory methods that mirror the Python SDK's top-level functions.
 * For interactive, stateful conversations use {@code ClaudeSdkClient}.
 */
public final class ClaudeAgentSdk {

    private ClaudeAgentSdk() {}

    /**
     * Run a one-shot, unidirectional query against Claude Code.
     *
     * <p>Returns an {@link Iterable} of {@link com.anthropic.claude.sdk.types.Message}s.
     * The underlying CLI subprocess is spawned on iteration start (or explicit
     * {@link QuerySession#start()}) and terminated when iteration completes or
     * {@link QuerySession#close()} is called.
     *
     * <p>Example:
     * <pre>{@code
     * try (QuerySession session = ClaudeAgentSdk.query("What is 2+2?", ClaudeAgentOptions.builder().build())) {
     *     for (Message m : session) {
     *         // ...
     *     }
     * }
     * }</pre>
     */
    public static QuerySession query(String prompt, ClaudeAgentOptions options) {
        return new QuerySession(prompt, options, null);
    }

    public static QuerySession query(String prompt) {
        return query(prompt, ClaudeAgentOptions.builder().build());
    }

    /**
     * Run a query using a custom {@link Transport}. Useful for testing or for
     * non-default transports (e.g. remote CLI over SSH).
     */
    public static QuerySession queryWith(String prompt, Transport transport) {
        return new QuerySession(prompt, ClaudeAgentOptions.builder().build(), transport);
    }
}
