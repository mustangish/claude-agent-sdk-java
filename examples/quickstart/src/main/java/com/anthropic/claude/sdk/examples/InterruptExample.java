package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Equivalent of Python's interrupt example using Java concurrent APIs. */
public class InterruptExample {
    public static void main(String[] args) throws Exception {
        try (ClaudeSdkClient client = new ClaudeSdkClient(ClaudeAgentOptions.builder().build())) {
            client.connect();
            client.query("Write a very long story about the history of computing");

            // Interrupt after 2 seconds
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(client::interrupt, 2, TimeUnit.SECONDS);

            client.receiveResponse().forEachRemaining(System.out::println);
            scheduler.shutdownNow();
        }
    }
}
