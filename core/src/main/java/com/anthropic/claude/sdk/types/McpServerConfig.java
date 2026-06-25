package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;

/** Discriminated union for MCP server configurations. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.PROPERTY,
              defaultImpl = McpServerConfig.McpStdioServerConfig.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = McpServerConfig.McpStdioServerConfig.class, name = "stdio"),
    @JsonSubTypes.Type(value = McpServerConfig.McpSSEServerConfig.class, name = "sse"),
    @JsonSubTypes.Type(value = McpServerConfig.McpHttpServerConfig.class, name = "http"),
    @JsonSubTypes.Type(value = McpServerConfig.McpSdkServerConfig.class, name = "sdk")
})
public sealed interface McpServerConfig
    permits McpServerConfig.McpStdioServerConfig,
            McpServerConfig.McpSSEServerConfig,
            McpServerConfig.McpHttpServerConfig,
            McpServerConfig.McpSdkServerConfig {

    record McpStdioServerConfig(
        @JsonInclude(JsonInclude.Include.NON_NULL) String type,
        String command,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> args,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> env
    ) implements McpServerConfig {
        public McpStdioServerConfig(String command, List<String> args, Map<String, String> env) {
            this("stdio", command, args, env);
        }
    }

    record McpSSEServerConfig(String type, String url,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> headers
    ) implements McpServerConfig {
        public McpSSEServerConfig(String url, Map<String, String> headers) {
            this("sse", url, headers);
        }
    }

    record McpHttpServerConfig(String type, String url,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> headers
    ) implements McpServerConfig {
        public McpHttpServerConfig(String url, Map<String, String> headers) {
            this("http", url, headers);
        }
    }

    /** In-process SDK MCP server — references an actual Java {@code McpServer} instance. */
    record McpSdkServerConfig(String type, String name, Object instance) implements McpServerConfig {
        public McpSdkServerConfig(String name, Object instance) {
            this("sdk", name, instance);
        }
    }
}
