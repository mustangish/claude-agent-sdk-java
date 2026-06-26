package com.anthropic.claude.sdk.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * 进程内 SDK MCP 服务器的注册表，以名称为键。
 *
 * <p>由 {@code InternalQuery.handleSdkMcpRequest} 使用，用于查找
 * 给定 JSONRPC 方法所属的服务器。
 */
public final class SdkMcpRegistry {

    private final Map<String, SdkMcpServer> servers = new HashMap<>();

    /**
     * 注册一个 SDK MCP 服务器。
     *
     * @param server  要注册的服务器（以其 {@code name} 为键）
     */
    public void register(SdkMcpServer server) {
        servers.put(server.name(), server);
    }

    /**
     * 按名称查找已注册的服务器。
     *
     * @param name  服务器名称
     * @return 服务器实例，如果未注册则返回 {@code null}
     */
    public SdkMcpServer find(String name) {
        return servers.get(name);
    }

    /**
     * 返回当前已注册服务器的数量。
     *
     * @return 注册表大小
     */
    public int size() {
        return servers.size();
    }
}
