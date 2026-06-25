package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.StreamEvent;

import java.util.Iterator;

/** Equivalent of Python's examples/include_partial_messages.py */
public class IncludePartialMessages {
    public static void main(String[] args) {
        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .includePartialMessages(true)
            .build();

        try (ClaudeSdkClient client = new ClaudeSdkClient(options)) {
            client.connect();
            client.query("Write a short poem");

            Iterator<Message> iter = client.receiveMessages();
            while (iter.hasNext()) {
                Message m = iter.next();
                if (m instanceof StreamEvent ev) {
                    // Partial message updates stream in as they're generated
                    System.out.println("[partial] " + ev.event());
                } else {
                    System.out.println(m);
                }
            }
        }
    }
}
