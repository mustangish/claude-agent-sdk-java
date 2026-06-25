package com.anthropic.claude.sdk.internal.message;

import com.anthropic.claude.sdk.errors.MessageParseError;
import com.anthropic.claude.sdk.types.AssistantMessage;
import com.anthropic.claude.sdk.types.AssistantMessageError;
import com.anthropic.claude.sdk.types.ContentBlock;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.RateLimitEvent;
import com.anthropic.claude.sdk.types.RateLimitInfo;
import com.anthropic.claude.sdk.types.RateLimitStatus;
import com.anthropic.claude.sdk.types.RateLimitType;
import com.anthropic.claude.sdk.types.ResultMessage;
import com.anthropic.claude.sdk.types.StreamEvent;
import com.anthropic.claude.sdk.types.SystemMessage;
import com.anthropic.claude.sdk.types.TaskNotificationStatus;
import com.anthropic.claude.sdk.types.TaskUpdatedStatus;
import com.anthropic.claude.sdk.types.TaskUsage;
import com.anthropic.claude.sdk.types.TextBlock;
import com.anthropic.claude.sdk.types.ThinkingBlock;
import com.anthropic.claude.sdk.types.UserMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses raw JSON messages from the CLI into typed {@link Message} instances.
 *
 * <p>Dispatches on the {@code "type"} discriminator field. Unrecognized types return null
 * (mirrors the Python SDK which yields {@code None} on unknown messages).
 */
public final class MessageParser {

    private final ObjectMapper mapper;

    public MessageParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Parse a raw CLI JSON message into a typed {@link Message}. Returns null for unknown types. */
    public Message parse(JsonNode data) {
        if (data == null || !data.isObject()) return null;
        JsonNode typeNode = data.get("type");
        if (typeNode == null || !typeNode.isTextual()) return null;
        String type = typeNode.asText();

        try {
            return switch (type) {
                case "user"            -> parseUserMessage(data);
                case "assistant"       -> parseAssistantMessage(data);
                case "system"          -> parseSystemMessage(data);
                case "result"          -> parseResultMessage(data);
                case "stream_event"    -> parseStreamEvent(data);
                case "rate_limit_event" -> parseRateLimitEvent(data);
                default -> null;
            };
        } catch (Exception e) {
            throw new MessageParseError("Failed to parse message of type " + type + ": " + e.getMessage(), data);
        }
    }

    private UserMessage parseUserMessage(JsonNode data) {
        // CLI wire format: {"type":"user","message":{"role":"user","content":"..."},"uuid":"...","parent_tool_use_id":"...","tool_use_result":{...}}
        JsonNode message = data.get("message");
        Object content = null;
        if (message != null && message.has("content")) {
            content = convertContent(message.get("content"));
        } else if (data.has("content")) {
            content = convertContent(data.get("content"));
        }
        String uuid = textOrNull(data, "uuid");
        String parentToolUseId = textOrNull(data, "parent_tool_use_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> toolUseResult = data.has("tool_use_result") && data.get("tool_use_result").isObject()
            ? mapper.convertValue(data.get("tool_use_result"), Map.class)
            : null;
        return new UserMessage(content, uuid, parentToolUseId, toolUseResult);
    }

    private AssistantMessage parseAssistantMessage(JsonNode data) {
        List<ContentBlock> blocks = new ArrayList<>();
        JsonNode content = data.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                ContentBlock cb = parseContentBlock(block);
                if (cb != null) blocks.add(cb);
            }
        }
        AssistantMessageError error = data.has("error") && data.get("error").isTextual()
            ? AssistantMessageError.fromWire(data.get("error").asText())
            : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = data.has("usage") && data.get("usage").isObject()
            ? mapper.convertValue(data.get("usage"), Map.class) : null;
        return new AssistantMessage(
            blocks,
            textOrNull(data, "model"),
            textOrNull(data, "parent_tool_use_id"),
            error,
            usage,
            textOrNull(data, "message_id"),
            textOrNull(data, "stop_reason"),
            textOrNull(data, "session_id"),
            textOrNull(data, "uuid")
        );
    }

    private SystemMessage parseSystemMessage(JsonNode data) {
        String subtype = textOrNull(data, "subtype");
        @SuppressWarnings("unchecked")
        Map<String, Object> baseData = data.isObject() ? mapper.convertValue(data, Map.class) : new HashMap<>();

        if (data.has("hook_event")) {
            return new SystemMessage.HookEventMessage(
                subtype,
                data.get("hook_event").asText(),
                textOrNull(data, "session_id"),
                textOrNull(data, "uuid"),
                baseData);
        }

        if (subtype == null) return new SystemMessage.GenericSystemMessage(null, baseData);

        return switch (subtype) {
            case "task_started" -> new SystemMessage.TaskStartedMessage(
                subtype,
                textOrNull(data, "task_id"),
                textOrNull(data, "description"),
                textOrNull(data, "uuid"),
                textOrNull(data, "session_id"),
                textOrNull(data, "tool_use_id"),
                textOrNull(data, "task_type"),
                baseData);
            case "task_progress" -> new SystemMessage.TaskProgressMessage(
                subtype,
                textOrNull(data, "task_id"),
                textOrNull(data, "description"),
                parseTaskUsage(data.get("usage")),
                textOrNull(data, "uuid"),
                textOrNull(data, "session_id"),
                textOrNull(data, "tool_use_id"),
                textOrNull(data, "last_tool_name"),
                baseData);
            case "task_notification" -> new SystemMessage.TaskNotificationMessage(
                subtype,
                textOrNull(data, "task_id"),
                data.has("status") && data.get("status").isTextual()
                    ? TaskNotificationStatus.fromWire(data.get("status").asText()) : null,
                textOrNull(data, "output_file"),
                textOrNull(data, "summary"),
                textOrNull(data, "uuid"),
                textOrNull(data, "session_id"),
                textOrNull(data, "tool_use_id"),
                data.has("usage") && data.get("usage").isObject() ? parseTaskUsage(data.get("usage")) : null,
                baseData);
            case "task_updated" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> patch = data.has("patch") && data.get("patch").isObject()
                    ? mapper.convertValue(data.get("patch"), Map.class) : Map.of();
                yield new SystemMessage.TaskUpdatedMessage(
                    subtype,
                    textOrNull(data, "task_id"),
                    patch,
                    data.has("status") && data.get("status").isTextual()
                        ? TaskUpdatedStatus.fromWire(data.get("status").asText()) : null,
                    textOrNull(data, "session_id"),
                    textOrNull(data, "uuid"),
                    baseData);
            }
            case "mirror_error" -> new SystemMessage.MirrorErrorMessage(
                subtype, null, textOrNull(data, "error"), baseData);
            default -> new SystemMessage.GenericSystemMessage(subtype, baseData);
        };
    }

    private TaskUsage parseTaskUsage(JsonNode usage) {
        if (usage == null || !usage.isObject()) return new TaskUsage(0, 0, 0);
        return new TaskUsage(
            usage.path("total_tokens").asInt(0),
            usage.path("tool_uses").asInt(0),
            usage.path("duration_ms").asInt(0));
    }

    private ResultMessage parseResultMessage(JsonNode data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = data.has("usage") && data.get("usage").isObject()
            ? mapper.convertValue(data.get("usage"), Map.class) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> modelUsage = data.has("modelUsage") && data.get("modelUsage").isObject()
            ? mapper.convertValue(data.get("modelUsage"), Map.class) : null;
        return new ResultMessage(
            textOrNull(data, "subtype"),
            data.path("duration_ms").asInt(0),
            data.path("duration_api_ms").asInt(0),
            data.path("is_error").asBoolean(false),
            data.path("num_turns").asInt(0),
            textOrNull(data, "session_id"),
            textOrNull(data, "stop_reason"),
            data.has("total_cost_usd") ? data.get("total_cost_usd").asDouble() : null,
            usage,
            textOrNull(data, "result"),
            data.has("structured_output") ? mapper.convertValue(data.get("structured_output"), Object.class) : null,
            modelUsage,
            null, null, null,
            data.has("api_error_status") ? data.get("api_error_status").asInt() : null,
            textOrNull(data, "uuid")
        );
    }

    private StreamEvent parseStreamEvent(JsonNode data) {
        return new StreamEvent(
            textOrNull(data, "uuid"),
            textOrNull(data, "session_id"),
            data.get("event"),
            textOrNull(data, "parent_tool_use_id")
        );
    }

    private RateLimitEvent parseRateLimitEvent(JsonNode data) {
        JsonNode info = data.get("rate_limit_info");
        if (info == null || !info.isObject()) {
            return new RateLimitEvent(
                new RateLimitInfo(RateLimitStatus.ALLOWED),
                textOrNull(data, "uuid"),
                textOrNull(data, "session_id"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = mapper.convertValue(info, Map.class);
        RateLimitInfo rateLimitInfo = new RateLimitInfo(
            info.has("status") && info.get("status").isTextual()
                ? RateLimitStatus.fromWire(info.get("status").asText()) : RateLimitStatus.ALLOWED,
            info.has("resets_at") && info.get("resets_at").isNumber() ? info.get("resets_at").asLong() : null,
            info.has("rate_limit_type") && info.get("rate_limit_type").isTextual()
                ? RateLimitType.fromWire(info.get("rate_limit_type").asText()) : null,
            info.has("utilization") && info.get("utilization").isNumber() ? info.get("utilization").asDouble() : null,
            null, null, null,
            raw
        );
        return new RateLimitEvent(rateLimitInfo,
            textOrNull(data, "uuid"),
            textOrNull(data, "session_id"));
    }

    private ContentBlock parseContentBlock(JsonNode block) {
        if (block == null || !block.isObject()) return null;
        String type = textOrNull(block, "type");
        if (type == null) return null;
        return switch (type) {
            case "text"     -> new TextBlock(textOrNull(block, "text"));
            case "thinking" -> new ThinkingBlock(textOrNull(block, "thinking"), textOrNull(block, "signature"));
            // tool_use/tool_result/server_tool_use/server_tool_result parsing simplified for MVP.
            default -> null;
        };
    }

    private Object convertContent(JsonNode content) {
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(textOrNull(block, "type"))) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(textOrNull(block, "text"));
                }
            }
            return sb.toString();
        }
        return content.asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return v.isNull() ? null : v.asText();
    }
}
