package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 权限更新配置。
 *
 * <p>对应 Python 控制协议的线缆格式。{@code type} 字段是鉴别器。
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
    /**
     * 创建一个 {@code addRules} 更新。
     *
     * @param rules  要添加的规则
     * @param behavior  行为（allow/deny/ask）
     * @param destination  目标（session/user/local/project）
     * @return 新的 PermissionUpdate
     */
    public static PermissionUpdate addRules(List<PermissionRuleValue> rules, PermissionBehavior behavior,
                                            PermissionUpdateDestination destination) {
        return new PermissionUpdate("addRules", rules, behavior, null, null, destination);
    }

    /**
     * 创建一个 {@code replaceRules} 更新。
     *
     * @param rules  新的规则集合
     * @param behavior  行为
     * @param destination  目标
     * @return 新的 PermissionUpdate
     */
    public static PermissionUpdate replaceRules(List<PermissionRuleValue> rules, PermissionBehavior behavior,
                                                PermissionUpdateDestination destination) {
        return new PermissionUpdate("replaceRules", rules, behavior, null, null, destination);
    }

    /**
     * 创建一个 {@code removeRules} 更新。
     *
     * @param rules  要删除的规则
     * @param behavior  行为
     * @param destination  目标
     * @return 新的 PermissionUpdate
     */
    public static PermissionUpdate removeRules(List<PermissionRuleValue> rules, PermissionBehavior behavior,
                                               PermissionUpdateDestination destination) {
        return new PermissionUpdate("removeRules", rules, behavior, null, null, destination);
    }

    /**
     * 创建一个 {@code setMode} 更新。
     *
     * @param mode  新的权限模式
     * @param destination  目标
     * @return 新的 PermissionUpdate
     */
    public static PermissionUpdate setMode(PermissionMode mode, PermissionUpdateDestination destination) {
        return new PermissionUpdate("setMode", null, null, mode, null, destination);
    }

    /**
     * 创建一个 {@code addDirectories} 更新。
     *
     * @param directories  要添加的目录列表
     * @param destination  目标
     * @return 新的 PermissionUpdate
     */
    public static PermissionUpdate addDirectories(List<String> directories, PermissionUpdateDestination destination) {
        return new PermissionUpdate("addDirectories", null, null, null, directories, destination);
    }

    /**
     * 创建一个 {@code removeDirectories} 更新。
     *
     * @param directories  要删除的目录列表
     * @param destination  目标
     * @return 新的 PermissionUpdate
     */
    public static PermissionUpdate removeDirectories(List<String> directories, PermissionUpdateDestination destination) {
        return new PermissionUpdate("removeDirectories", null, null, null, directories, destination);
    }

    /**
     * 转换为 CLI 期望的线缆协议字典格式。
     *
     * @return 表示此更新的线缆格式 map
     */
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
