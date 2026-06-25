package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.AssistantMessage;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.ContentBlock;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.TextBlock;

import java.util.Iterator;

/** Equivalent of Python's examples/streaming_mode.py */
public class StreamingMode {
    public static void main(String[] args) {
        try (ClaudeSdkClient client = new ClaudeSdkClient(ClaudeAgentOptions.builder().build())) {
            client.connect();
            client.query("Write a haiku about programming");

            Iterator<Message> iter = client.receiveResponse();
            while (iter.hasNext()) {
                Message msg = iter.next();
                if (msg instanceof AssistantMessage asst) {
                    for (ContentBlock block : asst.content()) {
                        if (block instanceof TextBlock tb) {
                            System.out.print(tb.text());
                        }
                    }
                    System.out.println();
                }
            }
        }
    }
}
