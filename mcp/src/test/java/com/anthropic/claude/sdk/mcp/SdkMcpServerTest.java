package com.anthropic.claude.sdk.mcp;

import com.anthropic.claude.sdk.types.McpServerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SdkMcpServerTest {

    public record GreetArgs(String name) {}

    @Test
    void builderRegistersAndInvokesTool() {
        var server = SdkMcpServer.builder("my-tools", "1.0.0")
            .tool("greet", "Greet a user", GreetArgs.class,
                args -> ToolResult.text("Hello, " + ((GreetArgs) args).name() + "!"))
            .build();

        assertThat(server.name()).isEqualTo("my-tools");
        assertThat(server.tools()).containsKey("greet");

        var tool = server.findTool("greet");
        assertThat(tool).isNotNull();
        ToolResult result = tool.handler().apply(new GreetArgs("Alice"));
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).get("text")).isEqualTo("Hello, Alice!");
    }

    @Test
    void builderRegistersMultipleTools() {
        var server = SdkMcpServer.builder("calc", "1.0")
            .tool("add", "Add two numbers", Map.class, args -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) args;
                double sum = ((Number) m.get("a")).doubleValue() + ((Number) m.get("b")).doubleValue();
                return ToolResult.text(String.valueOf(sum));
            })
            .tool("multiply", "Multiply", Map.class, args -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) args;
                double prod = ((Number) m.get("a")).doubleValue() * ((Number) m.get("b")).doubleValue();
                return ToolResult.text(String.valueOf(prod));
            })
            .build();

        assertThat(server.tools()).containsOnlyKeys("add", "multiply");
    }

    @Test
    void errorResultHasIsErrorFlag() {
        var result = ToolResult.error("bad input");
        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).get("text")).isEqualTo("bad input");
    }

    @Test
    void toConfigReturnsSdkServerConfig() {
        var server = SdkMcpServer.builder("calc", "1.0").build();
        McpServerConfig config = server.toConfig();
        assertThat(config).isInstanceOf(McpServerConfig.McpSdkServerConfig.class);
        var sdkConfig = (McpServerConfig.McpSdkServerConfig) config;
        assertThat(sdkConfig.name()).isEqualTo("calc");
        assertThat(sdkConfig.type()).isEqualTo("sdk");
        assertThat(sdkConfig.instance()).isSameAs(server);
    }

    @Test
    void registryFindsServers() {
        var registry = new SdkMcpRegistry();
        var server = SdkMcpServer.builder("test", "1.0").build();
        registry.register(server);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("test")).isSameAs(server);
        assertThat(registry.find("nope")).isNull();
    }
}
