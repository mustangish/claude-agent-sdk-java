package com.anthropic.claude.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * 当无法从 CLI 输出解析 {@code Message} 时抛出。
 */
public final class MessageParseError extends ClaudeSdkError {
    private final JsonNode data;

    public MessageParseError(String message, JsonNode data) {
        super(message);
        this.data = data;
    }

    public MessageParseError(String message) {
        this(message, null);
    }

    public JsonNode data() {
        return data;
    }

    // equals/hashCode kept value-based on (message, data) for testability
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageParseError that)) return false;
        return Objects.equals(getMessage(), that.getMessage()) && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMessage(), data);
    }
}
