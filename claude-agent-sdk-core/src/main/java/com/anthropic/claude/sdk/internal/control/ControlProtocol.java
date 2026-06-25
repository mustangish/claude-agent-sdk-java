package com.anthropic.claude.sdk.internal.control;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Wire-protocol types for SDK ↔ CLI control channel.
 *
 * <p>Mirrors the Python SDK's SDKControlRequest/Response types (lines 388-414 of types.py).
 * The CLI sends {@code control_request} messages with a {@code subtype} discriminator; the SDK
 * responds with {@code control_response} messages correlated by {@code request_id}.
 */
public final class ControlProtocol {

    private ControlProtocol() {}

    /** SDK → CLI: introduce the SDK to the CLI before any user messages. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitializeRequest(
        String subtype,
        Map<String, Object> hooks,
        Map<String, Object> agents
    ) {
        public InitializeRequest() {
            this("initialize", null, null);
        }
    }

    /** CLI → SDK: Claude wants to use a tool that requires permission. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CanUseToolRequest(
        String subtype,
        String toolName,
        JsonNode input,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode permissionSuggestions,
        @JsonInclude(JsonInclude.Include.NON_NULL) String blockedPath,
        @JsonInclude(JsonInclude.Include.NON_NULL) String decisionReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title,
        @JsonInclude(JsonInclude.Include.NON_NULL) String displayName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        String toolUseId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String agentId
    ) implements ControlRequest {}

    /** CLI → SDK: a hook event fires; invoke user-supplied callbacks. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HookCallbackRequest(
        String subtype,
        String callbackId,
        JsonNode input,
        @JsonInclude(JsonInclude.Include.NON_NULL) String toolUseId
    ) implements ControlRequest {}

    /** SDK → CLI: a control response (success). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ControlSuccessResponse(
        String subtype,
        @com.fasterxml.jackson.annotation.JsonProperty("request_id") String requestId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object response
    ) {
        public ControlSuccessResponse(String requestId) {
            this("success", requestId, null);
        }
        public ControlSuccessResponse(String requestId, Object response) {
            this("success", requestId, response);
        }
    }

    /** SDK → CLI: a control response (error). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ControlErrorResponse(
        String subtype,
        @com.fasterxml.jackson.annotation.JsonProperty("request_id") String requestId,
        String error
    ) {
        public ControlErrorResponse(String requestId, String error) {
            this("error", requestId, error);
        }
    }

    /**
     * Polymorphic union of all control request subtypes the CLI can send to the SDK.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "subtype", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = CanUseToolRequest.class, name = "can_use_tool"),
        @JsonSubTypes.Type(value = HookCallbackRequest.class, name = "hook_callback"),
        @JsonSubTypes.Type(value = InterruptRequest.class, name = "interrupt")
    })
    public sealed interface ControlRequest
        permits CanUseToolRequest, HookCallbackRequest, InterruptRequest {
        String subtype();
    }

    /** CLI → SDK: interrupt the current turn. */
    public record InterruptRequest(String subtype) implements ControlRequest {
        public InterruptRequest() { this("interrupt"); }
    }
}
