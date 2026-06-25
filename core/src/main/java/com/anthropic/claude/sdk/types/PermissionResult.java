package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** Sealed union for {@code canUseTool} callback results. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface PermissionResult permits PermissionResult.Allow, PermissionResult.Deny {

    record Allow(
        String behavior,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> updatedInput,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<PermissionUpdate> updatedPermissions
    ) implements PermissionResult {
        public Allow() { this("allow", null, null); }
        public Allow(Map<String, Object> updatedInput) { this("allow", updatedInput, null); }
        public static Allow withUpdatedPermissions(List<PermissionUpdate> perms) {
            return new Allow("allow", null, perms);
        }
    }

    record Deny(
        String behavior,
        String message,
        boolean interrupt
    ) implements PermissionResult {
        public Deny() { this("deny", "", false); }
        public Deny(String message) { this("deny", message, false); }
        public Deny(String message, boolean interrupt) { this("deny", message, interrupt); }
    }

    static Allow allow() { return new Allow(); }
    static Deny deny(String message) { return new Deny(message); }
}
