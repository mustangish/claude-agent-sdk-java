package com.anthropic.claude.sdk.types;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Callback invoked by the SDK to decide whether a tool call should be permitted. */
@FunctionalInterface
public interface CanUseTool {
    CompletionStage<PermissionResult> invoke(String toolName, Map<String, Object> toolInput, ToolPermissionContext context);

    static CanUseTool sync(Function<PermissionRequest, PermissionResult> fn) {
        return (toolName, input, ctx) -> {
            PermissionRequest req = new PermissionRequest(toolName, input, ctx);
            return CompletableFuture.completedFuture(fn.apply(req));
        };
    }

    record PermissionRequest(String toolName, Map<String, Object> toolInput, ToolPermissionContext context) {}
}
