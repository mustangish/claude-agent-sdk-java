package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Definition of a custom subagent invokable via the Agent tool. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentDefinition(
    String description,
    String prompt,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> tools,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> disallowedTools,
    @JsonInclude(JsonInclude.Include.NON_NULL) String model,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> skills,
    @JsonInclude(JsonInclude.Include.NON_NULL) String memory,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Object> mcpServers,
    @JsonInclude(JsonInclude.Include.NON_NULL) String initialPrompt,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer maxTurns,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean background,
    @JsonInclude(JsonInclude.Include.NON_NULL) Object effort,
    @JsonInclude(JsonInclude.Include.NON_NULL) PermissionMode permissionMode
) {
    public static AgentDefinition of(String description, String prompt) {
        return new AgentDefinition(description, prompt, null, null, null, null, null, null, null, null, null, null, null);
    }
}
