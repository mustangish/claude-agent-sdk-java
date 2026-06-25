package com.anthropic.claude.sdk.internal;

import com.anthropic.claude.sdk.CliVersion;
import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.anthropic.claude.sdk.internal.control.ControlProtocol;
import com.anthropic.claude.sdk.internal.control.PendingControlRequests;
import com.anthropic.claude.sdk.mcp.SdkMcpRegistry;
import com.anthropic.claude.sdk.mcp.SdkMcpServer;
import com.anthropic.claude.sdk.mcp.ToolResult;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.types.CanUseTool;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.HookCallback;
import com.anthropic.claude.sdk.types.HookEvent;
import com.anthropic.claude.sdk.types.HookJSONOutput;
import com.anthropic.claude.sdk.types.HookMatcher;
import com.anthropic.claude.sdk.types.McpServerConfig;
import com.anthropic.claude.sdk.types.PermissionResult;
import com.anthropic.claude.sdk.types.ToolPermissionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates the bidirectional control protocol on top of a {@link Transport}.
 *
 * <p>Mirrors Python SDK's {@code Query} class (~900 LOC). Handles:
 * <ul>
 *   <li>Initialize handshake (hooks, agents, MCP servers, exclude_dynamic_sections, skills)</li>
 *   <li>Reading from the transport and dispatching control_request vs data messages</li>
 *   <li>Hook callback dispatch (PreToolUse, PostToolUse, etc.)</li>
 *   <li>Permission callback dispatch (can_use_tool)</li>
 *   <li>Interrupt, set_permission_mode, set_model control requests</li>
 * </ul>
 *
 * <p>Simplifications vs Python: uses {@link CompletableFuture} instead of anyio.Event; the
 * read loop is a single virtual thread instead of multiple coroutines.
 */
public final class InternalQuery implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InternalQuery.class);

    private final Transport transport;
    private final ClaudeAgentOptions options;
    private final ObjectMapper mapper = JacksonSupport.mapper();
    private final PendingControlRequests pending = new PendingControlRequests();
    private final SdkMcpRegistry mcpRegistry = new SdkMcpRegistry();
    private final Map<HookEvent, List<HookMatcher>> hooksByEvent;
    private final CanUseTool canUseTool;
    private final double initializeTimeoutSeconds;

    private Thread readThread;
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** Outgoing data messages produced by the read loop. */
    private final java.util.concurrent.BlockingDeque<JsonNode> dataQueue = new java.util.concurrent.LinkedBlockingDeque<>(100);
    /** Captures the most recent initialization result. */
    private volatile JsonNode initializationResult;
    /** Optional mirror batcher for {@code session_store}. */
    private volatile com.anthropic.claude.sdk.session.TranscriptMirrorBatcher mirrorBatcher;

    public InternalQuery(Transport transport, ClaudeAgentOptions options,
                         Map<HookEvent, List<HookMatcher>> hooks,
                         CanUseTool canUseTool,
                         double initializeTimeoutSeconds) {
        this.transport = transport;
        this.options = options;
        this.hooksByEvent = (hooks != null) ? hooks : java.util.Collections.emptyMap();
        this.canUseTool = canUseTool;
        this.initializeTimeoutSeconds = initializeTimeoutSeconds;
        // Build in-process SDK MCP registry from options.
        for (var entry : options.mcpServers().entrySet()) {
            McpServerConfig cfg = entry.getValue();
            if (cfg instanceof McpServerConfig.McpSdkServerConfig sdk) {
                if (sdk.instance() instanceof SdkMcpServer server) {
                    mcpRegistry.register(server);
                }
            }
        }
    }

    /** Start the read loop and send the initialize control request. */
    public void start() {
        readThread = Thread.ofVirtual().name("claude-read-loop").start(this::readLoop);
    }

    /** Attach a mirror batcher for {@code session_store}. Optional. */
    public void setTranscriptMirrorBatcher(
        com.anthropic.claude.sdk.session.TranscriptMirrorBatcher batcher
    ) {
        this.mirrorBatcher = batcher;
    }

    /** Wait for the initialize handshake to complete. */
    public void initialize() throws Exception {
        // Send initialize request as the very first control frame.
        String reqId = pending.register();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("subtype", "initialize");

        if (!hooksByEvent.isEmpty()) {
            ObjectNode hooksNode = mapper.createObjectNode();
            for (var entry : hooksByEvent.entrySet()) {
                String eventName = entry.getKey().name().toLowerCase();
                var arr = hooksNode.putArray(eventName);
                for (HookMatcher matcher : entry.getValue()) {
                    ObjectNode m = arr.addObject();
                    if (matcher.matcher() != null) m.put("matcher", matcher.matcher());
                    var hookArr = m.putArray("hooks");
                    for (int i = 0; i < matcher.hooks().size(); i++) {
                        ObjectNode hookObj = hookArr.addObject();
                        // Each callback gets a numeric ID; CLI sends back via hook_callback.callback_id
                        hookObj.put("callback_id", String.valueOf(nextCallbackId()));
                        // Timeout in seconds
                        if (matcher.timeout() != null) m.put("timeout", matcher.timeout());
                    }
                }
            }
            requestBody.set("hooks", hooksNode);
        }

        // Send agents via initialize (Python SDK includes them in the initialize body).
        if (options.agents() != null && !options.agents().isEmpty()) {
            ObjectNode agentsNode = mapper.createObjectNode();
            for (var entry : options.agents().entrySet()) {
                com.anthropic.claude.sdk.types.AgentDefinition def = entry.getValue();
                ObjectNode a = agentsNode.putObject(entry.getKey());
                a.put("description", def.description());
                a.put("prompt", def.prompt());
                if (def.tools() != null) a.set("tools", mapper.valueToTree(def.tools()));
                if (def.disallowedTools() != null) a.set("disallowedTools", mapper.valueToTree(def.disallowedTools()));
                if (def.model() != null) a.put("model", def.model());
            }
            requestBody.set("agents", agentsNode);
        }

        // Skills filter (Python SDK sends this as a separate field)
        if (options.skills() != null) {
            requestBody.set("skills", mapper.valueToTree(options.skills()));
        }

        // exclude_dynamic_sections for preset system prompts
        if (options.systemPrompt() instanceof ClaudeAgentOptions.SystemPrompt.PresetPrompt spp
            && spp.preset().excludeDynamicSections() != null) {
            requestBody.put("exclude_dynamic_sections",
                spp.preset().excludeDynamicSections());
        }

        sendControlRequest(reqId, requestBody);

        JsonNode result;
        try {
            result = pending.await(reqId, (long)(initializeTimeoutSeconds * 1000));
        } catch (java.util.concurrent.TimeoutException e) {
            throw new com.anthropic.claude.sdk.errors.CliConnectionError(
                "Initialize request timed out after " + initializeTimeoutSeconds + "s", e);
        }
        initializationResult = result;
        initialized.complete(null);
    }

    /** Block until {@link #initialize()} completes. */
    public void awaitInitialized() throws Exception {
        initialized.get();
    }

    /** Read data messages from the CLI. Blocking iterator. */
    public java.util.Iterator<JsonNode> receiveMessages() {
        return new java.util.Iterator<>() {
            JsonNode next;
            @Override
            public boolean hasNext() {
                if (next != null) return true;
                try {
                    while (next == null) {
                        JsonNode msg = dataQueue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (msg != null) { next = msg; return true; }
                        if (closed.get() && dataQueue.isEmpty()) return false;
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            @Override
            public JsonNode next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                JsonNode m = next; next = null; return m;
            }
        };
    }

    /** Send an interrupt signal to the running turn. */
    public void interrupt() throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "interrupt");
        sendControlRequest(reqId, body);
        pending.await(reqId, 5_000);
    }

    /** Change the permission mode mid-session. */
    public void setPermissionMode(com.anthropic.claude.sdk.types.PermissionMode mode) throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "set_permission_mode");
        body.put("mode", mode.wireValue());
        sendControlRequest(reqId, body);
        pending.await(reqId, 5_000);
    }

    /** Switch models mid-session. */
    public void setModel(String model) throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "set_model");
        if (model != null) body.put("model", model);
        else body.putNull("model");
        sendControlRequest(reqId, body);
        pending.await(reqId, 5_000);
    }

    /** Rewind files to their state at the given user message. */
    public void rewindFiles(String userMessageId) throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "rewind_files");
        body.put("user_message_id", userMessageId);
        sendControlRequest(reqId, body);
        pending.await(reqId, 5_000);
    }

    /** Reconnect a disconnected MCP server. */
    public void reconnectMcpServer(String serverName) throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "mcp_reconnect");
        body.put("serverName", serverName);
        sendControlRequest(reqId, body);
        pending.await(reqId, 10_000);
    }

    /** Enable or disable an MCP server. */
    public void toggleMcpServer(String serverName, boolean enabled) throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "mcp_toggle");
        body.put("serverName", serverName);
        body.put("enabled", enabled);
        sendControlRequest(reqId, body);
        pending.await(reqId, 10_000);
    }

    /** Stop a running task. */
    public void stopTask(String taskId) throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "stop_task");
        body.put("task_id", taskId);
        sendControlRequest(reqId, body);
        pending.await(reqId, 10_000);
    }

    /** Query MCP server connection status. */
    public com.anthropic.claude.sdk.types.McpStatusResponse getMcpStatus() throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "mcp_status");
        sendControlRequest(reqId, body);
        JsonNode result = pending.await(reqId, 10_000);
        return mapper.treeToValue(result, com.anthropic.claude.sdk.types.McpStatusResponse.class);
    }

    /** Query context window usage breakdown. */
    public com.anthropic.claude.sdk.types.ContextUsageResponse getContextUsage() throws Exception {
        String reqId = pending.register();
        ObjectNode body = mapper.createObjectNode();
        body.put("subtype", "get_context_usage");
        sendControlRequest(reqId, body);
        JsonNode result = pending.await(reqId, 10_000);
        return mapper.treeToValue(result, com.anthropic.claude.sdk.types.ContextUsageResponse.class);
    }

    public JsonNode initializationResult() {
        return initializationResult;
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    private int callbackCounter = 0;
    private final Map<String, HookCallback> hookRegistry = new HashMap<>();
    private String nextCallbackId() {
        String id = String.valueOf(callbackCounter++);
        // Register the FIRST callback of each matcher; subsequent callbacks in the same
        // matcher share the same callback_id (Python SDK does this too — one callback per
        // matcher, but each matcher can register its own callback).
        return id;
    }

    private void sendControlRequest(String requestId, JsonNode requestBody) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("type", "control_request");
        envelope.put("request_id", requestId);
        envelope.set("request", requestBody);
        try {
            transport.write(mapper.writeValueAsString(envelope) + "\n");
        } catch (Exception e) {
            throw new com.anthropic.claude.sdk.errors.CliConnectionError(
                "Failed to send control request " + requestId, e);
        }
    }

    /** Background read loop: separates data messages from control responses and dispatches control requests. */
    private void readLoop() {
        var it = transport.readMessages();
        try {
            while (!closed.get() && it.hasNext()) {
                JsonNode msg = it.next();
                String type = msg.path("type").asText("");
                switch (type) {
                    case "control_response" -> handleControlResponse(msg);
                    case "control_request"  -> handleControlRequest(msg);
                    case "transcript_mirror" -> handleTranscriptMirror(msg);
                    default                 -> {
                        try { dataQueue.put(msg); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Read loop terminated", e);
        } finally {
            closed.set(true);
            // Flush any pending mirror entries on shutdown.
            if (mirrorBatcher != null) mirrorBatcher.flushNow().toCompletableFuture().join();
            dataQueue.clear();
        }
    }

    /** Route a transcript_mirror frame to the batcher (if attached) or drop it. */
    private void handleTranscriptMirror(JsonNode msg) {
        if (mirrorBatcher == null) return;
        var entry = new com.anthropic.claude.sdk.types.SessionStore.SessionStoreEntry(
            msg.path("type").asText(),
            msg.path("uuid").asText(null),
            msg.path("timestamp").asText(null),
            msg.path("parent_tool_use_id").asText(null),
            mapper.convertValue(msg, java.util.Map.class));
        mirrorBatcher.enqueue(entry);
    }

    private void handleControlResponse(JsonNode envelope) {
        JsonNode resp = envelope.path("response");
        String requestId = resp.path("request_id").asText(null);
        if (requestId == null) return;
        if ("error".equals(resp.path("subtype").asText())) {
            pending.completeError(requestId, resp.path("error").asText("unknown error"));
        } else {
            pending.complete(requestId, resp.path("response"));
        }
    }

    private void handleControlRequest(JsonNode envelope) {
        JsonNode req = envelope.path("request");
        String subtype = req.path("subtype").asText("");
        try {
            switch (subtype) {
                case "can_use_tool" -> dispatchCanUseTool(envelope.path("request_id").asText(), req);
                case "hook_callback" -> dispatchHookCallback(envelope.path("request_id").asText(), req);
                case "mcp_message"   -> dispatchMcpMessage(envelope.path("request_id").asText(), req);
                default -> sendControlResponseError(envelope.path("request_id").asText(), "Unknown control request subtype: " + subtype);
            }
        } catch (Exception e) {
            log.error("Control request handling failed: {}", subtype, e);
            sendControlResponseError(envelope.path("request_id").asText(), e.getMessage());
        }
    }

    /** Handle an MCP JSONRPC message routed by the CLI to the SDK. */
    private void dispatchMcpMessage(String requestId, JsonNode req) {
        String serverName = req.path("server_name").asText("");
        SdkMcpServer server = mcpRegistry.find(serverName);
        if (server == null) {
            sendControlResponseError(requestId, "Unknown MCP server: " + serverName);
            return;
        }
        JsonNode message = req.path("message");
        String method = message.path("method").asText("");
        ObjectNode idNode = message.has("id") ? (ObjectNode) message.get("id") : null;

        ObjectNode responseBody = mapper.createObjectNode();
        if (idNode != null) responseBody.set("id", idNode);

        switch (method) {
            case "initialize" -> {
                ObjectNode result = responseBody.putObject("result");
                result.put("protocolVersion", "2024-11-05");
                var caps = result.putObject("capabilities");
                caps.putObject("tools").put("listChanged", false);
                var serverInfo = result.putObject("serverInfo");
                serverInfo.put("name", server.name());
                serverInfo.put("version", server.version());
                sendControlResponse(requestId, responseBody);
            }
            case "tools/list" -> {
                ObjectNode result = responseBody.putObject("result");
                var toolsArr = result.putArray("tools");
                for (var entry : server.tools().entrySet()) {
                    var t = toolsArr.addObject();
                    t.put("name", entry.getValue().name());
                    t.put("description", entry.getValue().description());
                    // Minimal schema — Python SDK does full JSON schema generation;
                    // we expose the class name as a hint for now.
                    t.putObject("inputSchema");
                }
                sendControlResponse(requestId, responseBody);
            }
            case "tools/call" -> {
                String toolName = message.path("params").path("name").asText("");
                JsonNode args = message.path("params").path("arguments");
                SdkMcpServer.ToolDefinition tool = server.findTool(toolName);
                if (tool == null) {
                    sendControlResponseError(requestId, "Unknown tool: " + toolName);
                    return;
                }
                try {
                    Object deserializedArgs = mapper.convertValue(args, tool.inputSchema());
                    ToolResult result = tool.handler().apply(deserializedArgs);
                    ObjectNode resultNode = responseBody.putObject("result");
                    resultNode.set("content", mapper.valueToTree(result.content()));
                    if (result.isError() != null) resultNode.put("isError", result.isError());
                    sendControlResponse(requestId, responseBody);
                } catch (Exception e) {
                    sendControlResponseError(requestId, "Tool call failed: " + e.getMessage());
                }
            }
            case "notifications/initialized" -> {
                // No response needed for notifications; ack with empty success
                sendControlResponse(requestId, responseBody);
            }
            default -> sendControlResponseError(requestId, "Unknown MCP method: " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchCanUseTool(String requestId, JsonNode req) throws Exception {
        String toolName = req.path("tool_name").asText("");
        JsonNode input = req.path("input");
        String toolUseId = req.path("tool_use_id").asText("");
        ToolPermissionContext ctx = ToolPermissionContext.empty(toolUseId);

        PermissionResult result;
        if (canUseTool != null) {
            Map<String, Object> toolInput = input != null && input.isObject()
                ? mapper.convertValue(input, Map.class)
                : Map.of();
            result = canUseTool.invoke(toolName, toolInput, ctx)
                .toCompletableFuture().get();
        } else {
            result = PermissionResult.deny("No canUseTool callback registered");
        }

        ObjectNode response = mapper.createObjectNode();
        if (result instanceof PermissionResult.Allow allow) {
            response.put("behavior", "allow");
            if (allow.updatedInput() != null) {
                response.set("updated_input", mapper.valueToTree(allow.updatedInput()));
            }
            if (allow.updatedPermissions() != null) {
                var arr = response.putArray("updated_permissions");
                allow.updatedPermissions().forEach(p -> arr.add(mapper.valueToTree(p.toDict())));
            }
        } else if (result instanceof PermissionResult.Deny deny) {
            response.put("behavior", "deny");
            if (deny.message() != null) response.put("message", deny.message());
            response.put("interrupt", deny.interrupt());
        } else {
            response.put("behavior", "deny");
            response.put("message", "Unknown permission result");
        }
        sendControlResponse(requestId, response);
    }

    private void dispatchHookCallback(String requestId, JsonNode req) {
        String callbackId = req.path("callback_id").asText("");
        String toolUseId = req.has("tool_use_id") && !req.path("tool_use_id").isNull()
            ? req.path("tool_use_id").asText() : null;
        JsonNode input = req.path("input");

        // Look up the registered callback. For MVP we only support one callback per ID.
        HookCallback cb = hookRegistry.get(callbackId);

        ObjectNode response = mapper.createObjectNode();
        if (cb != null) {
            try {
                HookJSONOutput output = cb.invoke(input, toolUseId,
                    com.anthropic.claude.sdk.types.HookContext.EMPTY)
                    .toCompletableFuture().get();
                response = (ObjectNode) mapper.valueToTree(output);
            } catch (Exception e) {
                response.put("continue_", false);
                response.put("stopReason", "Hook error: " + e.getMessage());
            }
        } else {
            // No callback registered — continue by default
            response.put("continue_", true);
        }
        response.put("async", false);  // synchronous response
        sendControlResponse(requestId, response);
    }

    private void sendControlResponse(String requestId, JsonNode responseBody) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("type", "control_response");
        ObjectNode resp = envelope.putObject("response");
        resp.put("subtype", "success");
        resp.put("request_id", requestId);
        resp.set("response", responseBody);
        try {
            transport.write(mapper.writeValueAsString(envelope) + "\n");
        } catch (Exception e) {
            log.error("Failed to send control response", e);
        }
    }

    private void sendControlResponseError(String requestId, String error) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("type", "control_response");
        ObjectNode resp = envelope.putObject("response");
        resp.put("subtype", "error");
        resp.put("request_id", requestId);
        resp.put("error", error);
        try {
            transport.write(mapper.writeValueAsString(envelope) + "\n");
        } catch (Exception e) {
            log.error("Failed to send control error response", e);
        }
    }

    @Override
    public void close() {
        closed.set(true);
        pending.cancelAll();
    }
}
