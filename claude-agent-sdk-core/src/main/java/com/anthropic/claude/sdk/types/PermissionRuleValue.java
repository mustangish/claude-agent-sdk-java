package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

/** A single permission rule. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PermissionRuleValue(String toolName, String ruleContent) {

    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("toolName", toolName);
        if (ruleContent != null) m.put("ruleContent", ruleContent);
        return m;
    }
}
