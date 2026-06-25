package com.anthropic.claude.sdk.integration;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.AssistantMessage;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.ResultMessage;
import com.anthropic.claude.sdk.types.TextBlock;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeSdkClientSmokeTest {

    private static Path fakeCli() {
        try {
            return Paths.get(ClaudeSdkClientSmokeTest.class.getResource("/fake-claude.sh").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClaudeAgentOptions opts() {
        return ClaudeAgentOptions.builder().cliPath(fakeCli()).build();
    }

    @Test
    void clientConnectQueryReceiveResponse() {
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts())) {
            client.connect();
            client.query("Hello");

            List<Message> received = new ArrayList<>();
            Iterator<Message> iter = client.receiveResponse();
            while (iter.hasNext()) {
                received.add(iter.next());
            }

            assertThat(received).isNotEmpty();
            assertThat(received.get(received.size() - 1)).isInstanceOf(ResultMessage.class);
            assertThat(received).first().isInstanceOf(AssistantMessage.class);
        }
    }

    @Test
    void clientAutoCloseWithTryWithResources() {
        // The auto-close pattern (try-with-resources) should call disconnect().
        try (ClaudeSdkClient client = new ClaudeSdkClient(opts())) {
            client.connect();
            assertThat(client).isNotNull();
        }
        // If we get here, close() ran cleanly.
    }
}
