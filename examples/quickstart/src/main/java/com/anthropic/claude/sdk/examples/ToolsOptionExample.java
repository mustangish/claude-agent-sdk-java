package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.ToolsPreset;

import java.util.List;

/** Equivalent of Python's examples/tools_option.py */
public class ToolsOptionExample {
    public static void main(String[] args) {
        // Restrict to just Read + Glob
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .tools(List.of("Read", "Glob"))
            .build();

        try (QuerySession session = ClaudeAgentSdk.query(
                "List files in the current directory", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
