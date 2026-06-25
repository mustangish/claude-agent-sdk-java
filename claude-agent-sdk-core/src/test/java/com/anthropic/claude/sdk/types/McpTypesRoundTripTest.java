package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpTypesRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mcpServerStatusWithAllFields() throws Exception {
        var status = new McpServerStatus(
            "my-server",
            McpServerConnectionStatus.CONNECTED,
            new McpServerInfo("my-server", "1.0.0"),
            null,
            null,  // config omitted for round-trip simplicity
            "project",
            List.of(new McpToolInfo("add", "Add two numbers", null))
        );

        String json = mapper.writeValueAsString(status);
        assertThat(json).contains("\"name\":\"my-server\"");
        assertThat(json).contains("\"status\":\"connected\"");
        assertThat(json).contains("\"serverInfo\":{\"name\":\"my-server\",\"version\":\"1.0.0\"}");

        McpServerStatus parsed = mapper.readValue(json, McpServerStatus.class);
        assertThat(parsed.name()).isEqualTo("my-server");
        assertThat(parsed.status()).isEqualTo(McpServerConnectionStatus.CONNECTED);
        assertThat(parsed.serverInfo().name()).isEqualTo("my-server");
    }

    @Test
    void mcpStatusResponse() throws Exception {
        var resp = new McpStatusResponse(List.of(
            new McpServerStatus("srv-1", McpServerConnectionStatus.CONNECTED, null, null, null, null, null),
            new McpServerStatus("srv-2", McpServerConnectionStatus.FAILED, null, "boom", null, null, null)
        ));
        String json = mapper.writeValueAsString(resp);
        assertThat(json).contains("\"mcpServers\"");
        McpStatusResponse parsed = mapper.readValue(json, McpStatusResponse.class);
        assertThat(parsed.mcpServers()).hasSize(2);
        assertThat(parsed.mcpServers().get(1).error()).isEqualTo("boom");
    }

    @Test
    void contextUsageResponseRoundTrip() throws Exception {
        var resp = new ContextUsageResponse(
            List.of(new ContextUsageCategory("system", 1000, "blue", false)),
            5000,
            200000,
            200000,
            2.5,
            "claude-sonnet-4-5",
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null, null, null, null, null, null, null, null
        );
        String json = mapper.writeValueAsString(resp);
        assertThat(json).contains("\"totalTokens\":5000");
        assertThat(json).contains("\"percentage\":2.5");

        ContextUsageResponse parsed = mapper.readValue(json, ContextUsageResponse.class);
        assertThat(parsed.totalTokens()).isEqualTo(5000);
        assertThat(parsed.percentage()).isEqualTo(2.5);
    }
}
