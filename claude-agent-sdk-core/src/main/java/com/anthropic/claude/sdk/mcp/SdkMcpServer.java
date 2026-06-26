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
 * 进程内 SDK MCP 服务器，保存一组可被 Claude 调用的具名工具。
 *
 * <p>使用静态的 {@link #builder(String, String)} 方法构建，然后通过
 * {@link Builder#tool} 注册工具。使用 {@link #toConfig()} 获取一个
 * {@link McpServerConfig}，可以挂到
 * {@code ClaudeAgentOptions.mcpServers} 上。
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

    /**
     * 按名称查找工具。
     *
     * @param toolName  工具名
     * @return 工具定义，如果未注册则返回 {@code null}
     */
    public ToolDefinition findTool(String toolName) {
        return tools.get(toolName);
    }

    /**
     * {@link SdkMcpServer} 的构建器。
     */
    public static final class Builder {
        private final String name;
        private final String version;
        private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

        Builder(String name, String version) {
            this.name = name;
            this.version = version;
        }

        /**
         * 从带有 {@link Tool} 注解的方法注册工具。
         *
         * @param instance  拥有该方法的对象实例
         * @param method  标注了 {@link Tool} 的方法
         * @return 当前构建器
         */
        public Builder tool(Object instance, Method method) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) {
                throw new IllegalArgumentException("Method must be annotated with @Tool: " + method);
            }
            tools.put(annotation.name(), ToolDefinition.fromAnnotatedMethod(instance, method, annotation));
            return this;
        }

        /**
         * 注册一个工具，给定其名称、描述、输入类型和处理函数。
         *
         * @param toolName  工具名（Claude 用于引用此工具）
         * @param description  人类可读的工具描述
         * @param inputSchema  输入类型（{@code Map.class} 用于动态 schema，
         *                     或 TypedDict 类用于更复杂的 schema）
         * @param handler  工具处理函数
         * @return 当前构建器
         */
        public Builder tool(String toolName, String description, Class<?> inputSchema,
                            Function<Object, ToolResult> handler) {
            tools.put(toolName, new ToolDefinition(toolName, description, inputSchema, handler));
            return this;
        }

        public SdkMcpServer build() {
            return new SdkMcpServer(name, version, tools);
        }
    }

    /**
     * 工具元数据 + 调用处理函数。
     */
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
