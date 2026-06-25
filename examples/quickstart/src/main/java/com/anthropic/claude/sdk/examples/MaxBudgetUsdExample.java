package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;

/** Equivalent of Python's examples/max_budget_usd.py */
public class MaxBudgetUsdExample {
    public static void main(String[] args) {
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .maxBudgetUsd(0.50)
            .maxTurns(20)
            .build();

        try (QuerySession session = ClaudeAgentSdk.query(
                "Research quantum computing advances in 2026", options)) {
            for (Message m : session) {
                System.out.println(m);
            }
        }
    }
}
