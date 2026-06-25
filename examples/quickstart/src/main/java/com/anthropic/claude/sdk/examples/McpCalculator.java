package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.mcp.SdkMcpServer;
import com.anthropic.claude.sdk.mcp.ToolResult;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;

import java.util.List;
import java.util.Map;

/** Equivalent of Python's examples/mcp_calculator.py */
public class McpCalculator {
    public static void main(String[] argv) {
        SdkMcpServer calc = SdkMcpServer.builder("calc", "1.0.0")
            .tool("add", "Add two numbers", Map.class, args -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) args;
                double sum = ((Number) m.get("a")).doubleValue() + ((Number) m.get("b")).doubleValue();
                return ToolResult.text(String.valueOf(sum));
            })
            .tool("subtract", "Subtract two numbers", Map.class, args -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) args;
                double diff = ((Number) m.get("a")).doubleValue() - ((Number) m.get("b")).doubleValue();
                return ToolResult.text(String.valueOf(diff));
            })
            .build();

        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .mcpServers(Map.of("calc", calc.toConfig()))
            .allowedTools(List.of("mcp__calc__add", "mcp__calc__subtract"))
            .build();

        try (QuerySession session = ClaudeAgentSdk.query("What is 42 + 17?", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
