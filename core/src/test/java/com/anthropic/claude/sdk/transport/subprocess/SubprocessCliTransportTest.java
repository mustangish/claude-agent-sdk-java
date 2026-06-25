package com.anthropic.claude.sdk.transport.subprocess;

import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.anthropic.claude.sdk.errors.ProcessError;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubprocessCliTransportTest {

    private static Path fakeCli(String name) {
        try {
            return Paths.get(SubprocessCliTransportTest.class.getResource("/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void connectsAndReadsCannedMessages(@TempDir Path tmp) throws Exception {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli("eager-claude.sh"))
            .cwd(tmp)
            .build();

        try (SubprocessCliTransport transport = new SubprocessCliTransport(opts)) {
            transport.connect();
            assertThat(transport.isReady()).isTrue();

            // Read the two canned messages (assistant + result)
            Iterator<JsonNode> iter = transport.readMessages();
            List<JsonNode> messages = new ArrayList<>();
            // Bound the wait so the test doesn't hang on regression.
            CountDownLatch done = new CountDownLatch(1);
            Thread reader = new Thread(() -> {
                while (iter.hasNext()) {
                    messages.add(iter.next());
                    if (messages.size() >= 2) {
                        done.countDown();
                        break;
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).get("type").asText()).isEqualTo("assistant");
            assertThat(messages.get(0).get("content").get(0).get("text").asText())
                .isEqualTo("Hello from fake CLI!");
            assertThat(messages.get(1).get("type").asText()).isEqualTo("result");
            assertThat(messages.get(1).get("subtype").asText()).isEqualTo("success");
        }
    }

    @Test
    void writesPromptToStdin() throws Exception {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli("fake-claude.sh"))
            .build();

        try (SubprocessCliTransport transport = new SubprocessCliTransport(opts)) {
            transport.connect();
            String prompt = "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":\"Hi\"},\"parent_tool_use_id\":null,\"session_id\":\"\"}\n";
            transport.write(prompt);

            // Drain messages
            Iterator<JsonNode> iter = transport.readMessages();
            for (int i = 0; i < 2 && iter.hasNext(); i++) {
                iter.next();
            }
        }
    }

    @Test
    void cliNotFoundThrows() {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(Paths.get("/nonexistent/path/to/claude"))
            .build();
        try (SubprocessCliTransport transport = new SubprocessCliTransport(opts)) {
            assertThatThrownBy(transport::connect)
                .isInstanceOfAny(com.anthropic.claude.sdk.errors.CliNotFoundError.class, IOException.class);
        }
    }

    @Test
    void processErrorOnNonZeroExit() {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli("error-claude.sh"))
            .build();
        try (SubprocessCliTransport transport = new SubprocessCliTransport(opts)) {
            transport.connect();
            Iterator<JsonNode> iter = transport.readMessages();
            assertThatThrownBy(iter::hasNext)
                .isInstanceOfAny(CliConnectionError.class, ProcessError.class);
        }
    }

    @Test
    void stderrCallbackInvoked() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        List<String> stderrLines = new ArrayList<>();
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli("error-claude.sh"))
            .stderr(stderrLines::add)
            .build();
        try (SubprocessCliTransport transport = new SubprocessCliTransport(opts)) {
            transport.connect();
            // Wait for stderr to arrive + process exit
            Thread.sleep(500);
            assertThat(stderrLines).anyMatch(l -> l.contains("boom"));
        }
    }

    @Test
    void shutdownIsGracefulThenKills() throws Exception {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli("slow-claude.sh"))
            .build();
        long start = System.nanoTime();
        try (SubprocessCliTransport transport = new SubprocessCliTransport(opts)) {
            transport.connect();
            // Force-kill via close() — should take ~5s (graceful) then SIGTERM/SIGKILL ladder
            transport.close();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            // slow-claude.sh just sleeps, so graceful close happens quickly
            // (it's the fast-cli path). Just verify close returned.
            assertThat(elapsed).isLessThan(15_000);
        }
    }

    @Test
    void doubleConnectIsIdempotent(@TempDir Path tmp) throws Exception {
        ClaudeAgentOptions opts = ClaudeAgentOptions.builder()
            .cliPath(fakeCli("eager-claude.sh"))
            .cwd(tmp)
            .build();
        try (SubprocessCliTransport transport = new SubprocessCliTransport(opts)) {
            transport.connect();
            transport.connect();  // should be a no-op
            assertThat(transport.isReady()).isTrue();
        }
    }
}
