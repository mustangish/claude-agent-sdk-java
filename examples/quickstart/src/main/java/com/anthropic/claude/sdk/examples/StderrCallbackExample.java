package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Consumer;
import com.anthropic.claude.sdk.types.Message;

/** Equivalent of Python's examples/stderr_callback_example.py */
public class StderrCallbackExample {
    public static void main(String[] args) {
        Consumer<String> stderrLogger = line -> System.err.println("[CLI stderr] " + line);
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .stderr(stderrLogger)
            .build();

        try (QuerySession session = ClaudeAgentSdk.query("Hello", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
