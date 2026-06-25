package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Terminal result message — emitted once per query at completion. */
public record ResultMessage(
    String subtype,
    int durationMs,
    int durationApiMs,
    boolean isError,
    int numTurns,
    String sessionId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String stopReason,
    @JsonInclude(JsonInclude.Include.NON_NULL) Double totalCostUsd,
    @JsonInclude(JsonInclude.Include.NON_NULL) java.util.Map<String, Object> usage,
    @JsonInclude(JsonInclude.Include.NON_NULL) String result,
    @JsonInclude(JsonInclude.Include.NON_NULL) Object structuredOutput,
    @JsonInclude(JsonInclude.Include.NON_NULL) java.util.Map<String, Object> modelUsage,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Object> permissionDenials,
    @JsonInclude(JsonInclude.Include.NON_NULL) DeferredToolUse deferredToolUse,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> errors,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer apiErrorStatus,
    @JsonInclude(JsonInclude.Include.NON_NULL) String uuid
) implements Message {}
