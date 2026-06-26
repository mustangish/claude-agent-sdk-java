package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 钩子输入负载的多态联合类型，由 {@code hook_event_name} 字段分派。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "hook_event_name",
             include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = HookInput.PreToolUse.class,        name = "PreToolUse"),
    @JsonSubTypes.Type(value = HookInput.PostToolUse.class,       name = "PostToolUse"),
    @JsonSubTypes.Type(value = HookInput.PostToolUseFailure.class,name = "PostToolUseFailure"),
    @JsonSubTypes.Type(value = HookInput.UserPromptSubmit.class,  name = "UserPromptSubmit"),
    @JsonSubTypes.Type(value = HookInput.Stop.class,              name = "Stop"),
    @JsonSubTypes.Type(value = HookInput.SubagentStop.class,      name = "SubagentStop"),
    @JsonSubTypes.Type(value = HookInput.PreCompact.class,        name = "PreCompact"),
    @JsonSubTypes.Type(value = HookInput.Notification.class,      name = "Notification"),
    @JsonSubTypes.Type(value = HookInput.SubagentStart.class,     name = "SubagentStart"),
    @JsonSubTypes.Type(value = HookInput.PermissionRequest.class,name = "PermissionRequest")
})
public sealed interface HookInput
    permits HookInput.PreToolUse, HookInput.PostToolUse, HookInput.PostToolUseFailure,
            HookInput.UserPromptSubmit, HookInput.Stop, HookInput.SubagentStop,
            HookInput.PreCompact, HookInput.Notification, HookInput.SubagentStart,
            HookInput.PermissionRequest {

    String sessionId();
    String transcriptPath();
    String cwd();

    record PreToolUse(
        String sessionId, String transcriptPath, String cwd,
        String toolName, JsonNode toolInput, String toolUseId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String agentId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String agentType
    ) implements HookInput {}

    record PostToolUse(
        String sessionId, String transcriptPath, String cwd,
        String toolName, JsonNode toolInput, JsonNode toolResponse, String toolUseId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String agentId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String agentType
    ) implements HookInput {}

    record PostToolUseFailure(
        String sessionId, String transcriptPath, String cwd,
        String toolName, JsonNode toolInput, String toolUseId,
        String error,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isInterrupt
    ) implements HookInput {}

    record UserPromptSubmit(
        String sessionId, String transcriptPath, String cwd, String prompt
    ) implements HookInput {}

    record Stop(
        String sessionId, String transcriptPath, String cwd, boolean stopHookActive
    ) implements HookInput {}

    record SubagentStop(
        String sessionId, String transcriptPath, String cwd, boolean stopHookActive,
        String agentId, String agentTranscriptPath, String agentType
    ) implements HookInput {}

    record PreCompact(
        String sessionId, String transcriptPath, String cwd,
        String trigger,
        @JsonInclude(JsonInclude.Include.NON_NULL) String customInstructions
    ) implements HookInput {}

    record Notification(
        String sessionId, String transcriptPath, String cwd,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title,
        String notificationType
    ) implements HookInput {}

    record SubagentStart(
        String sessionId, String transcriptPath, String cwd,
        String agentId, String agentType
    ) implements HookInput {}

    record PermissionRequest(
        String sessionId, String transcriptPath, String cwd,
        String toolName, JsonNode toolInput,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode permissionSuggestions
    ) implements HookInput {}
}
