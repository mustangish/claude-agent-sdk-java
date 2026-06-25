package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.AgentDefinition;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;

import java.util.List;
import java.util.Map;

/** Equivalent of Python's examples/filesystem_agents.py */
public class FilesystemAgents {
    public static void main(String[] args) {
        AgentDefinition codeReviewer = AgentDefinition.of(
            "Reviews code for quality issues",
            "Review files for bugs, style issues, and missing tests.");

        AgentDefinition docWriter = AgentDefinition.of(
            "Writes documentation",
            "Generate README and Javadoc for the project.");

        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .agents(Map.of(
                "code-reviewer", codeReviewer,
                "doc-writer", docWriter))
            .allowedTools(List.of("Read", "Glob", "Grep", "Write"))
            .build();

        try (QuerySession session = ClaudeAgentSdk.query(
                "Use code-reviewer on src/", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
