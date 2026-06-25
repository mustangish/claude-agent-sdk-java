package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.HookEvent;
import com.anthropic.claude.sdk.types.HookJSONOutput;
import com.anthropic.claude.sdk.types.HookMatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Equivalent of Python's examples/hooks.py — PreToolUse hook that blocks dangerous bash. */
public class HooksExample {
    public static void main(String[] args) {
        HookMatcher matcher = new HookMatcher(
            "Bash",
            List.of((input, toolUseId, ctx) ->
                CompletableFuture.completedFuture(
                    HookJSONOutput.Sync.block("Bash is disabled in this environment"))));

        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .hooks(Map.of(HookEvent.PRE_TOOL_USE, List.of(matcher)))
            .build();

        try (ClaudeSdkClient client = new ClaudeSdkClient(options)) {
            client.connect();
            client.query("List files in /tmp");
            client.receiveResponse().forEachRemaining(System.out::println);
        }
    }
}
