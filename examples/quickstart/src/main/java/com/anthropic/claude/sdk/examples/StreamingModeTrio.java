package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;

import java.util.Iterator;

/** Java equivalent of Python's examples/streaming_mode_trio.py — uses standard threads. */
public class StreamingModeTrio {
    public static void main(String[] args) {
        try (ClaudeSdkClient client = new ClaudeSdkClient(ClaudeAgentOptions.builder().build())) {
            client.connect();
            client.query("Tell me a joke");

            Iterator<Message> iter = client.receiveResponse();
            while (iter.hasNext()) {
                System.out.println(iter.next());
            }
        }
    }
}
