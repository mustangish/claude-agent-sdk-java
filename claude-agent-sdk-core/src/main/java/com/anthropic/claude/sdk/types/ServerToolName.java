package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Server-side tool names (tools executed server-side by the API). */
public enum ServerToolName {
    ADVISOR("advisor"),
    WEB_SEARCH("web_search"),
    WEB_FETCH("web_fetch"),
    CODE_EXECUTION("code_execution"),
    BASH_CODE_EXECUTION("bash_code_execution"),
    TEXT_EDITOR_CODE_EXECUTION("text_editor_code_execution"),
    TOOL_SEARCH_TOOL_REGEX("tool_search_tool_regex"),
    TOOL_SEARCH_TOOL_BM25("tool_search_tool_bm25");

    private final String wireValue;

    ServerToolName(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ServerToolName fromWire(String value) {
        for (ServerToolName s : values()) {
            if (s.wireValue.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown ServerToolName: " + value);
    }
}
