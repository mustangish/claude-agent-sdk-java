package com.anthropic.claude.sdk.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * Shared {@link ObjectMapper} configured for the Claude Agent SDK wire format.
 *
 * <p>The CLI emits JSON with snake_case keys (e.g. {@code session_id},
 * {@code transcript_path}, {@code hook_event_name}). The Java SDK's record
 * components are camelCase ({@code sessionId}, {@code transcriptPath},
 * {@code hookEventName}). A single {@link PropertyNamingStrategies#SNAKE_CASE}
 * strategy bridges the two — both directions, with no per-field
 * {@code @JsonProperty} annotations needed.
 *
 * <p>All SDK code that touches JSON must use {@link #mapper()}, not
 * {@code new ObjectMapper()}. The Python reference implementation is the
 * single source of truth for field names; the strategy here keeps the Java
 * side byte-compatible with it.
 */
public final class JacksonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JacksonSupport() {}

    /** Returns the shared {@link ObjectMapper} configured for the SDK wire format. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
