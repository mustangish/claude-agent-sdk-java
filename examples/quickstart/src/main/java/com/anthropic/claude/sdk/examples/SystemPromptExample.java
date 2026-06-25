package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;

/** Equivalent of Python's examples/system_prompt.py */
public class SystemPromptExample {
    public static void main(String[] args) {
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .systemPrompt("You are a senior backend engineer who explains things concisely.")
            .build();

        try (QuerySession session = ClaudeAgentSdk.query(
                "Explain what a connection pool is", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
