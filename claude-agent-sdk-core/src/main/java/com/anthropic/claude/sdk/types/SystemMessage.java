package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * 带 subtype 鉴别器和负载的系统消息。
 *
 * <p>子类型（HookEventMessage、TaskStartedMessage 等）位于同一个密封
 * 层次结构中。基类的 {@link #subtype()} + {@link #data()} 字段保持填充，
 * 以便 {@code instanceof SystemMessage} 仍然匹配。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "subtype", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
public sealed interface SystemMessage extends Message
    permits SystemMessage.GenericSystemMessage,
            SystemMessage.HookEventMessage,
            SystemMessage.TaskStartedMessage,
            SystemMessage.TaskProgressMessage,
            SystemMessage.TaskNotificationMessage,
            SystemMessage.TaskUpdatedMessage,
            SystemMessage.MirrorErrorMessage {

    String subtype();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Map<String, Object> data();

    /** Default implementation when no specific subtype applies. */
    record GenericSystemMessage(String subtype, Map<String, Object> data) implements SystemMessage {}

    /** Hook lifecycle event emitted when {@code includeHookEvents} is enabled. */
    record HookEventMessage(
        String subtype,
        String hookEventName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String sessionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String uuid,
        Map<String, Object> data
    ) implements SystemMessage {}

    record TaskStartedMessage(
        String subtype,
        String taskId,
        String description,
        String uuid,
        String sessionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String toolUseId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String taskType,
        Map<String, Object> data
    ) implements SystemMessage {}

    record TaskProgressMessage(
        String subtype,
        String taskId,
        String description,
        TaskUsage usage,
        String uuid,
        String sessionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String toolUseId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastToolName,
        Map<String, Object> data
    ) implements SystemMessage {}

    record TaskNotificationMessage(
        String subtype,
        String taskId,
        TaskNotificationStatus status,
        String outputFile,
        String summary,
        String uuid,
        String sessionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String toolUseId,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaskUsage usage,
        Map<String, Object> data
    ) implements SystemMessage {}

    record TaskUpdatedMessage(
        String subtype,
        String taskId,
        Map<String, Object> patch,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaskUpdatedStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String sessionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String uuid,
        Map<String, Object> data
    ) implements SystemMessage {}

    /** Non-fatal — fired when {@code SessionStore.append()} fails. */
    record MirrorErrorMessage(
        String subtype,
        @JsonInclude(JsonInclude.Include.NON_NULL) SessionKey key,
        String error,
        Map<String, Object> data
    ) implements SystemMessage {}
}
