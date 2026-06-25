package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.SettingSource;

import java.util.List;

/** Equivalent of Python's examples/setting_sources.py */
public class SettingSourcesExample {
    public static void main(String[] args) {
        // Only load user settings, not project or local
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .settingSources(List.of(SettingSource.USER))
            .build();

        try (QuerySession session = ClaudeAgentSdk.query("Hello", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
