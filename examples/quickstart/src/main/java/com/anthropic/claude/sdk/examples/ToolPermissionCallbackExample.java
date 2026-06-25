package com.anthropic.claude.sdk.examples;

import com.anthropic.claude.sdk.client.ClaudeSdkClient;
import com.anthropic.claude.sdk.types.CanUseTool;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.PermissionResult;

import java.util.Iterator;
import java.util.List;

/** Equivalent of Python's examples/tool_permission_callback.py */
public class ToolPermissionCallbackExample {
    public static void main(String[] args) {
        // Allow Read, deny Write
        CanUseTool handler = CanUseTool.sync(req -> {
            if ("Read".equals(req.toolName())) return PermissionResult.allow();
            return PermissionResult.deny("Only Read is allowed");
        });

        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .canUseTool(handler)
            .allowedTools(List.of("Read", "Write"))  // both listed, canUseTool decides
            .build();

        try (ClaudeSdkClient client = new ClaudeSdkClient(options)) {
            client.connect();
            client.query("Write 'hello' to /tmp/test.txt");

            Iterator<Message> iter = client.receiveResponse();
            while (iter.hasNext()) System.out.println(iter.next());
        }
    }
}
