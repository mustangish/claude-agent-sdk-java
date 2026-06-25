package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Polymorphic hook output: either async (defers execution) or sync (immediate).
 *
 * <p>Full sealed hierarchy implemented in Phase 5.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface HookJSONOutput permits HookJSONOutput.Async, HookJSONOutput.Sync {

    /** Async hook — set {@code async = true} to defer execution. */
    record Async(boolean async, @JsonInclude(JsonInclude.Include.NON_NULL) Integer asyncTimeout) implements HookJSONOutput {}

    /** Sync hook output with control fields. */
    record Sync(
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean continue_,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean suppressOutput,
        @JsonInclude(JsonInclude.Include.NON_NULL) String stopReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String decision,
        @JsonInclude(JsonInclude.Include.NON_NULL) String systemMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> hookSpecificOutput
    ) implements HookJSONOutput {
        public static Sync allow() {
            return new Sync(null, null, null, null, null, null, null);
        }
        public static Sync block(String reason) {
            return new Sync(false, null, "Blocked by hook", "block", null, reason, null);
        }
    }
}
