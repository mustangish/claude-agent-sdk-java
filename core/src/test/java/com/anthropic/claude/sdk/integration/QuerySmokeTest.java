package com.anthropic.claude.sdk.integration;

import com.anthropic.claude.sdk.ClaudeAgentSdk;
import com.anthropic.claude.sdk.query.QuerySession;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuerySmokeTest {

    private static Path fakeCli() {
        try {
            return Paths.get(QuerySmokeTest.class.getResource("/fake-claude.sh").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClaudeAgentOptions opts() {
        return ClaudeAgentOptions.builder().cliPath(fakeCli()).build();
    }

    @Test
    void queryReturnsTwoMessages() {
        List<Message> messages = new ArrayList<>();
        try (QuerySession session = ClaudeAgentSdk.query("Hello", opts())) {
            for (Message m : session) {
                messages.add(m);
            }
        }
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
        AssistantMessage a = (AssistantMessage) messages.get(0);
        assertThat(a.content().get(0)).isInstanceOf(TextBlock.class);
        assertThat(((TextBlock) a.content().get(0)).text()).isEqualTo("Hello from fake CLI!");
        assertThat(messages.get(1)).isInstanceOf(ResultMessage.class);
    }
}
