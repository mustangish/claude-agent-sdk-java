package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Permission update configuration.
 *
 * <p>Mirrors the Python control protocol wire format. The {@code type} field is the discriminator.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PermissionUpdate(
    String type,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<PermissionRuleValue> rules,
    @JsonInclude(JsonInclude.Include.NON_NULL) PermissionBehavior behavior,
    @JsonInclude(JsonInclude.Include.NON_NULL) PermissionMode mode,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> directories,
    @JsonInclude(JsonInclude.Include.NON_NULL) PermissionUpdateDestination destination
) {
    public static PermissionUpdate addRules(List<PermissionRuleValue> rules, PermissionBehavior behavior,
                                            PermissionUpdateDestination destination) {
        return new PermissionUpdate("addRules", rules, behavior, null, null, destination);
    }

    public static PermissionUpdate replaceRules(List<PermissionRuleValue> rules, PermissionBehavior behavior,
                                                PermissionUpdateDestination destination) {
        return new PermissionUpdate("replaceRules", rules, behavior, null, null, destination);
    }

    public static PermissionUpdate removeRules(List<PermissionRuleValue> rules, PermissionBehavior behavior,
                                               PermissionUpdateDestination destination) {
        return new PermissionUpdate("removeRules", rules, behavior, null, null, destination);
    }

    public static PermissionUpdate setMode(PermissionMode mode, PermissionUpdateDestination destination) {
        return new PermissionUpdate("setMode", null, null, mode, null, destination);
    }

    public static PermissionUpdate addDirectories(List<String> directories, PermissionUpdateDestination destination) {
        return new PermissionUpdate("addDirectories", null, null, null, directories, destination);
    }

    public static PermissionUpdate removeDirectories(List<String> directories, PermissionUpdateDestination destination) {
        return new PermissionUpdate("removeDirectories", null, null, null, directories, destination);
    }

    /** Convert to the wire-protocol dict format the CLI expects. */
    public Map<String, Object> toDict() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("type", type);
        if (destination != null) result.put("destination", destination.wireValue());
        switch (type) {
            case "addRules", "replaceRules", "removeRules" -> {
                if (rules != null) {
                    result.put("rules", rules.stream().map(PermissionRuleValue::toDict).toList());
                }
                if (behavior != null) result.put("behavior", behavior.wireValue());
            }
            case "setMode" -> {
                if (mode != null) result.put("mode", mode.wireValue());
            }
            case "addDirectories", "removeDirectories" -> {
                if (directories != null) result.put("directories", directories);
            }
            default -> { /* unknown */ }
        }
        return result;
    }
}
