package com.anthropic.claude.sdk.internal.message;

import com.anthropic.claude.sdk.types.AssistantMessage;
import com.anthropic.claude.sdk.types.ContentBlock;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.RateLimitEvent;
import com.anthropic.claude.sdk.types.ResultMessage;
import com.anthropic.claude.sdk.types.SystemMessage;
import com.anthropic.claude.sdk.types.TextBlock;
import com.anthropic.claude.sdk.types.ThinkingBlock;
import com.anthropic.claude.sdk.types.UserMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MessageParser parser = new MessageParser(mapper);

    @Test
    void parsesUserMessage() throws Exception {
        JsonNode json = mapper.readTree("""
            {"type":"user","message":{"role":"user","content":"Hello"}}""");
        Message m = parser.parse(json);
        assertThat(m).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) m).content()).isEqualTo("Hello");
    }

    @Test
    void parsesAssistantMessage() throws Exception {
        JsonNode json = mapper.readTree("""
            {"type":"assistant","model":"claude-sonnet-4-5",
             "content":[
               {"type":"text","text":"Hi there!"},
               {"type":"thinking","thinking":"reasoning...","signature":"sig-1"}
             ]}""");
        Message m = parser.parse(json);
        assertThat(m).isInstanceOf(AssistantMessage.class);
        AssistantMessage asst = (AssistantMessage) m;
        assertThat(asst.model()).isEqualTo("claude-sonnet-4-5");
        assertThat(asst.content()).hasSize(2);
        assertThat(asst.content().get(0)).isInstanceOf(TextBlock.class);
        assertThat(((TextBlock) asst.content().get(0)).text()).isEqualTo("Hi there!");
        assertThat(asst.content().get(1)).isInstanceOf(ThinkingBlock.class);
        assertThat(((ThinkingBlock) asst.content().get(1)).signature()).isEqualTo("sig-1");
    }

    @Test
    void parsesResultMessage() throws Exception {
        JsonNode json = mapper.readTree("""
            {"type":"result","subtype":"success","duration_ms":1234,"duration_api_ms":1100,
             "is_error":false,"num_turns":1,"session_id":"sess-1",
             "stop_reason":"end_turn","total_cost_usd":0.005}""");
        Message m = parser.parse(json);
        assertThat(m).isInstanceOf(ResultMessage.class);
        ResultMessage r = (ResultMessage) m;
        assertThat(r.subtype()).isEqualTo("success");
        assertThat(r.numTurns()).isEqualTo(1);
        assertThat(r.totalCostUsd()).isEqualTo(0.005);
    }

    @Test
    void parsesTaskStartedSystemMessage() throws Exception {
        JsonNode json = mapper.readTree("""
            {"type":"system","subtype":"task_started","task_id":"t-1","description":"explore",
             "uuid":"u-1","session_id":"sess-1"}""");
        Message m = parser.parse(json);
        assertThat(m).isInstanceOf(SystemMessage.TaskStartedMessage.class);
        SystemMessage.TaskStartedMessage ts = (SystemMessage.TaskStartedMessage) m;
        assertThat(ts.taskId()).isEqualTo("t-1");
        assertThat(ts.description()).isEqualTo("explore");
    }

    @Test
    void parsesHookEventSystemMessage() throws Exception {
        JsonNode json = mapper.readTree("""
            {"type":"system","subtype":"hook_started","hook_event":"PreToolUse",
             "session_id":"sess-1","uuid":"u-1"}""");
        Message m = parser.parse(json);
        assertThat(m).isInstanceOf(SystemMessage.HookEventMessage.class);
        SystemMessage.HookEventMessage h = (SystemMessage.HookEventMessage) m;
        assertThat(h.hookEventName()).isEqualTo("PreToolUse");
    }

    @Test
    void parsesRateLimitEvent() throws Exception {
        JsonNode json = mapper.readTree("""
            {"type":"rate_limit_event","uuid":"u-1","session_id":"sess-1",
             "rate_limit_info":{"status":"allowed_warning","resets_at":1700000000}}""");
        Message m = parser.parse(json);
        assertThat(m).isInstanceOf(RateLimitEvent.class);
    }

    @Test
    void unknownTypeReturnsNull() throws Exception {
        JsonNode json = mapper.readTree("""
            {"type":"unknown_thing","foo":"bar"}""");
        assertThat(parser.parse(json)).isNull();
    }

    @Test
    void nullOrNonObjectReturnsNull() {
        assertThat(parser.parse(null)).isNull();
    }
}
