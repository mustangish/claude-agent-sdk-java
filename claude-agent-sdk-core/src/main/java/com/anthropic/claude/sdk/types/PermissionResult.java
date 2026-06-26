package com.anthropic.claude.sdk.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * {@code canUseTool} 回调结果的密封联合类型。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface PermissionResult permits PermissionResult.Allow, PermissionResult.Deny {

    /**
     * 允许工具调用的结果。
     *
     * @param behavior  固定为 {@code "allow"}
     * @param updatedInput  可选的修改后输入参数
     * @param updatedPermissions  可选的权限规则更新
     */
    record Allow(
        String behavior,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> updatedInput,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<PermissionUpdate> updatedPermissions
    ) implements PermissionResult {
        public Allow() { this("allow", null, null); }
        public Allow(Map<String, Object> updatedInput) { this("allow", updatedInput, null); }

        /**
         * 创建一个带权限规则更新的允许结果。
         *
         * @param perms  要追加的权限规则
         * @return 新的 Allow 结果
         */
        public static Allow withUpdatedPermissions(List<PermissionUpdate> perms) {
            return new Allow("allow", null, perms);
        }
    }

    /**
     * 拒绝工具调用的结果。
     *
     * @param behavior  固定为 {@code "deny"}
     * @param message  拒绝时显示给 Claude 的消息
     * @param interrupt  是否同时中断当前回合
     */
    record Deny(
        String behavior,
        String message,
        boolean interrupt
    ) implements PermissionResult {
        public Deny() { this("deny", "", false); }
        public Deny(String message) { this("deny", message, false); }
        public Deny(String message, boolean interrupt) { this("deny", message, interrupt); }
    }

    /**
     * 创建一个默认的允许结果。
     *
     * @return 新的 Allow 结果
     */
    static Allow allow() { return new Allow(); }

    /**
     * 创建一个带消息的拒绝结果。
     *
     * @param message  拒绝消息
     * @return 新的 Deny 结果
     */
    static Deny deny(String message) { return new Deny(message); }
}
