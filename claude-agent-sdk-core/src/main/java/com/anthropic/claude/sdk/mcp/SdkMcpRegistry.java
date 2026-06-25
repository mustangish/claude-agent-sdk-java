package com.anthropic.claude.sdk.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of in-process SDK MCP servers keyed by name.
 *
 * <p>Used by {@code InternalQuery.handleSdkMcpRequest} to look up which server owns
 * a given JSONRPC method.
 */
public final class SdkMcpRegistry {

    private final Map<String, SdkMcpServer> servers = new HashMap<>();

    public void register(SdkMcpServer server) {
        servers.put(server.name(), server);
    }

    public SdkMcpServer find(String name) {
        return servers.get(name);
    }

    public int size() {
        return servers.size();
    }
}
