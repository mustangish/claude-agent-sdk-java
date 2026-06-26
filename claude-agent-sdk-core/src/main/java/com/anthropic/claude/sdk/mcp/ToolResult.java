package com.anthropic.claude.sdk.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * {@link Tool} 返回的结果。
 *
 * <p>对应 Python SDK 的 MCP 工具结果格式：类型化内容块（text、image、
 * audio、resource_link、embedded_resource）的列表。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(
    List<Map<String, Object>> content,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isError
) {

    /**
     * 创建一个纯文本内容块的结果。
     *
     * @param text  文本内容
     * @return 包含单个 text 类型块的结果
     */
    public static ToolResult text(String text) {
        return new ToolResult(List.of(Map.of("type", "text", "text", text)), null);
    }

    /**
     * 创建一个标记为错误的文本结果。
     *
     * @param message  错误消息
     * @return 包含单个 text 类型块且 {@code isError=true} 的结果
     */
    public static ToolResult error(String message) {
        return new ToolResult(List.of(Map.of("type", "text", "text", message)), true);
    }

    /**
     * 从原始内容块列表创建结果。
     *
     * @param blocks  内容块列表（每块是一个 map，至少包含 {@code type} 字段）
     * @return 包装后的结果
     */
    public static ToolResult blocks(List<Map<String, Object>> blocks) {
        return new ToolResult(blocks, null);
    }
}
