package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip serialization tests for the core {@link Message} types.
 *
 * <p>Polymorphic dispatch of {@code Message} (resolving {@code type} field to the right subtype)
 * is implemented separately by {@code MessageParser} in Phase 4, not by Jackson default typing.
 * Here we test individual concrete-type serialization which matches the Python SDK's wire format.
 */
class MessageRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void userMessageTextRoundTrips() throws Exception {
        var original = UserMessage.text("Hello, world!");
        String json = mapper.writeValueAsString(original);
        var parsed = mapper.readValue(json, UserMessage.class);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void assistantMessageRoundTrips() throws Exception {
        var original = new AssistantMessage(
            List.of(new TextBlock("Sure, here is the answer.")),
            "claude-sonnet-4-5",
            null, null, null, null, "end_turn", null, null
        );
        String json = mapper.writeValueAsString(original);
        var parsed = mapper.readValue(json, AssistantMessage.class);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void resultMessageRoundTrips() throws Exception {
        var original = new ResultMessage(
            "success", 1234, 1100, false, 1, "sess-abc",
            "end_turn", 0.001234, Map.of("input_tokens", 100, "output_tokens", 50),
            "The answer is 42.", null, null, null, null, null, null, null
        );
        String json = mapper.writeValueAsString(original);
        var parsed = mapper.readValue(json, ResultMessage.class);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void thinkingConfigEnabledUsesSnakeCaseField() throws Exception {
        var original = new ThinkingConfig.Enabled(8192, ThinkingDisplay.SUMMARIZED);
        String json = mapper.writeValueAsString(original);
        // Wire format must use snake_case to match Python SDK
        assertThat(json).contains("\"budget_tokens\":8192");
        var parsed = mapper.readValue(json, ThinkingConfig.Enabled.class);
        assertThat(parsed.budgetTokens()).isEqualTo(8192);
    }

    @Test
    void permissionModeWireValue() throws Exception {
        String json = mapper.writeValueAsString(PermissionMode.ACCEPT_EDITS);
        assertThat(json).isEqualTo("\"acceptEdits\"");
        var parsed = mapper.readValue("\"bypassPermissions\"", PermissionMode.class);
        assertThat(parsed).isEqualTo(PermissionMode.BYPASS_PERMISSIONS);
    }

    @Test
    void toolResultBlockFactoryMethods() {
        var text = ToolResultBlock.text("tool-1", "result text");
        assertThat(text.isError()).isNull();
        assertThat(text.content()).isEqualTo("result text");

        var err = ToolResultBlock.error("tool-1", "something went wrong");
        assertThat(err.isError()).isTrue();
    }

    @Test
    void textBlockSerializedAsObjectWithTypeField() throws Exception {
        var block = new TextBlock("hello");
        String json = mapper.writeValueAsString(block);
        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"text\":\"hello\"");
    }

    @Test
    void thinkingBlockIncludesSignature() throws Exception {
        var block = new ThinkingBlock("let me think...", "sig-abc");
        String json = mapper.writeValueAsString(block);
        assertThat(json).contains("\"type\":\"thinking\"");
        assertThat(json).contains("\"signature\":\"sig-abc\"");
    }
}
