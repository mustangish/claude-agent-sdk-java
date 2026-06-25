package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Callback invoked on a hook event.
 *
 * <p>Full implementation lives in Phase 5 (Hooks). For Phase 1 we declare the type as a
 * functional interface that takes raw JSON input.
 */
@FunctionalInterface
public interface HookCallback {
    java.util.concurrent.CompletableFuture<HookJSONOutput> invoke(
        JsonNode input, String toolUseId, HookContext context);
}
