package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Hook-specific outputs, dispatched by {@code hook_event_name} on the wire.
 *
 * <p>Field names are mapped to snake_case on the wire via the shared
 * {@code PropertyNamingStrategies.SNAKE_CASE} strategy configured on
 * {@link com.anthropic.claude.sdk.internal.JacksonSupport#mapper()}.
 * No per-field {@code @JsonProperty} annotations are needed.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "hook_event_name", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = HookSpecificOutput.PreToolUse.class,         name = "PreToolUse"),
    @JsonSubTypes.Type(value = HookSpecificOutput.PostToolUse.class,        name = "PostToolUse"),
    @JsonSubTypes.Type(value = HookSpecificOutput.PostToolUseFailure.class,name = "PostToolUseFailure"),
    @JsonSubTypes.Type(value = HookSpecificOutput.UserPromptSubmit.class, name = "UserPromptSubmit"),
    @JsonSubTypes.Type(value = HookSpecificOutput.SessionStart.class,     name = "SessionStart"),
    @JsonSubTypes.Type(value = HookSpecificOutput.Notification.class,     name = "Notification"),
    @JsonSubTypes.Type(value = HookSpecificOutput.SubagentStart.class,    name = "SubagentStart"),
    @JsonSubTypes.Type(value = HookSpecificOutput.PermissionRequest.class,name = "PermissionRequest")
})
public sealed interface HookSpecificOutput
    permits HookSpecificOutput.PreToolUse, HookSpecificOutput.PostToolUse,
            HookSpecificOutput.PostToolUseFailure, HookSpecificOutput.UserPromptSubmit,
            HookSpecificOutput.SessionStart, HookSpecificOutput.Notification,
            HookSpecificOutput.SubagentStart, HookSpecificOutput.PermissionRequest {

    record PreToolUse(
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String permissionDecision,   // "allow"|"deny"|"ask"|"defer"
        @JsonInclude(JsonInclude.Include.NON_NULL) String permissionDecisionReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode updatedInput,
        @JsonInclude(JsonInclude.Include.NON_NULL) String additionalContext
    ) implements HookSpecificOutput {}

    record PostToolUse(
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String additionalContext,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode updatedToolOutput,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode updatedMCPToolOutput
    ) implements HookSpecificOutput {}

    record PostToolUseFailure(
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String additionalContext
    ) implements HookSpecificOutput {}

    record UserPromptSubmit(
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String additionalContext
    ) implements HookSpecificOutput {}

    record SessionStart(
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String additionalContext
    ) implements HookSpecificOutput {}

    record Notification(
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String additionalContext
    ) implements HookSpecificOutput {}

    record SubagentStart(
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String additionalContext
    ) implements HookSpecificOutput {}

    record PermissionRequest(
        String hookEventName,
        JsonNode decision
    ) implements HookSpecificOutput {}
}
