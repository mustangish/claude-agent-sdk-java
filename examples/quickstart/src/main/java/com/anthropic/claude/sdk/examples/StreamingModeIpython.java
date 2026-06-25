package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;

import java.util.Iterator;

/** Java equivalent of Python's examples/streaming_mode_ipython.py. */
public class StreamingModeIpython {
    public static void main(String[] args) {
        try (ClaudeSdkClient client = new ClaudeSdkClient(ClaudeAgentOptions.builder().build())) {
            client.connect();
            client.query("List the first 5 prime numbers");

            Iterator<Message> iter = client.receiveResponse();
            while (iter.hasNext()) System.out.println(iter.next());
        }
    }
}
