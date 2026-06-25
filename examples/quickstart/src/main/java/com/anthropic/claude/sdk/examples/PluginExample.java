package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.SdkPluginConfig;

import java.nio.file.Paths;
import java.util.List;

/** Equivalent of Python's examples/plugin_example.py */
public class PluginExample {
    public static void main(String[] args) {
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .plugins(List.of(SdkPluginConfig.local(Paths.get("/path/to/plugin").toString())))
            .build();

        try (QuerySession session = ClaudeAgentSdk.query("Hello", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
