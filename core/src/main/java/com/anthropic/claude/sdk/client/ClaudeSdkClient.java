package com.anthropic.claude.sdk.client;

import com.anthropic.claude.sdk.errors.CliConnectionError;
import com.anthropic.claude.sdk.internal.InternalQuery;
import com.anthropic.claude.sdk.internal.message.MessageParser;
import com.anthropic.claude.sdk.transport.Transport;
import com.anthropic.claude.sdk.transport.subprocess.SubprocessCliTransport;
import com.anthropic.claude.sdk.types.ClaudeAgentOptions;
import com.anthropic.claude.sdk.types.HookEvent;
import com.anthropic.claude.sdk.types.HookMatcher;
import com.anthropic.claude.sdk.types.Message;
import com.anthropic.claude.sdk.types.PermissionMode;
import com.anthropic.claude.sdk.types.ResultMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive, bidirectional client for Claude Code.
 *
 * <p>Supports multi-turn conversations with the CLI subprocess kept open between messages.
 * Uses {@link InternalQuery} for the control protocol (hooks, can_use_tool, interrupt,
 * set_permission_mode, set_model).
 */
public final class ClaudeSdkClient implements AutoCloseable {

    private final ClaudeAgentOptions options;
    private final Transport customTransport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MessageParser parser;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Transport transport;
    private InternalQuery query;

    public ClaudeSdkClient(ClaudeAgentOptions options) {
        this(options, null);
    }

    public ClaudeSdkClient(ClaudeAgentOptions options, Transport customTransport) {
        this.options = options != null ? options : ClaudeAgentOptions.builder().build();
        this.customTransport = customTransport;
        this.parser = new MessageParser(mapper);
    }

    /** Open the CLI subprocess and run the initialize handshake. */
    public void connect() {
        if (connected.get()) return;
        if (closed.get()) throw new IllegalStateException("Client is closed");

        if (customTransport != null) {
            this.transport = customTransport;
        } else {
            this.transport = new SubprocessCliTransport(options);
        }
        this.transport.connect();

        // Convert hooks from ClaudeAgentOptions.HookMatcher to internal Map<HookEvent, List<HookMatcher>>
        Map<HookEvent, List<HookMatcher>> hooks = options.hooks();

        this.query = new InternalQuery(
            transport,
            options,
            hooks,
            options.canUseTool(),
            60.0
        );
        this.query.start();
        try {
            this.query.initialize();
        } catch (Exception e) {
            // Initialize failed — clean up subprocess.
            this.query.close();
            this.transport.close();
            throw new CliConnectionError("Initialize handshake failed", e);
        }
        connected.set(true);
    }

    /** Send a prompt. */
    public void query(String prompt) {
        ensureConnected();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "user");
        message.put("message", Map.of("role", "user", "content", prompt));
        message.put("parent_tool_use_id", null);
        message.put("session_id", options.sessionId() != null ? options.sessionId() : "default");
        try {
            transport.write(mapper.writeValueAsString(message) + "\n");
        } catch (JsonProcessingException e) {
            throw new CliConnectionError("Failed to serialize prompt", e);
        }
    }

    /** Stream messages from the CLI. */
    public Iterator<Message> receiveMessages() {
        ensureConnected();
        Iterator<JsonNode> raw = query.receiveMessages();
        return new Iterator<>() {
            Message next;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (raw.hasNext()) {
                    Message parsed = parser.parse(raw.next());
                    if (parsed != null) {
                        next = parsed;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Message next() {
                if (!hasNext()) throw new NoSuchElementException();
                Message m = next;
                next = null;
                return m;
            }
        };
    }

    /** Stream messages until a {@link ResultMessage} is reached, then stop. */
    public Iterator<Message> receiveResponse() {
        return new Iterator<>() {
            final Iterator<Message> source = receiveMessages();
            Message next;
            boolean done = false;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                if (done) return false;
                while (source.hasNext()) {
                    Message m = source.next();
                    if (m instanceof ResultMessage) {
                        next = m;
                        done = true;
                        return true;
                    }
                    next = m;
                    return true;
                }
                done = true;
                return false;
            }

            @Override
            public Message next() {
                if (!hasNext()) throw new NoSuchElementException();
                Message m = next;
                next = null;
                return m;
            }
        };
    }

    /** Send an interrupt signal to the running turn. */
    public void interrupt() {
        ensureConnected();
        try {
            query.interrupt();
        } catch (Exception e) {
            throw new CliConnectionError("Interrupt failed", e);
        }
    }

    /** Change permission mode mid-session. */
    public void setPermissionMode(PermissionMode mode) {
        ensureConnected();
        try {
            query.setPermissionMode(mode);
        } catch (Exception e) {
            throw new CliConnectionError("set_permission_mode failed", e);
        }
    }

    /** Switch models mid-session. */
    public void setModel(String model) {
        ensureConnected();
        try {
            query.setModel(model);
        } catch (Exception e) {
            throw new CliConnectionError("set_model failed", e);
        }
    }

    /** Disconnect from the CLI. Safe to call multiple times. */
    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            try {
                if (query != null) query.close();
            } finally {
                try {
                    if (transport != null) transport.close();
                } finally {
                    transport = null;
                    query = null;
                    closed.set(true);
                }
            }
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    private void ensureConnected() {
        if (!connected.get()) throw new CliConnectionError("Not connected. Call connect() first.");
    }
}
