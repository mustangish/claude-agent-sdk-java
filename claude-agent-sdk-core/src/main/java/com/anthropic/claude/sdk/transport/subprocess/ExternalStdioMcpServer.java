package com.anthropic.claude.sdk.transport.subprocess;

import com.anthropic.claude.sdk.internal.JacksonSupport;
import com.anthropic.claude.sdk.types.McpServerConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Helper that manages an external stdio MCP server subprocess and proxies JSONRPC messages
 * between the SDK and the server.
 *
 * <p>Mirrors Python SDK's stdio MCP support. SSE/HTTP variants are not ported yet (Java MCP SDK
 * would be the natural choice for those — see Phase 11 notes).
 */
public final class ExternalStdioMcpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExternalStdioMcpServer.class);

    private final String serverName;
    private final Process process;
    private final BufferedReader stdoutReader;
    private final Writer stdinWriter;
    private final BlockingDeque<JsonNode> responseQueue = new LinkedBlockingDeque<>(100);
    private final ObjectMapper mapper = JacksonSupport.mapper();
    private final Thread readerThread;
    private volatile boolean closed = false;

    public ExternalStdioMcpServer(String serverName, McpServerConfig.McpStdioServerConfig config) throws IOException {
        this.serverName = serverName;
        ProcessBuilder pb = new ProcessBuilder(buildCommand(config))
            .redirectErrorStream(false);
        this.process = pb.start();
        ProcessRegistry.register(process);
        this.stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stdinWriter = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        this.readerThread = Thread.ofVirtual().name("mcp-stdio-reader-" + serverName).start(this::readLoop);
    }

    private static List<String> buildCommand(McpServerConfig.McpStdioServerConfig config) {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.command());
        if (config.args() != null) cmd.addAll(config.args());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (config.env() != null) pb.environment().putAll(config.env());
        // We need the actual command list back
        return cmd;
    }

    /** Send a JSONRPC request to the server and wait for the response (matched by id). */
    public JsonNode sendRequest(JsonNode message, long timeoutMs) throws Exception {
        if (closed) throw new IllegalStateException("MCP server " + serverName + " is closed");
        Object msgWithJsonrpc = mapper.convertValue(message, Object.class);
        // Add jsonrpc field if missing
        if (msgWithJsonrpc instanceof java.util.Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) map;
            if (!m.containsKey("jsonrpc")) m.put("jsonrpc", "2.0");
        }
        synchronized (stdinWriter) {
            stdinWriter.write(mapper.writeValueAsString(msgWithJsonrpc) + "\n");
            stdinWriter.flush();
        }
        // Match response by id
        Object id = mapper.convertValue(message.path("id"), Object.class);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            JsonNode resp = responseQueue.poll(100, TimeUnit.MILLISECONDS);
            if (resp == null) continue;
            Object respId = mapper.convertValue(resp.path("id"), Object.class);
            if (java.util.Objects.equals(id, respId)) return resp;
            // Not ours — put back at the front
            responseQueue.addFirst(resp);
        }
        throw new java.util.concurrent.TimeoutException("MCP request timed out");
    }

    private void readLoop() {
        try {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                try {
                    JsonNode msg = mapper.readTree(line);
                    if (msg.has("id")) {
                        // It's a response — enqueue
                        responseQueue.put(msg);
                    } else {
                        // It's a notification or request from server — log and ignore (MVP)
                        log.debug("MCP server {} sent notification: {}", serverName, msg);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse MCP message from {}", serverName, e);
                }
            }
        } catch (IOException e) {
            log.debug("MCP server {} stdout closed", serverName, e);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (stdinWriter != null) stdinWriter.close();
        } catch (IOException ignored) {}
        if (readerThread != null) {
            readerThread.interrupt();
            try { readerThread.join(500); } catch (InterruptedException ignored) {}
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException ignored) {}
        }
        ProcessRegistry.unregister(process);
    }
}
