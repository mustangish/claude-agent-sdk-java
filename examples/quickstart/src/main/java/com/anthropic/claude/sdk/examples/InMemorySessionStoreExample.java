package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.session.InMemorySessionStore;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;

/** Equivalent of Python's examples/session_stores/in_memory.py */
public class InMemorySessionStoreExample {
    public static void main(String[] args) {
        var store = new InMemorySessionStore();
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .sessionStore(store)
            .build();

        try (QuerySession session = ClaudeAgentSdk.query("Hello", options)) {
            for (Message m : session) System.out.println(m);
        }

        System.out.println("Sessions stored: " + store.sessionCount());
    }
}
