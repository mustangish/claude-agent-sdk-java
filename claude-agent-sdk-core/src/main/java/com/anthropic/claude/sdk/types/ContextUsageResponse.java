package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Response from {@code ClaudeSdkClient.getContextUsage()}.
 *
 * <p>Mirrors the Python SDK's wire format from the CLI's {@code /context} command.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextUsageResponse(
    List<ContextUsageCategory> categories,
    int totalTokens,
    int maxTokens,
    int rawMaxTokens,
    double percentage,
    String model,
    boolean isAutoCompactEnabled,
    List<Map<String, Object>> memoryFiles,
    List<Map<String, Object>> mcpTools,
    List<Map<String, Object>> agents,
    List<List<Map<String, Object>>> gridRows,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer autoCompactThreshold,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> deferredBuiltinTools,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> systemTools,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> systemPromptSections,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> slashCommands,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> skills,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> messageBreakdown,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> apiUsage
) {}
