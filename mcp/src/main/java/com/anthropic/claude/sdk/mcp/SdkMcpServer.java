package com.anthropic.claude.sdk.mcp;

import com.anthropic.claude.sdk.types.McpServerConfig;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * In-process SDK MCP server holding a set of named tools callable by Claude.
 *
 * <p>Build with the static {@link #builder(String, String)} method, then register tools
 * via {@link Builder#tool}. Use {@link #toConfig()} to obtain a {@link McpServerConfig}
 * that can be plugged into {@code ClaudeAgentOptions.mcpServers}.
 */
public final class SdkMcpServer {

    private final String name;
    private final String version;
    private final Map<String, ToolDefinition> tools;

    private SdkMcpServer(String name, String version, Map<String, ToolDefinition> tools) {
        this.name = name;
        this.version = version;
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
    }

    public String name() { return name; }
    public String version() { return version; }
    public Map<String, ToolDefinition> tools() { return tools; }

    public McpServerConfig.McpSdkServerConfig toConfig() {
        return new McpServerConfig.McpSdkServerConfig("sdk", name, this);
    }

    public static Builder builder(String name, String version) {
        return new Builder(name, version);
    }

    /** Look up a tool by name. */
    public ToolDefinition findTool(String toolName) {
        return tools.get(toolName);
    }

    /** Builder for {@link SdkMcpServer}. */
    public static final class Builder {
        private final String name;
        private final String version;
        private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

        Builder(String name, String version) {
            this.name = name;
            this.version = version;
        }

        /** Register a tool from a method annotated with {@link Tool}. */
        public Builder tool(Object instance, Method method) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) {
                throw new IllegalArgumentException("Method must be annotated with @Tool: " + method);
            }
            tools.put(annotation.name(), ToolDefinition.fromAnnotatedMethod(instance, method, annotation));
            return this;
        }

        /** Register a tool given its name, description, input type, and handler. */
        public Builder tool(String toolName, String description, Class<?> inputSchema,
                            Function<Object, ToolResult> handler) {
            tools.put(toolName, new ToolDefinition(toolName, description, inputSchema, handler));
            return this;
        }

        public SdkMcpServer build() {
            return new SdkMcpServer(name, version, tools);
        }
    }

    /** Tool metadata + invocation handler. */
    public record ToolDefinition(
        String name,
        String description,
        Class<?> inputSchema,
        Function<Object, ToolResult> handler
    ) {

        public static ToolDefinition fromAnnotatedMethod(Object instance, Method method, Tool annotation) {
            return new ToolDefinition(
                annotation.name(),
                annotation.description(),
                annotation.inputSchema(),
                args -> invokeSync(instance, method, args)
            );
        }

        private static ToolResult invokeSync(Object instance, Method method, Object args) {
            try {
                Object result = method.invoke(instance, args);
                if (result instanceof CompletableFuture<?> cf) {
                    return (ToolResult) cf.get();
                }
                return (ToolResult) result;
            } catch (Exception e) {
                return ToolResult.error("Tool invocation failed: " + e.getMessage());
            }
        }
    }
}
