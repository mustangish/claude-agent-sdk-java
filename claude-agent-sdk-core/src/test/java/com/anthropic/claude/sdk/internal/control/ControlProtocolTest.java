package com.anthropic.claude.sdk.internal.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControlProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void canUseToolRequestRoundTrip() throws Exception {
        String wire = """
            {"type":"control_request","request_id":"r1",
             "request":{"subtype":"can_use_tool","tool_name":"Bash",
                        "input":{"command":"ls"},
                        "tool_use_id":"tu-1"}}""";
        JsonNode envelope = mapper.readTree(wire);
        assertThat(envelope.path("type").asText()).isEqualTo("control_request");

        JsonNode req = envelope.path("request");
        assertThat(req.path("subtype").asText()).isEqualTo("can_use_tool");
        assertThat(req.path("tool_name").asText()).isEqualTo("Bash");
        assertThat(req.path("tool_use_id").asText()).isEqualTo("tu-1");
        assertThat(req.path("input").path("command").asText()).isEqualTo("ls");
    }

    @Test
    void hookCallbackRequestStructure() throws Exception {
        String wire = """
            {"type":"control_request","request_id":"r2",
             "request":{"subtype":"hook_callback","callback_id":"0",
                        "input":{"session_id":"s-1","hook_event_name":"PreToolUse",
                                 "tool_name":"Bash","tool_input":{"command":"ls"}}}}""";
        JsonNode envelope = mapper.readTree(wire);
        assertThat(envelope.path("request").path("callback_id").asText()).isEqualTo("0");
    }

    @Test
    void controlSuccessResponseFormat() throws Exception {
        var resp = new ControlProtocol.ControlSuccessResponse("req-1", mapper.createObjectNode());
        JsonNode node = mapper.valueToTree(resp);
        assertThat(node.path("subtype").asText()).isEqualTo("success");
        assertThat(node.path("request_id").asText()).isEqualTo("req-1");
    }

    @Test
    void controlErrorResponseFormat() throws Exception {
        var resp = new ControlProtocol.ControlErrorResponse("req-2", "boom");
        JsonNode node = mapper.valueToTree(resp);
        assertThat(node.path("subtype").asText()).isEqualTo("error");
        assertThat(node.path("error").asText()).isEqualTo("boom");
    }
}
