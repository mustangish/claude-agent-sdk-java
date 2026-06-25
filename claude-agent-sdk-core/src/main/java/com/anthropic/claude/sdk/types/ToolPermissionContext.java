package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Context information passed to {@code canUseTool} callbacks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolPermissionContext(
    @JsonInclude(JsonInclude.Include.NON_NULL) Object signal,
    List<PermissionUpdate> suggestions,
    String toolUseId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String agentId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String blockedPath,
    @JsonInclude(JsonInclude.Include.NON_NULL) String decisionReason,
    @JsonInclude(JsonInclude.Include.NON_NULL) String title,
    @JsonInclude(JsonInclude.Include.NON_NULL) String displayName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String description
) {
    public ToolPermissionContext {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public static ToolPermissionContext empty(String toolUseId) {
        return new ToolPermissionContext(null, List.of(), toolUseId, null, null, null, null, null, null);
    }
}
